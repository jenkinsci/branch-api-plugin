/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Actionable;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.views.JobColumn;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Like {@link JobColumn} only is aware of {@link PrimaryInstanceMetadataAction} and {@link ObjectMetadataAction}
 *
 * @since 2.0.1
 */
@Restricted(NoExternalUse.class) // we are not exposing this outside of
public class ItemColumn extends ListViewColumn {
    /**
     * Default constructor
     */
    @DataBoundConstructor
    public ItemColumn() {
    }

    /**
     * Determines if the item has a {@link PrimaryInstanceMetadataAction}.
     *
     * @param job the item.
     * @return {@code true} if and only if the item has a {@link PrimaryInstanceMetadataAction}.
     */
    @SuppressWarnings("unused") // used via Jelly EL binding
    public boolean isPrimary(Object job) {
        return job instanceof Actionable && ((Actionable) job).getAction(PrimaryInstanceMetadataAction.class) != null;
    }

    /**
     * Determines if the item is orphaned.
     * @param item the item.
     * @return {@code true} if and only if the item is orphaned.
     */
    @SuppressWarnings("rawtypes")
    public boolean isOrphaned(Object item) {
        if (item instanceof Job) {
            Job job = (Job) item;
            ItemGroup parent = job.getParent();
            if (parent instanceof MultiBranchProject) {
                BranchProjectFactory factory = ((MultiBranchProject) parent).getProjectFactory();
                return factory.isProject(job) && factory.getBranch(job) instanceof Branch.Dead;
            }
        }
        if (item instanceof MultiBranchProject) {
            MultiBranchProject<?,?> project = (MultiBranchProject<?,?>) item;
            BranchProjectFactory factory = project.getProjectFactory();
            try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                for (Job c: project.getItems()) {
                    if (factory.isProject(c) && !(factory.getBranch(c) instanceof Branch.Dead)) {
                        // if we have at least one not-dead branch then the project is alive
                        return false;
                    }
                }
                // if we have no child projects or all child projects are dead, then the project is dead
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the tool-tip title of a job.
     *
     * @param job the job.
     * @return the tool-tip title unescaped for use in an attribute.
     */
    @SuppressWarnings("unused") // used via Jelly EL binding
    public String getTitle(Object job) {
        // Jelly will take care of quote and ampersand escaping for us
        if (job instanceof Actionable) {
            Actionable actionable = (Actionable) job;
            ObjectMetadataAction action = actionable.getAction(ObjectMetadataAction.class);
            if (action != null) {
                String displayName = action.getObjectDisplayName();
                if (StringUtils.isBlank(displayName) || displayName.equals(actionable.getDisplayName())) {
                    // if the display name is the same, then the description is more useful
                    String description = action.getObjectDescription();
                    return description != null ? description : displayName;
                }
                return displayName;
            }
        }
        return null;
    }

    /**
     * Our extension.
     */
    @Extension
    public static class DescriptorImpl extends ListViewColumnDescriptor {
        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.ItemColumn_DisplayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean shownByDefault() {
            return false;
        }
    }

    /**
     * Hide this column from user views as it would only confuse them.
     */
    @Extension
    public static class DescriptorVisibilityFilterImpl extends DescriptorVisibilityFilter {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean filter(Object context, @NonNull Descriptor descriptor) {
            return !(descriptor instanceof BranchStatusColumn.DescriptorImpl)
                    || context instanceof MultiBranchProjectViewHolder.ViewImpl
                    || context instanceof OrganizationFolderViewHolder.ViewImpl;
        }
    }
}
