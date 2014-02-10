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

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.Util;
import hudson.XmlFile;
import hudson.console.AnnotatedLargeText;
import hudson.model.AbstractBuild;
import hudson.model.AbstractItem;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.AsyncPeriodicWork;
import hudson.model.BallColor;
import hudson.model.BuildableItem;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Computer;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.HealthReport;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Label;
import hudson.model.ListView;
import hudson.model.Node;
import hudson.model.PeriodicWork;
import hudson.model.Queue;
import hudson.model.ResourceList;
import hudson.model.Result;
import hudson.model.Saveable;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.model.ViewGroupMixIn;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.SaveableListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.queue.SubTask;
import hudson.scheduler.CronTabList;
import hudson.scm.PollingResult;
import hudson.security.ACL;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FlushProofOutputStream;
import hudson.util.FormApply;
import hudson.util.PersistedList;
import hudson.util.TimeUnit2;
import hudson.views.DefaultViewsTabBar;
import hudson.views.ViewsTabBar;
import jenkins.model.Jenkins;
import jenkins.model.ProjectNamingStrategy;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.impl.NullSCMSource;
import jenkins.util.TimeDuration;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerFallback;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

/**
 * Abstract base class for multiple-branch based projects.
 *
 * @param <P> the project type
 * @param <R> the run type
 */
public abstract class MultiBranchProject<P extends AbstractProject<P, R> & TopLevelItem,
        R extends AbstractBuild<P, R>>
        extends AbstractItem implements TopLevelItem, ItemGroup<P>, Saveable, ViewGroup, StaplerFallback,
        SCMSourceOwner, BuildableItem, Queue.FlyweightTask {

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(MultiBranchProject.class.getName());

    /**
     * The user supplied branch sources.
     */
    private final PersistedList<BranchSource> sources = new PersistedList<BranchSource>(this);

    /**
     * The source for dead branches.
     */
    private transient /*almost final*/ NullSCMSource nullSCMSource;

    /**
     * The branch source for dead branches.
     */
    private /*almost final*/ DeadBranchStrategy deadBranchStrategy;

    /**
     * The memory cache of all child jobs for each branch.
     */
    @CheckForNull
    private transient Map<String, Map<String, P>> branchItems;

    /**
     * Our indexing.
     */
    @CheckForNull
    private transient BranchIndexing<P, R> indexing;

    /**
     * The factory for building child job instances.
     */
    private BranchProjectFactory<P, R> factory;

    /**
     * The implementation of {@link ViewGroup} that we delegate to.
     */
    private transient /*almost final*/ ViewGroupMixIn viewGroupMixIn;

    /**
     * {@link View}s.
     */
    private /*almost final*/ CopyOnWriteArrayList<View> views;

    /**
     * Currently active Views tab bar.
     */
    private volatile ViewsTabBar viewsTabBar;

    /**
     * Name of the primary view.
     */
    private volatile String primaryView;

    /**
     * List of all {@link Trigger}s for this project.
     */
    private List<Trigger<?>> triggers = new Vector<Trigger<?>>();

    /**
     * Constructor, mandated by {@link TopLevelItem}.
     *
     * @param parent the parent of this multibranch job.
     * @param name   the name of the multibranch job.
     */
    protected MultiBranchProject(ItemGroup parent, String name) {
        super(parent, name);
        init();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        init();

    }

    /**
     * Consolidated initialization code.
     */
    private synchronized void init() {
        if (nullSCMSource == null) {
            nullSCMSource = new NullSCMSource();
        }
        nullSCMSource.setOwner(this);
        if (views == null) {
            views = new CopyOnWriteArrayList<View>();
        }
        if (views.size() == 0) {
            ListView lv = new ListView("All", this);
            views.add(lv);
            try {
                try {// HACK ALERT: ListView doesn't expose us a method to set this as of 1.480 yet,
                    // so we forcibly do it.
                    Field f = ListView.class.getDeclaredField("includeRegex");
                    f.setAccessible(true);
                    f.set(lv, ".*");
                    f = ListView.class.getDeclaredField("includePattern");
                    f.setAccessible(true);
                    f.set(lv, Pattern.compile(".*"));
                } catch (Throwable e) {
                    LOGGER.log(Level.WARNING, "Initial view may be configured incorrectly", e);
                }
                lv.save();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to set up the initial view", e);
            }
        }
        if (viewsTabBar == null) {
            viewsTabBar = new DefaultViewsTabBar();
        }
        if (primaryView == null) {
            primaryView = views.get(0).getViewName();
        }
        viewGroupMixIn = new ViewGroupMixIn(this) {
            protected List<View> views() {
                return views;
            }

            protected String primaryView() {
                return primaryView;
            }

            protected void primaryView(String name) {
                primaryView = name;
            }
        };
        for (SCMSource source : getSCMSources()) {
            source.setOwner(this);
        }
        if (deadBranchStrategy == null) {
            deadBranchStrategy = new DefaultDeadBranchStrategy();
        }
        deadBranchStrategy.setOwner(this);
        final BranchProjectFactory<P, R> factory = getProjectFactory();
        factory.setOwner(this);
        Map<String, Map<String, P>> branchItems = getBranchItems();
        if (getBranchesDir().isDirectory()) {
            for (File branch : getBranchesDir().listFiles(new FileFilter() {
                public boolean accept(File pathname) {
                    return pathname.isDirectory() && new File(pathname, "config.xml").isFile();
                }
            })) {
                try {
                    Item item = Items.load(this, branch);
                    if (factory.isProject(item)) {
                        P project = factory.asProject(item);
                        assert project != null;
                        Branch b = factory.getBranch(project);
                        SCMSource source = getSCMSource(b.getSourceId());
                        if (source == null || source == nullSCMSource) {
                            source = nullSCMSource;
                            factory.setBranch(project, new Branch.Dead(b.getHead(), b.getProperties()));
                        } else {
                            // Sync the properties with those that the strategy dictates
                            BranchPropertyStrategy propertyStrategy = getBranchPropertyStrategy(source);
                            b = new Branch(b.getSourceId(), b.getHead(), b.getScm(),
                                    propertyStrategy == null
                                            ? Collections.<BranchProperty>emptyList()
                                            : propertyStrategy.getPropertiesFor(b.getHead()));
                            factory.setBranch(project, b);
                        }
                        factory.decorate(project);
                        Map<String, P> projects;
                        projects = branchItems.get(source.getId());
                        if (projects == null) {
                            projects = new LinkedHashMap<String, P>();
                            branchItems.put(source.getId(), projects);
                        }
                        projects.put(project.getName(), project);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to load " + branch, e);
                }
            }
        }
        for (Map<String, P> projects : branchItems.values()) {
            for (P project : projects.values()) {
                try {
                    project.onLoad(this, project.getName());
                } catch (IOException e) {
                    // TODO log on-load errors
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        }
        loadIndexing();
        if (triggers == null) {
            triggers = new Vector<Trigger<?>>();
        }
        for (Trigger t : triggers) {
            //noinspection unchecked
            t.start(this, false);
        }
    }

    /**
     * Loads the most recent {@link BranchIndexing}.
     */
    private synchronized void loadIndexing() {
        if (!getIndexingDir().isDirectory() && !getIndexingDir().mkdirs()) {
            LOGGER.log(Level.WARNING, "Could not create directory {0}", getIndexingDir());
        }
        final BranchIndexing<P, R> indexing = new BranchIndexing<P, R>(null);
        this.indexing = indexing;
        indexing.setProject(this);
        XmlFile file = indexing.getDataFile();
        if (file != null && file.exists()) {
            try {
                file.unmarshal(indexing);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load " + file, e);
            }
        }
        if (indexing.getTimeInMillis() == 0 && isBuildable()) {
            scheduleBuild();
        }
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
        if (branchItems != null) {
            for (Map.Entry<String, Map<String, P>> entry : branchItems.entrySet()) {
                SCMSource source = getSCMSource(entry.getKey());
                if (source == null) {
                    source = nullSCMSource;
                }
                for (P project : entry.getValue().values()) {
                    this.factory.decorate(this.factory.setBranch(project,
                            newBranch(source, this.factory.getBranch(project).getHead())));
                }
            }
        }
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
        return sources.toList();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public List<SCMSource> getSCMSources() {
        List<SCMSource> result = new ArrayList<SCMSource>();
        for (BranchSource source : sources) {
            result.add(source.getSource());
        }
        return result;
    }

    /**
     * Adds a new {@link hudson.triggers.Trigger} to this {@link MultiBranchProject} if not active yet.
     */
    @SuppressWarnings("unused") // API mirroring of Project
    public synchronized void addTrigger(Trigger<?> trigger) throws IOException {
        addToList(trigger, triggers);
    }

    /**
     * Removes a new {@link hudson.triggers.Trigger} to this {@link MultiBranchProject} if not active yet.
     */
    @SuppressWarnings("unused") // API mirroring of Project
    public synchronized void removeTrigger(TriggerDescriptor trigger) throws IOException {
        removeFromList(trigger, triggers);
    }

    /**
     * Helper method to add an item to a {@link List} of {@link Describable} instances and maintain the invariant
     * that there can only be at most one instance of any specific {@link Descriptor}.
     *
     * @param item the item to add.
     * @param list the {@link List} to add to.
     * @param <T>  the type of {@link Describable} that the {@link List} holds.
     * @throws IOException if the change to the {@link List} could not be persisted.
     */
    private synchronized <T extends Describable<T>>
    void addToList(T item, List<T> list) throws IOException {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getDescriptor() == item.getDescriptor()) {
                // replace
                list.set(i, item);
                save();
                return;
            }
        }
        // add
        list.add(item);
        save();
    }

    /**
     * Helper method to remove an item from a {@link List} of {@link Describable} instances by specifying the any
     * specific {@link Descriptor}.
     *
     * @param item the {@link Descriptor} of the item to remove.
     * @param list the {@link List} to remove from.
     * @param <T>  the type of {@link Describable} that the {@link List} holds.
     * @throws IOException if the change to the {@link List} could not be persisted.
     */
    private synchronized <T extends Describable<T>>
    void removeFromList(Descriptor<T> item, List<T> list) throws IOException {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getDescriptor() == item) {
                // found it
                list.remove(i);
                save();
                return;
            }
        }
    }

    /**
     * Returns the {@link Trigger} as a {@link Map} keyed by {@link TriggerDescriptor}.
     *
     * @return the {@link Trigger} as a {@link Map} keyed by {@link TriggerDescriptor}.
     */
    @SuppressWarnings("unchecked")
    public synchronized Map<TriggerDescriptor, Trigger> getTriggers() {
        return (Map) Descriptor.toMap(triggers);
    }

    /**
     * Gets the specific trigger, or null if the property is not configured for this job.
     */
    @SuppressWarnings("unused") // API mirroring of Project
    public <T extends Trigger> T getTrigger(Class<T> clazz) {
        for (Trigger p : triggers) {
            if (clazz.isInstance(p)) {
                return clazz.cast(p);
            }
        }
        return null;
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
    public Branch newBranch(@NonNull SCMSource source, @NonNull SCMHead head) {
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
        SCMTrigger.SCMTriggerCause cause = new SCMTrigger.SCMTriggerCause(source.getDescriptor().getDisplayName());
        scheduleBuild(0, cause);
    }

    /**
     * Returns the special dead branch source.
     *
     * @return the special dead branch source.
     */
    @NonNull
    @SuppressWarnings("unused") // used by stapler
    public DeadBranchStrategy getDeadBranchStrategy() {
        return deadBranchStrategy;
    }

    /**
     * Returns a map of all branch jobs.
     *
     * @return a map of all branch jobs.
     */
    @NonNull
    private synchronized Map<String, Map<String, P>> getBranchItems() {
        if (branchItems == null) {
            branchItems = new LinkedHashMap<String, Map<String, P>>();
        }
        return branchItems;
    }

    /**
     * Gets the {@link Map} of branch projects for a specific {@link SCMSource}.
     *
     * @param source the {@link SCMSource}.
     * @return the {@link Map} of branch projects keyed by {@link jenkins.scm.api.SCMHead#getName()}.
     */
    @NonNull
    private synchronized Map<String, P> getOrCreateBranchItemsFor(@NonNull SCMSource source) {
        Map<String, P> items = getBranchItems().get(source.getId());
        if (items == null) {
            items = new LinkedHashMap<String, P>();
            getBranchItems().put(source.getId(), items);
        }
        return items;
    }

    /**
     * Populates the {@link #branchItems} from the supplied {@link DeadBranchStrategy} using the specified
     * {@link
     * BranchProjectFactory}.
     *
     * @param listener       the listener.
     * @param source         the source to populate from.
     * @param factory        the factory to use for creating projects.
     * @param lostBranches   when a branch moves between branch sources, we don't reload the project instance
     *                       from memory but instead use the existing instance. This map is the instances that
     *                       have been evicted from previous sources in the current rebuild.
     * @param scheduleBuilds the builds to schedule.
     * @return the set of branch names that were added.
     */
    private Set<String> populateBranchItemsFromSCM(final TaskListener listener,
                                                   final SCMSource source,
                                                   final BranchProjectFactory<P, R> factory,
                                                   final Map<String, P> lostBranches,
                                                   final Map<P, SCMRevision> scheduleBuilds)
            throws IOException, InterruptedException {
        final Map<String, P> items = getOrCreateBranchItemsFor(source);
        final Set<String> branchNames = new HashSet<String>();
        SCMHeadObserver observer = new SCMHeadObserver() {
            public void observe(@NonNull SCMHead head, @NonNull SCMRevision revision) {
                String branchName = head.getName();
                branchNames.add(branchName);
                P project = lostBranches.remove(branchName);
                boolean needSave;
                Branch branch = newBranch(source, head);
                if (project == null) {
                    needSave = true;
                    project = factory.newInstance(branch);
                    items.put(branchName, project);
                    if (project.getConfigFile().exists()) {
                        try {
                            project.getConfigFile().unmarshal(project);
                            factory.decorate(project);
                            project.onLoad(MultiBranchProject.this, branchName);
                        } catch (IOException e) {
                            e.printStackTrace(listener.error("Could not load old configuration of " + branchName));
                        }
                    } else {
                        factory.decorate(project);
                        project.onCreatedFromScratch();
                    }
                    scheduleBuilds.put(project, revision);
                } else {
                    needSave = !branch.equals(factory.getBranch(project));
                    project = factory.decorate(factory.setBranch(project, branch));
                    items.put(branch.getName(), project);
                    if (revision.isDeterministic()) {
                        SCMRevision lastBuild = getProjectFactory().getRevision(project);
                        if (!revision.equals(lastBuild)) {
                            needSave = true;
                            scheduleBuilds.put(project, revision);
                        }
                    } else {
                        // fall back to polling when we have a non-deterministic revision/hash.
                        PollingResult pollingResult = project.poll(listener);
                        if (pollingResult.hasChanges()) {
                            needSave = true;
                            scheduleBuilds.put(project, revision);
                        }
                    }
                }
                if (needSave) {
                    try {
                        project.save();
                    } catch (IOException e) {
                        e.printStackTrace(listener.error("Could not save changed to " + branchName));
                    }
                }
            }
        };
        source.fetch(observer, listener);
        for (Iterator<Map.Entry<String, P>> iterator = items.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, P> entry = iterator.next();
            if (branchNames.contains(entry.getKey())) {
                continue;
            }
            lostBranches.put(entry.getKey(), entry.getValue());
            iterator.remove();
        }
        return branchNames;
    }

    /**
     * Runs the dead branch cleanup for this job.
     *
     * @param listener a listener for reporting interesting things.
     * @throws IOException          if something went wrong.
     * @throws InterruptedException if we were interrupted.
     */
    private void runDeadBranchCleanup(TaskListener listener) throws IOException, InterruptedException {
        if (deadBranchStrategy == null) {
            return;
        }
        if (getBranchItems().isEmpty()) {
            return;
        }

        Map<String, P> branchProjectMap = getBranchItems().get(nullSCMSource.getId());
        deadBranchStrategy.runDeadBranchCleanup(listener, branchProjectMap);
    }

    /**
     * Returns {@code true} iff this branch can be deleted by the user.
     *
     * @param branch the branch.
     * @return {@code true} iff this branch is one of the dead branches.
     */
    public boolean canDelete(P branch) {
        Map<String, P> deadBranches = getBranchItems().get(nullSCMSource.getId());
        return deadBranches != null && deadBranches.containsValue(branch);
    }

    /**
     * Returns all the child jobs.
     *
     * @return all the child jobs.
     */
    @NonNull
    public Collection<P> getItems() {
        List<P> items = new ArrayList<P>();
        for (Map<String, P> source : getBranchItems().values()) {
            items.addAll(source.values());
        }
        return items;
    }

    /**
     * Returns the named child job or {@code null} if no such job exists.
     *
     * @param name the name of the child job.
     * @return the named child job or {@code null} if no such job exists.
     */
    @CheckForNull
    public P getItem(String name) {
        if (name == null) {
            return null;
        }
        if (name.indexOf('%') != -1) {
            String decoded = rawDecode(name);
            for (Map<String, P> source : getBranchItems().values()) {
                P item = source.get(decoded);
                if (item != null) {
                    return item;
                }
            }
            // fall through for double decoded call paths
        }
        for (Map<String, P> source : getBranchItems().values()) {
            P item = source.get(name);
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    /**
     * Returns the named branch job or {@code null} if no such branch exists.
     *
     * @param name the name of the branch
     * @return the named branch job or {@code null} if no such branch exists.
     */
    @CheckForNull
    @SuppressWarnings("unused")// by stapler for URL binding
    public P getBranch(String name) {
        return getItem(name);
    }

    /**
     * Used in <tt>sidepanel.jelly</tt> to decide whether to display
     * the config/delete/build links.
     */
    @SuppressWarnings("unused")// by jelly
    public boolean isConfigurable() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public String getUrlChildPrefix() {
        return "branch";
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public File getRootDirFor(P child) {
        File dir = new File(getBranchesDir(), Util.rawEncode(child.getName()));
        if (!dir.isDirectory() && !dir.mkdirs()) {
            LOGGER.log(Level.WARNING, "Could not create directory {0}", dir);
        }
        return dir;
    }

    /**
     * Returns the directory that all branches are stored in.
     *
     * @return the directory that all branches are stored in.
     */
    @NonNull
    public File getBranchesDir() {
        return new File(getRootDir(), "branches");
    }

    /**
     * Returns the directory that branch indexing is stored in.
     *
     * @return the directory that branch indexing is stored in.
     */
    @NonNull
    public File getIndexingDir() {
        return new File(getRootDir(), "indexing");
    }

    /**
     * {@inheritDoc}
     */
    public void onRenamed(P item, String oldName, String newName) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public void onDeleted(P item) throws IOException {
        getOrCreateBranchItemsFor(nullSCMSource).values().remove(item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public Collection<? extends Job> getAllJobs() {
        return getItems();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public MultiBranchProjectDescriptor getDescriptor() {
        return (MultiBranchProjectDescriptor) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Returns the current/most recent indexing details.
     *
     * @return the current/most recent indexing details.
     */
    @SuppressWarnings("unused") // URL binding for stapler.
    public synchronized BranchIndexing<P, R> getIndexing() {
        return indexing;
    }

    /**
     * Returns whether the name of this job can be changed by user.
     */
    public boolean isNameEditable() {
        return true;
    }

    /**
     * Returns {@code true} if any child job is building or if indexing is in progress.
     *
     * @return {@code true} if any child job is building or if indexing is in progress.
     */
    public boolean isBuilding() {
        if (getIndexing().isBuilding()) {
            return true;
        }
        for (P child : getItems()) {
            if (child.isBuilding()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handles a POST request to trigger indexing.
     *
     * @throws IOException      if things go wrong.
     * @throws ServletException if things go wrong.
     */
    @SuppressWarnings("unused") // URL binding for stapler, duck api for Project
    @RequirePOST
    public HttpResponse doBuild(@QueryParameter TimeDuration delay) throws IOException, ServletException {
        return doIndexingSubmit(delay);
    }

    /**
     * Handles a POST request to trigger indexing.
     *
     * @throws IOException      if things go wrong.
     * @throws ServletException if things go wrong.
     */
    @SuppressWarnings("unused") // URL binding for stapler
    @RequirePOST
    public HttpResponse doIndexingSubmit(@QueryParameter TimeDuration delay) throws IOException, ServletException {
        checkPermission(BUILD);

        if (!isBuildable()) {
            throw HttpResponses.error(SC_INTERNAL_SERVER_ERROR, new IOException(getFullName() + " cannot be indexed"));
        }

        scheduleBuild(delay == null ? 0 : delay.getTime(), new Cause.UserIdCause());
        return HttpResponses.forwardToPreviousPage();
    }

    /**
     * Accepts submission from the configuration page.
     *
     * @param req request.
     * @param rsp response.
     * @throws IOException      if things go wrong.
     * @throws ServletException if things go wrong.
     */
    @RequirePOST
    @SuppressWarnings({"unused", "unchecked"}) // URL binding for stapler
    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, Descriptor.FormException {
        checkPermission(CONFIGURE);

        boolean reindex = false;
        synchronized (this) {
            description = req.getParameter("description");

            try {
                JSONObject json = req.getSubmittedForm();

                setDisplayName(json.optString("displayNameOrNull"));

                Set<String> oldSourceIds = new HashSet<String>();
                for (SCMSource source : getSCMSources()) {
                    oldSourceIds.add(source.getId());
                }
                sources.replaceBy(req.bindJSONToList(BranchSource.class, json.opt("sources")));
                for (SCMSource scmSource : getSCMSources()) {
                    scmSource.setOwner(this);
                }

                deadBranchStrategy =
                        req.bindJSON(DeadBranchStrategy.class, json.getJSONObject("deadBranchStrategy"));
                deadBranchStrategy.setOwner(this);

                setProjectFactory(req.bindJSON(BranchProjectFactory.class, json.getJSONObject("projectFactory")));

                submit(req, rsp);

                for (Trigger t : triggers) {
                    t.stop();
                }
                triggers = buildDescribable(req, req.getSubmittedForm(), Trigger.for_(this));
                for (Trigger t : triggers) {
                    t.start(this, true);
                }

                save();
                ItemListener.fireOnUpdated(this);
                Set<String> newSourceIds = new HashSet<String>();
                for (SCMSource source : getSCMSources()) {
                    newSourceIds.add(source.getId());
                }
                reindex = !newSourceIds.equals(oldSourceIds);

                String newName = req.getParameter("name");
                final ProjectNamingStrategy namingStrategy = Jenkins.getInstance().getProjectNamingStrategy();
                if (newName != null && !newName.equals(name)) {
                    // check this error early to avoid HTTP response splitting.
                    Jenkins.checkGoodName(newName);
                    namingStrategy.checkName(newName);
                    rsp.sendRedirect("rename?newName=" + URLEncoder.encode(newName, "UTF-8"));
                } else {
                    if (namingStrategy.isForceExistingJobs()) {
                        namingStrategy.checkName(name);
                    }
                    FormApply.success(".").generateResponse(req, rsp, null);
                }
            } catch (JSONException e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                pw.println("Failed to parse form data. Please report this problem as a bug");
                pw.println("JSON=" + req.getSubmittedForm());
                pw.println();
                e.printStackTrace(pw);

                rsp.setStatus(SC_BAD_REQUEST);
                sendError(sw.toString(), req, rsp, true);
            }
        }
        if (reindex) {
            scheduleBuild();
        }
    }

    /**
     * Renames this job.
     */
    @RequirePOST
    public/* not synchronized. see renameTo() */void doDoRename(
            StaplerRequest req, StaplerResponse rsp) throws IOException,
            ServletException {

        if (!hasPermission(CONFIGURE)) {
            // rename is essentially delete followed by a create
            checkPermission(CREATE);
            checkPermission(DELETE);
        }

        String newName = req.getParameter("newName");
        Jenkins.checkGoodName(newName);

        if (isBuilding()) {
            // redirect to page explaining that we can't rename now
            rsp.sendRedirect("rename?newName=" + URLEncoder.encode(newName, "UTF-8"));
            return;
        }

        renameTo(newName);
        // send to the new job page
        // note we can't use getUrl() because that would pick up old name in the
        // Ancestor.getUrl()
        rsp.sendRedirect2("../" + newName);
    }

    /**
     * Builds a list of {@link Describable} instances from the stapler request, the specified {@link JSONObject}
     * and the list of permitted {@link Descriptor}.
     *
     * @param req         the request.
     * @param data        the {@link JSONObject}.
     * @param descriptors the allowed {@link Descriptor}s.
     * @return the {@link List} of corresponding {@link Describable} instances.
     * @throws Descriptor.FormException if things go wrong.
     * @throws ServletException         if things go wrong.
     */
    private <T extends Describable<T>> List<T> buildDescribable(StaplerRequest req,
                                                                JSONObject data,
                                                                List<? extends Descriptor<T>> descriptors)
            throws Descriptor.FormException, ServletException {

        List<T> r = new Vector<T>();
        for (Descriptor<T> d : descriptors) {
            String safeName = d.getJsonSafeClassName();
            if (req.getParameter(safeName) != null) {
                T instance = d.newInstance(req, data.getJSONObject(safeName));
                r.add(instance);
            }
        }
        return r;
    }

    /**
     * Derived class can override this to perform additional config submission
     * work.
     */
    protected void submit(StaplerRequest req, StaplerResponse rsp)
            throws IOException, ServletException, Descriptor.FormException {
    }

    /**
     * Fallback to the primary view.
     */
    @NonNull
    @SuppressWarnings("unused") // duck API for view container
    public View getStaplerFallback() {
        return getPrimaryView();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @SuppressWarnings("unused") // duck API for view container
    public ItemGroup<? extends TopLevelItem> getItemGroup() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @SuppressWarnings("unused") // duck API for view container
    public ViewsTabBar getViewsTabBar() {
        return viewsTabBar;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @SuppressWarnings("unused") // duck API for view container
    public List<Action> getViewActions() {
        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unused") // duck API for view container
    public void addView(View v) throws IOException {
        viewGroupMixIn.addView(v);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unused") // duck API for view container
    public boolean canDelete(View view) {
        return viewGroupMixIn.canDelete(view);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unused") // duck API for view container
    public void deleteView(View view) throws IOException {
        viewGroupMixIn.deleteView(view);
    }

    /**
     * {@inheritDoc}
     */
    @Exported
    @NonNull
    @SuppressWarnings("unused") // duck API for view container
    public View getPrimaryView() {
        if (getBranchItems().isEmpty()) {
            // when there's no branches to show, switch to the special welcome view
            return getWelcomeView();
        }
        return viewGroupMixIn.getPrimaryView();
    }

    /**
     * Creates a place holder view when there's no active branch indexed.
     */
    protected View getWelcomeView() {
        return new MultiBranchProjectWelcomeView(this);
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    @SuppressWarnings("unused") // duck API for view container
    public View getView(String name) {
        return viewGroupMixIn.getView(name);
    }

    /**
     * {@inheritDoc}
     */
    @Exported
    @NonNull
    @SuppressWarnings("unused") // duck API for view container
    public Collection<View> getViews() {
        return viewGroupMixIn.getViews();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unused") // duck API for view container
    public void onViewRenamed(View view, String oldName, String newName) {
        viewGroupMixIn.onViewRenamed(view, oldName, newName);
    }

    /**
     * Used as the color of the status ball for the project.
     *
     * @return the color of the status ball for the project.
     */
    @Exported(visibility = 2, name = "color")
    @SuppressWarnings("unused") // duck API for Job like TopLevelItem
    public BallColor getIconColor() {
        BallColor c = BallColor.DISABLED;
        boolean animated = false;

        for (P item : getItems()) {
            BallColor d = item.getIconColor();
            animated |= d.isAnimated();
            d = d.noAnime();
            if (d.compareTo(c) < 0) {
                c = d;
            }
        }
        if (animated) {
            c = c.anime();
        }

        return c;
    }

    /**
     * Get the current health report for a folder.
     *
     * @return the health report. Never returns null
     */
    @SuppressWarnings("unused") // duck API for Job like TopLevelItem
    public HealthReport getBuildHealth() {
        List<HealthReport> reports = getBuildHealthReports();
        return reports.isEmpty() ? new HealthReport() : reports.get(0);
    }

    /**
     * Get the current health reports for a job.
     *
     * @return the health reports. Never returns null
     */
    @Exported(name = "healthReport")
    @SuppressWarnings("unused") // duck API for Job like TopLevelItem
    public List<HealthReport> getBuildHealthReports() {
        int branchCount = 0;
        int branchBuild = 0;
        int branchSuccess = 0;
        long branchAge = 0;
        for (P item : getItems()) {
            branchCount++;
            R lastBuild = item.getLastBuild();
            if (lastBuild != null) {
                branchBuild++;
                Result r = lastBuild.getResult();
                if (r != null && r.isBetterOrEqualTo(Result.SUCCESS)) {
                    branchSuccess++;
                }
                branchAge += TimeUnit2.MILLISECONDS.toDays(lastBuild.getTimeInMillis() - System.currentTimeMillis());
            }
        }
        List<HealthReport> reports = new ArrayList<HealthReport>();
        if (branchCount > 0) {
            reports.add(new HealthReport(branchSuccess * 100 / branchCount, Messages._Health_BranchSuccess()));
            reports.add(new HealthReport(branchBuild * 100 / branchCount, Messages._Health_BranchBuilds()));
            reports.add(new HealthReport(Math.min(100, Math.max(0, (int) (100 - (branchAge / branchCount)))),
                    Messages._Health_BranchAge()));
            Collections.sort(reports);
        }
        return reports;
    }

    /**
     * Background thread to tidy up dead branches.
     */
    @Extension
    @SuppressWarnings("unused")
    public static class DeadBranchCleanupThread extends AsyncPeriodicWork {

        /**
         * Our logger.
         */
        private static final Logger LOGGER = Logger.getLogger(DeadBranchCleanupThread.class.getName());

        /**
         * Can be used to disable workspace clean up.
         */
        public static final boolean disabled =
                Boolean.getBoolean(DeadBranchCleanupThread.class.getName() + ".disabled");

        /**
         * Our constructor.
         */
        public DeadBranchCleanupThread() {
            super("Old branch clean-up");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getRecurrencePeriod() {
            return PeriodicWork.DAY;
        }

        /**
         * Helper static method for use from the Groovy shell.
         */
        @SuppressWarnings("unused") // API contract
        public static void invoke() {
            Jenkins.getInstance().getExtensionList(AsyncPeriodicWork.class).get(DeadBranchCleanupThread.class).run();
        }

        /**
         * {@inheritDoc}
         */
        protected void execute(TaskListener listener) throws InterruptedException, IOException {
            if (disabled) {
                LOGGER.fine("Disabled. Skipping execution");
                return;
            }

            for (MultiBranchProject<?, ?> p
                    : Jenkins.getInstance().getAllItems(MultiBranchProject.class)) {
                p.runDeadBranchCleanup(listener);
            }
        }

    }

    /**
     * Represents the branch indexing job.
     *
     * @param <P> the type of project that the branch projects consist of.
     * @param <R> the type of runs that the branch projects use.
     */
    public static class BranchIndexing<P extends AbstractProject<P, R> & TopLevelItem,
            R extends AbstractBuild<P, R>> extends Actionable implements Queue.Executable, Saveable {

        /**
         * The parent {@link MultiBranchProject}.
         */
        @CheckForNull
        private transient MultiBranchProject<P, R> project;

        /**
         * Any previous {@link BranchIndexing}.
         */
        @CheckForNull
        private transient BranchIndexing<P, R> previousIndexing;

        /**
         * The {@link Executor} that the {@link BranchIndexing} is running on or {@code null} if the job is not fully
         * running yet.
         */
        @CheckForNull
        private transient volatile Executor executor;

        /**
         * The current {@link Result}
         */
        @NonNull
        private volatile Result result = Result.NOT_BUILT;

        /**
         * The past couple of durations for this indexing activity to assist in estimating future duration.
         */
        @CheckForNull
        private List<Long> durations;

        /**
         * The time that indexing started at.
         */
        private long timestamp;

        /**
         * The duration of this indexing.
         */
        private long duration;

        /**
         * Charset in which the log file is written.
         * For compatibility reason, this field may be null.
         * For persistence, this field is string and not {@link Charset}.
         *
         * @see #getCharset()
         */
        @CheckForNull
        protected String charset;

        /**
         * Name of the slave this project was built on.
         * Null or "" if built by the master. (null happens when we read old record that didn't have this information.)
         */
        @CheckForNull
        private String builtOn;

        /**
         * Constructor.
         *
         * @param previousIndexing any previous indexing.
         */
        public BranchIndexing(@CheckForNull BranchIndexing<P, R> previousIndexing) {
            this.previousIndexing = previousIndexing;
        }

        /**
         * Returns the parent.
         *
         * @return the parent.
         */
        @CheckForNull
        public MultiBranchProject<P, R> getProject() {
            return project;
        }

        /**
         * Returns the parent.
         *
         * @return the parent.
         */
        @CheckForNull
        public MultiBranchProject<P, R> getParent() {
            return getProject();
        }

        /**
         * Sets the parent.
         *
         * @param project the parent.
         */
        public void setProject(@CheckForNull MultiBranchProject<P, R> project) {
            this.project = project;
        }

        /**
         * Save the settings to a file.
         */
        public synchronized void save() throws IOException {
            if (BulkChange.contains(this)) {
                return;
            }
            XmlFile dataFile = getDataFile();
            if (dataFile == null) {
                return;
            }
            dataFile.write(this);
            SaveableListener.fireOnChange(this, dataFile);
        }

        /**
         * Returns the data file.
         *
         * @return the data file.
         */
        @CheckForNull
        private XmlFile getDataFile() {
            return project == null
                    ? null
                    : new XmlFile(Items.XSTREAM, new File(project.getIndexingDir(), "indexing.xml"));
        }

        /**
         * Returns the log file.
         *
         * @return the log file.
         */
        @CheckForNull
        public File getLogFile() {
            return project == null ? null : new File(project.getIndexingDir(), "indexing.log");
        }

        /**
         * Returns {@code true} if this indexing is in progress.
         *
         * @return {@code true} if this indexing is in progress.
         */
        public boolean isBuilding() {
            return Result.NOT_BUILT == result && executor != null;
        }

        /**
         * Returns true if the log file is still being updated.
         */
        public boolean isLogUpdated() {
            return Result.NOT_BUILT == result;
        }

        /**
         * Returns the name of this {@link BranchIndexing}'s parent.
         *
         * @return the name of this {@link BranchIndexing}'s parent.
         */
        @CheckForNull
        public String getName() {
            return project == null ? null : project.getName();
        }

        /**
         * {@inheritDoc}
         */
        public String getDisplayName() {
            return "Indexing";
        }

        /**
         * Mimic method.
         *
         * @return the URL of the indexing.
         */
        @SuppressWarnings("unused") // used from jelly pages
        public String getUrl() {
            return project == null ? null : project.getUrl() + "indexing/";
        }

        /**
         * Returns a {@link hudson.model.Slave} on which this build was done.
         *
         * @return {@code null}, for example if the slave that this build run no longer exists.
         */
        @SuppressWarnings("unused") // used from jelly pages
        public Node getBuiltOn() {
            if (builtOn == null || builtOn.equals("")) {
                return Jenkins.getInstance();
            } else {
                return Jenkins.getInstance().getNode(builtOn);
            }
        }

        /**
         * Returns the name of the slave it was built on; null or "" if built by the master.
         * (null happens when we read old record that didn't have this information.)
         */
        @Exported(name = "builtOn")
        @SuppressWarnings("unused") // used by Jenkins API
        public String getBuiltOnStr() {
            return builtOn;
        }

        /**
         * Gets the charset in which the log file is written.
         *
         * @return never {@code null}.
         */
        @NonNull
        public final Charset getCharset() {
            if (charset == null) {
                return Charset.defaultCharset();
            }
            return Charset.forName(charset);
        }

        /**
         * Used to URL-bind {@link hudson.console.AnnotatedLargeText}.
         */
        @SuppressWarnings("unchecked")
        @NonNull
        public AnnotatedLargeText getLogText() {
            return new AnnotatedLargeText(getLogFile(), getCharset(), !isLogUpdated(), this);
        }

        /**
         * Returns an input stream that reads from the log file.
         * It will use a gzip-compressed log file (log.gz) if that exists.
         *
         * @return an input stream from the log file, or null if none exists
         * @throws IOException
         */
        @NonNull
        public InputStream getLogInputStream() throws IOException {
            File logFile = getLogFile();
            if (logFile != null) {
                if (logFile.exists()) {
                    return new FileInputStream(logFile);
                }

                File compressedLogFile = new File(logFile.getParentFile(), logFile.getName() + ".gz");
                if (compressedLogFile.exists()) {
                    return new GZIPInputStream(new FileInputStream(compressedLogFile));
                }
            }

            return new NullInputStream(0);
        }

        /**
         * Returns a {@link Reader} of the log content.
         *
         * @return a {@link Reader} of the log content.
         * @throws IOException if the log content cannot be read.
         */
        @NonNull
        @SuppressWarnings("unused") // bound via Stapler
        public synchronized Reader getLogReader() throws IOException {
            return new InputStreamReader(getLogInputStream(),
                    charset == null ? Charset.defaultCharset().name() : charset);
        }

        /**
         * Renders the progressive console log.
         *
         * @param req the request.
         * @param rsp the response.
         * @throws IOException if things go wrong.
         */
        @SuppressWarnings("unused") // bound via Stapler
        public void doConsoleText(StaplerRequest req, StaplerResponse rsp) throws IOException {
            rsp.setContentType("text/plain;charset=UTF-8");
            // Prevent jelly from flushing stream so Content-Length header can be added afterwards
            FlushProofOutputStream out = new FlushProofOutputStream(rsp.getCompressedOutputStream(req));
            try {
                getLogText().writeLogTo(0, out);
            } catch (IOException e) {
                // see comment in writeLogTo() method
                InputStream input = getLogInputStream();
                try {
                    IOUtils.copy(input, out);
                } finally {
                    IOUtils.closeQuietly(input);
                }
            }
            out.close();
        }

        /**
         * Used from <tt>console.jelly</tt> to write annotated log to the given output.
         */
        public void writeLogTo(long offset, XMLOutput out) throws IOException {
            try {
                getLogText().writeHtmlTo(offset, out.asWriter());
            } catch (IOException e) {
                // try to fall back to the old getLogInputStream()
                // mainly to support .gz compressed files
                // In this case, console annotation handling will be turned off.
                InputStream input = getLogInputStream();
                try {
                    IOUtils.copy(input, out.asWriter());
                } finally {
                    IOUtils.closeQuietly(input);
                }
            }
        }

        /**
         * Writes the complete log from the start to finish to the {@link OutputStream}.
         * <p/>
         * If someone is still writing to the log, this method will not return until the whole log
         * file gets written out.
         */
        @SuppressWarnings("unused") // bound via Stapler
        public void writeWholeLogTo(OutputStream out) throws IOException, InterruptedException {
            long pos = 0;
            AnnotatedLargeText logText;
            do {
                logText = getLogText();
                pos = logText.writeLogTo(pos, out);
            } while (!logText.isComplete());
        }

        /**
         * Returns the build status icon URL.
         *
         * @return the build status icon URL.
         */
        @SuppressWarnings("unused") // used via Jelly EL
        public String getBuildStatusUrl() {
            return getIconColor().getImage();
        }

        /**
         * Returns the {@link Cause}s that triggered a build.
         * <p/>
         * <p/>
         * If a build sits in the queue for a long time, multiple build requests made during this period
         * are all rolled up into one build, hence this method may return a list.
         *
         * @return can be empty but never null. read-only.
         */
        public List<Cause> getCauses() {
            CauseAction a = getAction(CauseAction.class);
            if (a == null) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(a.getCauses());
        }

        /**
         * Returns the result of indexing.
         *
         * @return the result of indexing.
         */
        @Exported
        @NonNull
        public Result getResult() {
            return result;
        }

        /**
         * When the build is scheduled.
         */
        @Exported
        @NonNull
        public Calendar getTimestamp() {
            GregorianCalendar c = new GregorianCalendar();
            c.setTimeInMillis(timestamp);
            return c;
        }

        /**
         * Same as {@link #getTimestamp()} but in a different type.
         */
        @NonNull
        public final Date getTime() {
            return new Date(timestamp);
        }

        /**
         * Same as {@link #getTimestamp()} but in a different type, that is since the time of the epoc.
         */
        public final long getTimeInMillis() {
            return timestamp;
        }

        /**
         * Gets the string that says how long since this build has started.
         *
         * @return string like "3 minutes" "1 day" etc.
         */
        @SuppressWarnings("unused") // used via Jelly EL
        @NonNull
        public String getTimestampString() {
            long duration = new GregorianCalendar().getTimeInMillis() - timestamp;
            return Util.getPastTimeString(duration);
        }

        /**
         * Returns the timestamp formatted in xs:dateTime.
         */
        @SuppressWarnings("unused") // used via Jelly EL
        @NonNull
        public String getTimestampString2() {
            return Util.XS_DATETIME_FORMATTER.format(new Date(timestamp));
        }

        /**
         * Gets the string that says how long the build took to run.
         */
        @SuppressWarnings("unused") // used via Jelly EL
        @NonNull
        public String getDurationString() {
            if (isBuilding()) {
                return hudson.model.Messages.Run_InProgressDuration(
                        Util.getTimeSpanString(System.currentTimeMillis() - timestamp));
            }
            return Util.getTimeSpanString(duration);
        }

        /**
         * Gets the icon color for display.
         */
        @SuppressWarnings("unused") // used via Jelly EL
        public BallColor getIconColor() {
            if (!isBuilding()) {
                // already built
                return getResult().color;
            }

            if (getPreviousResult() == null) {
                return BallColor.GREY;
            }

            // a new build is in progress
            BallColor baseColor;
            if (Result.SUCCESS.equals(getPreviousResult())) {
                baseColor = BallColor.BLUE;
            } else if (Result.FAILURE.equals(getPreviousResult())) {
                baseColor = BallColor.RED;
            } else if (Result.ABORTED.equals(getPreviousResult())) {
                baseColor = BallColor.ABORTED;
            } else {
                baseColor = BallColor.RED;
            }

            return baseColor.anime();
        }

        /**
         * {@inheritDoc}
         */
        public void run() {
            final MultiBranchProject<P, R> parent = project;
            if (parent == null) {
                return;
            }
            this.executor = Executor.currentExecutor();
            Computer computer = Computer.currentComputer();
            assert builtOn == null;
            Charset charset = null;
            if (computer != null) {
                builtOn = computer.getName();
                charset = computer.getDefaultCharset();
                this.charset = charset.name();
            }
            StreamBuildListener listener = null;
            timestamp = System.currentTimeMillis();
            try {
                OutputStream logger = new FileOutputStream(getLogFile());
                listener = new StreamBuildListener(logger, charset);

                listener.getLogger().println(Messages._MultiBranchProject_IndexingStartedAt(getTime()));
                listener.started(getCauses());
                BranchProjectFactory<P, R> factory = parent.getProjectFactory();
                Map<String, P> lostBranches = new HashMap<String, P>();
                for (Map<String, P> branches : parent.getBranchItems().values()) {
                    lostBranches.putAll(branches);
                }
                Map<P, SCMRevision> scheduleBuilds = new LinkedHashMap<P, SCMRevision>();
                for (SCMSource source : parent.getSCMSources()) {
                    parent.populateBranchItemsFromSCM(listener, source, factory, lostBranches, scheduleBuilds);
                }
                // now add in the dead branches
                for (P project : lostBranches.values()) {
                    Branch b = factory.getBranch(project);
                    if (!(b instanceof Branch.Dead)) {
                        factory.decorate(factory.setBranch(project, new Branch.Dead(b.getHead(), b.getProperties())));
                    }
                }
                parent.getBranchItems().put(parent.nullSCMSource.getId(), lostBranches);
                parent.runDeadBranchCleanup(listener);
                if (!scheduleBuilds.isEmpty()) {
                    listener.getLogger().println("Scheduling builds for branches:");
                    for (Map.Entry<P, SCMRevision> entry : scheduleBuilds.entrySet()) {
                        listener.getLogger().println("    " + entry.getKey().getName());
                        if (entry.getKey().scheduleBuild(0, new TimerTrigger.TimerTriggerCause())) {
                            try {
                                factory.setRevisionHash(entry.getKey(), entry.getValue());
                            } catch (IOException e) {
                                e.printStackTrace(listener.error("Could not update last revision hash"));
                            }
                        }
                    }
                }
                result = Result.SUCCESS;
            } catch (InterruptedException e) {
                result = Result.ABORTED;
                e.printStackTrace(listener.fatalError("Interrupted"));
            } catch (Exception e) {
                result = Result.FAILURE;
                if (listener == null) {
                    LOGGER.log(Level.WARNING, "Could not index as unable to create indexing log file", e);
                } else {
                    e.printStackTrace(listener.fatalError("Failed to index"));
                }
            } finally {
                duration = System.currentTimeMillis() - timestamp;
                if (durations == null) {
                    durations = new ArrayList<Long>();
                }
                while (durations.size() > 32) {
                    durations.remove(0);
                }
                durations.add(duration);
                if (listener != null) {
                    listener.finished(result);
                }
                if (listener != null) {
                    listener.closeQuietly();
                }
                try {
                    save();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Could not save indexing", e);
                }
                executor = null;
            }
        }

        /**
         * {@inheritDoc}
         */
        public long getEstimatedDuration() {
            if (durations == null || durations.isEmpty()) {
                return -1;
            }
            long total = 0;
            int count = 0;
            for (Long duration : durations) {
                if (duration != null) {
                    total += duration;
                    count++;
                }
            }
            if (count > 0) {
                return total / count;
            }
            return -1;
        }

        /**
         * {@inheritDoc}
         */
        public String getSearchUrl() {
            return "indexing/";
        }

        /**
         * Returns the previous {@link Result} or {@code null} if there is none.
         *
         * @return the previous {@link Result} or {@code null} if there is none.
         */
        @CheckForNull
        public Result getPreviousResult() {
            return previousIndexing == null ? null : previousIndexing.result;
        }
    }

    /**
     * Returns {@code true} if and only if this project can be indexed.
     *
     * @return {@code true} if and only if this project can be indexed.
     */
    public boolean isBuildable() {
        return !getSCMSources().isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    public boolean scheduleBuild() {
        return scheduleBuild(new Cause.LegacyCodeCause());
    }

    /**
     * {@inheritDoc}
     */
    public boolean scheduleBuild(int quietPeriod) {
        return scheduleBuild(quietPeriod, new Cause.LegacyCodeCause());
    }

    /**
     * {@inheritDoc}
     */
    public boolean scheduleBuild(Cause c) {
        return scheduleBuild(0, c);
    }

    /**
     * {@inheritDoc}
     */
    public boolean scheduleBuild(int quietPeriod, Cause c) {
        return scheduleBuild(quietPeriod, c, new Action[0]);
    }

    /**
     * Schedules a build.
     * <p/>
     * Important: the actions should be persistable without outside references (e.g. don't store
     * references to this project). To provide parameters for a parameterized project, add a ParametersAction. If
     * no ParametersAction is provided for such a project, one will be created with the default parameter values.
     *
     * @param quietPeriod the quiet period to observer
     * @param c           the cause for this build which should be recorded
     * @param actions     a list of Actions that will be added to the build
     * @return whether the build was actually scheduled
     */
    public boolean scheduleBuild(int quietPeriod, Cause c, Action... actions) {
        scheduleBuild2(quietPeriod, c, actions);
        return true;
    }

    /**
     * Schedules a build of this project, and returns a {@link java.util.concurrent.Future} object
     * to wait for the completion of the build.
     *
     * @param actions For the convenience of the caller, this array can contain null,
     *                and those will be silently ignored.
     */
    @WithBridgeMethods(Future.class)
    @NonNull
    public QueueTaskFuture<R> scheduleBuild2(int quietPeriod, Cause c, Action... actions) {
        return scheduleBuild2(quietPeriod, c, Arrays.asList(actions));
    }

    /**
     * {@inheritDoc}
     */
    public void checkAbortPermission() {
        checkPermission(AbstractProject.ABORT);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isBuildBlocked() {
        return getCauseOfBlockage() != null;
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    public String getWhyBlocked() {
        CauseOfBlockage causeOfBlockage = getCauseOfBlockage();
        return causeOfBlockage == null ? null : causeOfBlockage.getShortDescription();
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    public CauseOfBlockage getCauseOfBlockage() {
        final BranchIndexing<P, R> indexing = getIndexing();
        if (indexing != null && indexing.isBuilding()) {
            return CauseOfBlockage.fromMessage(Messages._MultiBranchProject_NoConcurrentIndexing());
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasAbortPermission() {
        return hasPermission(AbstractProject.ABORT);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isConcurrentBuild() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public Collection<? extends SubTask> getSubTasks() {
        return Collections.singleton(this);
    }

    /**
     * {@inheritDoc}
     */
    public Label getAssignedLabel() {
        // we can run anywhere
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized Node getLastBuiltOn() {
        BranchIndexing<P, R> indexing = this.indexing;
        if (indexing != null && Result.NOT_BUILT.equals(indexing.getResult())) {
            indexing = indexing.previousIndexing;
        }
        if (indexing != null) {
            Node node = indexing.getBuiltOn();
            if (node != null) {
                return node;
            }
        }
        // try to run on master if the master will support running.
        return Jenkins.getInstance().getNumExecutors() > 0 ? Jenkins.getInstance() : null;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized long getEstimatedDuration() {
        return indexing == null ? -1 : indexing.getEstimatedDuration();
    }

    /**
     * {@inheritDoc}
     */
    public synchronized Queue.Executable createExecutable() throws IOException {
        BranchIndexing<P, R> indexing = new BranchIndexing<P, R>(this.indexing);
        indexing.setProject(this);
        this.indexing = indexing;
        return this.indexing;
    }

    /**
     * {@inheritDoc}
     */
    public Queue.Task getOwnerTask() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Object getSameNodeConstraint() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public ResourceList getResourceList() {
        return ResourceList.EMPTY;
    }

    /**
     * Jenkins 1.520+ expects this method.
     *
     * @return the default fallback authentication object.
     */
    @NonNull
    public Authentication getDefaultAuthentication() {
        // backward compatible behaviour.
        return ACL.SYSTEM;
    }

    /**
     * Schedules a build of this project, and returns a {@link Future} object
     * to wait for the completion of the build.
     *
     * @param actions For the convenience of the caller, this collection can contain null,
     *                and those will be silently ignored.
     */
    @SuppressWarnings("unchecked")
    @WithBridgeMethods(Future.class)
    public QueueTaskFuture<R> scheduleBuild2(int quietPeriod, Cause c, Collection<? extends Action> actions) {
        if (!isBuildable()) {
            return null;
        }

        List<Action> queueActions = new ArrayList<Action>(actions);

        if (c != null) {
            queueActions.add(new CauseAction(c));
        }

        Queue.WaitingItem i = Jenkins.getInstance().getQueue().schedule(this, quietPeriod, queueActions);
        if (i != null) {
            return (QueueTaskFuture) i.getFuture();
        }
        return null;
    }

    /**
     * Runs every minute to check {@link hudson.triggers.TimerTrigger} and schedules build.
     */
    @SuppressWarnings("unused") // instantiated by Jenkins
    @Extension
    public static class Cron extends PeriodicWork {
        /**
         * A calendar to use.
         */
        private final Calendar cal = new GregorianCalendar();

        /**
         * The hack field we want to access.
         */
        private final Field tabs;

        /**
         * Constructor.
         *
         * @throws NoSuchFieldException
         */
        @SuppressWarnings("unused") // instantiated by Jenkins
        public Cron() throws NoSuchFieldException {
            tabs = Trigger.class.getDeclaredField("tabs");
            tabs.setAccessible(true);
        }

        /**
         * {@inheritDoc}
         */
        public long getRecurrencePeriod() {
            return MIN;
        }

        /**
         * {@inheritDoc}
         */
        public void doRun() {
            while (new Date().getTime() - cal.getTimeInMillis() > 1000) {
                LOGGER.fine("cron checking " + DateFormat.getDateTimeInstance().format(cal.getTime()));

                try {
                    checkTriggers(cal);
                } catch (Throwable e) {
                    LOGGER.log(Level.WARNING, "Cron thread throw an exception", e);
                    // bug in the code. Don't let the thread die.
                    e.printStackTrace();
                }

                cal.add(Calendar.MINUTE, 1);
            }
        }

        /**
         * Checks the triggers.
         */
        public void checkTriggers(final Calendar cal) {
            Jenkins inst = Jenkins.getInstance();

            for (MultiBranchProject<?, ?> p : inst.getAllItems(MultiBranchProject.class)) {
                for (Trigger t : p.getTriggers().values()) {
                    LOGGER.fine("cron checking " + p.getName());

                    CronTabList tabs;
                    try {
                        tabs = (CronTabList) this.tabs.get(t);
                    } catch (IllegalAccessException e) {
                        continue;
                    }
                    if (tabs.check(cal)) {
                        LOGGER.config("cron triggered " + p.getName());
                        try {
                            t.run();
                        } catch (Throwable e) {
                            // t.run() is a plugin, and some of them throw RuntimeException and other things.
                            // don't let that cancel the polling activity. report and move on.
                            LOGGER.log(Level.WARNING, t.getClass().getName() + ".run() failed for " + p.getName(), e);
                        }
                    }
                }
            }
        }

    }

    /**
     * We need to be able to limit concurrent indexing.
     */
    @SuppressWarnings("unused") // instantiated by Jenkins
    @Extension
    public static class ThrottleIndexingQueueTaskDispatcher extends QueueTaskDispatcher {

        /**
         * {@inheritDoc}
         */
        @Override
        public CauseOfBlockage canRun(Queue.Item item) {
            if (item.task instanceof MultiBranchProject) {
                if (indexingCount() > 5) {
                    // TODO make the limit configurable
                    return CauseOfBlockage.fromMessage(Messages._MultiBranchProject_MaxConcurrentIndexing());
                }
            }
            return null;
        }

        /**
         * Gets the number of current indexing tasks.
         *
         * @return number of current indexing tasks.
         */
        public int indexingCount() {
            int result = indexingCount(Jenkins.getInstance());
            for (Node n : Jenkins.getInstance().getNodes()) {
                result += indexingCount(n);
            }
            return result;
        }

        /**
         * Gets the number of current indexing tasks on the specified node.
         *
         * @param node the node.
         * @return number of current indexing tasks on the specified node.
         */
        public int indexingCount(@CheckForNull Node node) {
            int result = 0;
            @CheckForNull
            Computer computer = node == null ? null : node.toComputer();
            if (computer != null) { // not all nodes will have a computer
                for (Executor e : computer.getExecutors()) {
                    Queue.Executable executable = e.getCurrentExecutable();
                    if (executable != null && executable.getParent() instanceof MultiBranchProject) {
                        result++;
                    }
                }
                for (Executor e : computer.getOneOffExecutors()) {
                    Queue.Executable executable = e.getCurrentExecutable();
                    if (executable != null && executable.getParent() instanceof MultiBranchProject) {
                        result++;
                    }
                }
            }
            return result;
        }
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

}
