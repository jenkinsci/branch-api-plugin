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
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.util.DescribableList;
import hudson.util.PersistedList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMNavigatorOwner;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCategory;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.impl.SingleSCMNavigator;
import jenkins.scm.impl.UncategorizedSCMSourceCategory;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jvnet.localizer.LocaleProvider;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * A folder-like collection of {@link MultiBranchProject}s, one per repository.
 */
@Restricted(NoExternalUse.class) // not currently intended as an API
@SuppressWarnings({"unchecked", "rawtypes"}) // mistakes in various places
public final class OrganizationFolder extends ComputedFolder<MultiBranchProject<?,?>> implements SCMNavigatorOwner {

    private final DescribableList<SCMNavigator,SCMNavigatorDescriptor> navigators = new DescribableList<SCMNavigator, SCMNavigatorDescriptor>(this);
    private final DescribableList<MultiBranchProjectFactory,MultiBranchProjectFactoryDescriptor> projectFactories = new DescribableList<MultiBranchProjectFactory,MultiBranchProjectFactoryDescriptor>(this);

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
        super.submit(req, rsp);
        navigators.rebuildHetero(req, req.getSubmittedForm(), ExtensionList.lookup(SCMNavigatorDescriptor.class), "navigators");
        projectFactories.rebuildHetero(req, req.getSubmittedForm(), ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class), "projectFactories");
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

    /**
     * {@inheritDoc}
     */
    @Override
    protected void computeChildren(final ChildObserver<MultiBranchProject<?,?>> observer, final TaskListener listener) throws IOException, InterruptedException {
        listener.getLogger().format("Updating actions...%n");
        Map<Class<? extends Action>, Action> persistentActions = new HashMap<>();
        for (ListIterator<SCMNavigator> iterator = navigators.listIterator(navigators.size()); iterator.hasPrevious(); ) {
            SCMNavigator navigator = iterator.previous();
            // first navigator always wins in case of duplicate keys
            persistentActions.putAll(navigator.fetchActions(this, listener));
        }
        // update any persistent actions for the SCMNavigator
        if (!persistentActions.isEmpty()) {
            BulkChange bc = new BulkChange(this);
            try {
                for (Map.Entry<Class<? extends Action>, Action> entry: persistentActions.entrySet()) {
                    if (entry.getValue() == null) {
                        removeActions(entry.getKey());
                    } else {
                        replaceActions(entry.getKey(), entry.getValue());
                    }
                }
                bc.commit();
            }  finally {
                bc.abort();
            }
        }
        for (SCMNavigator navigator : navigators) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            listener.getLogger().format("Consulting %s%n", navigator.getDescriptor().getDisplayName());
            navigator.visitSources(new SCMSourceObserver() {
                @Override
                public SCMSourceOwner getContext() {
                    return OrganizationFolder.this;
                }
                @Override
                public TaskListener getListener() {
                    return listener;
                }
                @Override
                public SCMSourceObserver.ProjectObserver observe(final String projectName) {
                    return new ProjectObserver() {
                        List<SCMSource> sources = new ArrayList<SCMSource>();
                        @Override
                        public void addSource(SCMSource source) {
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
                        public void addAttribute(String key, Object value) throws IllegalArgumentException, ClassCastException {
                            throw new IllegalArgumentException();
                        }
                        @Override
                        public void complete() throws IllegalStateException, InterruptedException {
                            try {
                                MultiBranchProjectFactory factory = null;
                                Map<String, Object> attributes = Collections.<String,Object>emptyMap();
                                for (MultiBranchProjectFactory candidateFactory : projectFactories) {
                                    if (candidateFactory.recognizes(OrganizationFolder.this, projectName, sources, attributes, listener)) {
                                        factory = candidateFactory;
                                        break;
                                    }
                                }
                                if (factory == null) {
                                    return;
                                }
                                MultiBranchProject<?,?> existing = observer.shouldUpdate(projectName);
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
                                MultiBranchProject<?, ?> project = factory.createNewProject(OrganizationFolder.this, projectName, sources, attributes, listener);
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
                public void addAttribute(String key, Object value) throws IllegalArgumentException, ClassCastException {
                    throw new IllegalArgumentException();
                }
            });
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

    }
}
