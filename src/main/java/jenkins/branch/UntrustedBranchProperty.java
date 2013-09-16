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

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Publisher;

import java.util.HashMap;
import java.util.Map;

/**
 * Indicates that the branch contains code changes from authors who do not otherwise have the write access
 * to the repository.
 * <p/>
 * <p/>
 * Such code can contain malicious changes, so this flag serves as a signal to allow other Jenkins to
 * set up the build isolation to protect the build infrastructure.
 * <p/>
 * <p/>
 * Some examples of where the trusted vs non-trusted distinction becomes important:
 * <ul>
 * <li>Github pull requests should be non-trusted as they can be created by <em>any</em> user</li>
 * <li>A Subversion branching structure such as {@code trunk, branches, tags, sandbox} would probably have
 * {@code trunk, branches, tags} as trusted and {@code sandbox} as untrusted where the Subversion permissions
 * give any authenticated user write access to {@code sandbox} but allow the project team to commit to all four
 * locations</li>
 * </ul>
 *
 * @author Stephen Connolly
 * @author Kohsuke Kawaguchi
 */
public class UntrustedBranchProperty extends BranchProperty {

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Descriptor<Publisher>, Publisher> configurePublishers(Map<Descriptor<Publisher>, Publisher> publishers) {
        // TODO allow white-listing of publishers
        return new HashMap<Descriptor<Publisher>, Publisher>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Descriptor<BuildWrapper>, BuildWrapper> configureBuildWrappers(Map<Descriptor<BuildWrapper>,
            BuildWrapper> wrappers) {
        // TODO add a build wrapper that puts the execution in a "secured" context.
        return super.configureBuildWrappers(wrappers);
    }

    /**
     * Our {@link Descriptor}.
     */
    @Extension
    @SuppressWarnings("unused") // instantiated by Jenkins
    public static class DescriptorImpl extends BranchPropertyDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Untrusted";
        }
    }
}
