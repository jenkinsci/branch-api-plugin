/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import antlr.ANTLRException;
import com.cloudbees.hudson.plugins.folder.AbstractFolderDescriptor;
import com.cloudbees.hudson.plugins.folder.FolderIcon;
import com.cloudbees.hudson.plugins.folder.FolderIconDescriptor;
import com.cloudbees.hudson.plugins.folder.computed.ChildObserver;
import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;
import com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger;
import com.cloudbees.hudson.plugins.folder.views.AbstractFolderViewHolder;
import com.thoughtworks.xstream.XStreamException;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.util.DescribableList;
import hudson.util.LogTaskListener;
import hudson.util.PersistedList;
import hudson.util.io.ReopenableRotatingFileOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMEventListener;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMNavigatorOwner;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCategory;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceEvent;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.impl.SingleSCMNavigator;
import jenkins.scm.impl.UncategorizedSCMSourceCategory;
import org.apache.commons.io.Charsets;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.jvnet.localizer.LocaleProvider;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import static jenkins.scm.api.SCMEvent.Type.CREATED;
import static jenkins.scm.api.SCMEvent.Type.UPDATED;

/**
 * A folder-like collection of {@link MultiBranchProject}s, one per repository.
 */
@Restricted(NoExternalUse.class) // not currently intended as an API
@SuppressWarnings({"unchecked", "rawtypes"}) // mistakes in various places
public final class OrganizationFolder extends ComputedFolder<MultiBranchProject<?,?>>
        implements SCMNavigatorOwner, IconSpec {

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(MultiBranchProject.class.getName());
    /**
     * Our navigators.
     */
    private final DescribableList<SCMNavigator,SCMNavigatorDescriptor> navigators = new DescribableList<SCMNavigator, SCMNavigatorDescriptor>(this);
    /**
     * Our project factories.
     */
    private final DescribableList<MultiBranchProjectFactory,MultiBranchProjectFactoryDescriptor> projectFactories = new DescribableList<MultiBranchProjectFactory,MultiBranchProjectFactoryDescriptor>(this);

    /**
     * {@inheritDoc}
     */
    public OrganizationFolder(ItemGroup parent, String name) {
        super(parent, name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreatedFromScratch() {
        super.onCreatedFromScratch();
        for (MultiBranchProjectFactoryDescriptor d : ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class)) {
            MultiBranchProjectFactory f = d.newInstance();
            if (f != null) {
                projectFactories.add(f);
            }
        }
        try {
            addTrigger(new PeriodicFolderTrigger("1d"));
        } catch (ANTLRException x) {
            throw new IllegalStateException(x);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        navigators.setOwner(this);
        projectFactories.setOwner(this);
        if (!(getFolderViews() instanceof OrganizationFolderViewHolder)) {
            resetFolderViews();
        }
        if (!(getIcon() instanceof MetadataActionFolderIcon)) {
            setIcon(newDefaultFolderIcon());
        }
    }

    public DescribableList<SCMNavigator,SCMNavigatorDescriptor> getNavigators() {
        return navigators;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<SCMNavigator> getSCMNavigators() {
        return navigators;
    }

    public DescribableList<MultiBranchProjectFactory,MultiBranchProjectFactoryDescriptor> getProjectFactories() {
        return projectFactories;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void submit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, Descriptor.FormException {
        String navDigest;
        try {
            navDigest = Util.getDigestOf(Items.XSTREAM2.toXML(navigators));
        } catch (XStreamException e) {
            navDigest = null;
        }
        String facDigest;
        try {
            facDigest = Util.getDigestOf(Items.XSTREAM2.toXML(projectFactories));
        } catch (XStreamException e) {
            facDigest = null;
        }
        super.submit(req, rsp);
        navigators.rebuildHetero(req, req.getSubmittedForm(), ExtensionList.lookup(SCMNavigatorDescriptor.class), "navigators");
        projectFactories.rebuildHetero(req, req.getSubmittedForm(), ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class), "projectFactories");
        for (SCMNavigator n : navigators) {
            n.afterSave(this);
        }
        recalculateAfterSubmitted(navDigest == null
                || !navDigest.equals(Util.getDigestOf(Items.XSTREAM2.toXML(navigators))));
        recalculateAfterSubmitted(facDigest == null
                || !facDigest.equals(Util.getDigestOf(Items.XSTREAM2.toXML(projectFactories))));
    }

    /**
     * {@inheritDoc}
     */
    @Nonnull
    @Override
    protected FolderComputation<MultiBranchProject<?, ?>> createComputation(
            @CheckForNull FolderComputation<MultiBranchProject<?, ?>> previous) {
        return new OrganizationScan(OrganizationFolder.this, previous);
    }

    @Override
    public boolean isHasEvents() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void computeChildren(final ChildObserver<MultiBranchProject<?,?>> observer, final TaskListener listener) throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        listener.getLogger().format("[%tc] Starting organization scan...%n", start);
        try {
            listener.getLogger().format("[%tc] Updating actions...%n", System.currentTimeMillis());
            Map<Class<? extends Action>, Action> persistentActions = new HashMap<>();
            for (ListIterator<SCMNavigator> iterator = navigators.listIterator(navigators.size());
                 iterator.hasPrevious(); ) {
                SCMNavigator navigator = iterator.previous();
                // first navigator always wins in case of duplicate keys
                persistentActions.putAll(navigator.fetchActions(this, listener));
            }
            // update any persistent actions for the SCMNavigator
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
            for (SCMNavigator navigator : navigators) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                listener.getLogger().format("[%tc] Consulting %s%n", System.currentTimeMillis(),
                        navigator.getDescriptor().getDisplayName());
                navigator.visitSources(new SCMSourceObserverImpl(listener, observer));
            }
        } finally {
            long end = System.currentTimeMillis();
            listener.getLogger().format("[%tc] Finished organization scan. Scan took %s%n", end,
                    Util.getTimeSpanString(end - start));

        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected AbstractFolderViewHolder newFolderViewHolder() {
        return new OrganizationFolderViewHolder(this);
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
        if (navigators.size() == 1) {
            result = navigators.get(0).getDescriptor().getIconClassName();
        } else {
            result = null;
            for (int i = 0; i < navigators.size(); i++) {
                String iconClassName = navigators.get(i).getDescriptor().getIconClassName();
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
        for (SCMNavigator navigator: navigators) {
            String pronoun = Util.fixEmptyAndTrim(navigator.getPronoun());
            if (pronoun != null) {
                result.add(pronoun);
            }
        }
        return result.isEmpty() ? super.getPronoun() : StringUtils.join(result, " / ");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SCMSource> getSCMSources() {
        // Probably unused unless onSCMSourceUpdated implemented, but just in case:
        Set<SCMSource> result = new HashSet<SCMSource>();
        for (MultiBranchProject<?,?> child : getItems()) {
            result.addAll(child.getSCMSources());
        }
        return new ArrayList<SCMSource>(result);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SCMSource getSCMSource(String sourceId) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSCMSourceUpdated(SCMSource source) {
        // TODO possibly we should recheck whether this project remains valid
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SCMSourceCriteria getSCMSourceCriteria(SCMSource source) {
        return null;
    }

    /**
     * Will create an specialized view when there are no repositories or branches found, which contain a Jenkinsfile
     * or other MARKER file.
     */
    @Override
    public View getPrimaryView() {
        if (getItems().isEmpty()) {
            return getWelcomeView();
        }
        return super.getPrimaryView();
    }

    /**
     * Creates a place holder view when there's no active branch indexed.
     */
    protected View getWelcomeView() {
        return new OrganizationFolderEmptyView(this);
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
     * Our descriptor
     */
    @Extension
    public static class DescriptorImpl extends AbstractFolderDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            if (Jenkins.getActiveInstance().getInitLevel().compareTo(InitMilestone.EXTENSIONS_AUGMENTED) > 0) {
                List<SCMNavigatorDescriptor> navs = remove(ExtensionList.lookup(SCMNavigatorDescriptor.class),
                        SingleSCMNavigator.DescriptorImpl.class);
                if (navs.size() == 1) {
                    return Messages.OrganizationFolder_DisplayName(StringUtils.defaultIfBlank(
                            navs.get(0).getPronoun(),
                            Messages.OrganizationFolder_DefaultPronoun())
                    );
                }
            }
            return Messages.OrganizationFolder_DisplayName(Messages._OrganizationFolder_DefaultPronoun());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new OrganizationFolder(parent, name);
        }

        /**
         * Used to categorize {@link OrganizationFolder} instances.
         *
         * @return A string with the category identifier. {@code TopLevelItemDescriptor#getCategoryId()}
         */
        //@Override TODO once baseline is 2.x
        @NonNull
        public String getCategoryId() {
            return "nested-projects";
        }

        /**
         * A description of this {@link OrganizationFolder}.
         *
         * @return A string with the description. {@code TopLevelItemDescriptor#getDescription()}.
         */
        //@Override TODO once baseline is 2.x
        @NonNull
        public String getDescription() {
            if (Jenkins.getActiveInstance().getInitLevel().compareTo(InitMilestone.EXTENSIONS_AUGMENTED) > 0) {
                List<SCMNavigatorDescriptor> navs = remove(ExtensionList.lookup(SCMNavigatorDescriptor.class),
                        SingleSCMNavigator.DescriptorImpl.class);
                SCMSourceCategory uncategorized = genericScmSourceCategory(navs);

                Locale locale = LocaleProvider.getLocale();
                return Messages.OrganizationFolder_Description(orJoinDisplayName(ExtensionList.lookup(MultiBranchProjectDescriptor.class)),
                        uncategorized.getDisplayName().toString(locale).toLowerCase(locale), orJoinDisplayName(navs));
            }
            Locale locale = LocaleProvider.getLocale();
            return Messages.OrganizationFolder_Description(Messages.OrganizationFolder_DefaultProject(),
                    new UncategorizedSCMSourceCategory().getDisplayName().toString(locale).toLowerCase(locale),
                    Messages.OrganizationFolder_DefaultPronoun()
            );
        }

        //@Override TODO once baseline is 2.x
        public String getIconFilePathPattern() {
            List<SCMNavigatorDescriptor> descriptors =
                    remove(ExtensionList.lookup(SCMNavigatorDescriptor.class),
                            SingleSCMNavigator.DescriptorImpl.class);
            if (descriptors.size() == 1) {
                return descriptors.get(0).getIconFilePathPattern();
            } else {
                return "plugin/branch-api/images/:size/organization-folder.png";
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconClassName() {
            List<SCMNavigatorDescriptor> descriptors =
                    remove(ExtensionList.lookup(SCMNavigatorDescriptor.class),
                            SingleSCMNavigator.DescriptorImpl.class);
            if (descriptors.size() == 1) {
                return descriptors.get(0).getIconClassName();
            } else {
                return "icon-branch-api-organization-folder";
            }
        }

        /**
         * Joins the {@link Descriptor#getDisplayName()} in a chain of "or".
         * @param navs the {@link Descriptor}s.
         * @return a string representing a disjoint list of the display names.
         */
        private static String orJoinDisplayName(List<? extends Descriptor> navs) {
            String providers;
            switch (navs.size()) {
                case 1:
                    providers = navs.get(0).getDisplayName();
                    break;
                case 2:
                    providers = Messages.OrganizationFolder_OrJoin2(navs.get(0).getDisplayName(),
                            navs.get(1).getDisplayName());
                    break;
                case 3:
                    providers = Messages.OrganizationFolder_OrJoinN_Last(Messages.OrganizationFolder_OrJoinN_First(
                            navs.get(0).getDisplayName(), navs.get(1).getDisplayName()),
                            navs.get(2).getDisplayName());
                    break;
                default:
                    String wip = Messages.OrganizationFolder_OrJoinN_First(
                            navs.get(0).getDisplayName(), navs.get(1).getDisplayName());
                    for (int i = 2; i < navs.size() - 2; i++) {
                        wip = Messages.OrganizationFolder_OrJoinN_Middle(wip, navs.get(i).getDisplayName());
                    }
                    providers = Messages.OrganizationFolder_OrJoinN_Last(wip,
                            navs.get(navs.size() - 1).getDisplayName());
                    break;
            }
            return providers;
        }

        /**
         * Creates a filtered sublist.
         *
         * @param base the base list
         * @param type the type to remove from the base list
         * @return the list will all instances of the supplied type removed.
         */
        @Nonnull
        public static <T> List<T> remove(@Nonnull Iterable<T> base, @Nonnull Class<? extends T> type) {
            List<T> r = new ArrayList<T>();
            for (T i : base) {
                if (!type.isInstance(i))
                    r.add(i);
            }
            return r;
        }

        /**
         * Gets the {@link SCMSourceCategory#isUncategorized()} of a list of {@link SCMNavigatorDescriptor} instances.
         * @param descriptors the {@link SCMNavigatorDescriptor} instances.
         * @return the {@link SCMSourceCategory}.
         */
        private SCMSourceCategory genericScmSourceCategory(List<? extends SCMNavigatorDescriptor> descriptors) {
            List<SCMSourceCategory> sourceCategories = new ArrayList<>();
            for (SCMNavigatorDescriptor d: descriptors) {
                sourceCategories.addAll(d.getCategories());
            }
            SCMSourceCategory uncategorized = new UncategorizedSCMSourceCategory();
            for (SCMSourceCategory c: SCMSourceCategory.simplify(SCMSourceCategory.addUncategorizedIfMissing(sourceCategories)).values()) {
                if (c.isUncategorized()) {
                    uncategorized = c;
                    break;
                }
            }
            return uncategorized;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<FolderIconDescriptor> getIconDescriptors() {
            return Collections.<FolderIconDescriptor>singletonList(
                    Jenkins.getActiveInstance().getDescriptorByType(MetadataActionFolderIcon.DescriptorImpl.class)
            );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isIconConfigurable() {
            return false;
        }

        static {
            IconSet.icons.addIcon(
                    new Icon("icon-branch-api-organization-folder icon-sm",
                            "plugin/branch-api/images/16x16/organization-folder.png",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-branch-api-organization-folder icon-md",
                            "plugin/branch-api/images/24x24/organization-folder.png",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-branch-api-organization-folder icon-lg",
                            "plugin/branch-api/images/32x32/organization-folder.png",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-branch-api-organization-folder icon-xlg",
                            "plugin/branch-api/images/48x48/organization-folder.png",
                            Icon.ICON_XLARGE_STYLE));
        }
    }

    /**
     * Our scan.
     */
    public static class OrganizationScan extends FolderComputation<MultiBranchProject<?, ?>> {
        public OrganizationScan(OrganizationFolder folder, FolderComputation<MultiBranchProject<?, ?>> previous) {
            super(folder, previous);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.OrganizationFolder_OrganizationScan_displayName(getParent().getPronoun());
        }

        @Override
        public void run() {
            long start = System.currentTimeMillis();
            try {
                super.run();
            } finally {
                long end = System.currentTimeMillis();
                LOGGER.log(Level.INFO, "{0} #{1,time,yyyyMMdd.HHmmss} organization scan action completed: {2} in {3}",
                        new Object[]{
                                getParent().getFullName(), start, getResult(), Util.getTimeSpanString(end - start)
                        }
                );
            }
        }

    }

    @Extension
    public static class SCMEventListenerImpl extends SCMEventListener {

        public TaskListener globalEventsListener() {
            File eventsFile =
                    new File(Jenkins.getActiveInstance().getRootDir(), OrganizationFolder.class.getName() + ".log");
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
            if (CREATED == event.getType() || UPDATED == event.getType()) {
                for (OrganizationFolder p : Jenkins.getActiveInstance().getAllItems(OrganizationFolder.class)) {
                    // we want to catch when a branch is created / updated and consequently becomes eligible
                    // against the criteria. First check if the event matches one of the navigators
                    SCMNavigator navigator = null;
                    for (SCMNavigator n : p.getSCMNavigators()) {
                        if (event.isMatch(n)) {
                            global.getLogger().format("Found match against %s%n", p.getFullName());
                            navigator = n;
                            break;
                        }
                    }
                    if (navigator == null) {
                        continue;
                    }
                    // ok, now check if any of the sources are a match... if they are then this event is not our
                    // concern
                    for (SCMSource s : p.getSCMSources()) {
                        if (event.isMatch(s)) {
                            // already have a source that will see this
                            global.getLogger()
                                    .format("Project %s already has a corresponding sub-project%n", p.getFullName());
                            navigator = null;
                            break;
                        }
                    }
                    if (navigator != null) {
                        global.getLogger()
                                .format("Project %s does not have a corresponding sub-project%n", p.getFullName());
                        TaskListener listener;
                        try {
                            listener = p.getComputation().createEventsListener();
                        } catch (IOException e) {
                            listener = new LogTaskListener(LOGGER, Level.FINE);
                        }
                        ChildObserver childObserver = p.createEventsChildObserver();
                        long start = System.currentTimeMillis();
                        listener.getLogger().format("[%tc] Received %s event with timestamp %tc%n",
                                start, event.getType().name(), event.getTimestamp());
                        try {
                            navigator.visitSources(p.new SCMSourceObserverImpl(listener, childObserver), event);
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

        @Override
        public void onSCMSourceEvent(SCMSourceEvent<?> event) {
            TaskListener global = globalEventsListener();
            global.getLogger().format("[%tc] Received %s %s event with timestamp %tc%n",
                    System.currentTimeMillis(), event.getClass().getName(), event.getType().name(),
                    event.getTimestamp());
            if (CREATED == event.getType()) {
                for (OrganizationFolder p : Jenkins.getActiveInstance().getAllItems(OrganizationFolder.class)) {
                    boolean haveMatch = false;
                    for (SCMNavigator n : p.getSCMNavigators()) {
                        if (event.isMatch(n)) {
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
                        ChildObserver childObserver = p.createEventsChildObserver();
                        long start = System.currentTimeMillis();
                        listener.getLogger().format("[%tc] Received %s event with timestamp %tc%n",
                                start, event.getType().name(), event.getTimestamp());
                        try {
                            for (SCMNavigator n : p.getSCMNavigators()) {
                                if (event.isMatch(n)) {
                                    try {
                                        n.visitSources(p.new SCMSourceObserverImpl(listener, childObserver), event);
                                    } catch (IOException e) {
                                        e.printStackTrace(listener.error(e.getMessage()));
                                    }
                                }
                            }
                        } catch (InterruptedException e) {
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

    private class SCMSourceObserverImpl extends SCMSourceObserver {
        private final TaskListener listener;
        private final ChildObserver<MultiBranchProject<?, ?>> observer;

        public SCMSourceObserverImpl(TaskListener listener, ChildObserver<MultiBranchProject<?, ?>> observer) {
            this.listener = listener;
            this.observer = observer;
        }

        @NonNull
        @Override
        public SCMSourceOwner getContext() {
            return OrganizationFolder.this;
        }

        @NonNull
        @Override
        public TaskListener getListener() {
            return listener;
        }

        @NonNull
        @Override
        public ProjectObserver observe(@NonNull final String projectName) {
            return new ProjectObserver() {
                List<SCMSource> sources = new ArrayList<SCMSource>();

                @Override
                public void addSource(@NonNull SCMSource source) {
                    sources.add(source);
                    source.setOwner(OrganizationFolder.this);
                }

                private List<BranchSource> createBranchSources() {
                    if (sources == null) {
                        throw new IllegalStateException();
                    }
                    List<BranchSource> branchSources = new ArrayList<BranchSource>();
                    for (SCMSource source : sources) {
                        // TODO do we want/need a more general BranchPropertyStrategyFactory?
                        branchSources.add(new BranchSource(source));
                    }
                    sources = null; // make sure complete gets called just once
                    return branchSources;
                }

                @Override
                public void addAttribute(@NonNull String key, Object value)
                        throws IllegalArgumentException, ClassCastException {
                    throw new IllegalArgumentException();
                }

                @Override
                public void complete() throws IllegalStateException, InterruptedException {
                    try {
                        MultiBranchProjectFactory factory = null;
                        Map<String, Object> attributes = Collections.<String, Object>emptyMap();
                        for (MultiBranchProjectFactory candidateFactory : projectFactories) {
                            if (candidateFactory.recognizes(
                                    OrganizationFolder.this, projectName, sources, attributes, listener
                            )) {
                                factory = candidateFactory;
                                break;
                            }
                        }
                        if (factory == null) {
                            return;
                        }
                        MultiBranchProject<?, ?> existing = observer.shouldUpdate(projectName);
                        if (existing != null) {
                            PersistedList<BranchSource> sourcesList = existing.getSourcesList();
                            sourcesList.clear();
                            sourcesList.addAll(createBranchSources());
                            existing.setOrphanedItemStrategy(getOrphanedItemStrategy());
                            factory.updateExistingProject(existing, attributes, listener);
                            existing.scheduleBuild();
                            return;
                        }
                        if (!observer.mayCreate(projectName)) {
                            listener.getLogger().println("Ignoring duplicate child " + projectName);
                            return;
                        }
                        MultiBranchProject<?, ?> project = factory.createNewProject(
                                OrganizationFolder.this, projectName, sources, attributes, listener
                        );
                        project.setOrphanedItemStrategy(getOrphanedItemStrategy());
                        project.getSourcesList().addAll(createBranchSources());
                        try {
                            project.addTrigger(new PeriodicFolderTrigger("1d"));
                        } catch (ANTLRException x) {
                            throw new IllegalStateException(x);
                        }
                        observer.created(project);
                        project.scheduleBuild();
                    } catch (InterruptedException x) {
                        throw x;
                    } catch (Exception x) {
                        x.printStackTrace(listener.error("Failed to create or update a subproject " + projectName));
                    }
                }
            };
        }

        @Override
        public void addAttribute(@NonNull String key, Object value)
                throws IllegalArgumentException, ClassCastException {
            throw new IllegalArgumentException();
        }
    }
}
