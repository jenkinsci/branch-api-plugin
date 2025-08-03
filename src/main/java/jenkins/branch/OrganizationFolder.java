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

import com.cloudbees.hudson.plugins.folder.AbstractFolderDescriptor;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.ChildNameGenerator;
import com.cloudbees.hudson.plugins.folder.FolderIcon;
import com.cloudbees.hudson.plugins.folder.FolderIconDescriptor;
import com.cloudbees.hudson.plugins.folder.computed.ChildObserver;
import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import com.cloudbees.hudson.plugins.folder.computed.EventOutputStreams;
import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;
import com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger;
import com.cloudbees.hudson.plugins.folder.views.AbstractFolderViewHolder;
import com.thoughtworks.xstream.XStreamException;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.XmlFile;
import hudson.console.ModelHyperlinkNote;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Saveable;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.listeners.SaveableListener;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.util.DescribableList;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMEventListener;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMNavigatorEvent;
import jenkins.scm.api.SCMNavigatorOwner;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceEvent;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import net.sf.json.JSONObject;

import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;

import static hudson.Functions.printStackTrace;
import static jenkins.scm.api.SCMEvent.Type.CREATED;
import static jenkins.scm.api.SCMEvent.Type.UPDATED;

/**
 * A folder-like collection of {@link MultiBranchProject}s, one per repository.
 */
@SuppressWarnings({"unchecked", "rawtypes"}) // mistakes in various places
public final class OrganizationFolder extends ComputedFolder<MultiBranchProject<?,?>>
        implements SCMNavigatorOwner, IconSpec {

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(OrganizationFolder.class.getName());
    static final String COMPLETED_PROCESSING_EVENT = "[%tc] Finished processing %s %s event from %s with timestamp %tc, processed in %dms. Matched %d.%n";
    /**
     * Our navigators.
     */
    private final DescribableList<SCMNavigator,SCMNavigatorDescriptor> navigators = new DescribableList<>(this);
    /**
     * Our project factories.
     */
    private final DescribableList<MultiBranchProjectFactory,MultiBranchProjectFactoryDescriptor> projectFactories = new DescribableList<>(this);
    /**
     * The rules for automatic building of branches.
     *
     * @since 2.0.12
     */
    private DescribableList<BranchBuildStrategy, BranchBuildStrategyDescriptor> buildStrategies = new DescribableList<>(this);

    /**
     * The branches properties.
     *
     * @since 2.5.9
     */
    private BranchPropertyStrategy strategy;

    /**
     * The persisted state maintained outside of the config file.
     *
     * @since 2.0
     */
    private transient /*almost final*/ State state = new State(this);

    /**
     * The navigator digest used to detect if we need to trigger a rescan on save.
     *
     * @since 2.0
     */
    private transient String navDigest;

    /**
     * The factory digest used to detect if we need to trigger a rescan on save.
     *
     * @since 2.0
     */
    private transient String facDigest;

    /**
     * The {@link #propertyStrategy} digest used to detect if we need to trigger a rescan on save.
     *
     * @since 2.5.9
     */
    private transient String propsDigest;

    /**
     * The {@link #buildStrategies} digest used to detect if we need to trigger a rescan on save.
     *
     * @since 2.0.12
     */
    private transient String bbsDigest;

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

        if( projectFactories.isEmpty() ) {
            for (MultiBranchProjectFactoryDescriptor d : ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class)) {
                MultiBranchProjectFactory f = d.newInstance();
                if (f != null) {
                    projectFactories.add(f);
                }
            }
        }

        addTrigger(new PeriodicFolderTrigger("1d"));
        try {
            addProperty(OrganizationChildTriggersProperty.newDefaultInstance());
            addProperty(OrganizationChildOrphanedItemsProperty.newDefaultInstance());
        } catch (IOException e) {
            throw new IllegalStateException(e);
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
        if (buildStrategies == null) {
            buildStrategies = new DescribableList<>(this);
        } else {
            buildStrategies.setOwner(this);
        }
        if (!(getFolderViews() instanceof OrganizationFolderViewHolder)) {
            resetFolderViews();
        }
        if (getIcon() == null) {
            setIcon(newDefaultFolderIcon());
        }
        if (getProperties().get(OrganizationChildTriggersProperty.class) == null) {
            try {
                addProperty(OrganizationChildTriggersProperty.newDefaultInstance());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        if (getProperties().get(OrganizationChildOrphanedItemsProperty.class) == null) {
            try {
                addProperty(OrganizationChildOrphanedItemsProperty.newDefaultInstance());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        PropertyMigration.applyAll(this);
        if (state == null) {
            state = new State(this);
        }
        try {
            state.load();
        } catch (XStreamException | IOException e) {
            LOGGER.log(Level.WARNING, "Could not read persisted state, will be recovered on next index.", e);
            state.reset();
        }
        if (getComputation().getLogFile().isFile()) {
            // TODO find a more reliable way to detect if the folder has not been scanned since creation
            // Basically we want the first save after a config change to trigger a scan.
            // The above condition will cover the very first save, but will not cover the case of the configuration
            // being changed *by code not the user*, saved and then Jenkins restarted before the scan occurs.
            // Should not be a big deal as periodic scan will pick it up eventually and user can always manually force
            // the issue by triggering a manual scan
            try {
                navDigest = Util.getDigestOf(Items.XSTREAM2.toXML(navigators));
            } catch (XStreamException e) {
                navDigest = null;
            }
            try {
                facDigest = Util.getDigestOf(Items.XSTREAM2.toXML(projectFactories));
            } catch (XStreamException e) {
                facDigest = null;
            }
            try {
                propsDigest = Util.getDigestOf(Items.XSTREAM2.toXML(strategy));
            } catch (XStreamException e) {
                propsDigest = null;
            }
            try {
                bbsDigest = Util.getDigestOf(Items.XSTREAM2.toXML(buildStrategies));
            } catch (XStreamException e) {
                bbsDigest = null;
            }
        }
    }

    @Override
    public MultiBranchProject<?, ?> getItem(String name) throws AccessDeniedException {
        if (name == null) {
            return null;
        }
        MultiBranchProject<?, ?> item = super.getItem(name);
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
     * Returns the child job with the specified project name or {@code null} if no such child job exists.
     *
     * @param projectName the name of the project.
     * @return the child job or {@code null} if no such job exists or if the requesting user does ave permission to
     * view it.
     * @since 2.0.0
     */
    @edu.umd.cs.findbugs.annotations.CheckForNull
    public MultiBranchProject<?,?> getItemByProjectName(@NonNull String projectName) {
        return super.getItem(NameEncoder.encode(projectName));
    }

    /**
     * @deprecated Directly check {@link List#size} of {@link #getSCMNavigators} if desired.
     */
    @Deprecated
    public boolean isSingleOrigin() {
        return navigators.size() == 1;
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
     * Gets the strategy.
     *
     * @return the strategy.
     * @since 2.5.9
     */
    public BranchPropertyStrategy getStrategy() {
        return strategy != null ? strategy : new DefaultBranchPropertyStrategy(new BranchProperty[0]);
    }

    /**
     * Sets the branch property strategy.
     *
     * @param strategy chosen.
     * @since 2.5.9
     */
    public void setStrategy(BranchPropertyStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * The {@link BranchBuildStrategy}s to apply.
     *
     * @return The {@link BranchBuildStrategy}s to apply.
     * @since 2.0.12
     */
    public DescribableList<BranchBuildStrategy, BranchBuildStrategyDescriptor> getBuildStrategies() {
        return buildStrategies;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void submit(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException, Descriptor.FormException {
        super.submit(req, rsp);

        JSONObject json = req.getSubmittedForm();
        navigators.rebuildHetero(req, json, ExtensionList.lookup(SCMNavigatorDescriptor.class), "navigators");
        projectFactories.rebuildHetero(req, json, ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class), "projectFactories");
        buildStrategies.rebuildHetero(req, json, ExtensionList.lookup(BranchBuildStrategyDescriptor.class), "buildStrategies");
        strategy = req.bindJSON(BranchPropertyStrategy.class, json.getJSONObject("strategy"));

        for (SCMNavigator n : navigators) {
            n.afterSave(this);
        }
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
        String propsDigest;
        try {
            propsDigest = Util.getDigestOf(Items.XSTREAM2.toXML(strategy));
        } catch (XStreamException e) {
            propsDigest = null;
        }
        String bbsDigest;
        try {
            bbsDigest = Util.getDigestOf(Items.XSTREAM2.toXML(buildStrategies));
        } catch (XStreamException e) {
            bbsDigest = null;
        }
        recalculateAfterSubmitted(!StringUtils.equals(navDigest, this.navDigest));
        recalculateAfterSubmitted(!StringUtils.equals(facDigest, this.facDigest));
        recalculateAfterSubmitted(!StringUtils.equals(propsDigest, this.propsDigest));
        recalculateAfterSubmitted(!StringUtils.equals(bbsDigest, this.bbsDigest));
        this.navDigest = navDigest;
        this.facDigest = facDigest;
        this.propsDigest = propsDigest;
        this.bbsDigest = bbsDigest;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected FolderComputation<MultiBranchProject<?, ?>> createComputation(
            @CheckForNull FolderComputation<MultiBranchProject<?, ?>> previous) {
        return new OrganizationScan(OrganizationFolder.this, previous);
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
    public boolean isBuildable() {
        return super.isBuildable() && !navigators.isEmpty() && !projectFactories.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void computeChildren(final ChildObserver<MultiBranchProject<?,?>> observer, final TaskListener listener) throws IOException, InterruptedException {
        // capture the current digests to prevent unnecessary rescan if re-saving after scan
        try {
            navDigest = Util.getDigestOf(Items.XSTREAM2.toXML(navigators));
        } catch (XStreamException e) {
            navDigest = null;
        }
        try {
            facDigest = Util.getDigestOf(Items.XSTREAM2.toXML(projectFactories));
        } catch (XStreamException e) {
            facDigest = null;
        }
        try {
            bbsDigest = Util.getDigestOf(Items.XSTREAM2.toXML(buildStrategies));
        } catch (XStreamException e) {
            bbsDigest = null;
        }
        long start = System.currentTimeMillis();
        listener.getLogger().format("[%tc] Starting organization scan...%n", start);
        try {
            listener.getLogger().format("[%tc] Updating actions...%n", System.currentTimeMillis());
            Map<SCMNavigator, List<Action>> navigatorActions = new HashMap<>();
            for (SCMNavigator navigator : navigators) {
                List<Action> actions;
                try {
                    actions = navigator.fetchActions(this, null, listener);
                } catch (IOException e) {
                    printStackTrace(e, listener.error("[%tc] Could not refresh actions for navigator %s",
                            System.currentTimeMillis(), navigator));
                    // preserve previous actions if we have some transient error fetching now (e.g. API rate limit)
                    actions = Util.fixNull(state.getActions().get(navigator));
                }
                navigatorActions.put(navigator, actions);
            }
            // update any persistent actions for the SCMNavigator
            if (!navigatorActions.equals(state.getActions())) {
                boolean saveProject = false;
                for (List<Action> actions : navigatorActions.values()) {
                    for (Action a : actions) {
                        // undo any hacks that attached the contributed actions without attribution
                        saveProject = removeActions(a.getClass()) || saveProject;
                    }
                }
                BulkChange bc = new BulkChange(state);
                try {
                    state.setActions(navigatorActions);
                    try {
                        bc.commit();
                    } catch (IOException | RuntimeException e) {
                        listener.error("[%tc] Could not persist folder level actions",
                                System.currentTimeMillis());
                        throw e;
                    }
                    if (saveProject) {
                        try {
                            save();
                        } catch (IOException | RuntimeException e) {
                            listener.error(
                                    "[%tc] Could not persist folder level configuration changes",
                                    System.currentTimeMillis());
                            throw e;
                        }
                    }
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
                try {
                    navigator.visitSources(new SCMSourceObserverImpl(listener, observer, navigator, null));
                } catch (IOException | InterruptedException | RuntimeException e) {
                    listener.error("[%tc] Could not fetch sources from navigator %s",
                            System.currentTimeMillis(), navigator);
                    throw e;
                }
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
     * Get the term used in the UI to represent the source for this kind of
     * {@link Item}. Must start with a capital letter.
     * @return term used in the UI to represent the souce
     */
    public String getSourcePronoun() {
        Set<String> result = new TreeSet<>();
        for (SCMNavigator navigator: navigators) {
            String pronoun = Util.fixEmptyAndTrim(navigator.getPronoun());
            if (pronoun != null) {
                result.add(pronoun);
            }
        }
        return result.isEmpty() ? this.getPronoun() : StringUtils.join(result, " / ");
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<SCMSource> getSCMSources() {
        Set<SCMSource> result = new HashSet<>();
        for (MultiBranchProject<?,?> child : getItems(MultiBranchProject::isBuildable)) {
            result.addAll(child.getSCMSources());
        }
        return new ArrayList<>(result);
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
    public void onSCMSourceUpdated(@NonNull SCMSource source) {
        // TODO possibly we should recheck whether this project remains valid
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SCMSourceCriteria getSCMSourceCriteria(@NonNull SCMSource source) {
        return null;
    }

    /**
     * Will create an specialized view when there are no repositories or branches found, which contain a Jenkinsfile
     * or other MARKER file.
     */
    @Override
    public View getPrimaryView() {
        if (!hasVisibleItems()) {
            return getWelcomeView();
        }
        return super.getPrimaryView();
    }

    /**
     * Creates a place-holder view when there's no active repositories indexed.
     *
     * @return a place-holder view for when there's no active repositories indexed.
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
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ACL getACL() {
        final ACL acl = super.getACL();
        if (getParent() instanceof ComputedFolder<?>) {
            return new ACL() {
                @Override
                public boolean hasPermission2(@NonNull Authentication a, @NonNull Permission permission) {
                    if (ACL.SYSTEM2.equals(a)) {
                        return true;
                    } else if (SUPPRESSED_PERMISSIONS.contains(permission)) {
                        return false;
                    } else {
                        return acl.hasPermission2(a, permission);
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
     * Our descriptor
     */
    @Extension
    @Symbol("organizationFolder")
    public static class DescriptorImpl extends AbstractFolderDescriptor {

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.OrganizationFolder_DisplayName();
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
        @Override
        @NonNull
        public String getCategoryId() {
            return "nested-projects";
        }

        /**
         * Gets all the {@link BranchPropertyStrategyDescriptor} instances applicable to the specified project and source.
         *
         * @return all the {@link BranchPropertyStrategyDescriptor} instances  applicable to the specified project and
         *         source.
         */
        public List<BranchPropertyStrategyDescriptor> propertyStrategyDescriptors() {
            return BranchPropertyStrategyDescriptor.all();
        }

        /**
         * A description of this {@link OrganizationFolder}.
         *
         * @return A string with the description. {@code TopLevelItemDescriptor#getDescription()}.
         */
        @Override
        @NonNull
        public String getDescription() {
            return Messages.OrganizationFolder_Description();
        }

        @Override
        public String getIconFilePathPattern() {
            return "plugin/branch-api/images/organization-folder.svg";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getIconClassName() {
            return "symbol-business-outline plugin-ionicons-api";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<FolderIconDescriptor> getIconDescriptors() {
            return Collections.singletonList(
                    Jenkins.get().getDescriptorByType(MetadataActionFolderIcon.DescriptorImpl.class)
            );
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isIconConfigurable() {
            return true;
        }

        @Override
        @NonNull
        public final ChildNameGenerator<OrganizationFolder, ? extends TopLevelItem> childNameGenerator() {
            return ChildNameGeneratorImpl.INSTANCE;
        }

        static {
            IconSet.icons.addIcon(
                    new Icon("icon-branch-api-organization-folder icon-sm",
                            "plugin/branch-api/images/organization-folder.svg",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-branch-api-organization-folder icon-md",
                            "plugin/branch-api/images/organization-folder.svg",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-branch-api-organization-folder icon-lg",
                            "plugin/branch-api/images/organization-folder.svg",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-branch-api-organization-folder icon-xlg",
                            "plugin/branch-api/images/organization-folder.svg",
                            Icon.ICON_XLARGE_STYLE));
        }
    }

    private static class ChildNameGeneratorImpl extends ChildNameGenerator<OrganizationFolder, MultiBranchProject<?,?>> {

        private static final ChildNameGeneratorImpl INSTANCE = new ChildNameGeneratorImpl();

        @Override
        @CheckForNull
        public String itemNameFromItem(@NonNull OrganizationFolder parent, @NonNull MultiBranchProject<?, ?> item) {
            ProjectNameProperty property = item.getProperties().get(ProjectNameProperty.class);
            if (property != null) {
                return NameEncoder.encode(property.getName());
            }
            String name = item.getName();
            if (name != null) {
                return NameEncoder.encode(name);
            }
            return null;
        }

        @Override
        @CheckForNull
        public String dirNameFromItem(@NonNull OrganizationFolder parent, @NonNull MultiBranchProject<?, ?> item) {
            ProjectNameProperty property = item.getProperties().get(ProjectNameProperty.class);
            if (property != null) {
                return NameMangler.apply(property.getName());
            }
            String name = item.getName();
            if (name != null) {
                return NameMangler.apply(name);
            }
            return null;
        }

        @Override
        @NonNull
        public String itemNameFromLegacy(@NonNull OrganizationFolder parent, @NonNull String legacyDirName) {
            return NameEncoder.decode(legacyDirName);
        }

        @Override
        @NonNull
        public String dirNameFromLegacy(@NonNull OrganizationFolder parent, @NonNull String legacyDirName) {
            return NameMangler.apply(NameEncoder.decode(legacyDirName));
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
            return Messages.OrganizationFolder_OrganizationScan_displayName(((OrganizationFolder)getParent()).getSourcePronoun());
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

    /**
     * Listens for events from the SCM event system.
     *
     * @since 2.0
     */
    @Extension
    public static class SCMEventListenerImpl extends SCMEventListener {

        private final EventOutputStreams globalEvents = createGlobalEvents();

        private EventOutputStreams createGlobalEvents() {
            File logsDir = new File(Jenkins.get().getRootDir(), "logs");
            if (!logsDir.isDirectory() && !logsDir.mkdirs()) {
                LOGGER.log(Level.WARNING, "Could not create logs directory: {0}", logsDir);
            }
            final File eventsFile = new File(logsDir, OrganizationFolder.class.getName() + ".log");
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
         * The {@link TaskListener} for events that we cannot assign to an organization folder.
         * @return The {@link TaskListener} for events that we cannot assign to an organization folder.
         */
        @Restricted(NoExternalUse.class)
        public StreamTaskListener globalEventsListener() {
            return new StreamBuildListener(globalEvents.get(), StandardCharsets.UTF_8);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onSCMHeadEvent(SCMHeadEvent<?> event) {
            try (StreamTaskListener global = globalEventsListener()) {
                String globalEventDescription = StringUtils.defaultIfBlank(event.description(), event.getClass().getName());
                long started = System.currentTimeMillis();
                global.getLogger().format("[%tc] Received %s %s event from %s with timestamp %tc%n",
                        started,
                        globalEventDescription,
                        event.getType().name(),
                        event.getOrigin(), event.getTimestamp());
                int matchCount = 0;
                if (CREATED == event.getType() || UPDATED == event.getType()) {
                    try {
                        for (OrganizationFolder p : Jenkins.get().getAllItems(OrganizationFolder.class)) {
                            if (!p.isBuildable()) {
                                if (LOGGER.isLoggable(Level.FINER)) {
                                    LOGGER.log(Level.FINER,
                                            "{0} {1} {2,date} {2,time}: Ignoring {3} because it is disabled",
                                            new Object[]{
                                                    globalEventDescription,
                                                    event.getType().name(), event.getTimestamp(), p.getFullName()
                                            }
                                    );
                                }
                                continue;
                            }
                            // we want to catch when a branch is created / updated and consequently becomes eligible
                            // against the criteria. First check if the event matches one of the navigators
                            SCMNavigator navigator = null;
                            for (SCMNavigator n : p.getSCMNavigators()) {
                                if (event.isMatch(n)) {
                                    matchCount++;
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
                                            .format("Project %s already has a corresponding sub-project%n",
                                                    p.getFullName());
                                    navigator = null;
                                    break;
                                }
                            }
                            if (navigator != null) {
                                global.getLogger()
                                        .format("Project %s does not have a corresponding sub-project%n",
                                                p.getFullName());
                                String localEventDescription = StringUtils.defaultIfBlank(
                                        event.descriptionFor(navigator),
                                        globalEventDescription
                                );
                                try (StreamTaskListener listener = p.getComputation().createEventsListener();
                                     ChildObserver childObserver = p.openEventsChildObserver()) {
                                    long start = System.currentTimeMillis();
                                    listener.getLogger()
                                            .format("[%tc] Received %s %s event from %s with timestamp %tc%n",
                                                    start, localEventDescription, event.getType().name(),
                                                    event.getOrigin(),
                                                    event.getTimestamp());
                                    try {
                                        navigator.visitSources(
                                                p.new SCMSourceObserverImpl(listener, childObserver, navigator, event),
                                                event);
                                    } catch (IOException e) {
                                        printStackTrace(e, listener.error(e.getMessage()));
                                    } catch (InterruptedException e) {
                                        listener.error(e.getMessage());
                                        throw e;
                                    } finally {
                                        long end = System.currentTimeMillis();
                                        listener.getLogger().format(
                                                "[%tc] %s %s event from %s with timestamp %tc processed in %s%n",
                                                end, localEventDescription, event.getType().name(),
                                                event.getOrigin(), event.getTimestamp(),
                                                Util.getTimeSpanString(end - start));
                                    }
                                } catch (IOException e) {
                                    printStackTrace(e, global.error(
                                            "[%tc] %s encountered an error while processing %s %s event from %s with "
                                                    + "timestamp %tc",
                                            System.currentTimeMillis(), ModelHyperlinkNote.encodeTo(p),
                                            globalEventDescription, event.getType().name(),
                                            event.getOrigin(), event.getTimestamp()));
                                } catch (InterruptedException e) {
                                    global.error(
                                            "[%tc] %s was interrupted while processing %s %s event from %s with "
                                                    + "timestamp %tc",
                                            System.currentTimeMillis(), ModelHyperlinkNote.encodeTo(p),
                                            globalEventDescription, event.getType().name(),
                                            event.getOrigin(), event.getTimestamp());
                                    throw e;
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        printStackTrace(e, global.error(
                                "[%tc] Interrupted while processing %s %s event from %s with timestamp %tc",
                                System.currentTimeMillis(), globalEventDescription, event.getType().name(),
                                event.getOrigin(), event.getTimestamp()));
                    }
                }
                long ended = System.currentTimeMillis();
                global.getLogger()
                        .format(COMPLETED_PROCESSING_EVENT,
                                ended, globalEventDescription, event.getType().name(),
                                event.getOrigin(), event.getTimestamp(), ended - started,  matchCount);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not close global event log file", e);
            }

        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onSCMNavigatorEvent(SCMNavigatorEvent<?> event) {
            try (StreamTaskListener global = globalEventsListener()) {
                long started = System.currentTimeMillis();
                global.getLogger().format("[%tc] Received %s %s event from %s with timestamp %tc%n",
                    started, event.getClass().getName(), event.getType().name(),
                        event.getOrigin(), event.getTimestamp());
                int matchCount = 0;
                if (UPDATED == event.getType()) {
                    Set<SCMNavigator> matches = new HashSet<>();
                    try {
                        for (OrganizationFolder p : Jenkins.get().getAllItems(OrganizationFolder.class)) {
                            matches.clear();
                            for (SCMNavigator n : p.getSCMNavigators()) {
                                if (event.isMatch(n)) {
                                    matches.add(n);
                                }
                            }
                            if (!matches.isEmpty()) {
                                matchCount++;
                                try (StreamTaskListener listener = p.getComputation().createEventsListener()) {
                                    Map<SCMNavigator, List<Action>> navigatorActions = new HashMap<>();
                                    for (SCMNavigator navigator : matches) {
                                        try {
                                            List<Action> newActions = navigator.fetchActions(p, event, listener);
                                            List<Action> oldActions = p.state.getActions(navigator);
                                            if (oldActions == null || !oldActions.equals(newActions)) {
                                                navigatorActions.put(navigator, newActions);
                                            }
                                        } catch (IOException e) {
                                            printStackTrace(e,
                                                    listener.error("Could not fetch metadata from %s", navigator));
                                        } catch (InterruptedException e) {
                                            listener.error(e.getMessage());
                                            throw e;
                                        }
                                    }
                                    // update any persistent actions for the SCMNavigator
                                    if (!navigatorActions.isEmpty()) {
                                        boolean saveProject = false;
                                        for (List<Action> actions : navigatorActions.values()) {
                                            for (Action a : actions) {
                                                // undo any hacks that attached the contributed actions without attribution

                                                saveProject = p.removeActions(a.getClass()) || saveProject;
                                            }
                                        }
                                        BulkChange bc = new BulkChange(p.state);
                                        try {
                                            for (Map.Entry<SCMNavigator, List<Action>> entry : navigatorActions
                                                    .entrySet()) {
                                                p.state.setActions(entry.getKey(), entry.getValue());
                                            }
                                            bc.commit();
                                            if (saveProject) {
                                                p.save();
                                            }
                                        } catch (IOException e) {
                                            printStackTrace(e, listener.error("Could not persist updated metadata"));
                                        } finally {
                                            bc.abort();
                                        }
                                    }
                                } catch (IOException e) {
                                    printStackTrace(e, global.error(
                                            "[%tc] %s encountered an error while processing %s %s event from %s with "
                                                    + "timestamp %tc",

                                            System.currentTimeMillis(), ModelHyperlinkNote.encodeTo(p),
                                            event.getClass().getName(), event.getType().name(),
                                            event.getOrigin(), event.getTimestamp()));
                                } catch (InterruptedException e) {
                                    global.error(
                                            "[%tc] %s was interrupted while processing %s %s event from %s with "
                                                    + "timestamp %tc",
                                            System.currentTimeMillis(), ModelHyperlinkNote.encodeTo(p),
                                            event.getClass().getName(), event.getType().name(),
                                            event.getOrigin(), event.getTimestamp());
                                    throw e;
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        printStackTrace(e, global.error(
                                "[%tc] Interrupted while processing %s %s event from %s with timestamp %tc",
                                System.currentTimeMillis(), event.getClass().getName(), event.getType().name(),
                                event.getOrigin(), event.getTimestamp()));
                    }
                }
                long ended = System.currentTimeMillis();
                global.getLogger()
                        .format(OrganizationFolder.COMPLETED_PROCESSING_EVENT,
                            ended, event.getClass().getName(), event.getType().name(),
                                event.getOrigin(), event.getTimestamp(), ended - started, matchCount);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not close global event log file", e);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onSCMSourceEvent(SCMSourceEvent<?> event) {
            try (StreamTaskListener global = globalEventsListener()) {
                long started = System.currentTimeMillis();
                global.getLogger().format("[%tc] Received %s %s event from %s with timestamp %tc%n",
                    started, event.getClass().getName(), event.getType().name(),
                        event.getOrigin(), event.getTimestamp());
                int matchCount = 0;
                if (CREATED == event.getType()) {
                    try {
                        for (OrganizationFolder p : Jenkins.get().getAllItems(OrganizationFolder.class)) {
                            boolean haveMatch = false;
                            for (SCMNavigator n : p.getSCMNavigators()) {
                                if (event.isMatch(n)) {
                                    global.getLogger().format("Found match against %s%n", p.getFullName());
                                    haveMatch = true;
                                    break;
                                }
                            }
                            if (haveMatch) {
                                matchCount++;
                                try (StreamTaskListener listener = p.getComputation().createEventsListener();
                                     ChildObserver childObserver = p.openEventsChildObserver()) {
                                    long start = System.currentTimeMillis();
                                    listener.getLogger()
                                            .format("[%tc] Received %s %s event from %s with timestamp %tc%n",
                                                    start, event.getClass().getName(), event.getType().name(),
                                                    event.getOrigin(), event.getTimestamp());
                                    try {
                                        for (SCMNavigator n : p.getSCMNavigators()) {
                                            if (event.isMatch(n)) {
                                                try {
                                                    n.visitSources(
                                                            p.new SCMSourceObserverImpl(listener, childObserver, n,
                                                                    event),
                                                            event
                                                    );
                                                } catch (IOException e) {
                                                    printStackTrace(e, listener.error(e.getMessage()));
                                                }
                                            }
                                        }
                                    } catch (InterruptedException e) {
                                        listener.error(e.getMessage());
                                        throw e;
                                    } finally {
                                        long end = System.currentTimeMillis();
                                        listener.getLogger().format(
                                                "[%tc] %s %s event from %s with timestamp %tc processed in %s%n",
                                                end, event.getClass().getName(), event.getType().name(),
                                                event.getOrigin(), event.getTimestamp(),
                                                Util.getTimeSpanString(end - start));
                                    }
                                } catch (IOException e) {
                                    printStackTrace(e, global.error(
                                            "[%tc] %s encountered an error while processing %s %s event from %s with "
                                                    + "timestamp %tc",

                                            System.currentTimeMillis(), ModelHyperlinkNote.encodeTo(p),
                                            event.getClass().getName(), event.getType().name(),
                                            event.getOrigin(), event.getTimestamp()));
                                } catch (InterruptedException e) {
                                    global.error(
                                            "[%tc] %s was interrupted while processing %s %s event from %s with "
                                                    + "timestamp %tc",
                                            System.currentTimeMillis(), ModelHyperlinkNote.encodeTo(p),
                                            event.getClass().getName(), event.getType().name(),
                                            event.getOrigin(), event.getTimestamp());
                                    throw e;
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        printStackTrace(e, global.error(
                                "[%tc] Interrupted while processing %s %s event from %s with timestamp %tc",
                                System.currentTimeMillis(), event.getClass().getName(), event.getType().name(),
                                event.getOrigin(), event.getTimestamp()));
                    }
                }
                long ended = System.currentTimeMillis();
                global.getLogger()
                        .format(OrganizationFolder.COMPLETED_PROCESSING_EVENT,
                            ended, event.getClass().getName(), event.getType().name(),
                                event.getOrigin(), event.getTimestamp(), ended - started, matchCount);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not close global event log file", e);
            }

        }
    }

    private class SCMSourceObserverImpl extends SCMSourceObserver {
        private final TaskListener listener;
        private final ChildObserver<MultiBranchProject<?, ?>> observer;
        private final SCMEvent<?> event;
        private final SCMNavigator navigator;

        public SCMSourceObserverImpl(TaskListener listener, ChildObserver<MultiBranchProject<?, ?>> observer,
                                     SCMNavigator navigator, SCMEvent<?> event) {
            this.listener = listener;
            this.observer = observer;
            this.navigator = navigator;
            this.event = event;
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
                List<SCMSource> sources = new ArrayList<>();

                @Override
                public void addSource(@NonNull SCMSource source) {
                    sources.add(source);
                    source.setOwner(OrganizationFolder.this);
                }

                private List<BranchSource> createBranchSources() {
                    if (sources == null) {
                        throw new IllegalStateException();
                    }
                    List<BranchSource> branchSources = new ArrayList<>();
                    for (SCMSource source : sources) {
                        BranchSource branchSource = new BranchSource(source);
                        branchSource.setBuildStrategies(buildStrategies);
                        branchSource.setStrategy(strategy);
                        branchSources.add(branchSource);
                    }
                    return branchSources;
                }

                @Override
                public void addAttribute(@NonNull String key, Object value)
                        throws IllegalArgumentException, ClassCastException {
                    throw new IllegalArgumentException();
                }

                private boolean recognizes(Map<String, Object> attributes, MultiBranchProjectFactory candidateFactory)
                        throws IOException, InterruptedException {
                    return candidateFactory.recognizes(
                                    OrganizationFolder.this,
                                    projectName,
                                    sources,
                                    attributes,
                                    event instanceof SCMHeadEvent ? (SCMHeadEvent<?>) event : null,
                                    listener);
                }

                @Override
                public void complete() throws IllegalStateException, IOException, InterruptedException {
                    try {
                        MultiBranchProjectFactory factory = null;
                        Map<String, Object> attributes = Collections.emptyMap();
                        for (MultiBranchProjectFactory candidateFactory : projectFactories) {
                            boolean recognizes = recognizes(attributes, candidateFactory);
                            LOGGER.fine(() -> candidateFactory + " recognizes " + projectName + " with " + attributes + "? " + recognizes);
                            if (recognizes) {
                                factory = candidateFactory;
                                break;
                            }
                        }
                        if (factory == null) {
                            return;
                        }
                        String folderName = NameEncoder.encode(projectName);
                        // HACK: observer.shouldUpdate will restore the buildable flag of the child, so pre-inspect
                        MultiBranchProject<?, ?> existing = items.get(folderName);
                        boolean wasBuildable = existing != null && existing.isBuildable();
                        boolean wasDisabled = existing != null && existing.isDisabled();
                        // END_HACK: now that we know if it was buildable, we can now proceed to see about updating
                        existing = observer.shouldUpdate(folderName);
                        try {
                            if (existing != null) {
                                completeExisting(factory, attributes, existing, wasBuildable, wasDisabled);
                            } else {
                                completeNew(factory, attributes, folderName);
                            }
                        } finally {
                            observer.completed(folderName);
                        }
                    } catch (InterruptedException | IOException x) {
                        throw x;
                    } catch (Exception x) {
                        printStackTrace(x, listener.error("Failed to create or update a subproject " + projectName));
                    }
                }

                private void completeExisting(MultiBranchProjectFactory factory, Map<String, Object> attributes, MultiBranchProject<?, ?> existing, boolean wasBuildable, boolean wasDisabled) throws IOException, InterruptedException {
                    BulkChange bc = new BulkChange(existing);
                    try {
                        existing.setSourcesList(createBranchSources());
                        factory.updateExistingProject(existing, attributes, listener);
                        ProjectNameProperty property =
                                existing.getProperties().get(ProjectNameProperty.class);
                        if (property == null || !projectName.equals(property.getName())) {
                            existing.getProperties().remove(ProjectNameProperty.class);
                            existing.addProperty(new ProjectNameProperty(projectName));
                        }
                        for (AbstractFolderProperty<?> folderProperty : getProperties()) {
                            if (folderProperty instanceof OrganizationFolderProperty) {
                                ((OrganizationFolderProperty) folderProperty).applyDecoration(existing,
                                        listener);
                            }
                        }
                    } finally {
                        bc.commit();
                    }
                    existing.fireSCMSourceAfterSave(existing.getSCMSources());
                    if (isBuildable() && existing.isBuildable()
                            && (!wasBuildable || wasDisabled || existing.updateDigests())) {
                        // if the digests changed or this is now buildable where previously it was not
                        // schedule the build
                        existing.scheduleBuild(cause());
                    }
                }

                private void completeNew(MultiBranchProjectFactory factory, Map<String, Object> attributes, String folderName) throws IOException, InterruptedException {
                    if (!observer.mayCreate(folderName)) {
                        listener.getLogger()
                                .println("Ignoring duplicate child " + projectName + " named " + folderName);
                        return;
                    }
                    if (getItem(folderName) != null) {
                        throw new IllegalStateException(
                                "JENKINS-42511: attempted to redundantly create " + folderName + " in "
                                        + OrganizationFolder.this);
                    }
                    MultiBranchProject<?, ?> project = factory.createNewProject(
                            OrganizationFolder.this, folderName, sources, attributes, listener
                    );
                    BulkChange bc = new BulkChange(project);
                    try {
                        if (!projectName.equals(folderName)) {
                            project.setDisplayName(projectName);
                        }
                        project.addProperty(new ProjectNameProperty(projectName));
                        project.getSourcesList().addAll(createBranchSources());
                        for (AbstractFolderProperty<?> property: getProperties()) {
                            if (property instanceof OrganizationFolderProperty) {
                                ((OrganizationFolderProperty) property).applyDecoration(project, listener);
                            }
                        }
                    } finally {
                        bc.commit();
                    }
                    observer.created(project);
                    project.fireSCMSourceAfterSave(project.getSCMSources());
                    if (isBuildable() && project.isBuildable()) {
                        // schedule the build
                        project.scheduleBuild(cause());
                    }
                }

            };
        }

        @Override
        public void addAttribute(@NonNull String key, Object value)
                throws IllegalArgumentException, ClassCastException {
            throw new IllegalArgumentException();
        }

        private Cause cause() {
            if (event instanceof SCMHeadEvent) {
                return new BranchEventCause(event, ((SCMHeadEvent) event).descriptionFor(navigator));
            }
            if (event instanceof SCMSourceEvent) {
                return new BranchEventCause(event, ((SCMSourceEvent) event).descriptionFor(navigator));
            }
            if (event instanceof SCMNavigatorEvent) {
                return new BranchEventCause(event, ((SCMNavigatorEvent) event).descriptionFor(navigator));
            }
            if (event != null){
                return new BranchEventCause(event, event.description());
            }
            return new BranchIndexingCause();
        }
    }

    /**
     * Adds the {@link OrganizationFolder.State#getActions()} to {@link OrganizationFolder#getAllActions()}.
     *
     * @since 2.0
     */
    @Extension
    public static class StateActionFactory extends TransientActionFactory<OrganizationFolder> {

        @Override
        public Class<OrganizationFolder> type() {
            return OrganizationFolder.class;
        }

        @NonNull
        @Override
        public Collection<? extends Action> createFor(@NonNull OrganizationFolder target) {
            List<Action> result = new ArrayList<>();
            for (List<Action> actions: target.state.getActions().values()) {
                result.addAll(actions);
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
        private final transient OrganizationFolder owner;
        /**
         * The {@link SCMNavigator#fetchActions(SCMNavigatorOwner, SCMNavigatorEvent, TaskListener)} for each {@link SCMNavigator} keyed by the digest of the {@link SCMNavigator}.
         */
        private final Map<String,List<Action>> actions = new HashMap<>();

        private State(OrganizationFolder owner) {
            this.owner = owner;
        }

        public synchronized void reset() {
            actions.clear();
        }

        public final XmlFile getStateFile() {
            return new XmlFile(Items.XSTREAM, new File(owner.getRootDir(), "state.xml"));
        }

        public synchronized void load() throws IOException {
            if (getStateFile().exists()) {
                getStateFile().unmarshal(this);
            }
        }

        /**
         * Save the settings to a file.
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

        public List<Action> getActions(SCMNavigator navigator) {
            if (owner.getSCMNavigators().contains(navigator)) {
                return Collections.unmodifiableList(Util.fixNull(actions.get(navigator.getId())));
            }
            return null;
        }

        public void setActions(SCMNavigator navigator, List<Action> actions) {
            this.actions.put(navigator.getId(), new ArrayList<>(actions));
        }

        public Map<SCMNavigator, List<Action>> getActions() {
            List<SCMNavigator> navigators = owner.getSCMNavigators();
            Map<SCMNavigator, List<Action>> result = new HashMap<>(navigators.size());
            for (SCMNavigator navigator: navigators) {
                result.put(navigator, Collections.unmodifiableList(Util.fixNull(actions.get(navigator.getId()))));
            }
            return result;
        }

        public void setActions(Map<SCMNavigator, List<Action>> actions) {
            Set<String> keys = new HashSet<>();
            for (Map.Entry<SCMNavigator, List<Action>> entry: actions.entrySet()) {
                String id = entry.getKey().getId();
                this.actions.put(id, new ArrayList<>(Util.fixNull(entry.getValue())));
                keys.add(id);
            }
            this.actions.keySet().retainAll(keys);
        }
    }
}
