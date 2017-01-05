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

import hudson.Extension;
import hudson.Util;
import hudson.markup.MarkupFormatter;
import hudson.model.Actionable;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.views.JobColumn;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
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
     * Gets the description of a job.
     *
     * @param job the job.
     * @return the description.
     */
    @SuppressWarnings("unused") // used via Jelly EL binding
    public String getDescription(Object job) {
        if (job instanceof Actionable) {
            ObjectMetadataAction action = ((Actionable)job).getAction(ObjectMetadataAction.class);
            return action != null ? Util.escape(action.getObjectDescription()) : null;
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
        public boolean filter(Object context, Descriptor descriptor) {
            return !(descriptor instanceof BranchStatusColumn.DescriptorImpl)
                    || context instanceof MultiBranchProjectViewHolder.ViewImpl
                    || context instanceof OrganizationFolderViewHolder.ViewImpl;
        }
    }
}
