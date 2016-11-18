/*
 * The MIT License
 *
 * Copyright (c) 2011-2016, CloudBees, Inc., Stephen Connolly.
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
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.View;
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
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
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
import jenkins.scm.impl.NullSCMSource;
import jenkins.triggers.SCMTriggerItem;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.apache.commons.io.Charsets;
import org.apache.commons.lang.StringUtils;
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
    public String getPronoun() {
        Set<String> result = new TreeSet<>();
        for (BranchSource source: sources) {
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
    public boolean isHasEvents() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void computeChildren(final ChildObserver<P> observer, final TaskListener listener)
            throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        listener.getLogger().format("[%tc] Starting branch indexing...%n", start);
        try {
            final BranchProjectFactory<P, R> _factory = getProjectFactory();
            List<SCMSource> scmSources = getSCMSources();
            Map<Class<? extends Action>, Action> persistentActions = new HashMap<>();
            for (ListIterator<SCMSource> iterator = scmSources.listIterator(scmSources.size());
                 iterator.hasPrevious(); ) {
                SCMSource source = iterator.previous();
                persistentActions.putAll(source.fetchActions(listener)); // first source always wins
            }
            // update any persistent actions for the SCMSource
            if (!persistentActions.isEmpty()) {
                BulkChange bc = new BulkChange(this);
                try {
                    for (Map.Entry<Class<? extends Action>, Action> entry : persistentActions.entrySet()) {
                        if (entry.getValue() == null) {
                            removeActions(entry.getKey());
                        } else {
                            replaceActions(entry.getKey(), entry.getValue());
                        }
                    }
                    bc.commit();
                } finally {
                    bc.abort();
                }
            }
            for (final SCMSource source : scmSources) {
                source.fetch(new SCMHeadObserverImpl(source, observer, listener, _factory, new BranchIndexingCause()),
                        listener);
            }
        } finally {
            long end = System.currentTimeMillis();
            listener.getLogger().format("[%tc] Finished branch indexing. Indexing took %s%n", end,
                    Util.getTimeSpanString(end - start));
        }
    }

    private void scheduleBuild(BranchProjectFactory<P, R> factory, final P item, SCMRevision revision,
                               TaskListener listener, String name, Cause cause, Action... actions) {
        Action[] _actions = new Action[actions.length+1];
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
    protected Collection<P> orphanedItems(Collection<P> orphaned, TaskListener listener) throws IOException, InterruptedException {
        BranchProjectFactory<P, R> _factory = getProjectFactory();
        for (P project : orphaned) {
            if (!_factory.isProject(project)) {
                listener.getLogger().println("Detected unsupported subitem " + project + ", skipping");
                continue; // TODO perhaps better to remove from list passed to super, and return it from here
            }
            Branch b = _factory.getBranch(project);
            if (!(b instanceof Branch.Dead)) {
                _factory.decorate(
                        _factory.setBranch(project, new Branch.Dead(b.getHead(), b.getProperties(), b.getActions())));
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
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        String description = super.getDescription();
        if (StringUtils.isNotBlank(description)) {
            return description;
        }
        MetadataAction action = getAction(MetadataAction.class);
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
            MetadataAction action = getAction(MetadataAction.class);
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
     * Creates a place holder view when there's no active branch indexed.
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

    @Extension
    public static class SCMEventListenerImpl extends SCMEventListener {
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

        @Override
        public void onSCMHeadEvent(SCMHeadEvent<?> event) {
            TaskListener global = globalEventsListener();
            global.getLogger().format("[%tc] Received %s %s event with timestamp %tc%n",
                    System.currentTimeMillis(), event.getClass().getName(), event.getType().name(),
                    event.getTimestamp());
            // not interested in removal as that needs to be handled by the computation in order to ensure that
            // other sources do not want to take ownership and also to ensure that the dead branch strategy
            // is enforced correctly
            if (SCMEvent.Type.CREATED == event.getType()) {
                for (MultiBranchProject<?, ?> p : Jenkins.getActiveInstance().getAllItems(MultiBranchProject.class)) {
                    boolean haveMatch = false;
                    final BranchProjectFactory _factory = p.getProjectFactory();
                    for (SCMSource source : p.getSCMSources()) {
                        if (event.isMatch(source)) {
                            haveMatch = true;
                            global.getLogger().format("Found match against %s%n", p.getFullName());
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
                                start, event.getType().name(), event.getTimestamp());
                        ChildObserver childObserver = p.createEventsChildObserver();
                        try {
                            for (SCMSource source : p.getSCMSources()) {
                                if (event.isMatch(source)) {
                                    source.fetch(
                                            p.getSCMSourceCriteria(source),
                                            event.filter(
                                                    source,
                                                    p.new SCMHeadObserverImpl(
                                                            source,
                                                            childObserver,
                                                            listener,
                                                            _factory, new BranchEventCause(event)
                                                    )
                                            ),
                                            listener
                                    );
                                }
                            }
                        } catch (IOException | InterruptedException e) {
                            e.printStackTrace(listener.error(e.getMessage()));
                        } finally {
                            long end = System.currentTimeMillis();
                            listener.getLogger().format("[%tc] %s event processed in %s%n",
                                    end, event.getType().name(), Util.getTimeSpanString(end - start));
                        }
                    }
                }
            } else if (SCMEvent.Type.UPDATED == event.getType() || SCMEvent.Type.REMOVED == event.getType()) {
                Map<SCMSource, SCMHead> matches = new IdentityHashMap<>();
                Set<Job<?, ?>> jobs = new HashSet<>();
                for (MultiBranchProject<?, ?> p : Jenkins.getActiveInstance().getAllItems(MultiBranchProject.class)) {
                    final BranchProjectFactory _factory = p.getProjectFactory();
                    matches.clear();
                    jobs.clear();
                    for (Job i : p.getItems()) {
                        if (_factory.isProject(i)) {
                            Branch branch = _factory.getBranch(i);
                            if (branch instanceof Branch.Dead) {
                                // try to bring back from the dead by checking all sources in sequence
                                // the first to match the event and that contains the head wins!
                                for (SCMSource src : p.getSCMSources()) {
                                    if (!event.isMatch(src)) {
                                        continue;
                                    }
                                    Map<SCMHead, SCMRevision> revisionMap = event.heads(src);
                                    SCMHead head = branch.getHead();
                                    if (revisionMap.containsKey(head)) {
                                        matches.put(src, head);
                                        jobs.add(i);
                                        break;
                                    }
                                }
                            } else {
                                SCMSource src = p.getSCMSource(branch.getSourceId());
                                if (src == null) {
                                    continue;
                                }
                                Map<SCMHead, SCMRevision> revisionMap = event.heads(src);
                                SCMHead head = branch.getHead();
                                if (revisionMap.containsKey(head)) {
                                    matches.put(src, head);
                                    jobs.add(i);
                                }
                            }
                        }
                    }
                    if (!matches.isEmpty()) {
                        global.getLogger().format("Found match against %s%n", p.getFullName());
                        TaskListener listener;
                        try {
                            listener = p.getComputation().createEventsListener();
                        } catch (IOException e) {
                            listener = new LogTaskListener(LOGGER, Level.FINE);
                        }
                        long start = System.currentTimeMillis();
                        listener.getLogger().format("[%tc] Received %s event with timestamp %tc%n",
                                start, event.getType().name(), event.getTimestamp());
                        ChildObserver childObserver = p.createEventsChildObserver();
                        try {
                            for (Map.Entry<SCMSource, SCMHead> m : matches.entrySet()) {
                                m.getKey().fetch(
                                        p.getSCMSourceCriteria(m.getKey()),
                                        SCMHeadObserver.filter(
                                                p.new SCMHeadObserverImpl(
                                                        m.getKey(),
                                                        childObserver,
                                                        listener,
                                                        _factory, new BranchEventCause(event)
                                                ),
                                                m.getValue()
                                        ),
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
                                        new Branch.Dead(branch.getHead(), branch.getProperties())
                                ));
                                j.save();
                            }
                        } catch (IOException | InterruptedException e) {
                            e.printStackTrace(listener.error(e.getMessage()));
                        } finally {
                            long end = System.currentTimeMillis();
                            listener.getLogger().format("[%tc] %s event processed in %s%n",
                                    end, event.getType().name(), Util.getTimeSpanString(end - start));
                        }
                    } else {
                        // didn't match an existing branch, maybe the criteria now match against an updated branch
                        boolean haveMatch = false;
                        for (SCMSource source : p.getSCMSources()) {
                            if (event.isMatch(source)) {
                                haveMatch = true;
                                global.getLogger().format("Found match against %s%n", p.getFullName());
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
                                    start, event.getType().name(), event.getTimestamp());
                            ChildObserver childObserver = p.createEventsChildObserver();
                            try {
                                for (SCMSource source : p.getSCMSources()) {
                                    if (event.isMatch(source)) {
                                        source.fetch(
                                                p.getSCMSourceCriteria(source),
                                                event.filter(
                                                        source,
                                                        p.new SCMHeadObserverImpl(
                                                                source,
                                                                childObserver,
                                                                listener,
                                                                _factory, new BranchEventCause(event)
                                                        )
                                                ),
                                                listener
                                        );
                                    }
                                }
                            } catch (IOException | InterruptedException e) {
                                e.printStackTrace(listener.error(e.getMessage()));
                            } finally {
                                long end = System.currentTimeMillis();
                                listener.getLogger().format("[%tc] %s event processed in %s%n",
                                        end, event.getType().name(), Util.getTimeSpanString(end - start));
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void onSCMSourceEvent(SCMSourceEvent<?> event) {
            TaskListener global = globalEventsListener();
            global.getLogger().format("[%tc] Received %s %s event with timestamp %tc%n",
                    System.currentTimeMillis(), event.getClass().getName(), event.getType().name(),
                    event.getTimestamp());
            // not interested in creation as that is an event for org folders
            // not interested in removal as that is an event for org folders
            if (SCMEvent.Type.UPDATED == event.getType()) {
                // we are only interested in updates as they would trigger the actions being updated
                for (MultiBranchProject<?, ?> p : Jenkins.getActiveInstance().getAllItems(MultiBranchProject.class)) {
                    boolean haveMatch = false;
                    List<SCMSource> scmSources = p.getSCMSources();
                    for (SCMSource source : scmSources) {
                        if (event.isMatch(source)) {
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
                            Map<Class<? extends Action>, Action> persistentActions = new HashMap<>();
                            for (ListIterator<SCMSource> iterator = scmSources.listIterator(scmSources.size());
                                 iterator.hasPrevious(); ) {
                                SCMSource source = iterator.previous();
                                persistentActions.putAll(source.fetchActions(listener)); // first source always wins
                            }
                            // update any persistent actions for the SCMSource
                            if (!persistentActions.isEmpty()) {
                                BulkChange bc = new BulkChange(p);
                                try {
                                    for (Map.Entry<Class<? extends Action>, Action> entry : persistentActions
                                            .entrySet()) {
                                        if (entry.getValue() == null) {
                                            p.removeActions(entry.getKey());
                                        } else {
                                            p.replaceActions(entry.getKey(), entry.getValue());
                                        }
                                    }
                                    bc.commit();
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
        }
    }

    private class SCMHeadObserverImpl extends SCMHeadObserver {
        private final SCMSource source;
        private final ChildObserver<P> observer;
        private final TaskListener listener;
        private final BranchProjectFactory<P, R> _factory;
        private final Cause cause;

        public SCMHeadObserverImpl(SCMSource source, ChildObserver<P> observer, TaskListener listener,
                                   BranchProjectFactory<P, R> _factory, Cause cause) {
            this.source = source;
            this.observer = observer;
            this.listener = listener;
            this._factory = _factory;
            this.cause = cause;
        }

        @Override
        public void observe(@NonNull SCMHead head, @NonNull SCMRevision revision) {
            Branch branch = newBranch(source, head);
            String rawName = branch.getName();
            String encodedName = branch.getEncodedName();
            P project = observer.shouldUpdate(encodedName);
            Map<Class<? extends Action>, Action> headActions = null;
            Action[] revisionActions = new Action[0];
            try {
                headActions = source.fetchActions(head, listener);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace(listener.error("Could not fetch metadata of branch %s", rawName));
            }
            if (headActions != null) {
                branch.getActions().addAll(headActions.values());
            }
            try {
                List<Action> actions = new ArrayList<Action>();
                for (Action a : source.fetchActions(revision, listener).values()) {
                    if (a != null) {
                        actions.add(a);
                    }
                }
                revisionActions = actions.toArray(new Action[actions.size()]);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace(listener.error("Could not fetch metadata for revision %s of branch %s",
                        revision, rawName));
            }
            if (project != null) {
                if (!_factory.isProject(project)) {
                    listener.getLogger().println("Detected unsupported subitem " + project + ", skipping");
                    return;
                }
                Branch origBranch = _factory.getBranch(project);
                boolean rebuild = origBranch instanceof Branch.Dead && !(branch instanceof Branch.Dead);
                boolean needSave = !branch.equals(origBranch) || !branch.getActions().equals(origBranch.getActions());
                _factory.decorate(_factory.setBranch(project, branch));
                if (rebuild) {
                    listener.getLogger().format(
                            "%s reopened: %s (%s)%n",
                            StringUtils.defaultIfEmpty(head.getPronoun(), "Branch"),
                            rawName,
                            revision
                    );
                    needSave = true;
                    scheduleBuild(_factory, project, revision, listener, rawName, cause, revisionActions);
                } else if (revision.isDeterministic()) {
                    SCMRevision lastBuild = _factory.getRevision(project);
                    if (!revision.equals(lastBuild)) {
                        listener.getLogger().format("Changes detected: %s (%s → %s)%n", rawName, lastBuild, revision);
                        needSave = true;
                        scheduleBuild(_factory, project, revision, listener, rawName, cause, revisionActions);
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
                            scheduleBuild(_factory, project, revision, listener, rawName, cause, revisionActions);
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
            scheduleBuild(_factory, project, revision, listener, rawName, cause, revisionActions);
        }
    }
}
