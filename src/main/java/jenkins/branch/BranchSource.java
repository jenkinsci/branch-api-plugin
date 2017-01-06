/*
 * The MIT License
 *
 * Copyright (c) 2011-2017, CloudBees, Inc., Stephen Connolly.
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
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.util.ArrayList;
import java.util.Collections;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * A source of branches, which consists of a source and a strategy for creating properties of the branches from this
 * source.
 *
 * @author Stephen Connolly
 */
public class BranchSource extends AbstractDescribableImpl<BranchSource> {
    /**
     * The source.
     */
    @NonNull
    private final SCMSource source;

    /**
     * The strategy.
     */
    private BranchPropertyStrategy strategy;

    /**
     * The rules for automatic building of branches.
     *
     * @since 2.0.0
     */
    @CheckForNull
    private List<BranchBuildStrategy> buildStrategies;

    @DataBoundConstructor
    public BranchSource(SCMSource source) {
        this.source = source;
    }

    @Deprecated
    public BranchSource(SCMSource source, BranchPropertyStrategy strategy) {
        this.source = source;
        this.strategy = strategy;
    }

    // TODO make RobustCollectionConverter treat nonnull fields as critical
    private Object readResolve() {
        if (source == null) {
            throw new IllegalStateException("Unloadable SCM Source");
        }
        return this;
    }

    /**
     * Gets the source.
     *
     * @return the source.
     */
    @NonNull
    public SCMSource getSource() {
        return source;
    }

    /**
     * Gets the strategy.
     *
     * @return the strategy.
     */
    public BranchPropertyStrategy getStrategy() {
        return strategy != null ? strategy : new DefaultBranchPropertyStrategy(new BranchProperty[0]);
    }

    @DataBoundSetter
    public void setStrategy(BranchPropertyStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Gets the rules for automatic building of branches to apply on this source.
     *
     * @return the rules for automatic building of branches to apply on this source.
     * @since 2.0.0
     */
    @NonNull
    public List<BranchBuildStrategy> getBuildStrategies() {
        return buildStrategies == null
                ? Collections.<BranchBuildStrategy>emptyList()
                : Collections.unmodifiableList(buildStrategies);
    }

    /**
     * Gets the rules for automatic building of branches to apply on this source.
     *
     * @param buildStrategies the rules for automatic building of branches to apply on this source.
     * @since 2.0.0
     */
    @DataBoundSetter
    public void setBuildStrategies(@CheckForNull List<BranchBuildStrategy> buildStrategies) {
        this.buildStrategies = buildStrategies == null || buildStrategies.isEmpty()
                ? null
                : new ArrayList<>(buildStrategies);
    }

    /**
     * Our {@link Descriptor}.
     */
    @Extension
    @SuppressWarnings("unused") // instantiated by Jenkins
    public static final class DescriptorImpl extends Descriptor<BranchSource> {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Branch source";
        }

        /**
         * Gets all the {@link BranchPropertyStrategyDescriptor} instances applicable to the specified project and source.
         *
         * @param project          the project
         * @param sourceDescriptor the source.
         * @return all the {@link BranchPropertyStrategyDescriptor} instances  applicable to the specified project and
         *         source.
         */
        public List<BranchPropertyStrategyDescriptor> propertyStrategyDescriptors(
                @NonNull MultiBranchProject project, @NonNull SCMSourceDescriptor sourceDescriptor) {
            return BranchPropertyStrategyDescriptor.all(project, sourceDescriptor);
        }

        /**
         * Gets all the {@link BranchBuildStrategyDescriptor} instances applicable to the specified project and source.
         *
         * @param project          the project
         * @param sourceDescriptor the source.
         * @return all the {@link BranchBuildStrategyDescriptor} instances applicable to the specified project and
         *         source.
         * @since 2.0.0
         */
        public List<BranchBuildStrategyDescriptor> buildStrategiesDescriptors(
                @NonNull MultiBranchProject project, @NonNull SCMSourceDescriptor sourceDescriptor) {
            return BranchBuildStrategyDescriptor.all(project, sourceDescriptor);
        }

    }
}
