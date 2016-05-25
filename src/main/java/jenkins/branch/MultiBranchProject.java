/*
 * The MIT License
 *
 * Copyright (c) 2011-2013, CloudBees, Inc., Stephen Connolly.
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

import com.cloudbees.hudson.plugins.folder.computed.ChildObserver;
import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;
import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.CauseAction;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.scm.PollingResult;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.util.PersistedList;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.impl.NullSCMSource;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import javax.servlet.ServletException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.triggers.SCMTriggerItem;

/**
 * Abstract base class for multiple-branch based projects.
 *
 * @param <P> the project type
 * @param <R> the run type
 */
public abstract class MultiBranchProject<P extends Job<P, R> & TopLevelItem,
        R extends Run<P, R>>
        extends ComputedFolder<P> implements SCMSourceOwner {

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(MultiBranchProject.class.getName());

    /**
     * The user supplied branch sources.
     */
    private /*almost final*/ PersistedList<BranchSource> sources = new BranchSourceList(this);

    /**
     * The source for dead branches.
     */
    private transient /*almost final*/ NullSCMSource nullSCMSource;

    /**
     * The factory for building child job instances.
     */
    private BranchProjectFactory<P, R> factory;

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
    protected void computeChildren(final ChildObserver<P> observer, final TaskListener listener) throws IOException, InterruptedException {
        final BranchProjectFactory<P, R> _factory = getProjectFactory();
        for (final SCMSource source : getSCMSources()) {
            source.fetch(new SCMHeadObserver() {
                @Override
                public void observe(@NonNull SCMHead head, @NonNull SCMRevision revision) {
                    Branch branch = newBranch(source, head);
                    String rawName = branch.getName();
                    String encodedName = branch.getEncodedName();
                    P project = observer.shouldUpdate(encodedName);
                    if (project != null) {
                        if (!_factory.isProject(project)) {
                            listener.getLogger().println("Detected unsupported subitem " + project + ", skipping");
                            return;
                        }
                        boolean needSave = !branch.equals(_factory.getBranch(project));
                        _factory.decorate(_factory.setBranch(project, branch));
                        if (revision.isDeterministic()) {
                            SCMRevision lastBuild = _factory.getRevision(project);
                            if (!revision.equals(lastBuild)) {
                                listener.getLogger().println("Changes detected in " + rawName + " (" + lastBuild + " → " + revision + ")");
                                needSave = true;
                                scheduleBuild(_factory, project, revision, listener, rawName);
                            } else {
                                listener.getLogger().println("No changes detected in " + rawName + " (still at " + revision + ")");
                            }
                        } else {
                            // fall back to polling when we have a non-deterministic revision/hash.
                            SCMTriggerItem scmProject = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(project);
                            if (scmProject != null) {
                                PollingResult pollingResult = scmProject.poll(listener);
                                if (pollingResult.hasChanges()) {
                                    listener.getLogger().println("Changes detected in " + rawName);
                                    needSave = true;
                                    scheduleBuild(_factory, project, revision, listener, rawName);
                                } else {
                                    listener.getLogger().println("No changes detected in " + rawName);
                                }
                            }
                        }
                        if (needSave) {
                            try {
                                project.save();
                            } catch (IOException e) {
                                e.printStackTrace(listener.error("Could not save changes to " + rawName));
                            }
                        }
                        return;
                    }
                    if (!observer.mayCreate(encodedName)) {
                        listener.getLogger().println("Ignoring duplicate branch project " + rawName);
                        return;
                    }
                    project = _factory.newInstance(branch);
                    if (!project.getName().equals(encodedName)) {
                        throw new IllegalStateException("Name of created project " + project + " did not match expected " + encodedName);
                    }
                    if (!rawName.equals(encodedName) && project.getDisplayName().equals(encodedName)) {
                        try {
                            project.setDisplayName(rawName);
                        } catch (IOException e) {
                            e.printStackTrace(listener.error("Could not save changes to " + rawName));
                        }
                    }
                    _factory.decorate(project);
                    observer.created(project);
                    scheduleBuild(_factory, project, revision, listener, rawName);
                }
            }, listener);
        }
    }

    private void scheduleBuild(BranchProjectFactory<P,R> factory, final P item, SCMRevision revision, TaskListener listener, String name) {
        if (ParameterizedJobMixIn.scheduleBuild2(item, 0, new CauseAction(new BranchIndexingCause())) != null) {
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
    protected Collection<P> orphanedItems(Collection<P> orphaned, TaskListener listener) throws IOException, InterruptedException {
        BranchProjectFactory<P, R> _factory = getProjectFactory();
        for (P project : orphaned) {
            if (!_factory.isProject(project)) {
                listener.getLogger().println("Detected unsupported subitem " + project + ", skipping");
                continue; // TODO perhaps better to remove from list passed to super, and return it from here
            }
            Branch b = _factory.getBranch(project);
            if (!(b instanceof Branch.Dead)) {
                _factory.decorate(_factory.setBranch(project, new Branch.Dead(b.getHead(), b.getProperties())));
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
            // fall through for double decoded call paths
        }
        return super.getItem(name);
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
    private static final Set<Permission> SUPPRESSED_PERMISSIONS = ImmutableSet.of(Item.CONFIGURE, Item.DELETE, View.CONFIGURE, View.CREATE, View.DELETE);

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
        synchronized (this) {
                JSONObject json = req.getSubmittedForm();

                /*
                Set<String> oldSourceIds = new HashSet<String>();
                for (SCMSource source : getSCMSources()) {
                    oldSourceIds.add(source.getId());
                }
                */

                sources.replaceBy(req.bindJSONToList(BranchSource.class, json.opt("sources")));
                for (SCMSource scmSource : getSCMSources()) {
                    scmSource.setOwner(this);
                }

                setProjectFactory(req.bindJSON(BranchProjectFactory.class, json.getJSONObject("projectFactory")));

                save();

                /* TODO currently ComputedFolder.save always reschedules indexing; could define API to be more discerning
                Set<String> newSourceIds = new HashSet<String>();
                for (SCMSource source : getSCMSources()) {
                    newSourceIds.add(source.getId());
                }
                reindex = !newSourceIds.equals(oldSourceIds);
                */
        }
    }

    /**
     * {@inheritDoc}
     */
    @Exported
    @NonNull
    @SuppressWarnings("unused") // duck API for view container
    @Override
    public View getPrimaryView() {
        if (getItems().isEmpty()) {
            // when there are no branches nor pull requests to show, switch to the special welcome view
            return getWelcomeView();
        }
        return super.getPrimaryView();
    }

    /**
     * Creates a place holder view when there's no active branch indexed.
     */
    protected View getWelcomeView() {
        return new MultiBranchProjectEmptyView(this);
    }

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

        public BranchIndexing(@NonNull MultiBranchProject<P,R> project, @CheckForNull BranchIndexing<P, R> previousIndexing) {
            super(project, previousIndexing);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MultiBranchProject<P,R> getParent() {
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
            return "Branch Indexing";
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
        return new BranchIndexing<P,R>(this, (BranchIndexing) previous);
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

        BranchSourceList(MultiBranchProject<?,?> owner) {
            super(owner);
        }

        @Override
        protected void onModified() throws IOException {
            super.onModified();
            for (BranchSource branchSource : this) {
                branchSource.getSource().setOwner((MultiBranchProject) owner);
            }
        }

    }

}
