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
import hudson.Extension;
import hudson.model.ListView;
import hudson.model.View;
import hudson.model.ViewDescriptor;
import hudson.model.ViewGroup;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.views.DefaultViewsTabBar;
import hudson.views.StatusColumn;
import hudson.views.ViewsTabBar;
import hudson.views.WeatherColumn;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMSourceCategory;
import net.jcip.annotations.GuardedBy;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.core.Authentication;

import static java.util.Arrays.asList;

/**
 * Holds the view configuration for an {@link OrganizationFolder}.
 *
 * @since 2.0
 */
public class OrganizationFolderViewHolder extends AbstractFolderViewHolder {
    /**
     * Our owning {@link OrganizationFolder}.
     */
    @NonNull
    private final OrganizationFolder owner;
    /**
     * The {@link ViewsTabBar} instance.
     */
    private transient ViewsTabBar tabBar;
    /**
     * The list of {@link View}s.
     */
    @GuardedBy("this")
    private transient volatile List<View> views = null;
    /**
     * The primary view name.
     */
    @GuardedBy("this")
    private transient volatile String primaryView = null;

    /**
     * Constructor.
     *
     * @param owner the owning {@link OrganizationFolder}.
     */
    public OrganizationFolderViewHolder(OrganizationFolder owner) {
        super();
        this.owner = owner;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<View> getViews() {
        ensureViews();
        return views;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setViews(@NonNull List<? extends hudson.model.View> views) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPrimaryView() {
        ensureViews();
        return primaryView;
    }

    /**
     * Initialize views and primaryView
     */
    private void ensureViews() {
        if (views == null) {
            synchronized(this) {
                if (views == null) {
                    List<View> views = new ArrayList<>();
                    for (SCMSourceCategory c : SCMSourceCategory.collectAndSimplify(owner.getNavigators()).values()) {
                        views.add(new ViewImpl(owner, c));
                        if (c.isUncategorized()) {
                            primaryView = c.getName();
                        }
                    }
                    this.views = views;
                }
            }
        }
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
            tabBar = new DefaultViewsTabBar();
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
        private final SCMSourceCategory category;

        /**
         * Creates a new view.
         *
         * @param owner    the owner.
         * @param category the category.
         */
        public ViewImpl(ViewGroup owner, @NonNull SCMSourceCategory category) {
            super(category.getName(), owner);
            this.category = category;
            try {
                getJobFilters().replaceBy(asList(new MultiBranchCategoryFilter(category)));
                getColumns().replaceBy(asList(
                        new StatusColumn(),
                        new WeatherColumn(),
                        new ItemColumn(),
                        new DescriptionColumn()
                ));
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
            return category.getDisplayName() + " (" + getItems().size() + ")";
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
                public boolean hasPermission2(@NonNull Authentication a, @NonNull Permission permission) {
                    if (View.CREATE.equals(permission)
                            || View.CONFIGURE.equals(permission)
                            || View.DELETE.equals(permission)) {
                        return false;
                    }
                    return acl.hasPermission2(a, permission);
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
                return "Organization folder All view";
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
