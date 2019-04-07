/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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

import com.cloudbees.hudson.plugins.folder.computed.DefaultOrphanedItemStrategy;
import com.cloudbees.hudson.plugins.folder.computed.OrphanedItemStrategy;
import com.cloudbees.hudson.plugins.folder.computed.OrphanedItemStrategyDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Items;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Configures the {@link OrphanedItemStrategy} to use for children of a {@link OrganizationFolder}.
 *
 * @since 2.4.0
 */
public class OrganizationChildOrphanedItemsProperty extends OrganizationFolderProperty<OrganizationFolder> {

    /**
     * Our {@link OrphanedItemStrategy}
     */
    private final OrphanedItemStrategy strategy;

    /**
     * Our constructor.
     */
    @DataBoundConstructor
    public OrganizationChildOrphanedItemsProperty(OrphanedItemStrategy strategy) {
        this.strategy = strategy != null
                ? strategy
                : new DefaultOrphanedItemStrategy(true, "", "");
    }

    /**
     * Returns the strategy we enforce.
     *
     * @return the strategy we enforce.
     */
    public OrphanedItemStrategy getStrategy() {
        return strategy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorate(@NonNull MultiBranchProject<?, ?> child, @NonNull TaskListener listener)
            throws IOException {
        if (!child.getOrphanedItemStrategy().getClass().equals(strategy.getClass())
                || !Items.XSTREAM2.toXML(strategy).equals(Items.XSTREAM2.toXML(child.getOrphanedItemStrategy()))) {
            child.setOrphanedItemStrategy(
                    (OrphanedItemStrategy) Items.XSTREAM2.fromXML(Items.XSTREAM2.toXML(strategy)));
        }
    }

    /**
     * Our descriptor.
     */
    @Extension
    public static class DescriptorImpl extends OrganizationFolderPropertyDescriptor {
        /**
         * {@inheritDoc}
         */
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.OrganizationChildOrphanedItemsProperty_DisplayName();
        }

        /**
         * Gets the {@link OrphanedItemStrategyDescriptor}s applicable to the children of the {@link
         * OrganizationFolder}.
         *
         * @return the {@link OrphanedItemStrategyDescriptor}s applicable to the children of the {@link
         *         OrganizationFolder}.
         */
        @Restricted(DoNotUse.class) // used by Jelly
        @NonNull
        public List<OrphanedItemStrategyDescriptor> getStrategyDescriptors() {
            List<OrphanedItemStrategyDescriptor> result = new ArrayList<OrphanedItemStrategyDescriptor>();
            for (OrphanedItemStrategyDescriptor descriptor : ExtensionList
                    .lookup(OrphanedItemStrategyDescriptor.class)) {
                if (descriptor.isApplicable(MultiBranchProject.class)) {
                    result.add(descriptor);
                }
            }
            return result;
        }
    }
}
