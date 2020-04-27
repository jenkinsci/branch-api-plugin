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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.scm.api.SCMHead;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A very simple {@link BranchPropertyStrategy} that just returns the same set of properties for all {@link SCMHead}
 * instances.
 *
 * @author Stephen Connolly
 */
public class DefaultBranchPropertyStrategy extends BranchPropertyStrategy {

    /**
     * The properties that all {@link SCMHead}s will get.
     */
    @NonNull
    private final List<BranchProperty> properties;

    /**
     * Stapler's constructor.
     *
     * @param props the properties.
     */
    @DataBoundConstructor
    public DefaultBranchPropertyStrategy(@CheckForNull BranchProperty[] props) {
        this.properties = props == null ? Collections.<BranchProperty>emptyList() : Arrays.asList(props);
    }

    /**
     * Gets the properties.
     *
     * @return the properties.
     */
    @NonNull
    @SuppressWarnings("unused")// by stapler
    public List<BranchProperty> getProps() {
        return properties;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<BranchProperty> getPropertiesFor(SCMHead head) {
        return new ArrayList<>(properties);
    }

    /**
     * Our {@link BranchPropertyStrategyDescriptor}.
     */
    @Symbol("allBranchesSameProperties")
    @Extension
    @SuppressWarnings("unused") // by jenkins
    public static class DescriptorImpl extends BranchPropertyStrategyDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.DefaultBranchPropertyStrategy_DisplayName();
        }
    }

}
