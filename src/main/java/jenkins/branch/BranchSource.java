/*
 * The MIT License
 *
 * Copyright (c) 2011-2013, CloudBees, Inc., Stephen Connolly.
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
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;

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
    private final SCMSource source;

    /**
     * The strategy.
     */
    private final BranchPropertyStrategy strategy;

    /**
     * Stapler's constructor.
     *
     * @param source   the source.
     * @param strategy the strategy.
     */
    @DataBoundConstructor
    @SuppressWarnings("unused")
    public BranchSource(SCMSource source, BranchPropertyStrategy strategy) {
        this.source = source;
        this.strategy = strategy;
    }

    /**
     * Gets the source.
     *
     * @return the source.
     */
    public SCMSource getSource() {
        return source;
    }

    /**
     * Gets the strategy.
     *
     * @return the strategy.
     */
    public BranchPropertyStrategy getStrategy() {
        return strategy;
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

    }
}
