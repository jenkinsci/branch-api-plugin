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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Publisher;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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

    private final Set<String> publisherWhitelist;

    @DataBoundConstructor
    public UntrustedBranchProperty(String[] publisherWhitelist) {
        this.publisherWhitelist = publisherWhitelist == null ? Collections.<String>emptySet() : new TreeSet<String>(
                Arrays.asList(publisherWhitelist));
    }

    public Set<String> getPublisherWhitelist() {
        return Collections.unmodifiableSet(publisherWhitelist);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Map<Descriptor<Publisher>, Publisher> configurePublishers(
            @NonNull Map<Descriptor<Publisher>, Publisher> publishers) {
        Map<Descriptor<Publisher>, Publisher> result = new LinkedHashMap<Descriptor<Publisher>, Publisher>();
        Set<String> whitelist = getPublisherWhitelist();
        if (!whitelist.isEmpty()) {
            for (Map.Entry<Descriptor<Publisher>, Publisher> entry : publishers.entrySet()) {
                if (whitelist.contains(entry.getKey().clazz.getName())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Map<Descriptor<BuildWrapper>, BuildWrapper> configureBuildWrappers(@NonNull Map<Descriptor<BuildWrapper>,
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

        public Map<String,Descriptor<Publisher>> getPublisherDescriptors() {
            Map<String,Descriptor<Publisher>> result = new LinkedHashMap<String,Descriptor<Publisher>>();
            for (Descriptor<Publisher> d: Publisher.all()) {
                result.put(d.clazz.getName(), d);
            }
            return result;
        }
    }
}
