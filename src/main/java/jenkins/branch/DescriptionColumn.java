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
import hudson.Util;
import hudson.model.Actionable;
import hudson.model.Item;
import hudson.model.Job;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;
import java.io.IOException;
import jenkins.model.Jenkins;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link ListViewColumn} that shows the description text of a {@link Job} with priority given to
 * {@link ObjectMetadataAction#getObjectDescription()}.
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
    public ObjectMetadataAction getPropertyOf(Item item) {
        if (item instanceof Actionable) {
            return ((Actionable) item).getAction(ObjectMetadataAction.class);
        }
        return null;
    }

    /**
     * Gets the formatted description of a job.
     *
     * @param p   the metadata action.
     * @param job the job.
     * @return the description. It is never unfiltered, unescaped HTML.
     * @throws IOException if there was an issue encoding the description.
     */
    @Restricted(NoExternalUse.class)
    @SuppressWarnings("unused") // used via Jelly EL binding
    public String formattedDescription(@CheckForNull Object p, @NonNull Object job) throws IOException {
        if (p instanceof ObjectMetadataAction) {
            // when the description comes from the metadata, assume plain text and use Util.escape
            String objectDescription = ((ObjectMetadataAction) p).getObjectDescription();
            String description = objectDescription == null ? null : Util.escape(objectDescription);
            if (StringUtils.isNotBlank(description)) {
                return description;
            }
        }
        if (job instanceof Job) {
            // when the description comes from the job configuration, assume user provided and use markup formatter
            return Jenkins.getActiveInstance().getMarkupFormatter().translate(((Job) job).getDescription());
        } else {
            return "";
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
