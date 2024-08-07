/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc.
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
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import jenkins.scm.api.SCMHead;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Allows named branches to get different properties from the rest.
 *
 * @author Stephen Connolly
 */
public class NamedExceptionsBranchPropertyStrategy extends BranchPropertyStrategy {
    /**
     * The properties that all non-exception {@link SCMHead}s will get.
     */
    @NonNull
    private final List<BranchProperty> defaultProperties;

    /**
     * The configured exceptions.
     */
    @NonNull
    private final List<Named> namedExceptions;

    /**
     * Stapler's constructor.
     *
     * @param defaultProperties the properties.
     * @param namedExceptions the named exceptions.
     */
    @DataBoundConstructor
    public NamedExceptionsBranchPropertyStrategy(@CheckForNull BranchProperty[] defaultProperties,
                                                 @CheckForNull Named[] namedExceptions) {
        this.defaultProperties =
                defaultProperties == null ? Collections.emptyList() : Arrays.asList(defaultProperties);
        this.namedExceptions =
                namedExceptions == null ? Collections.emptyList() : Arrays.asList(namedExceptions);
    }

    /**
     * Gets the default properties.
     *
     * @return the default properties.
     */
    @NonNull
    @SuppressWarnings("unused")// by stapler
    public List<BranchProperty> getDefaultProperties() {
        return defaultProperties;
    }

    /**
     * Gets the named exceptions to the defaults.
     *
     * @return the named exceptions to the defaults.
     */
    @NonNull
    @SuppressWarnings("unused")// by stapler
    public List<Named> getNamedExceptions() {
        return namedExceptions;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<BranchProperty> getPropertiesFor(SCMHead head) {
        List<BranchProperty> properties = new ArrayList<>();

        for (Named named : namedExceptions) {
            if (named.isMatch(head)) {
                properties.addAll(named.getProps());
            }
        }

        if (properties.isEmpty()) {
            // if no one defined adds default
            properties.addAll(defaultProperties);
        }
        return properties;
    }

    /**
     * Our {@link BranchPropertyStrategyDescriptor}.
     */
    @Symbol("namedBranchesDifferent")
    @Extension
    @SuppressWarnings("unused") // by jenkins
    public static class DescriptorImpl extends BranchPropertyStrategyDescriptor {
        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.NamedExceptionsBranchPropertyStrategy_DisplayName();
        }
    }

    /**
     * Holds the specific named exception details.
     */
    public static class Named extends AbstractDescribableImpl<Named> {
        /**
         * The properties that all {@link SCMHead}s will get.
         */
        @NonNull
        private final List<BranchProperty> props;

        /**
         * The name to match
         */
        @NonNull
        private final String name;

        /**
         * Constructor
         *
         * @param name the names to match.
         * @param props the properties that the matching branches will get.
         */
        @SuppressWarnings("unused") // via stapler
        @DataBoundConstructor
        public Named(@CheckForNull String name, @CheckForNull BranchProperty[] props) {
            this.name = Util.fixNull(name);
            this.props = props == null ? Collections.emptyList() : Arrays.asList(props);
        }

        /**
         * Returns the exception properties.
         *
         * @return the exception properties.
         */
        @NonNull
        public List<BranchProperty> getProps() {
            return props;
        }

        /**
         * Returns the name(s) to match.
         *
         * @return the name(s) to match.
         */
        @NonNull
        public String getName() {
            return name;
        }

        /**
         * Returns {@code true} if the head is a match.
         *
         * @param head the head.
         * @return {@code true} if the head is a match.
         */
        public boolean isMatch(@NonNull SCMHead head) {
            return isMatch(head.getName(), this.name);
        }

        /**
         * Returns {@code true} if and only if the branch name matches one of the name(s).
         *
         * @param branchName the branch name.
         * @param names      the name(s) that are valid to match against.
         * @return {@code true} if and only if the branch name matches one of the name(s).
         */
        public static boolean isMatch(String branchName, String names) {
            for (String name : StringUtils.split(names, ",")) {
                name = name.trim();
                boolean invertMatch;
                if (name.startsWith("!")) {
                    name = name.substring(1);
                    invertMatch = true;
                } else if (name.startsWith("\\!") || name.startsWith("\\\\!")) {
                    // provide an escape hatch
                    name = name.substring(1);
                    invertMatch = false;
                } else {
                    invertMatch = false;
                }
                boolean match;
                if (name.indexOf('*') == -1 && name.indexOf('?') == -1) {
                    match = name.equalsIgnoreCase(branchName);
                } else {
                    name = name.replace('\\', File.separatorChar).replace('/', File.separatorChar);
                    branchName = branchName.replace('\\', File.separatorChar).replace('/', File.separatorChar);
                    match = SelectorUtils.matchPath(name, branchName, false);
                }
                if (invertMatch ? !match : match) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Our {@link hudson.model.Descriptor}
         */
        @Extension
        @SuppressWarnings("unused") // instantiated by Jenkins.
        public static class DescriptorImpl extends Descriptor<Named> {

            /**
             * {@inheritDoc}
             */
            @NonNull
            @Override
            public String getDisplayName() {
                return "Named exception";
            }
        }
    }
}
