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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Actionable;
import hudson.model.Item;
import hudson.model.Job;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link ListViewColumn} that shows the description text of a {@link Job} with priority given to
 * {@link MetadataAction#getObjectDescription()}.
 *
 * @author Kohsuke Kawaguchi
 */
public class DescriptionColumn extends ListViewColumn {
    /**
     * Constructor.
     */
    @DataBoundConstructor
    public DescriptionColumn() {
    }

    /**
     * Gets the metadata of an item.
     *
     * @param item the item.
     * @return the metadata or {@code null}
     */
    @Restricted(NoExternalUse.class)
    @SuppressWarnings("unused") // used via Jelly EL binding
    @CheckForNull
    public MetadataAction getPropertyOf(Item item) {
        if (item instanceof Actionable) {
            return ((Actionable) item).getAction(MetadataAction.class);
        }
        return null;
    }

    /**
     * Gets the description of a job.
     *
     * @param p   the metadata action.
     * @param job the job.
     * @return the description.
     */
    @Restricted(NoExternalUse.class)
    @SuppressWarnings("unused") // used via Jelly EL binding
    public String description(@CheckForNull Object p, @NonNull Object job) {
        if (p instanceof MetadataAction) {
            return StringUtils.defaultIfBlank(((MetadataAction) p).getObjectDescription(),
                    job instanceof Job ? ((Job) job).getDescription() : "");
        } else {
            return job instanceof Job ? ((Job) job).getDescription() : "";
        }
    }

    /**
     * Our descriptor.
     */
    @Extension
    public static class DescriptorImpl extends ListViewColumnDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.DescriptionColumn_displayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean shownByDefault() {
            return false;
        }
    }
}
