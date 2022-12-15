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

import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import com.cloudbees.hudson.plugins.folder.computed.DefaultOrphanedItemStrategy;
import com.cloudbees.hudson.plugins.folder.computed.OrphanedItemStrategy;
import com.cloudbees.hudson.plugins.folder.computed.OrphanedItemStrategyDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
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
     * @param strategy the OrphanedItemStrategy to be applied to this property
     */
    @DataBoundConstructor
    public OrganizationChildOrphanedItemsProperty(OrphanedItemStrategy strategy) {
        this.strategy = strategy != null
                ? strategy
                : new DefaultOrphanedItemStrategy(true, "", "");
    }

    /**
     * Creates a new default instance of this property.
     *
     * @return a new default instance of this property.
     */
    public static OrganizationChildOrphanedItemsProperty newDefaultInstance() {
        return new OrganizationChildOrphanedItemsProperty(new Inherit());
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
        OrphanedItemStrategy strategy;
        if (this.strategy instanceof Inherit) {
            // special case
            ItemGroup parent = child.getParent();
            if (parent instanceof OrganizationFolder) {
                strategy = ((OrganizationFolder) parent).getOrphanedItemStrategy();
            } else {
                // should never happen, if it does then no-op is safest
                return;
            }
        } else {
            strategy = this.strategy;
        }
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
        @NonNull
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
            List<OrphanedItemStrategyDescriptor> result = new ArrayList<>();
            for (OrphanedItemStrategyDescriptor d : ExtensionList.lookup(OrphanedItemStrategyDescriptor.class)) {
                if (d instanceof Inherit.DescriptorImpl) {
                    result.add(0, d);
                } else if (d.isApplicable(MultiBranchProject.class)) {
                    result.add(d);
                }
            }
            return result;
        }
    }

    /**
     * Special marker class to flag copying the parent strategy.
     */
    @Restricted(NoExternalUse.class)
    public final static class Inherit extends OrphanedItemStrategy {

        /**
         * Our constructor.
         */
        @DataBoundConstructor
        public Inherit() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <I extends TopLevelItem> Collection<I> orphanedItems(ComputedFolder<I> owner, Collection<I> orphaned,
                                                                    TaskListener listener)
                throws IOException, InterruptedException {
            return orphaned;
        }

        /**
         * Our descriptor.
         */
        @Restricted(NoExternalUse.class)
        @Extension
        public final static class DescriptorImpl extends OrphanedItemStrategyDescriptor {
            /**
             * {@inheritDoc}
             */
            @NonNull
            @Override
            public String getDisplayName() {
                return Messages.OrganizationChildOrphanedItemsProperty_Inherit();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean isApplicable(Class<? extends ComputedFolder> folderType) {
                return false;
            }
        }
    }
}
