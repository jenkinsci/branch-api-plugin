/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

import com.cloudbees.hudson.plugins.folder.views.AbstractFolderViewHolder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.ListView;
import hudson.model.View;
import hudson.model.ViewDescriptor;
import hudson.model.ViewGroup;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.util.DescribableList;
import hudson.views.ListViewColumn;
import hudson.views.StatusColumn;
import hudson.views.ViewsTabBar;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import jenkins.scm.api.SCMHeadCategory;
import net.jcip.annotations.GuardedBy;
import org.acegisecurity.Authentication;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Holds the view configuration for an {@link MultiBranchProject}.
 *
 * @since 2.0
 */
public class MultiBranchProjectViewHolder extends AbstractFolderViewHolder {
    /**
     * Our owning {@link MultiBranchProject}.
     */
    @NonNull
    private final MultiBranchProject<?, ?> owner;
    /**
     * The {@link ViewsTabBar} instance.
     */
    private transient ViewsTabBar tabBar;
    /**
     * The list of {@link View}s.
     */
    @GuardedBy("this")
    private transient List<View> views = null;
    /**
     * The primary view name.
     */
    @GuardedBy("this")
    private transient String primaryView = null;

    /**
     * Constructor.
     *
     * @param owner the owning {@link MultiBranchProject}.
     */
    public MultiBranchProjectViewHolder(MultiBranchProject<?, ?> owner) {
        super();
        this.owner = owner;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public synchronized List<View> getViews() {
        if (owner.getItems().isEmpty()) {
            // when there are no branches nor pull requests to show, switch to the special welcome view
            return Collections.singletonList(owner.getWelcomeView());
        }
        if (views == null) {
            List<View> views = new ArrayList<>();
            for (SCMHeadCategory c : SCMHeadCategory.collectAndSimplify(owner.getSCMSources()).values()) {
                views.add(new ViewImpl(owner, c));
                if (c.isUncategorized()) {
                    primaryView = c.getName();
                }
            }
            this.views = views;
        }
        return views;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setViews(@NonNull List<? extends View> views) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized String getPrimaryView() {
        if (owner.getItems().isEmpty()) {
            // when there are no branches nor pull requests to show, switch to the special welcome view
            return BaseEmptyView.VIEW_NAME;
        }
        if (primaryView == null) {
            getViews(); // will set primaryView for us
        }
        return primaryView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPrimaryView(@CheckForNull String name) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isPrimaryModifiable() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ViewsTabBar getTabBar() {
        if (tabBar == null) {
            tabBar = new LocalizedViewsTabBar();
        }
        return tabBar;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTabBar(@NonNull ViewsTabBar tabBar) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTabBarModifiable() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void invalidateCaches() {
        views = null;
        primaryView = null;
    }

    /**
     * A custom category specific view.
     */
    @Restricted(NoExternalUse.class)
    public static class ViewImpl extends ListView {

        /**
         * The category that this view selects.
         */
        @NonNull
        private final SCMHeadCategory category;

        /**
         * Creates a new view.
         *
         * @param owner    the owner.
         * @param category the category.
         */
        public ViewImpl(ViewGroup owner, @NonNull SCMHeadCategory category) {
            super(category.getName(), owner);
            this.category = category;
            try {
                getJobFilters().replaceBy(Collections.singletonList(new BranchCategoryFilter(category)));
                DescribableList<ListViewColumn, Descriptor<ListViewColumn>> columns = getColumns();
                columns.replace(columns.get(StatusColumn.class), new BranchStatusColumn());
            } catch (IOException e) {
                // ignore
            }
            setRecurse(false);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return category.getDisplayName().toString();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isRecurse() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ACL getACL() {
            final ACL acl = super.getACL();
            return new ACL() {
                @Override
                public boolean hasPermission(@Nonnull Authentication a, @Nonnull Permission permission) {
                    if (View.CREATE.equals(permission)
                            || View.CONFIGURE.equals(permission)
                            || View.DELETE.equals(permission)) {
                        return false;
                    }
                    return acl.hasPermission(a, permission);
                }
            };
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void save() throws IOException {
            // no-op
        }

        /**
         * Our descriptor
         */
        @Extension
        public static class ViewDescriptorImpl extends ViewDescriptor {

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return "Multibranch Project All view";
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isInstantiable() {
                return false;
            }
        }
    }
}
