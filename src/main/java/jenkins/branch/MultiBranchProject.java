/*
 * The MIT License
 *
 * Copyright (c) 2011-2017, CloudBees, Inc., Stephen Connolly.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.branch;

import com.cloudbees.hudson.plugins.folder.FolderIcon;
import com.cloudbees.hudson.plugins.folder.computed.ChildObserver;
import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;
import com.cloudbees.hudson.plugins.folder.views.AbstractFolderViewHolder;
import com.google.common.collect.ImmutableSet;
import com.thoughtworks.xstream.XStreamException;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.Util;
import hudson.XmlFile;
import hudson.console.ModelHyperlinkNote;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.listeners.SaveableListener;
import hudson.scm.PollingResult;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.util.LogTaskListener;
import hudson.util.PersistedList;
import hudson.util.io.ReopenableRotatingFileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.TransientActionFactory;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMEventListener;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceEvent;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.mixin.TagSCMHead;
import jenkins.scm.impl.NullSCMSource;
import jenkins.triggers.SCMTriggerItem;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Abstract base class for multiple-branch based projects.
 *
 * @param <P> the project type
 * @param <R> the run type
 */
public abstract class MultiBranchProject<P extends Job<P, R> & TopLevelItem,
        R extends Run<P, R>>
        extends ComputedFolder<P> implements SCMSourceOwner, IconSpec {

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(MultiBranchProject.class.getName());

    /**
     * The user supplied branch sources.
     */
    private /*almost final*/ PersistedList<BranchSource> sources = new BranchSourceList(this);

    /**
     * The persisted state maintained outside of the config file.
     *
     * @since 2.0
     */
    private transient /*almost final*/ State state = new State(this);

    /**
     * The source for dead branches.
     */
    private transient /*almost final*/ NullSCMSource nullSCMSource;

    /**
     * The factory for building child job instances.
     */
    private BranchProjectFactory<P, R> factory;

    private transient String srcDigest, facDigest;

    /**
     * Work-around for JENKINS-41121 issues.
     */
    private transient Set<String> legacySourceIds;

    /**
     * Constructor, mandated by {@link TopLevelItem}.
     *
     * @param parent the parent of this multibranch job.
     * @param name   the name of the multibranch job.
     */
    protected MultiBranchProject(ItemGroup parent, String name) {
        super(parent, name);
        init2();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        init2();
        // migrate any non-mangled names
        ArrayList<P> items = new ArrayList<>(getItems());
        BranchProjectFactory<P, R> projectFactory = getProjectFactory();
        for (P item : items) {
            if (projectFactory.isProject(item)) {
                String itemName = item.getName();
                Branch branch = projectFactory.getBranch(item);
                String mangledName = branch.getEncodedName();
                if (!itemName.equals(mangledName)) {
                    if (super.getItem(mangledName) == null) {
                        LOGGER.log(Level.INFO, "Non-mangled name detected for branch {0}. Renaming {1}/{2} to {1}/{3}",
                                new Object[]{branch.getName(), getFullName(), itemName, mangledName});
                        item.renameTo(mangledName);
                        if (item.getDisplayNameOrNull() == null) {
                            item.setDisplayName(branch.getName());
                            item.save();
                        }
                    } // else will be removed by the orphaned item strategy on next scan
                }
            }
        }
        try {
            srcDigest = Util.getDigestOf(Items.XSTREAM2.toXML(sources));
        } catch (XStreamException e) {
            srcDigest = null;
        }
        try {
            facDigest = Util.getDigestOf(Items.XSTREAM2.toXML(projectFactory));
        } catch (XStreamException e) {
            facDigest = null;
        }
        if (state == null) {
            state = new State(this);
        }
        if (new File(getRootDir(), ".jenkins-41121").isFile()) {
            legacySourceIds = new HashSet<>(FileUtils.readLines(new File(getRootDir(), ".jenkins-41121")));
        } else if (state.getStateFile().exists()) {
            legacySourceIds = null;
        } else {
            legacySourceIds = new HashSet<>();
            for (BranchSource source : sources) {
                legacySourceIds.add(source.getSource().getId());
            }
            // store the IDs across restarts until we have a full run
            FileUtils.writeLines(new File(getRootDir(), ".jenkins-41121"), legacySourceIds);
        }
        try {
            state.load();
        } catch (XStreamException | IOException e) {
            LOGGER.log(Level.WARNING, "Could not read persisted state, will be recovered on next index.", e);
            state.reset();
        }
    }

    /**
     * Consolidated initialization code.
     */
    private synchronized void init2() {
        if (sources == null) {
            sources = new PersistedList<BranchSource>(this);
        }
        if (nullSCMSource == null) {
            nullSCMSource = new NullSCMSource();
        }
        nullSCMSource.setOwner(this);
        for (SCMSource source : getSCMSources()) {
            source.setOwner(this);
        }
        final BranchProjectFactory<P, R> factory = getProjectFactory();
        factory.setOwner(this);
        if (!(getFolderViews() instanceof MultiBranchProjectViewHolder)) {
            resetFolderViews();
        }
        if (!(getIcon() instanceof MetadataActionFolderIcon)) {
            setIcon(newDefaultFolderIcon());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected AbstractFolderViewHolder newFolderViewHolder() {
        return new MultiBranchProjectViewHolder(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected FolderIcon newDefaultFolderIcon() {
        return new MetadataActionFolderIcon();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIconClassName() {
        String result;
        if (sources.size() == 1) {
            result = sources.get(0).getSource().getDescriptor().getIconClassName();
        } else {
            result = null;
            for (int i = 0; i < sources.size(); i++) {
                String iconClassName = sources.get(i).getSource().getDescriptor().getIconClassName();
                if (i == 0) {
                    result = iconClassName;
                } else if (!StringUtils.equals(result, iconClassName)) {
                    result = null;
                    break;
                }
            }
        }

        return result != null ? result : getDescriptor().getIconClassName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPronoun() {
        Set<String> result = new TreeSet<>();
        for (BranchSource source : sources) {
            String pronoun = Util.fixEmptyAndTrim(source.getSource().getPronoun());
            if (pronoun != null) {
                result.add(pronoun);
            }
        }
        return result.isEmpty() ? super.getPronoun() : StringUtils.join(result, " / ");
    }

    /**
     * Returns the {@link BranchProjectFactory}.                                                          ˜
     *
     * @return the {@link BranchProjectFactory}.
     */
    @NonNull
    public synchronized BranchProjectFactory<P, R> getProjectFactory() {
        if (factory == null) {
            setProjectFactory(newProjectFactory());
        }
        return factory;
    }

    /**
     * Returns the base class of the projects that are managed by this {@link MultiBranchProject}.
     *
     * @return the base class of the projects that are managed by this {@link MultiBranchProject}.
     * @since 2.0
     */
    @SuppressWarnings("unchecked")
    public final Class<P> getProjectClass() {
        return (Class<P>) getDescriptor().getProjectClass();
    }

    /**
     * Sets the {@link BranchProjectFactory}.
     *
     * @param projectFactory the new {@link BranchProjectFactory}.
     */
    public synchronized void setProjectFactory(BranchProjectFactory<P, R> projectFactory) {
        projectFactory.getClass(); // throw NPE if null
        if (factory == projectFactory) {
            return;
        }
        if (factory != null) {
            factory.setOwner(null);
        }
        factory = projectFactory;
        factory.setOwner(this);
    }

    /**
     * Creates a new instance of the default project factory to be used for a new instance of the project type.
     *
     * @return a new default {@link BranchProjectFactory}.
     */
    @NonNull
    protected abstract BranchProjectFactory<P, R> newProjectFactory();

    /**
     * The sources of branches.
     *
     * @return the sources of branches.
     */
    @NonNull
    public List<BranchSource> getSources() {
        if (sources != null) {
            return sources.toList();
        } else {
            // return empty, this object is still being constructed
            return Collections.emptyList();
        }
    }

    /**
     * Offers direct access to the configurable list of branch sources.
     * Intended for use from scripting and testing.
     *
     * @return the sources list.
     */
    @NonNull
    public PersistedList<BranchSource> getSourcesList() {
        return sources;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public List<SCMSource> getSCMSources() {
        List<SCMSource> result = new ArrayList<SCMSource>();
        if (sources != null) {
            for (BranchSource source : sources) {
                result.add(source.getSource());
            }
        } // else, ok to ignore and return empty, this object is still being constructed
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    public SCMSource getSCMSource(@CheckForNull String sourceId) {
        for (SCMSource source : getSCMSources()) {
            if (source.getId().equals(sourceId)) {
                return source;
            }
        }
        return nullSCMSource;
    }

    /**
     * Returns the {@link BranchPropertyStrategy} for a specific {@link SCMSource}.
     *
     * @param source the specific {@link SCMSource}.
     * @return the {@link BranchPropertyStrategy} to use.
     */
    @CheckForNull
    public BranchPropertyStrategy getBranchPropertyStrategy(@NonNull SCMSource source) {
        for (BranchSource s : getSources()) {
            if (s.getSource().equals(source)) {
                return s.getStrategy();
            }
        }
        return null;
    }

    /**
     * Creates a {@link Branch} for a specific {@link SCMSource} and {@link SCMHead}.
     *
     * @param source the {@link SCMSource}
     * @param head   the {@link SCMHead}.
     * @return the {@link Branch}
     */
    @NonNull
    private Branch newBranch(@NonNull SCMSource source, @NonNull SCMHead head) {
        source.getClass(); // throw NPE if null
        head.getClass(); // throw NPE if null

        String sourceId = source.getId();
        if (NullSCMSource.ID.equals(sourceId)) {
            return new Branch.Dead(head, Collections.<BranchProperty>emptyList());
        } else {
            final BranchPropertyStrategy strategy = getBranchPropertyStrategy(source);
            return new Branch(sourceId, head, source.build(head),
                    strategy != null ? strategy.getPropertiesFor(head) : Collections.<BranchProperty>emptyList());
        }
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    public SCMSourceCriteria getSCMSourceCriteria(@NonNull SCMSource source) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public void onSCMSourceUpdated(@NonNull SCMSource source) {
        scheduleBuild(0, new BranchIndexingCause());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isHasEvents() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void computeChildren(final ChildObserver<P> observer, final TaskListener listener)
            throws IOException, InterruptedException {
        // capture the current digests to prevent unnecessary reindex if re-saving after index
        try {
            srcDigest = Util.getDigestOf(Items.XSTREAM2.toXML(sources));
        } catch (XStreamException e) {
            srcDigest = null;
        }
        try {
            facDigest = Util.getDigestOf(Items.XSTREAM2.toXML(getProjectFactory()));
        } catch (XStreamException e) {
            facDigest = null;
        }
        long start = System.currentTimeMillis();
        listener.getLogger().format("[%tc] Starting branch indexing...%n", start);
        try {
            final BranchProjectFactory<P, R> _factory = getProjectFactory();
            List<SCMSource> scmSources = getSCMSources();
            Map<String, List<Action>> sourceActions = new LinkedHashMap<>();
            for (SCMSource source : scmSources) {
                try {
                    sourceActions.put(source.getId(), source.fetchActions(null, listener));
                } catch (IOException | InterruptedException | RuntimeException e) {
                    e.printStackTrace(listener.error("[%tc] Could not update folder level actions from source %s",
                            System.currentTimeMillis(), source.getId()));
                    throw e;
                }
            }
            // update any persistent actions for the SCMSource
            if (!sourceActions.equals(state.sourceActions)) {
                boolean saveProject = false;
                for (List<Action> actions : sourceActions.values()) {
                    for (Action a : actions) {
                        // undo any hacks that attached the contributed actions without attribution
                        saveProject = removeActions(a.getClass()) || saveProject;
                    }
                }
                BulkChange bc = new BulkChange(state);
                try {
                    state.sourceActions.keySet().retainAll(sourceActions.keySet());
                    state.sourceActions.putAll(sourceActions);
                    try {
                        bc.commit();
                    } catch (IOException | RuntimeException e) {
                        e.printStackTrace(listener.error("[%tc] Could not persist folder level actions",
                                System.currentTimeMillis()));
                        throw e;
                    }
                    if (saveProject) {
                        try {
                            save();
                        } catch (IOException | RuntimeException e) {
                            e.printStackTrace(listener.error(
                                    "[%tc] Could not persist folder level configuration changes",
                                    System.currentTimeMillis()));
                            throw e;
                        }
                    }
                } finally {
                    bc.abort();
                }
            }
            for (final SCMSource source : scmSources) {
                try {
                    source.fetch(new SCMHeadObserverImpl(source, observer, listener, _factory,
                            new IndexingCauseFactory(), null), listener);
                } catch (IOException | InterruptedException | RuntimeException e) {
                    e.printStackTrace(listener.error("[%tc] Could not fetch branches from source %s",
                            System.currentTimeMillis(), source.getId()));
                    throw e;
                }
            }
            // JENKINS-41121 we have done the first full scan, all branches correctly attributed now
            if (legacySourceIds != null) {
                FileUtils.deleteQuietly(new File(getRootDir(), ".jenkins-41121"));
                legacySourceIds = null;
            }
        } finally {
            long end = System.currentTimeMillis();
            listener.getLogger().format("[%tc] Finished branch indexing. Indexing took %s%n", end,
                    Util.getTimeSpanString(end - start));
        }
    }

    private void scheduleBuild(BranchProjectFactory<P, R> factory, final P item, SCMRevision revision,
                               TaskListener listener, String name, Cause[] cause, Action... actions) {
        Action[] _actions = new Action[actions.length + 1];
        _actions[0] = new CauseAction(cause);
        System.arraycopy(actions, 0, _actions, 1, actions.length);
        if (ParameterizedJobMixIn.scheduleBuild2(item, 0, _actions) != null) {
            listener.getLogger().println("Scheduled build for branch: " + name);
            try {
                factory.setRevisionHash(item, revision);
            } catch (IOException e) {
                e.printStackTrace(listener.error("Could not update last revision hash"));
            }
        } else {
            listener.getLogger().println("Did not schedule build for branch: " + name);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Collection<P> orphanedItems(Collection<P> orphaned, TaskListener listener)
            throws IOException, InterruptedException {
        BranchProjectFactory<P, R> _factory = getProjectFactory();
        for (P project : orphaned) {
            if (!_factory.isProject(project)) {
                listener.getLogger().println("Detected unsupported subitem " + project + ", skipping");
                continue; // TODO perhaps better to remove from list passed to super, and return it from here
            }
            Branch b = _factory.getBranch(project);
            if (!(b instanceof Branch.Dead)) {
                _factory.decorate(
                        _factory.setBranch(project, new Branch.Dead(b)));
            }
        }
        return super.orphanedItems(orphaned, listener);
    }

    /**
     * Returns the named child job or {@code null} if no such job exists.
     *
     * @param name the name of the child job.
     * @return the named child job or {@code null} if no such job exists.
     */
    @Override
    @CheckForNull
    public P getItem(String name) {
        if (name == null) {
            return null;
        }
        if (name.indexOf('%') != -1) {
            String decoded = rawDecode(name);
            P item = super.getItem(decoded);
            if (item != null) {
                return item;
            }
            item = super.getItem(NameMangler.apply(decoded));
            if (item != null) {
                return item;
            }
            // fall through for double decoded call paths
        }
        P item = super.getItem(name);
        if (item != null) {
            return item;
        }
        return super.getItem(NameMangler.apply(name));
    }

    /**
     * Returns the child job with the specified branch name or {@code null} if no such child job exists.
     *
     * @param branchName the name of the branch.
     * @return the child job or {@code null} if no such job exists or if the requesting user does ave permission to
     * view it.
     * @since 2.0.0
     */
    @CheckForNull
    public P getItemByBranchName(@NonNull String branchName) {
        BranchProjectFactory<P, R> factory = getProjectFactory();
        P item = getItem(NameMangler.apply(branchName));
        if (item != null && factory.isProject(item) && branchName.equals(factory.getBranch(item).getName())) {
            return item;
        }
        for (P p: getItems()) {
            if (factory.isProject(p) && branchName.equals(factory.getBranch(p).getName())) {
                return p;
            }
        }
        return null;
    }

    /**
     * Returns the named branch job or {@code null} if no such branch exists.
     *
     * @param name the name of the branch
     * @return the named branch job or {@code null} if no such branch exists.
     * @deprecated use {@link #getItem(String)} or {@link #getJob(String)} directly
     */
    @Deprecated
    @CheckForNull
    @SuppressWarnings("unused")// by stapler for URL binding
    public P getBranch(String name) {
        return getItem(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ACL getACL() {
        final ACL acl = super.getACL();
        if (getParent() instanceof OrganizationFolder) {
            return new ACL() {
                @Override
                public boolean hasPermission(Authentication a, Permission permission) {
                    if (ACL.SYSTEM.equals(a)) {
                        return true;
                    } else if (SUPPRESSED_PERMISSIONS.contains(permission)) {
                        return false;
                    } else {
                        return acl.hasPermission(a, permission);
                    }
                }
            };
        } else {
            return acl;
        }
    }

    private static final Set<Permission> SUPPRESSED_PERMISSIONS =
            ImmutableSet.of(Item.CONFIGURE, Item.DELETE, View.CONFIGURE, View.CREATE, View.DELETE);

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public File getRootDirFor(P child) {
        File dir = super.getRootDirFor(child);
        if (!dir.isDirectory() && !dir.mkdirs()) { // TODO is this really necessary?
            LOGGER.log(Level.WARNING, "Could not create directory {0}", dir);
        }
        return dir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        String description = super.getDescription();
        if (StringUtils.isNotBlank(description)) {
            return description;
        }
        ObjectMetadataAction action = getAction(ObjectMetadataAction.class);
        if (action != null) {
            return action.getObjectDescription();
        }
        return super.getDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        String displayName = getDisplayNameOrNull();
        if (displayName == null) {
            ObjectMetadataAction action = getAction(ObjectMetadataAction.class);
            if (action != null && StringUtils.isNotBlank(action.getObjectDisplayName())) {
                return action.getObjectDisplayName();
            }
        }
        return super.getDisplayName();
    }

    /**
     * Returns the directory that all branches are stored in.
     *
     * @return the directory that all branches are stored in.
     */
    @Override
    @NonNull
    public File getJobsDir() {
        return new File(getRootDir(), "branches");
    }

    /**
     * Returns the directory that branch indexing is stored in.
     *
     * @return the directory that branch indexing is stored in.
     */
    @Override
    @NonNull
    public File getComputationDir() {
        return new File(getRootDir(), "indexing");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MultiBranchProjectDescriptor getDescriptor() {
        return (MultiBranchProjectDescriptor) super.getDescriptor();
    }

    /**
     * Returns the current/most recent indexing details.
     *
     * @return the current/most recent indexing details.
     */
    @SuppressWarnings("unused") // URL binding for stapler.
    public synchronized BranchIndexing<P, R> getIndexing() {
        return (BranchIndexing) getComputation();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, Descriptor.FormException {
        super.submit(req, rsp);
        List<SCMSource> _sources = new ArrayList<>();
        synchronized (this) {
            JSONObject json = req.getSubmittedForm();
            sources.replaceBy(req.bindJSONToList(BranchSource.class, json.opt("sources")));
            for (SCMSource scmSource : getSCMSources()) {
                scmSource.setOwner(this);
                _sources.add(scmSource);
            }
            setProjectFactory(req.bindJSON(BranchProjectFactory.class, json.getJSONObject("projectFactory")));
        }
        for (SCMSource scmSource : _sources) {
            scmSource.afterSave();
        }
        String srcDigest;
        try {
            srcDigest = Util.getDigestOf(Items.XSTREAM2.toXML(sources));
        } catch (XStreamException e) {
            srcDigest = null;
        }
        String facDigest;
        try {
            facDigest = Util.getDigestOf(Items.XSTREAM2.toXML(getProjectFactory()));
        } catch (XStreamException e) {
            facDigest = null;
        }
        recalculateAfterSubmitted(!StringUtils.equals(srcDigest, this.srcDigest));
        recalculateAfterSubmitted(!StringUtils.equals(facDigest, this.facDigest));
        this.srcDigest = srcDigest;
        this.facDigest = facDigest;
    }

    /**
     * Creates a place-holder view when there's no active branch indexed.
     *
     * @return the place-holder view when there's no active branch indexed.
     */
    protected View getWelcomeView() {
        return new MultiBranchProjectEmptyView(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(String name) {
        if (name.equals("Welcome")) {
            return getWelcomeView();
        } else {
            return super.getView(name);
        }
    }

    /**
     * Represents the branch indexing job.
     *
     * @param <P> the type of project that the branch projects consist of.
     * @param <R> the type of runs that the branch projects use.
     */
    public static class BranchIndexing<P extends Job<P, R> & TopLevelItem,
            R extends Run<P, R>> extends FolderComputation<P> {

        public BranchIndexing(@NonNull MultiBranchProject<P, R> project,
                              @CheckForNull BranchIndexing<P, R> previousIndexing) {
            super(project, previousIndexing);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MultiBranchProject<P, R> getParent() {
            return (MultiBranchProject) super.getParent();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected XmlFile getDataFile() {
            return new XmlFile(Items.XSTREAM, new File(getParent().getComputationDir(), "indexing.xml"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public File getLogFile() {
            return new File(getParent().getComputationDir(), "indexing.log");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.MultiBranchProject_BranchIndexing_displayName(getParent().getPronoun());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getUrl() {
            return getParent().getUrl() + "indexing/";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getSearchUrl() {
            return "indexing/";
        }

        @Override
        public void run() {
            long start = System.currentTimeMillis();
            try {
                super.run();
            } finally {
                long end = System.currentTimeMillis();
                LOGGER.log(Level.INFO, "{0} #{1,time,yyyyMMdd.HHmmss} branch indexing action completed: {2} in {3}",
                        new Object[]{
                                getParent().getFullName(), start, getResult(), Util.getTimeSpanString(end - start)
                        }
                );
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBuildable() {
        if (sources == null) {
            return false; // still loading
        }
        return !sources.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected FolderComputation<P> createComputation(FolderComputation<P> previous) {
        return new BranchIndexing<P, R>(this, (BranchIndexing) previous);
    }

    /**
     * Inverse function of {@link Util#rawEncode(String)}
     *
     * @param s the encoded string.
     * @return the decoded string.
     */
    @NonNull
    public static String rawDecode(@NonNull String s) {
        final byte[] bytes; // should be US-ASCII but we can be tolerant
        try {
            bytes = s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("JLS specification mandates UTF-8 as a supported encoding", e);
        }
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (int i = 0; i < bytes.length; i++) {
            final int b = bytes[i];
            if (b == '%' && i + 2 < bytes.length) {
                final int u = Character.digit((char) bytes[++i], 16);
                final int l = Character.digit((char) bytes[++i], 16);
                if (u != -1 && l != -1) {
                    buffer.write((char) ((u << 4) + l));
                    continue;
                }
                // should be a valid encoding but we can be tolerant
                i -= 2;
            }
            buffer.write(b);
        }
        try {
            return new String(buffer.toByteArray(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("JLS specification mandates UTF-8 as a supported encoding", e);
        }
    }

    private static class BranchSourceList extends PersistedList<BranchSource> {

        BranchSourceList(MultiBranchProject<?, ?> owner) {
            super(owner);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void onModified() throws IOException {
            super.onModified();
            for (BranchSource branchSource : this) {
                branchSource.getSource().setOwner((MultiBranchProject) owner);
            }
        }
    }

    /**
     * Our event listener.
     */
    @Extension
    public static class SCMEventListenerImpl extends SCMEventListener {
        /**
         * The {@link TaskListener} for events that we cannot assign to a multi-branch project.
         *
         * @return The {@link TaskListener} for events that we cannot assign to a multi-branch project.
         */
        public TaskListener globalEventsListener() {
            File eventsFile =
                    new File(Jenkins.getActiveInstance().getRootDir(), MultiBranchProject.class.getName() + ".log");
            boolean rotate = eventsFile.length() > 30 * 1024;
            OutputStream os = new ReopenableRotatingFileOutputStream(eventsFile, 5);
            if (rotate) {
                try {
                    ((ReopenableRotatingFileOutputStream) os).rewind();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Could not rotate " + eventsFile, e);
                }
            }
            return new StreamBuildListener(os, Charsets.UTF_8);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onSCMHeadEvent(final SCMHeadEvent<?> event) {
            TaskListener global = globalEventsListener();
            String eventClass = event.getClass().getName();
            String eventType = event.getType().name();
            long eventTimestamp = event.getTimestamp();
            global.getLogger().format("[%tc] Received %s %s event with timestamp %tc%n",
                    System.currentTimeMillis(), eventClass, eventType,
                    eventTimestamp);
            LOGGER.log(Level.FINE, "{0} {1} {2,date} {2,time}: onSCMHeadEvent",
                    new Object[]{
                            eventClass, eventType, eventTimestamp
                    }
            );
            int matchCount = 0;
            // not interested in removal of dead items as that needs to be handled by the computation in order
            // to ensure that other sources do not want to take ownership and also to ensure that the dead branch
            // strategy is enforced correctly
            if (SCMEvent.Type.CREATED == event.getType()) {
                matchCount = processHeadCreate(event, global, eventClass, eventType, eventTimestamp, matchCount);
            } else if (SCMEvent.Type.UPDATED == event.getType() || SCMEvent.Type.REMOVED == event.getType()) {
                matchCount = processHeadUpdate(event, global, eventClass, eventType, eventTimestamp, matchCount);
            }
            global.getLogger().format("[%tc] Finished processing %s %s event with timestamp %tc. Matched %d.%n",
                    System.currentTimeMillis(), eventClass, eventType,
                    eventTimestamp, matchCount);

        }

        private int processHeadCreate(SCMHeadEvent<?> event, TaskListener global, String eventClass, String eventType,
                                      long eventTimestamp, int matchCount) {
            Set<String> sourceIds = new HashSet<>();
            for (MultiBranchProject<?, ?> p : Jenkins.getActiveInstance().getAllItems(MultiBranchProject.class)) {
                sourceIds.clear();
                String pFullName = p.getFullName();
                LOGGER.log(Level.FINER, "{0} {1} {2,date} {2,time}: Checking {3} for a match",
                        new Object[]{
                                eventClass, eventType, eventTimestamp, pFullName
                        }
                );
                boolean haveMatch = false;
                final BranchProjectFactory _factory = p.getProjectFactory();
                SOURCES:
                for (SCMSource source : p.getSCMSources()) {
                    if (event.isMatch(source)) {
                        LOGGER.log(Level.FINE, "{0} {1} {2,date} {2,time}: Project {3}: Matches source {4}",
                                new Object[]{
                                        eventClass, eventType, eventTimestamp, pFullName, source.getId()
                                }
                        );
                        for (SCMHead h : event.heads(source).keySet()) {
                            String name = h.getName();
                            Job job = p.getItemByBranchName(name);
                            if (job == null) {
                                LOGGER.log(Level.FINE, "{0} {1} {2,date} {2,time}: Project {3}: Source {4}: Match: {5}",
                                        new Object[]{
                                                eventClass, eventType, eventTimestamp, pFullName, source.getId(), name
                                        }
                                );
                                // only interested in create events that actually could create a new branch
                                haveMatch = true;
                                matchCount++;
                                global.getLogger().format("Found match against %s (new branch %s)%n", pFullName, name);
                                break SOURCES;
                            }
                            Branch branch = _factory.getBranch(job);
                            if (branch instanceof Branch.Dead) {
                                LOGGER.log(Level.FINE, "{0} {1} {2,date} {2,time}: Project {3}: Source {4}: Match: {5}",
                                        new Object[]{
                                                eventClass, eventType, eventTimestamp, pFullName, source.getId(), name
                                        }
                                );
                                // only interested in create events that actually could create a new branch
                                haveMatch = true;
                                matchCount++;
                                global.getLogger().format("Found match against %s (resurrect branch %s)%n", pFullName, name);
                                break SOURCES;
                            }
                            String sourceId = branch.getSourceId();
                            if (StringUtils.equals(sourceId, source.getId())) {
                                LOGGER.log(Level.FINER,
                                        "{0} {1} {2,date} {2,time}: Project {3}: Source {4}: Already have: {5}",
                                        new Object[]{
                                                eventClass,
                                                eventType,
                                                eventTimestamp,
                                                pFullName,
                                                source.getId(),
                                                name
                                        }
                                );
                                continue;
                            }
                            if (sourceIds.contains(sourceId)) {
                                LOGGER.log(Level.FINER,
                                        "{0} {1} {2,date} {2,time}: Project {3}: Source {4}: Ignored as "
                                                + "already have {5} from higher priority source {6}",
                                        new Object[]{
                                                eventClass,
                                                eventType,
                                                eventTimestamp,
                                                pFullName,
                                                source.getId(),
                                                name,
                                                sourceId
                                        }
                                );
                                continue;
                            }
                            LOGGER.log(Level.FINE,
                                    "{0} {1} {2,date} {2,time}: Project {3}: Source {4}: Match: {5} "
                                            + "overriding lower priority source {6}",
                                    new Object[]{
                                            eventClass,
                                            eventType,
                                            eventTimestamp,
                                            pFullName,
                                            source.getId(),
                                            name,
                                            sourceId
                                    }
                            );
                            // only interested in create events that actually could create a new branch
                            haveMatch = true;
                            matchCount++;
                            global.getLogger().format("Found match against %s (takeover branch %s)%n", pFullName, name);
                            break SOURCES;
                        }
                        LOGGER.log(Level.FINE, "{0} {1} {2,date} {2,time}: Project {3}: No new projects for {4}",
                                new Object[]{
                                        eventClass, eventType, eventTimestamp, pFullName, source.getId()
                                }
                        );
                    } else {
                        LOGGER.log(Level.FINEST, "{0} {1} {2,date} {2,time}: Project {3}: Does not matches source {4}",
                                new Object[]{
                                        eventClass, eventType,
                                        eventTimestamp, pFullName,
                                        source.getId()
                                }
                        );
                    }
                    sourceIds.add(source.getId());
                }
                if (haveMatch) {
                    TaskListener listener;
                    try {
                        listener = p.getComputation().createEventsListener();
                    } catch (IOException e) {
                        listener = new LogTaskListener(LOGGER, Level.FINE);
                    }
                    long start = System.currentTimeMillis();
                    listener.getLogger().format("[%tc] Received %s event with timestamp %tc%n",
                            start, eventType, eventTimestamp);
                    ChildObserver childObserver = p.createEventsChildObserver();
                    try {
                        for (SCMSource source : p.getSCMSources()) {
                            if (event.isMatch(source)) {
                                source.fetch(
                                        p.getSCMSourceCriteria(source),
                                        p.new SCMHeadObserverImpl(
                                                source,
                                                childObserver,
                                                listener,
                                                _factory,
                                                new EventCauseFactory(event),
                                                event),
                                        event,
                                        listener
                                );
                            }
                        }
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace(listener.error(e.getMessage()));
                    } finally {
                        long end = System.currentTimeMillis();
                        listener.getLogger().format("[%tc] %s event processed in %s%n",
                                end, eventType, Util.getTimeSpanString(end - start));
                    }
                }
            }
            return matchCount;
        }

        private int processHeadUpdate(SCMHeadEvent<?> event, TaskListener global, String eventClass, String eventType,
                                      long eventTimestamp, int matchCount) {
            Map<SCMSource, SCMHead> matches = new IdentityHashMap<>();
            Set<String> candidateNames = new HashSet<>();
            Map<SCMSource, Map<SCMHead,SCMRevision>> revisionMaps = new IdentityHashMap<>();
            Set<Job<?, ?>> jobs = new HashSet<>();
            for (MultiBranchProject<?, ?> p : Jenkins.getActiveInstance().getAllItems(MultiBranchProject.class)) {
                String pFullName = p.getFullName();
                LOGGER.log(Level.FINER, "{0} {1} {2,date} {2,time}: Checking {3} for a match",
                        new Object[]{
                                eventClass, eventType, eventTimestamp, pFullName
                        }
                );
                final BranchProjectFactory _factory = p.getProjectFactory();
                matches.clear();
                candidateNames.clear();
                revisionMaps.clear();
                for (SCMSource source : p.getSCMSources()) {
                    Map<SCMHead, SCMRevision> eventHeads = event.heads(source);
                    if (!eventHeads.isEmpty()) {
                        revisionMaps.put(source, eventHeads);
                        for (SCMHead h: eventHeads.keySet()) {
                            candidateNames.add(h.getName());
                        }
                    }
                }
                jobs.clear();
                for (Job i : p.getItems()) {
                    if (_factory.isProject(i)) {
                        Branch branch = _factory.getBranch(i);
                        if (!candidateNames.contains(branch.getName())) {
                            continue;
                        }
                        if (branch instanceof Branch.Dead) {
                            LOGGER.log(Level.FINEST, "{0} {1} {2,date} {2,time}: Checking {3} -> Resurrect dead "
                                            + "branch {4} (job {5})?",
                                    new Object[]{
                                            eventClass, eventType, eventTimestamp, pFullName, branch.getName(),
                                            i.getName()
                                    }
                            );
                            // try to bring back from the dead by checking all sources in sequence
                            // the first to match the event and that contains the head wins!
                            SOURCES: for (SCMSource src : p.getSCMSources()) {
                                Map<SCMHead, SCMRevision> revisionMap = revisionMaps.get(src);
                                if (revisionMap == null) {
                                    continue;
                                }
                                SCMHead head = branch.getHead();
                                for (SCMHead h: revisionMap.keySet()) {
                                    // for bringing back from the dead we need to check the name as it could be
                                    // back from the dead on a different source from original
                                    if (h.getName().equals(head.getName())) {
                                        matches.put(src, head);
                                        jobs.add(i);
                                        break SOURCES;
                                    }
                                }
                            }
                        } else {
                            // TODO takeover
                            LOGGER.log(Level.FINEST,
                                    "{0} {1} {2,date} {2,time}: Checking {3} -> Matches existing branch {4} "
                                            + "(job {5})?",
                                    new Object[]{
                                            eventClass, eventType, eventTimestamp, pFullName, branch.getName(),
                                            i.getName()
                                    }
                            );
                            SCMSource src = p.getSCMSource(branch.getSourceId());
                            if (src == null) {
                                continue;
                            }
                            Map<SCMHead, SCMRevision> revisionMap = revisionMaps.get(src);
                            if (revisionMap == null) {
                                // we got here because a source matches the name
                                // need to find the source that did match the name and compare priorities
                                // to see if this is a takeover
                                LOGGER.log(Level.FINEST,
                                        "{0} {1} {2,date} {2,time}: Checking {3} -> Event does not match current "
                                                + "source of {4} (job {5}), checking for take-over",
                                        new Object[]{
                                                eventClass, eventType, eventTimestamp, pFullName, branch.getName(),
                                                i.getName()
                                        }
                                );

                                // check who has priority
                                int ourPriority = Integer.MAX_VALUE;
                                int oldPriority = Integer.MAX_VALUE;
                                SCMSource ourSource = null;
                                int priority = 1;
                                for (SCMSource s : p.getSCMSources()) {
                                    String sId = s.getId();
                                    Map<SCMHead, SCMRevision> rMap = revisionMaps.get(s);
                                    if (ourPriority > priority && oldPriority > priority && rMap != null) {
                                        // only need to check for takeover when the event is higher priority
                                        for (SCMHead h: rMap.keySet()) {
                                            if (branch.getName().equals(h.getName())) {
                                                ourPriority = priority;
                                                ourSource = s;
                                                break;
                                            }
                                        }
                                    }
                                    if (sId.equals(src.getId())) {
                                        oldPriority = priority;
                                    }
                                    priority++;
                                }
                                if (oldPriority < ourPriority) {
                                    LOGGER.log(Level.FINEST,
                                            "{0} {1} {2,date} {2,time}: Checking {3} -> Ignoring event for {4} "
                                                    + "(job {5}) from source #{6} as source #{7} owns the branch name",
                                            new Object[]{
                                                    eventClass, eventType, eventTimestamp, pFullName, branch.getName(),
                                                    i.getName(), ourPriority, oldPriority
                                            }
                                    );
                                    continue;
                                } else {
                                    LOGGER.log(Level.FINER,
                                            "{0} {1} {2,date} {2,time}: Checking {3} -> Takeover event for {4} "
                                                    + "(job {5}) by source #{5} from source #{6}",
                                            new Object[]{
                                                    eventClass, eventType, eventTimestamp, pFullName, branch.getName(),
                                                    i.getName(), ourPriority, oldPriority
                                            }
                                    );
                                    assert ourSource != null;
                                    src = ourSource;
                                    revisionMap = revisionMaps.get(ourSource);
                                    assert revisionMap != null;
                                }
                            }
                            SCMHead head = branch.getHead();
                            // The distinguishing key for branch projects is the name, so check on the name
                            boolean match = false;
                            for (SCMHead h: revisionMap.keySet()) {
                                if (h.getName().equals(head.getName())) {
                                    match = true;
                                    break;
                                }
                            }
                            if (match) {
                                LOGGER.log(Level.FINE,
                                        "{0} {1} {2,date} {2,time}: Checking {3} -> Event matches source of {4} "
                                                + "(job {5})",
                                        new Object[]{
                                                eventClass, eventType, eventTimestamp, pFullName, branch.getName(),
                                                i.getName()
                                        }
                                );
                                if (SCMEvent.Type.UPDATED == event.getType()) {
                                    SCMRevision revision = revisionMap.get(head);
                                    if (revision != null && revision.isDeterministic()) {
                                        SCMRevision lastBuild = _factory.getRevision(i);
                                        if (revision.equals(lastBuild)) {
                                            // we are not interested in events that tell us a revision
                                            // we have already seen
                                            LOGGER.log(Level.FINE,
                                                    "{0} {1} {2,date} {2,time}: Checking {3} -> Ignoring event as "
                                                            + "revision {4} is same as last build of {5} (job {6})",
                                                    new Object[]{
                                                            eventClass,
                                                            eventType,
                                                            eventTimestamp,
                                                            pFullName,
                                                            revision,
                                                            branch.getName(),
                                                            i.getName()
                                                    }
                                            );
                                            continue;
                                        }
                                    }
                                }
                                matches.put(src, head);
                                jobs.add(i);
                            }
                        }
                    }
                }
                if (!matches.isEmpty()) {
                    matchCount++;
                    global.getLogger().format("Found match against %s%n", pFullName);
                    TaskListener listener;
                    try {
                        listener = p.getComputation().createEventsListener();
                    } catch (IOException e) {
                        listener = new LogTaskListener(LOGGER, Level.FINE);
                    }
                    long start = System.currentTimeMillis();
                    listener.getLogger().format("[%tc] Received %s event with timestamp %tc%n",
                            start, eventType, eventTimestamp);
                    ChildObserver childObserver = p.createEventsChildObserver();
                    try {
                        for (Map.Entry<SCMSource, SCMHead> m : matches.entrySet()) {
                            m.getKey().fetch(
                                    p.getSCMSourceCriteria(m.getKey()),
                                    p.new SCMHeadObserverImpl(
                                            m.getKey(),
                                            childObserver,
                                            listener,
                                            _factory,
                                            new EventCauseFactory(event),
                                            event),
                                    event,
                                    listener
                            );
                        }
                        // now dis-associate branches that no-longer exist
                        Set<String> names = childObserver.observed();
                        for (Job<?, ?> j : jobs) {
                            if (names.contains(j.getName())) {
                                // observed, so not dead
                                continue;
                            }
                            Branch branch = _factory.getBranch(j);
                            String sourceId = branch.getSourceId();
                            boolean foundSource = false;
                            for (SCMSource s : matches.keySet()) {
                                if (sourceId.equals(s.getId())) {
                                    foundSource = true;
                                }
                            }
                            if (!foundSource) {
                                // not safe to switch to a dead branch
                                continue;
                            }
                            _factory.decorate(_factory.setBranch(
                                    j,
                                    new Branch.Dead(branch)
                            ));
                            j.save();
                        }
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace(listener.error(e.getMessage()));
                    } finally {
                        long end = System.currentTimeMillis();
                        listener.getLogger().format("[%tc] %s event processed in %s%n",
                                end, eventType, Util.getTimeSpanString(end - start));
                    }
                } else {
                    // didn't match an existing branch, maybe the criteria now match against an updated branch
                    boolean haveMatch = false;
                    for (SCMSource source : p.getSCMSources()) {
                        if (event.isMatch(source)) {
                            for (SCMHead h : event.heads(source).keySet()) {
                                if (p.getItemByBranchName(h.getName()) == null) {
                                    // only interested in create events that actually could create a new branch
                                    haveMatch = true;
                                    break;
                                }
                            }
                            if (haveMatch) {
                                global.getLogger().format("Found match against %s%n", pFullName);
                                break;
                            }
                            break;
                        }
                    }
                    if (haveMatch) {
                        TaskListener listener;
                        try {
                            listener = p.getComputation().createEventsListener();
                        } catch (IOException e) {
                            listener = new LogTaskListener(LOGGER, Level.FINE);
                        }
                        long start = System.currentTimeMillis();
                        listener.getLogger().format("[%tc] Received %s event with timestamp %tc%n",
                                start, eventType, eventTimestamp);
                        ChildObserver childObserver = p.createEventsChildObserver();
                        try {
                            for (SCMSource source : p.getSCMSources()) {
                                if (event.isMatch(source)) {
                                    source.fetch(
                                            p.getSCMSourceCriteria(source),
                                            p.new SCMHeadObserverImpl(
                                                    source,
                                                    childObserver,
                                                    listener,
                                                    _factory,
                                                    new EventCauseFactory(event),
                                                    event
                                            ),
                                            event,
                                            listener
                                    );
                                }
                            }
                        } catch (IOException | InterruptedException e) {
                            e.printStackTrace(listener.error(e.getMessage()));
                        } finally {
                            long end = System.currentTimeMillis();
                            listener.getLogger().format("[%tc] %s event processed in %s%n",
                                    end, eventType, Util.getTimeSpanString(end - start));
                        }
                    }
                }
            }
            return matchCount;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onSCMSourceEvent(SCMSourceEvent<?> event) {
            TaskListener global = globalEventsListener();
            global.getLogger().format("[%tc] Received %s %s event with timestamp %tc%n",
                    System.currentTimeMillis(), event.getClass().getName(), event.getType().name(),
                    event.getTimestamp());
            int matchCount = 0;
            // not interested in creation as that is an event for org folders
            // not interested in removal as that is an event for org folders
            if (SCMEvent.Type.UPDATED == event.getType()) {
                // we are only interested in updates as they would trigger the actions being updated
                for (MultiBranchProject<?, ?> p : Jenkins.getActiveInstance().getAllItems(MultiBranchProject.class)) {
                    boolean haveMatch = false;
                    List<SCMSource> scmSources = p.getSCMSources();
                    for (SCMSource s : scmSources) {
                        if (event.isMatch(s)) {
                            global.getLogger().format("Found match against %s%n", p.getFullName());
                            haveMatch = true;
                            break;
                        }
                    }
                    if (haveMatch) {
                        TaskListener listener;
                        try {
                            listener = p.getComputation().createEventsListener();
                        } catch (IOException e) {
                            listener = new LogTaskListener(LOGGER, Level.FINE);
                        }

                        try {
                            Map<String, List<Action>> stateActions = new HashMap<>();
                            for (SCMSource source : scmSources) {
                                List<Action> oldActions = p.state.sourceActions.get(source.getId());
                                List<Action> newActions;
                                try {
                                    newActions = source.fetchActions(event, listener);
                                } catch (IOException e) {
                                    e.printStackTrace(listener.error("Could not refresh actions for source %s",
                                            source.getId()
                                    ));
                                    // preserve previous actions if we have some transient error fetching now (e.g.
                                    // API rate limit)
                                    newActions = oldActions;
                                }
                                if (oldActions == null || !oldActions.equals(newActions)) {
                                    stateActions.put(source.getId(), newActions);
                                }
                            }
                            if (!stateActions.isEmpty()) {
                                boolean saveProject = false;
                                for (List<Action> actions : stateActions.values()) {
                                    for (Action a : actions) {
                                        // undo any hacks that attached the contributed actions without attribution
                                        saveProject = p.removeActions(a.getClass()) || saveProject;
                                    }
                                }
                                BulkChange bc = new BulkChange(p.state);
                                try {
                                    p.state.sourceActions.putAll(stateActions);
                                    bc.commit();
                                    if (saveProject) {
                                        p.save();
                                    }
                                } finally {
                                    bc.abort();
                                }
                            }
                        } catch (IOException | InterruptedException e) {
                            e.printStackTrace(listener.error(e.getMessage()));
                        }
                    }
                }
            }
            global.getLogger().format("[%tc] Finished processing %s %s event with timestamp %tc. Matched %d.%n",
                    System.currentTimeMillis(), event.getClass().getName(), event.getType().name(),
                    event.getTimestamp(), matchCount);
        }

    }

    /**
     * We need to be able to inject causes which may include causes contributed by the triggering event.
     */
    private static abstract class CauseFactory {
        /**
         * Create the cause instances on demand. The instances in the array probably need to be new instances
         * for each build as we cannot know the assumptions that plugin authors have made in
         * {@link Cause#onAddedTo(Run)}.
         *
         * @return an array of new cause instances.
         */
        @NonNull
        abstract Cause[] create();
    }

    /**
     * A cause factory for {@link BranchIndexingCause}.
     */
    private static class IndexingCauseFactory extends CauseFactory {
        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        Cause[] create() {
            return new Cause[]{new BranchIndexingCause()};
        }
    }

    /**
     * A cause factory for {@link BranchEventCause}.
     */
    private static class EventCauseFactory extends CauseFactory {
        /**
         * The event.
         */
        @NonNull
        private final SCMHeadEvent<?> event;

        EventCauseFactory(SCMHeadEvent<?> event) {
            this.event = event;
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        Cause[] create() {
            Cause[] eventCauses = event.asCauses();
            Cause[] result = new Cause[eventCauses.length + 1];
            result[0] = new BranchEventCause(event);
            if (eventCauses.length > 0) {
                System.arraycopy(eventCauses, 0, result, 1, eventCauses.length);
            }
            return result;
        }
    }

    /**
     * Our observer.
     */
    private class SCMHeadObserverImpl extends SCMHeadObserver {
        /**
         * The source that we are observing.
         */
        @NonNull
        private final SCMSource source;
        /**
         * The child observer.
         */
        @NonNull
        private final ChildObserver<P> observer;
        /**
         * The task listener.
         */
        @NonNull
        private final TaskListener listener;
        /**
         * The project factory.
         */
        @NonNull
        private final BranchProjectFactory<P, R> _factory;
        /**
         * A source of {@link Cause} instances to use when triggering builds.
         */
        @NonNull
        private final CauseFactory causeFactory;
        /**
         * The optional event to use when scoping queries.
         */
        @CheckForNull
        private final SCMHeadEvent<?> event;

        /**
         * Constructor.
         *
         * @param source       The source that we are observing.
         * @param observer     The child observer.
         * @param listener     The task listener.
         * @param _factory     The project factory.
         * @param causeFactory A source of {@link Cause} instances to use when triggering builds.
         * @param event        The optional event to use when scoping queries.
         */
        public SCMHeadObserverImpl(@NonNull SCMSource source, @NonNull ChildObserver<P> observer,
                                   @NonNull TaskListener listener, @NonNull BranchProjectFactory<P, R> _factory,
                                   @NonNull CauseFactory causeFactory, @CheckForNull SCMHeadEvent<?> event) {
            this.source = source;
            this.observer = observer;
            this.listener = listener;
            this._factory = _factory;
            this.causeFactory = causeFactory;
            this.event = event;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void observe(@NonNull SCMHead head, @NonNull SCMRevision revision) {
            Branch branch = newBranch(source, head);
            String rawName = branch.getName();
            String encodedName = branch.getEncodedName();
            P project = observer.shouldUpdate(encodedName);
            Branch origBranch;
            if (project == null) {
                origBranch = null;
            } else {
                if (!_factory.isProject(project)) {
                    listener.getLogger().println("Detected unsupported subitem "
                            + ModelHyperlinkNote.encodeTo(project) + ", skipping");
                    return;
                }
                origBranch = _factory.getBranch(project);
                if (!(origBranch instanceof Branch.Dead)) {
                    if (!source.getId().equals(origBranch.getSourceId())) {
                        // check who has priority
                        int ourPriority = Integer.MAX_VALUE;
                        int oldPriority = Integer.MAX_VALUE;
                        int p = 1;
                        for (BranchSource s : sources) {
                            String sId = s.getSource().getId();
                            if (sId.equals(source.getId())) {
                                ourPriority = p;
                            }
                            if (sId.equals(origBranch.getSourceId())) {
                                oldPriority = p;
                            }
                            p++;
                        }
                        if (oldPriority < ourPriority) {
                            listener.getLogger().println(
                                    "Ignoring " + ModelHyperlinkNote.encodeTo(project) + " from source #"
                                            + ourPriority + " as source #" +
                                            oldPriority + " owns the branch name");
                            return;
                        } else {
                            if (oldPriority == Integer.MAX_VALUE) {
                                listener.getLogger().println(
                                        "Takeover for " + ModelHyperlinkNote.encodeTo(project) + " by source #"
                                                + ourPriority
                                                + " from source that no longer exists");
                            } else {
                                listener.getLogger().println(
                                        "Takeover for " + ModelHyperlinkNote.encodeTo(project) + " by source #"
                                                + ourPriority
                                                + " from source #" + oldPriority);
                            }
                        }
                    }
                }
            }
            Action[] revisionActions = new Action[0];
            boolean headActionsFetched = false;
            try {
                branch.setActions(source.fetchActions(head, event, listener));
                headActionsFetched = true;
            } catch (IOException | InterruptedException e) {
                e.printStackTrace(listener.error("Could not fetch metadata of branch %s", rawName));
            }
            try {
                List<Action> actions = source.fetchActions(revision, event, listener);
                revisionActions = actions.toArray(new Action[actions.size()]);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace(listener.error("Could not fetch metadata for revision %s of branch %s",
                        revision, rawName));
            }
            if (project != null) {
                boolean rebuild = (origBranch instanceof Branch.Dead && !(branch instanceof Branch.Dead))
                        || !(source.getId().equals(origBranch.getSourceId()));
                if (!headActionsFetched) {
                    // we didn't fetch them so replicate previous actions
                    branch.setActions(origBranch.getActions());
                }
                boolean needSave = !branch.equals(origBranch) || !branch.getActions().equals(origBranch.getActions());
                _factory.decorate(_factory.setBranch(project, branch));
                if (rebuild
                        && event == null // JENKINS-41121 doesn't apply to events, they are new builds
                        && legacySourceIds != null // JENKINS-41121 doesn't apply to new projects or after first index
                        && !(legacySourceIds.contains(source.getId())) // JENKINS-41121 doesn't apply if source retained
                        && legacySourceIds.contains(origBranch.getSourceId())
                        && revision.isDeterministic()) {
                    // JENKINS-41121 suppress automatic rebuild for branches being re-assoicated with the correct
                    // source id on their first successful scan only.
                    needSave = true;
                } else if (rebuild) {
                    listener.getLogger().format(
                            "%s reopened: %s (%s)%n",
                            StringUtils.defaultIfEmpty(head.getPronoun(), "Branch"),
                            rawName,
                            revision
                    );
                    needSave = true;
                    if (isAutomaticBuild(source, head)) {
                        scheduleBuild(
                                _factory,
                                project,
                                revision,
                                listener,
                                rawName,
                                causeFactory.create(),
                                revisionActions
                        );
                    } else {
                        listener.getLogger().format("No automatic builds for %s%n", rawName);
                    }
                } else if (revision.isDeterministic()) {
                    SCMRevision lastBuild = _factory.getRevision(project);
                    if (!revision.equals(lastBuild)) {
                        listener.getLogger().format("Changes detected: %s (%s → %s)%n", rawName, lastBuild, revision);
                        needSave = true;
                        if (isAutomaticBuild(source, head)) {
                            scheduleBuild(
                                    _factory,
                                    project,
                                    revision,
                                    listener,
                                    rawName,
                                    causeFactory.create(),
                                    revisionActions
                            );
                        } else {
                            listener.getLogger().format("No automatic builds for %s%n", rawName);
                        }
                    } else {
                        listener.getLogger().format("No changes detected: %s (still at %s)%n", rawName, revision);
                    }
                } else {
                    // fall back to polling when we have a non-deterministic revision/hash.
                    SCMTriggerItem scmProject = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(project);
                    if (scmProject != null) {
                        PollingResult pollingResult = scmProject.poll(listener);
                        if (pollingResult.hasChanges()) {
                            listener.getLogger().format("Changes detected: %s%n", rawName);
                            needSave = true;
                            if (isAutomaticBuild(source, head)) {
                                scheduleBuild(
                                        _factory,
                                        project,
                                        revision,
                                        listener,
                                        rawName,
                                        causeFactory.create(),
                                        revisionActions
                                );
                            } else {
                                listener.getLogger().format("No automatic builds for %s%n", rawName);
                            }
                        } else {
                            listener.getLogger().format("No changes detected: %s%n", rawName);
                        }
                    }
                }

                try {
                    if (needSave) {
                        project.save();
                    }
                } catch (IOException e) {
                    e.printStackTrace(listener.error("Could not save changes to " + rawName));
                }
                return;
            }
            if (!observer.mayCreate(encodedName)) {
                listener.getLogger().println("Ignoring duplicate branch project " + rawName);
                return;
            }
            project = _factory.newInstance(branch);
            if (!project.getName().equals(encodedName)) {
                throw new IllegalStateException(
                        "Name of created project " + project + " did not match expected " + encodedName);
            }
            // HACK ALERT
            // ==========
            // We don't want to trigger a save, so we will do some trickery to ensure that observer.created(project)
            // performs the save
            BulkChange bc = new BulkChange(project);
            try {
                if (project.getDisplayNameOrNull() == null && !rawName.equals(encodedName)) {
                    project.setDisplayName(rawName);
                }
            } catch (IOException e) {
                // ignore even if it does happen we didn't want a save
            } finally {
                bc.abort();
            }
            // decorate contract is to ensure it dowes not trigger a save
            _factory.decorate(project);
            // ok it is now up to the observer to ensure it does the actual save.
            observer.created(project);
            if (isAutomaticBuild(source, head)) {
                scheduleBuild(
                        _factory,
                        project,
                        revision,
                        listener,
                        rawName,
                        causeFactory.create(),
                        revisionActions
                );
            } else {
                listener.getLogger().format("No automatic builds for %s%n", rawName);
            }
        }
    }

    /**
     * Tests if the specified {@link SCMHead} should be automatically built when discovered / modified.
     * @param source the source.
     * @param head the head.
     * @return {@code true} if the head should be automatically built when discovered / modified.
     */
    private boolean isAutomaticBuild(SCMSource source, SCMHead head) {
        BranchSource branchSource = null;
        for (BranchSource s: sources) {
            if (s.getSource().getId().equals(source.getId())) {
                branchSource = s;
                break;
            }
        }
        if (branchSource == null) {
            // no match, means no build
            return false;
        }
        List<BranchBuildStrategy> buildStrategies = branchSource.getBuildStrategies();
        if (buildStrategies.isEmpty()) {
            // we will use default behaviour, build anything but tags
            return !(head instanceof TagSCMHead);
        } else {
            for (BranchBuildStrategy s: buildStrategies) {
                if (s.isAutomaticBuild(source, head)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Adds the {@link MultiBranchProject.State#sourceActions} to {@link MultiBranchProject#getAllActions()}.
     *
     * @since 2.0
     */
    @Extension
    public static class StateActionFactory extends TransientActionFactory<MultiBranchProject> {

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<MultiBranchProject> type() {
            return MultiBranchProject.class;
        }

        /**
         * {@inheritDoc}
         */
        @Nonnull
        @Override
        public Collection<? extends Action> createFor(@Nonnull MultiBranchProject target) {
            List<Action> result = new ArrayList<>();
            MultiBranchProject<?, ?> project = target;
            for (BranchSource b : project.getSources()) {
                List<Action> actions = project.state.sourceActions.get(b.getSource().getId());
                if (actions != null && !actions.isEmpty()) {
                    result.addAll(actions);
                }
            }
            return result;
        }
    }

    /**
     * The persisted state.
     *
     * @since 2.0
     */
    private static class State implements Saveable {
        /**
         * Our owner.
         */
        @NonNull
        private transient final MultiBranchProject<?, ?> owner;
        /**
         * The {@link SCMSource#fetchActions(SCMSourceEvent, TaskListener)} for each source, keyed by
         * {@link SCMSource#getId()}.
         */
        private final Map<String, List<Action>> sourceActions = new HashMap<>();

        /**
         * Constructor.
         *
         * @param owner our owner.
         */
        private State(@NonNull MultiBranchProject<?, ?> owner) {
            this.owner = owner;
        }

        /**
         * Clear the state completely.
         */
        public synchronized void reset() {
            sourceActions.clear();
        }

        /**
         * Loads the state from disk.
         *
         * @throws IOException if there was an issue loading the state from disk.
         */
        public synchronized void load() throws IOException {
            if (getStateFile().exists()) {
                getStateFile().unmarshal(this);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void save() throws IOException {
            synchronized (this) {
                if (BulkChange.contains(this)) {
                    return;
                }
                getStateFile().write(this);
            }
            SaveableListener.fireOnChange(this, getStateFile());
        }

        /**
         * The file that the state is persisted in.
         *
         * @return The file that the state is persisted in.
         */
        public final XmlFile getStateFile() {
            return new XmlFile(Items.XSTREAM, new File(owner.getRootDir(), "state.xml"));
        }
    }
}
