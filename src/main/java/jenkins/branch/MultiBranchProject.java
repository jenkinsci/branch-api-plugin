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

import com.cloudbees.hudson.plugins.folder.ChildNameGenerator;
import com.cloudbees.hudson.plugins.folder.FolderIcon;
import com.cloudbees.hudson.plugins.folder.computed.ChildObserver;
import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import com.cloudbees.hudson.plugins.folder.computed.EventOutputStreams;
import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;
import com.cloudbees.hudson.plugins.folder.views.AbstractFolderViewHolder;
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
import hudson.model.Failure;
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
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.SaveableListener;
import hudson.scm.PollingResult;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.util.PersistedList;
import hudson.util.StreamTaskListener;
import hudson.util.XStream2;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
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
import jenkins.scm.api.SCMHeadMigration;
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
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import static hudson.Functions.printStackTrace;

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
        PropertyMigration.applyAll(this);
        try {
            srcDigest = Util.getDigestOf(Items.XSTREAM2.toXML(sources));
        } catch (XStreamException e) {
            srcDigest = null;
        }
        BranchProjectFactory<P, R> factory = getProjectFactory();
        try {
            facDigest = Util.getDigestOf(Items.XSTREAM2.toXML(factory));
        } catch (XStreamException e) {
            facDigest = null;
        }
        if (state == null) {
            state = new State(this);
        }
        try {
            state.load();
        } catch (XStreamException | IOException e) {
            LOGGER.log(Level.WARNING, "Could not read persisted state, will be recovered on next index.", e);
            state.reset();
        }
        // optimize lookup of sources by building a temporary map that is equivalent to getSCMSource(id) in results
        Map<String,SCMSource> sourceMap = new HashMap<>();
        for (BranchSource source : sources) {
            SCMSource s = source.getSource();
            String id = s.getId();
            if (!sourceMap.containsKey(id)) { // only the first match should win
                sourceMap.put(id, s);
            }
        }
        for (P item : getItems()) {
            if (factory.isProject(item)) {
                Branch oldBranch = factory.getBranch(item);
                SCMSource source = sourceMap.get(oldBranch.getSourceId());
                if (source == null || source instanceof NullSCMSource) {
                    continue;
                }
                SCMHead oldHead = oldBranch.getHead();
                SCMHead newHead = SCMHeadMigration.readResolveSCMHead(source, oldHead);
                if (newHead != oldHead) {
                    LOGGER.log(Level.INFO, "Job {0}: a plugin upgrade is requesting migration of branch from {1} to {2}",
                            new Object[]{item.getFullName(), oldHead.getClass(), newHead.getClass()}
                    );
                    try {
                        Branch newBranch = new Branch(oldBranch.getSourceId(), newHead, oldBranch.getScm(),
                                oldBranch.getProperties());
                        newBranch.setActions(oldBranch.getActions());
                        factory.setBranch(item, newBranch);
                        SCMRevision revision = factory.getRevision(item);
                        factory.setRevisionHash(item, SCMHeadMigration.readResolveSCMRevision(source, revision));
                    } catch (IOException | RuntimeException e) {
                        LogRecord lr = new LogRecord(Level.WARNING,
                                "Job {0}: Could not complete migration of branch from type {1} to {2}. "
                                        + "The side-effect of this is that the next index may trigger a rebuild "
                                        + "of the job (after which the issue will be resolved)");
                        lr.setThrown(e);
                        lr.setParameters(new Object[]{item.getFullName(), oldHead.getClass(), newHead.getClass()});
                        LOGGER.log(lr);
                    }
                }
            }
        }
    }

    /**
     * Consolidated initialization code.
     */
    private synchronized void init2() {
        if (sources == null) {
            sources = new PersistedList<>(this);
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
     * Get the term used in the UI to represent the source for this kind of
     * {@link Item}. Must start with a capital letter.
     * @return term used in the UI to represent the source
     */
    public String getSourcePronoun() {
        Set<String> result = new TreeSet<>();
        for (BranchSource source : sources) {
            String pronoun = Util.fixEmptyAndTrim(source.getSource().getPronoun());
            if (pronoun != null) {
                result.add(pronoun);
            }
        }
        return result.isEmpty() ? this.getPronoun() : StringUtils.join(result, " / ");
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
    @Exported
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
     * Offers direct access to set the configurable list of branch sources <strong>while</strong> preserving
     * branch source id associations for sources that are otherwise unmodified
     *
     * @param sources the new sources.
     * @throws IOException if the sources could not be persisted to disk.
     */
    public void setSourcesList(List<BranchSource> sources) throws IOException {
        if (this.sources.isEmpty() || sources.isEmpty()) {
            // easy
            this.sources.replaceBy(sources);
            return;
        }
        Set<String> oldIds = sourceIds(this.sources);
        Set<String> newIds = sourceIds(sources);
        if (oldIds.containsAll(newIds) || newIds.containsAll(oldIds)) {
            // either adding, removing, or updating without an id change
            this.sources.replaceBy(sources);
            return;
        }
        // Now we need to check if any of the new entries are effectively the same as an old entry that is being removed
        // we will store the ID changes in a map and process all the affected branches to update their sourceIds
        Map<String,String> changedIds = new HashMap<>();
        Set<String> additions = new HashSet<>(newIds);
        additions.removeAll(oldIds);
        Set<String> removals = new HashSet<>(oldIds);
        removals.removeAll(newIds);

        for (BranchSource addition : sources) {
            String additionId = addition.getSource().getId();
            if (!additions.contains(additionId)) {
                continue;
            }
            for (BranchSource removal : this.sources) {
                String removalId = removal.getSource().getId();
                if (!removals.contains(removalId)) {
                    continue;
                }
                if (!equalButForId(removal.getSource(), addition.getSource())) {
                    continue;
                }
                changedIds.put(removalId, additionId);
                // now take these two out of consideration
                removals.remove(removalId);
                additions.remove(additionId);
                break;
            }
        }
        this.sources.replaceBy(sources);
        BranchProjectFactory<P,R> factory = getProjectFactory();
        for (P item: getItems()) {
            if (!factory.isProject(item)) {
                continue;
            }
            Branch oldBranch = factory.getBranch(item);
            if (changedIds.containsKey(oldBranch.getSourceId())) {
                Branch newBranch = new Branch(changedIds.get(oldBranch.getSourceId()), oldBranch.getHead(), oldBranch.getScm(), oldBranch.getProperties());
                newBranch.setActions(oldBranch.getActions());
                factory.setBranch(item, newBranch);
            }
        }
    }

    private Set<String> sourceIds(List<BranchSource> sources) {
        Set<String> result = new HashSet<>();
        for (BranchSource s: sources) {
            result.add(s.getSource().getId());
        }
        return result;
    }

    private boolean equalButForId(SCMSource a, SCMSource b) {
        if (!a.getClass().equals(b.getClass())) {
            return false;
        }
        return SOURCE_ID_OMITTED_XSTREAM.toXML(a).equals(SOURCE_ID_OMITTED_XSTREAM.toXML(b));
    }

    private final static XStream2 SOURCE_ID_OMITTED_XSTREAM = new XStream2();

    static {
        SOURCE_ID_OMITTED_XSTREAM.omitField(SCMSource.class, "id");
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public List<SCMSource> getSCMSources() {
        List<SCMSource> result = new ArrayList<>();
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
        if (isBuildable()) {
            scheduleBuild(0, new BranchIndexingCause());
        }
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
                    printStackTrace(e, listener.error("[%tc] Could not update folder level actions from source %s",
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
                        printStackTrace(e, listener.error("[%tc] Could not persist folder level actions",
                                System.currentTimeMillis()));
                        throw e;
                    }
                    if (saveProject) {
                        try {
                            save();
                        } catch (IOException | RuntimeException e) {
                            printStackTrace(e, listener.error(
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
                    printStackTrace(e, listener.error("[%tc] Could not fetch branches from source %s",
                            System.currentTimeMillis(), source.getId()));
                    throw e;
                }
            }
        } finally {
            long end = System.currentTimeMillis();
            listener.getLogger().format("[%tc] Finished branch indexing. Indexing took %s%n", end,
                    Util.getTimeSpanString(end - start));
        }
    }

    private void scheduleBuild(BranchProjectFactory<P, R> factory, final P item, SCMRevision revision, TaskListener listener, String name, Cause[] causes, Action... actions) {
        if (!isBuildable()) {
            listener.getLogger().printf("Did not schedule build for branch: %s (%s is disabled)%n",
                    name, getDisplayName());
            return;
        }
        // JENKINS-48090 see Queue.Item.getCauses() which only operates on the first CauseAction
        // We need to merge any additional causes with the causes we are supplying
        int causeCount = 0;
        for (Action action : actions) {
            if (action instanceof CauseAction) {
                causeCount++;
            }
        }
        Action[] _actions;
        if (causeCount == 0) {
            // no existing causes, insert new one at start
            _actions = new Action[actions.length + 1];
            _actions[0] = new CauseAction(causes);
            System.arraycopy(actions, 0, _actions, 1, actions.length);
        } else {
            // existing causes, filter out and insert aggregate at start
            _actions = new Action[actions.length + 1 - causeCount];
            int i = 1;
            List<Cause> _causes = new ArrayList<>();
            Collections.addAll(_causes, causes);
            for (Action a: actions) {
                if (a instanceof CauseAction) {
                    _causes.addAll(((CauseAction) a).getCauses());
                } else {
                    _actions[i++] = a;
                }
            }
            _actions[0] = new CauseAction(_causes);
        }
        if (ParameterizedJobMixIn.scheduleBuild2(item, -1, _actions) != null) {
            listener.getLogger().println("Scheduled build for branch: " + name);
            try {
                factory.setRevisionHash(item, revision);
            } catch (IOException e) {
                printStackTrace(e, listener.error("Could not update last revision hash"));
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
        P item = super.getItem(name);
        if (item != null) {
            return item;
        }
        if (name.indexOf('%') != -1) {
            String decoded = NameEncoder.decode(name);
            item = super.getItem(decoded);
            if (item != null) {
                return item;
            }
            // fall through for double decoded call paths // TODO is this necessary
        }
        return super.getItem(NameEncoder.encode(name));
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
        return super.getItem(NameEncoder.encode(branchName));
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
        if (getParent() instanceof ComputedFolder<?>) {
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
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            Item.CONFIGURE, Item.DELETE, View.CONFIGURE, View.CREATE, View.DELETE)));

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
     * {@inheritDoc}
     */
    @Override
    public File getRootDirFor(P child) {
        return super.getRootDirFor(child); // need the bridge method to provide binary compatibility
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
            setSourcesList(req.bindJSONToList(BranchSource.class, json.opt("sources")));
            for (SCMSource scmSource : getSCMSources()) {
                scmSource.setOwner(this);
                _sources.add(scmSource);
            }
            setProjectFactory(req.bindJSON(BranchProjectFactory.class, json.getJSONObject("projectFactory")));
        }
        fireSCMSourceAfterSave(_sources);
        recalculateAfterSubmitted(updateDigests());
    }

    /**
     * Fires the {@link SCMSource#afterSave()} method for the supplied sources.
     * @param sources the sources.
     */
    protected void fireSCMSourceAfterSave(List<SCMSource> sources) {
        for (SCMSource scmSource : sources) {
            scmSource.afterSave();
        }
    }

    /**
     * Updates the digests used to detect changes to the sources and project factories (which would mandate a
     * recalculation).
     *
     * @return {@code true} if the digests have changed.
     */
    /*package*/ boolean updateDigests() {
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
        try {
            return !StringUtils.equals(srcDigest, this.srcDigest)
                    || !StringUtils.equals(facDigest, this.facDigest);
        } finally {
            this.srcDigest = srcDigest;
            this.facDigest = facDigest;
        }
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
            return Messages.MultiBranchProject_BranchIndexing_displayName(getParent().getSourcePronoun());
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
        return super.isBuildable() && !sources.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected FolderComputation<P> createComputation(FolderComputation<P> previous) {
        return new BranchIndexing<>(this, (BranchIndexing) previous);
    }

    /**
     * Inverse function of {@link Util#rawEncode(String)}
     *
     * @param s the encoded string.
     * @return the decoded string.
     */
    @NonNull
    public static String rawDecode(@NonNull String s) {
        // should be US-ASCII but we can be tolerant
        final byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
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
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
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

        private final EventOutputStreams globalEvents = createGlobalEvents();

        private EventOutputStreams createGlobalEvents() {
            File logsDir = new File(Jenkins.get().getRootDir(), "logs");
            if (!logsDir.isDirectory() && !logsDir.mkdirs()) {
                LOGGER.log(Level.WARNING, "Could not create logs directory: {0}", logsDir);
            }
            final File eventsFile = new File(logsDir, MultiBranchProject.class.getName() + ".log");
            if (!eventsFile.isFile()) {
                File oldFile = new File(logsDir.getParent(), eventsFile.getName());
                if (oldFile.isFile()) {
                    if (!oldFile.renameTo(eventsFile)) {
                        FileUtils.deleteQuietly(oldFile);
                    }
                }
            }
            return new EventOutputStreams(new EventOutputStreams.OutputFile() {
                @NonNull
                @Override
                public File get() {
                    return eventsFile;
                }
            },
                    250, TimeUnit.MILLISECONDS,
                    1024,
                    true,
                    32 * 1024,
                    5
            );
        }

        /**
         * The {@link TaskListener} for events that we cannot assign to a multi-branch project.
         *
         * @return The {@link TaskListener} for events that we cannot assign to a multi-branch project.
         */
        @Restricted(NoExternalUse.class)
        public StreamTaskListener globalEventsListener() {
            return new StreamBuildListener(globalEvents.get(), Charsets.UTF_8);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onSCMHeadEvent(final SCMHeadEvent<?> event) {
            try (StreamTaskListener global = globalEventsListener()) {
                String eventDescription = StringUtils.defaultIfBlank(event.description(), event.getClass().getName());
                String eventType = event.getType().name();
                String eventOrigin = event.getOrigin();
                long eventTimestamp = event.getTimestamp();
                global.getLogger().format("[%tc] Received %s %s event from %s with timestamp %tc%n",
                        System.currentTimeMillis(), eventDescription, eventType, eventOrigin,
                        eventTimestamp);
                LOGGER.log(Level.FINE, "{0} {1} {2,date} {2,time}: onSCMHeadEvent",
                        new Object[]{
                                eventDescription, eventType, eventTimestamp
                        }
                );
                int matchCount = 0;
                // not interested in removal of dead items as that needs to be handled by the computation in order
                // to ensure that other sources do not want to take ownership and also to ensure that the dead branch
                // strategy is enforced correctly
                try {
                    if (SCMEvent.Type.CREATED == event.getType()) {
                        matchCount = processHeadCreate(
                                event,
                                global,
                                eventDescription,
                                eventType,
                                eventOrigin,
                                eventTimestamp,
                                matchCount
                        );
                    } else if (SCMEvent.Type.UPDATED == event.getType() || SCMEvent.Type.REMOVED == event.getType()) {
                        matchCount = processHeadUpdate(
                                event,
                                global,
                                eventDescription,
                                eventType,
                                eventOrigin,
                                eventTimestamp,
                                matchCount
                        );
                    }
                } catch (InterruptedException e) {
                    printStackTrace(e, global.error("[%tc] Interrupted while processing %s %s event from %s with timestamp %tc",
                            System.currentTimeMillis(), eventDescription, eventType, eventOrigin, eventTimestamp));
                }
                global.getLogger()
                        .format("[%tc] Finished processing %s %s event from %s with timestamp %tc. Matched %d.%n",
                                System.currentTimeMillis(), eventDescription, eventType, eventOrigin,
                                eventTimestamp, matchCount);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not close global event log file", e);
            }
        }

        private int processHeadCreate(SCMHeadEvent<?> event, TaskListener global, String eventDescription,
                                      String eventType, String eventOrigin, long eventTimestamp, int matchCount)
                throws IOException, InterruptedException {
            Set<String> sourceIds = new HashSet<>();
            for (MultiBranchProject<?, ?> p : Jenkins.get().getAllItems(MultiBranchProject.class)) {
                String pFullName = p.getFullName();
                if (!p.isBuildable()) {
                    LOGGER.log(Level.FINER, "{0} {1} {2,date} {2,time}: Ignoring {3} because it is disabled",
                            new Object[]{
                                    eventDescription, eventType, eventTimestamp, pFullName
                            }
                    );
                    continue;
                }
                sourceIds.clear();
                LOGGER.log(Level.FINER, "{0} {1} {2,date} {2,time}: Checking {3} for a match",
                        new Object[]{
                                eventDescription, eventType, eventTimestamp, pFullName
                        }
                );
                boolean haveMatch = false;
                final BranchProjectFactory _factory = p.getProjectFactory();
                SOURCES:
                for (SCMSource source : p.getSCMSources()) {
                    if (event.isMatch(source)) {
                        LOGGER.log(Level.FINE, "{0} {1} {2,date} {2,time}: Project {3}: Matches source {4}",
                                new Object[]{
                                        eventDescription, eventType, eventTimestamp, pFullName, source.getId()
                                }
                        );
                        for (SCMHead h : event.heads(source).keySet()) {
                            String name = h.getName();
                            Job job = p.getItemByBranchName(name);
                            if (job == null) {
                                LOGGER.log(Level.FINE, "{0} {1} {2,date} {2,time}: Project {3}: Source {4}: Match: {5}",
                                        new Object[]{
                                                eventDescription, eventType, eventTimestamp, pFullName, source.getId(), name
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
                                                eventDescription, eventType, eventTimestamp, pFullName, source.getId(), name
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
                                                eventDescription,
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
                                                eventDescription,
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
                                            eventDescription,
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
                                        eventDescription, eventType, eventTimestamp, pFullName, source.getId()
                                }
                        );
                    } else {
                        LOGGER.log(Level.FINEST, "{0} {1} {2,date} {2,time}: Project {3}: Does not matches source {4}",
                                new Object[]{
                                        eventDescription, eventType,
                                        eventTimestamp, pFullName,
                                        source.getId()
                                }
                        );
                    }
                    sourceIds.add(source.getId());
                }
                if (haveMatch) {
                    long start = System.currentTimeMillis();
                    try (StreamTaskListener listener = p.getComputation().createEventsListener();
                         ChildObserver childObserver = p.openEventsChildObserver()) {
                        try {
                            listener.getLogger().format("[%tc] Received %s %s event from %s with timestamp %tc%n",
                                    start, eventDescription, eventType, eventOrigin, eventTimestamp);
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
                        } catch (IOException e) {
                            printStackTrace(e, listener.error(e.getMessage()));
                        } catch (InterruptedException e) {
                            printStackTrace(e, listener.error(e.getMessage()));
                            throw e;
                        } finally {
                            long end = System.currentTimeMillis();
                            listener.getLogger()
                                    .format("[%tc] %s %s event from %s with timestamp %tc processed in %s%n",
                                            end, eventDescription, eventType, eventOrigin, eventTimestamp,
                                            Util.getTimeSpanString(end - start));
                        }
                    } catch (IOException e) {
                        printStackTrace(e, global.error("[%tc] %s encountered an error while processing %s %s event from %s with timestamp %tc",
                                System.currentTimeMillis(), ModelHyperlinkNote.encodeTo(p), eventDescription, eventType, eventOrigin, eventTimestamp));
                    } catch (InterruptedException e) {
                        printStackTrace(e, global.error("[%tc] %s was interrupted while processing %s %s event from %s with timestamp %tc",
                                System.currentTimeMillis(), ModelHyperlinkNote.encodeTo(p), eventDescription, eventType, eventOrigin, eventTimestamp));
                        throw e;
                    }
                }
            }
            return matchCount;
        }

        private int processHeadUpdate(SCMHeadEvent<?> event, TaskListener global, String eventDescription,
                                      String eventType, String eventOrigin, long eventTimestamp, int matchCount)
                throws InterruptedException {
            Map<SCMSource, SCMHead> matches = new IdentityHashMap<>();
            Set<String> candidateNames = new HashSet<>();
            Map<SCMSource, Map<SCMHead,SCMRevision>> revisionMaps = new IdentityHashMap<>();
            Set<Job<?, ?>> jobs = new HashSet<>();
            for (MultiBranchProject<?, ?> p : Jenkins.get().getAllItems(MultiBranchProject.class)) {
                String pFullName = p.getFullName();
                if (!p.isBuildable()) {
                    LOGGER.log(Level.FINER, "{0} {1} {2,date} {2,time}: Ignoring {3} because it is disabled",
                            new Object[]{
                                    eventDescription, eventType, eventTimestamp, pFullName
                            }
                    );
                    continue;
                }
                LOGGER.log(Level.FINER, "{0} {1} {2,date} {2,time}: Checking {3} for a match",
                        new Object[]{
                                eventDescription, eventType, eventTimestamp, pFullName
                        }
                );
                final BranchProjectFactory _factory = p.getProjectFactory();
                matches.clear();
                candidateNames.clear();
                revisionMaps.clear();
                for (SCMSource source : p.getSCMSources()) {
                    if (event.isMatch(source)) {
                        Map<SCMHead, SCMRevision> eventHeads = event.heads(source);
                        if (!eventHeads.isEmpty()) {
                            revisionMaps.put(source, eventHeads);
                            for (SCMHead h : eventHeads.keySet()) {
                                candidateNames.add(h.getName());
                            }
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
                                            eventDescription, eventType, eventTimestamp, pFullName, branch.getName(),
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
                                            eventDescription, eventType, eventTimestamp, pFullName, branch.getName(),
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
                                                eventDescription, eventType, eventTimestamp, pFullName, branch.getName(),
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
                                                    eventDescription, eventType, eventTimestamp, pFullName, branch.getName(),
                                                    i.getName(), ourPriority, oldPriority
                                            }
                                    );
                                    continue;
                                } else {
                                    LOGGER.log(Level.FINER,
                                            "{0} {1} {2,date} {2,time}: Checking {3} -> Takeover event for {4} "
                                                    + "(job {5}) by source #{5} from source #{6}",
                                            new Object[]{
                                                    eventDescription, eventType, eventTimestamp, pFullName, branch.getName(),
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
                                                eventDescription, eventType, eventTimestamp, pFullName, branch.getName(),
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
                                                            eventDescription,
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
                    long start = System.currentTimeMillis();
                    try (StreamTaskListener listener = p.getComputation().createEventsListener();
                         ChildObserver childObserver = p.openEventsChildObserver()) {
                        try {
                            assert childObserver != null;
                            listener.getLogger().format("[%tc] Received %s %s event from %s with timestamp %tc%n",
                                    start, eventDescription, eventType, eventOrigin, eventTimestamp);
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
                        } catch (IOException e) {
                            printStackTrace(e, listener.error(e.getMessage()));
                        } catch (InterruptedException e) {
                            printStackTrace(e, listener.error(e.getMessage()));
                            throw e;
                        } finally {
                            long end = System.currentTimeMillis();
                            listener.getLogger()
                                    .format("[%tc] %s %s event from %s with timestamp %tc processed in %s%n",
                                            end, eventDescription, eventType, eventOrigin, eventTimestamp,
                                            Util.getTimeSpanString(end - start));
                        }
                    } catch (IOException e) {
                        printStackTrace(e, global.error(
                                "[%tc] %s encountered an error while processing %s %s event from %s with timestamp %tc",
                                System.currentTimeMillis(), ModelHyperlinkNote.encodeTo(p), eventDescription, eventType,
                                eventOrigin, eventTimestamp));
                    } catch (InterruptedException e) {
                        printStackTrace(e, global.error(
                                "[%tc] %s was interrupted while processing %s %s event from %s with timestamp %tc",
                                System.currentTimeMillis(), ModelHyperlinkNote.encodeTo(p), eventDescription, eventType,
                                eventOrigin, eventTimestamp));
                        throw e;
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
                                matchCount++;
                                global.getLogger().format("Found match against %s%n", pFullName);
                                break;
                            }
                            break;
                        }
                    }
                    if (haveMatch) {
                        long start = System.currentTimeMillis();
                        try (StreamTaskListener listener = p.getComputation().createEventsListener();
                             ChildObserver childObserver = p.openEventsChildObserver()) {
                            listener.getLogger().format("[%tc] Received %s %s event from %s with timestamp %tc%n",
                                    start, eventDescription, eventType, eventOrigin, eventTimestamp);
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
                            } catch (IOException e) {
                                printStackTrace(e, listener.error(e.getMessage()));
                            } catch (InterruptedException e) {
                                printStackTrace(e, listener.error(e.getMessage()));
                                throw e;
                            } finally {
                                long end = System.currentTimeMillis();
                                listener.getLogger().format(
                                        "[%tc] %s %s event from %s with timestamp %tc processed in %s%n",
                                        end, eventDescription, eventType, eventOrigin, eventTimestamp,
                                        Util.getTimeSpanString(end - start));
                            }
                        } catch (IOException e) {
                            printStackTrace(e, global.error(
                                    "[%tc] %s encountered an error while processing %s %s event from %s with "
                                            + "timestamp %tc",
                                    System.currentTimeMillis(), ModelHyperlinkNote.encodeTo(p), eventDescription, eventType,
                                    eventOrigin, eventTimestamp));
                        } catch (InterruptedException e) {
                            printStackTrace(e, global.error(
                                    "[%tc] %s was interrupted while processing %s %s event from %s with "
                                            + "timestamp %tc",
                                    System.currentTimeMillis(), ModelHyperlinkNote.encodeTo(p), eventDescription, eventType,
                                    eventOrigin, eventTimestamp));
                            throw e;
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
            try (StreamTaskListener global = globalEventsListener()) {
                String eventDescription = StringUtils.defaultIfBlank(event.description(), event.getClass().getName());
                global.getLogger().format("[%tc] Received %s %s event from %s with timestamp %tc%n",
                        System.currentTimeMillis(), eventDescription, event.getType().name(),
                        event.getOrigin(), event.getTimestamp());
                int matchCount = 0;
                // not interested in creation as that is an event for org folders
                // not interested in removal as that is an event for org folders
                if (SCMEvent.Type.UPDATED == event.getType()) {
                    // we are only interested in updates as they would trigger the actions being updated
                    try {
                        for (MultiBranchProject<?, ?> p : Jenkins.get()
                                .getAllItems(MultiBranchProject.class)) {
                            if (!p.isBuildable()) {
                                if (LOGGER.isLoggable(Level.FINER)) {
                                    LOGGER.log(Level.FINER,
                                            "{0} {1} {2,date} {2,time}: Ignoring {3} because it is disabled",
                                            new Object[]{
                                                    eventDescription, event.getType().name(), event.getTimestamp(),
                                                    p.getFullName()
                                            }
                                    );
                                }
                                continue;
                            }
                            boolean haveMatch = false;
                            List<SCMSource> scmSources = p.getSCMSources();
                            for (SCMSource s : scmSources) {
                                if (event.isMatch(s)) {
                                    matchCount++;
                                    global.getLogger().format("Found match against %s%n", p.getFullName());
                                    haveMatch = true;
                                    break;
                                }
                            }
                            if (haveMatch) {
                                try (StreamTaskListener listener = p.getComputation().createEventsListener()) {
                                    try {
                                        Map<String, List<Action>> stateActions = new HashMap<>();
                                        for (SCMSource source : scmSources) {
                                            List<Action> oldActions = p.state.sourceActions.get(source.getId());
                                            List<Action> newActions;
                                            try {
                                                newActions = source.fetchActions(event, listener);
                                            } catch (IOException e) {
                                                printStackTrace(e,
                                                        listener.error("Could not refresh actions for source %s",
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
                                                    // undo any hacks that attached the contributed actions without
                                                    // attribution
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
                                    } catch (IOException e) {
                                        printStackTrace(e, listener.error(e.getMessage()));
                                    } catch (InterruptedException e) {
                                        printStackTrace(e, listener.error(e.getMessage()));
                                        throw e;
                                    }
                                } catch (IOException e) {
                                    printStackTrace(e, global.error(
                                            "[%tc] %s encountered an error while processing %s %s event from %s with "
                                                    + "timestamp %tc",
                                            System.currentTimeMillis(), ModelHyperlinkNote.encodeTo(p),
                                            eventDescription, event.getType().name(),
                                            event.getOrigin(), event.getTimestamp()));
                                } catch (InterruptedException e) {
                                    printStackTrace(e, global.error(
                                            "[%tc] %s was interrupted while processing %s %s event from %s with "
                                                    + "timestamp %tc",
                                            System.currentTimeMillis(), ModelHyperlinkNote.encodeTo(p),
                                            eventDescription, event.getType().name(),
                                            event.getOrigin(), event.getTimestamp()));
                                    throw e;
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        printStackTrace(e, global.error(
                                "[%tc] Interrupted while processing %s %s event from %s with timestamp %tc",
                                System.currentTimeMillis(), eventDescription, event.getType().name(),
                                event.getOrigin(), event.getTimestamp()));
                    }
                }
                global.getLogger()
                        .format("[%tc] Finished processing %s %s event from %s with timestamp %tc. Matched %d.%n",
                                System.currentTimeMillis(), eventDescription, event.getType().name(),
                                event.getOrigin(), event.getTimestamp(), matchCount);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not close global event log file", e);
            }
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
         * @param source
         */
        @NonNull
        abstract Cause[] create(SCMSource source);
    }

    /**
     * A cause factory for {@link BranchIndexingCause}.
     */
    private static class IndexingCauseFactory extends CauseFactory {
        /**
         * {@inheritDoc}
         * @param source
         */
        @NonNull
        @Override
        Cause[] create(SCMSource source) {
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
         * @param source
         */
        @NonNull
        @Override
        Cause[] create(SCMSource source) {
            Cause[] eventCauses = event.asCauses();
            Cause[] result = new Cause[eventCauses.length + 1];
            result[0] = new BranchEventCause(event, event.descriptionFor(source));
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
        public void observe(@NonNull SCMHead head, @NonNull SCMRevision revision) throws IOException, InterruptedException {
            Branch branch = newBranch(source, head);
            String rawName = branch.getName();
            String encodedName = branch.getEncodedName();
            P project = observer.shouldUpdate(encodedName);
            try {
                Branch origBranch = getOrigBranch(project);
                setBranchActions(head, branch, origBranch);
                Action[] revisionActions = getRevisionActions(revision, rawName);
                if (project != null) {
                    if (origBranch == null) {
                        return;
                    }
                    observeExisting(head, revision, branch, rawName, project, origBranch, revisionActions);
                } else {
                    observeNew(head, revision, branch, rawName, encodedName, revisionActions);
                }
            } finally {
                observer.completed(encodedName);
            }
        }

        private void observeExisting(@NonNull SCMHead head, @NonNull SCMRevision revision, @NonNull Branch branch, String rawName, P project, Branch origBranch, Action[] revisionActions) {
            boolean rebuild = (origBranch instanceof Branch.Dead && !(branch instanceof Branch.Dead))
                    || !(source.getId().equals(origBranch.getSourceId()));
            boolean needSave = !branch.equals(origBranch)
                    || !branch.getActions().equals(origBranch.getActions())
                    || !Util.getDigestOf(Items.XSTREAM2.toXML(branch.getScm()))
                    .equals(Util.getDigestOf(Items.XSTREAM2.toXML(origBranch.getScm())));
            _factory.decorate(_factory.setBranch(project, branch));
            if (rebuild) {
                needSave = true;
                listener.getLogger().format(
                        "%s reopened: %s (%s)%n",
                        StringUtils.defaultIfEmpty(head.getPronoun(), "Branch"),
                        rawName,
                        revision
                );
                // the previous "revision" for this head is not a revision for the current source
                // either because the head was removed and then recreated, or because the head
                // was taken over by a different source, thus the previous revision is null
                doAutomaticBuilds(head, revision, rawName, project, revisionActions, null, null);
            } else {
                // get the previous revision
                SCMRevision scmLastBuiltRevision = _factory.getRevision(project);

                if (changesDetected(revision, project, scmLastBuiltRevision)) {
                    listener.getLogger()
                            .format("Changes detected: %s (%s → %s)%n", rawName, scmLastBuiltRevision, revision);

                    needSave = true;
                    // get the previous seen revision
                    SCMRevision scmLastSeenRevision = lastSeenRevisionOrDefault(project, scmLastBuiltRevision);
                    doAutomaticBuilds(head, revision, rawName, project, revisionActions, scmLastBuiltRevision, scmLastSeenRevision);

                } else {
                    listener.getLogger().format("No changes detected: %s (still at %s)%n", rawName, revision);
                }

            }

            try {
                if (needSave) {
                    project.save();
                }
            } catch (IOException e) {
                printStackTrace(e, listener.error("Could not save changes to " + rawName));
            }
        }

        private void observeNew(@NonNull SCMHead head, @NonNull SCMRevision revision, @NonNull Branch branch, String rawName, String encodedName, Action[] revisionActions) {
            P project;
            if (!observer.mayCreate(encodedName)) {
                listener.getLogger().println("Ignoring duplicate branch project " + rawName);
                return;
            }
            try (ChildNameGenerator.Trace trace = ChildNameGenerator.beforeCreateItem(
                    MultiBranchProject.this, encodedName, branch.getName()
            )) {
                if (getItem(encodedName) != null) {
                    throw new IllegalStateException(
                            "JENKINS-42511: attempted to redundantly create " + encodedName + " in "
                                    + MultiBranchProject.this);
                }
                project = _factory.newInstance(branch);
            }
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
            // decorate contract is to ensure it does not trigger a save
            _factory.decorate(project);
            // ok it is now up to the observer to ensure it does the actual save.
            observer.created(project);
            doAutomaticBuilds(head, revision, rawName, project, revisionActions, null, null);
        }

        private boolean changesDetected(@NonNull SCMRevision revision, @NonNull P project, SCMRevision scmLastBuiltRevision) {
            boolean changesDetected = false;

            if (revision.isDeterministic()) {
                if (!revision.equals(scmLastBuiltRevision)) {
                    changesDetected = true;
                }
            } else {
                // TODO check if we should compare the revisions anyway...
                // the revisions may not be deterministic, as in two checkouts of the same revision
                // may result in additional changes (looking at you CVS) but that doesn't mean
                // that the revisions are not capable of checking for differences, e.g. if
                // rev1 == 2017-12-13 and rev2 == 2017-12-12 these could show as non-equal while both
                // being non-deterministic (because they just specify the day not the timestamp within
                // the day.

                // fall back to polling when we have a non-deterministic revision/hash.
                SCMTriggerItem scmProject = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(project);
                if (scmProject != null) {
                    PollingResult pollingResult = scmProject.poll(listener);
                    if (pollingResult.hasChanges()) {
                        changesDetected = true;
                    }
                }
            }
            return changesDetected;
        }

        private Action[] getRevisionActions(@NonNull SCMRevision revision, String rawName) {
            Action[] revisionActions = new Action[0];
            try {
                List<Action> actions = source.fetchActions(revision, event, listener);
                revisionActions = actions.toArray(new Action[actions.size()]);
            } catch (IOException | InterruptedException e) {
                printStackTrace(e, listener.error("Could not fetch metadata for revision %s of branch %s",
                        revision, rawName));
            }
            return revisionActions;
        }

        private void setBranchActions(@NonNull SCMHead head, @NonNull Branch branch, @CheckForNull Branch origBranch) {
            try {
                branch.setActions(source.fetchActions(head, event, listener));
            } catch (IOException | InterruptedException e) {
                printStackTrace(e, listener.error("Could not fetch metadata of branch %s", branch.getName()));
                if (origBranch != null) {
                    // we didn't fetch them so replicate previous actions
                    branch.setActions(origBranch.getActions());
                }
            }
        }

        private Branch getOrigBranch(P project) {
            Branch origBranch = null;
            if (project != null) {
                if (!_factory.isProject(project)) {
                    listener.getLogger().println("Detected unsupported subitem "
                            + ModelHyperlinkNote.encodeTo(project) + ", skipping");
                } else {
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
                                origBranch = null;
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
            }
            return origBranch;
        }

        private SCMRevision lastSeenRevisionOrDefault(@NonNull P project, SCMRevision scmLastBuiltRevision) {
            SCMRevision scmLastSeenRevision = _factory.getLastSeenRevision(project);
            if (scmLastSeenRevision == null && scmLastBuiltRevision != null) {
                scmLastSeenRevision = scmLastBuiltRevision;
                try {
                    _factory.setLastSeenRevisionHash(project, scmLastBuiltRevision);
                } catch (IOException e) {
                    printStackTrace(e, listener.error("Could not update last seen revision hash"));
                }
            }
            return scmLastSeenRevision;
        }

        private void doAutomaticBuilds(@NonNull SCMHead head, @NonNull SCMRevision revision, @NonNull String rawName, @NonNull P project, Action[] revisionActions, SCMRevision scmLastBuiltRevision, SCMRevision scmLastSeenRevision) {
            if (isAutomaticBuild(head, revision, scmLastBuiltRevision, scmLastSeenRevision)) {
                scheduleBuild(
                        _factory,
                        project,
                        revision,
                        listener,
                        rawName,
                        causeFactory.create(source),
                        revisionActions
                );
            } else {
                listener.getLogger().format("No automatic build triggered for %s%n", rawName);
            }
            try {
                _factory.setLastSeenRevisionHash(project, revision);
            } catch (IOException e) {
                printStackTrace(e, listener.error("Could not update last seen revision hash"));
            }
        }

        /**
         * Tests if the specified {@link SCMHead} should be automatically built when discovered / modified.
         * @param head the head.
         * @param currRevision the current built revision.
         * @param lastBuiltRevision the previous built revision
         * @param lastSeenRevision the last seen revision
         * @return {@code true} if the head should be automatically built when discovered / modified.
         */
        private boolean isAutomaticBuild(@NonNull SCMHead head,
                                         @NonNull SCMRevision currRevision,
                                         @CheckForNull SCMRevision lastBuiltRevision,
                                         @CheckForNull SCMRevision lastSeenRevision) {
            BranchSource branchSource = null;
            for (BranchSource s: MultiBranchProject.this.sources) {
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
                    if (s.automaticBuild(source, head, currRevision, lastBuiltRevision, lastSeenRevision, listener)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    /**
     * Adds the {@link MultiBranchProject.State#sourceActions} to
     * {@link MultiBranchProject#getAllActions()}.
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

    /**
     * Veto attempts to copy branch projects outside of their multibranch container. Only works on Jenkins core
     * versions with JENKINS-34691 merged.
     */
    @Extension
    public static class CopyItemVeto extends ItemListener {

        /**
         * {@inheritDoc}
         */
        @Override
        public void onCheckCopy(Item item, ItemGroup parent) throws Failure {
            if (item.getParent() instanceof MultiBranchProject) {
                throw new Failure(Messages.MultiBranchProject_CopyItemVeto_reason());
            }
        }
    }
}
