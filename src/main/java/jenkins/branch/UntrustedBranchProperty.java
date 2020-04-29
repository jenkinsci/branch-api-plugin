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
import hudson.model.Build;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Project;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Publisher;
import java.lang.reflect.Type;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jvnet.tiger_types.Types;

/**
 * Indicates that the branch contains code changes from authors who do not otherwise have the write access
 * to the repository.
 * <p>
 * Such code can contain malicious changes, so this flag serves as a signal to allow other Jenkins to
 * set up the build isolation to protect the build infrastructure.
 * <p>
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
        this.publisherWhitelist = publisherWhitelist == null ? Collections.<String>emptySet() : new TreeSet<>(
                Arrays.asList(publisherWhitelist));
    }

    public Set<String> getPublisherWhitelist() {
        return Collections.unmodifiableSet(publisherWhitelist);
    }

    private class Decorator<P extends Project<P, B>, B extends Build<P, B>> extends ProjectDecorator<P, B> {
            /**
             * {@inheritDoc}
             */
            @NonNull
            @Override
            public List<Publisher> publishers(@NonNull List<Publisher> publishers) {
                List<Publisher> result = new ArrayList<>();
                Set<String> whitelist = getPublisherWhitelist();
                if (!whitelist.isEmpty()) {
                    for (Publisher publisher: publishers) {
                        if (whitelist.contains(publisher.getDescriptor().clazz.getName())) {
                            result.add(publisher);
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
            public List<BuildWrapper> buildWrappers(@NonNull List<BuildWrapper> wrappers) {
                // TODO add a build wrapper that puts the execution in a "secured" context.
                return super.buildWrappers(wrappers);
            }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P extends Job<P,B>,B extends Run<P,B>> JobDecorator<P,B> jobDecorator(Class<P> jobType) {
        if (Project.class.isAssignableFrom(jobType)) {
            return new Decorator/* untypable but safe: <P, B>*/();
        }
        return null;
    }

    /**
     * Our {@link Descriptor}.
     */
    @Symbol("untrustedBranches")
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
            Map<String,Descriptor<Publisher>> result = new LinkedHashMap<>();
            for (Descriptor<Publisher> d: Publisher.all()) {
                result.put(d.clazz.getName(), d);
            }
            return result;
        }

        @Override
        protected boolean isApplicable(MultiBranchProjectDescriptor projectDescriptor) {
            for (BranchProjectFactoryDescriptor d : projectDescriptor.getProjectFactoryDescriptors()) {
                Type factoryType = Types.getBaseClass(d.clazz, BranchProjectFactory.class);
                Type jobType = Types.getTypeArgument(factoryType, 0, /* if using rawtypes, err on the conservative side */ Project.class);
                if (Project.class.isAssignableFrom(Types.erasure(jobType))) {
                    return true;
                }
            }
            return false;
        }

    }
}
