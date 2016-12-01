/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;

/**
 * Creates {@link MultiBranchProject}s for repositories where recognized.
 *
 * Please define a 'getting-started' view for a subclass, if you would like to provide specific information to the user
 * how to get started using the type of project factory. This view is displayed when there are no subfolders found.
 *
 * @see OrganizationFolder#getProjectFactories
 * @since 0.2-beta-5
 */
public abstract class MultiBranchProjectFactory extends AbstractDescribableImpl<MultiBranchProjectFactory>
        implements ExtensionPoint {

    /**
     * Determines whether this factory recognizes a given configuration.
     *
     * @param parent     a folder
     * @param name       a project name
     * @param scmSources a set of SCM sources as added by
     * {@link jenkins.scm.api.SCMSourceObserver.ProjectObserver#addSource}
     * @param attributes a set of metadata attributes as added by
     * {@link jenkins.scm.api.SCMSourceObserver.ProjectObserver#addAttribute}
     * @param listener   a way of reporting progress
     * @return true if recognized
     * @throws InterruptedException if interrupted.
     * @throws IOException if there was an IO error.
     */
    @SuppressWarnings("deprecation")
    public boolean recognizes(@NonNull ItemGroup<?> parent, @NonNull String name,
                              @NonNull List<? extends SCMSource> scmSources, @NonNull Map<String, Object> attributes,
                              @NonNull TaskListener listener) throws IOException, InterruptedException {
        if (Util.isOverridden(MultiBranchProjectFactory.class, getClass(), "createProject", ItemGroup.class,
                String.class, List.class, Map.class, TaskListener.class)) {
            return createProject(parent, name, scmSources, attributes, listener) != null;
        } else {
            throw new AbstractMethodError(getClass().getName() + " must override recognizes");
        }
    }

    /**
     * Determines whether this factory recognizes a given configuration scoped to a specific {@link SCMHeadEvent}.
     *
     * @param parent     a folder
     * @param name       a project name
     * @param scmSources a set of SCM sources as added by
     *                   {@link jenkins.scm.api.SCMSourceObserver.ProjectObserver#addSource}
     * @param attributes a set of metadata attributes as added by
     *                   {@link jenkins.scm.api.SCMSourceObserver.ProjectObserver#addAttribute}
     * @param event      the {@link SCMHeadEvent} that the recognition test should be restricted to.
     * @param listener   a way of reporting progress
     * @return true if recognized
     * @throws InterruptedException if interrupted.
     * @throws IOException if there was an IO error.
     * @since 2.0
     */
    public boolean recognizes(@NonNull ItemGroup<?> parent, @NonNull String name,
                              @NonNull List<? extends SCMSource> scmSources, @NonNull Map<String, Object> attributes,
                              @NonNull SCMHeadEvent<?> event,
                              @NonNull TaskListener listener) throws IOException, InterruptedException {
        return recognizes(parent, name, scmSources, attributes, listener);
    }

    /**
     * Creates a new multibranch project which matches {@link #recognizes}.
     *
     * @param parent     a folder
     * @param name       a project name
     * @param scmSources a set of SCM sources as added by
     * {@link jenkins.scm.api.SCMSourceObserver.ProjectObserver#addSource}
     * @param attributes a set of metadata attributes as added by
     * {@link jenkins.scm.api.SCMSourceObserver.ProjectObserver#addAttribute}
     * @param listener   a way of reporting progress
     * @return a new uninitialized multibranch project (do not configure its
     * {@link MultiBranchProject#getSourcesList} or call {@link MultiBranchProject#onCreatedFromScratch})
     * @throws InterruptedException if interrupted.
     * @throws IOException if there was an IO error.
     */
    @NonNull
    @SuppressWarnings("deprecation")
    public MultiBranchProject<?, ?> createNewProject(@NonNull ItemGroup<?> parent, @NonNull String name,
                                                     @NonNull List<? extends SCMSource> scmSources,
                                                     @NonNull Map<String, Object> attributes,
                                                     @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        if (Util.isOverridden(MultiBranchProjectFactory.class, getClass(), "createProject", ItemGroup.class,
                String.class, List.class, Map.class, TaskListener.class)) {
            MultiBranchProject<?, ?> p = createProject(parent, name, scmSources, attributes, listener);
            if (p == null) {
                throw new IOException("recognized project " + name + " before, but now");
            }
            return p;
        } else {
            throw new AbstractMethodError(getClass().getName() + " must override createNewProject");
        }
    }

    /**
     * Updates an existing project which matches {@link #recognizes}.
     *
     * @param project    an existing project, perhaps created by this factory, perhaps not
     * @param attributes a set of metadata attributes as added by
     * {@link jenkins.scm.api.SCMSourceObserver.ProjectObserver#addAttribute}
     * @param listener   a way of reporting progress
     * @throws InterruptedException if interrupted.
     * @throws IOException if there was an IO error.
     */
    public void updateExistingProject(@NonNull MultiBranchProject<?, ?> project,
                                      @NonNull Map<String, Object> attributes, @NonNull TaskListener listener)
            throws IOException, InterruptedException {
    }

    /**
     * Creates a new multibranch project which matches {@link #recognizes}.
     *
     * @param parent     a folder
     * @param name       a project name
     * @param scmSources a set of SCM sources as added by
     *                   {@link jenkins.scm.api.SCMSourceObserver.ProjectObserver#addSource}
     * @param attributes a set of metadata attributes as added by
     *                   {@link jenkins.scm.api.SCMSourceObserver.ProjectObserver#addAttribute}
     * @param listener   a way of reporting progress
     * @return a new uninitialized multibranch project (do not configure its
     * {@link MultiBranchProject#getSourcesList} or call {@link MultiBranchProject#onCreatedFromScratch})
     * @throws InterruptedException if interrupted.
     * @throws IOException if there was an IO error.
     * @deprecated use {@link #createNewProject(ItemGroup, String, List, Map, TaskListener)}
     */
    @Deprecated
    @CheckForNull
    public MultiBranchProject<?, ?> createProject(@NonNull ItemGroup<?> parent, @NonNull String name,
                                                  @NonNull List<? extends SCMSource> scmSources,
                                                  @NonNull Map<String, Object> attributes,
                                                  @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        if (recognizes(parent, name, scmSources, attributes, listener)) {
            return createNewProject(parent, name, scmSources, attributes, listener);
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MultiBranchProjectFactoryDescriptor getDescriptor() {
        return (MultiBranchProjectFactoryDescriptor) super.getDescriptor();
    }

    /**
     * Creates a particular kind of multibranch project insofar as at least one {@link SCMHead} satisfies a probe.
     */
    public static abstract class BySCMSourceCriteria extends MultiBranchProjectFactory {

        /**
         * Defines how to decide whether or not a given repository should host our type of project.
         *
         * @param source a repository
         * @return criteria for treating its branches as a match
         */
        @NonNull
        protected abstract SCMSourceCriteria getSCMSourceCriteria(@NonNull SCMSource source);

        /**
         * Historical alias for {@link #createNewProject}.
         * @param parent     a folder
         * @param name       a project name
         * {@link jenkins.scm.api.SCMSourceObserver.ProjectObserver#addSource}
         * @param attributes a set of metadata attributes as added by
         * {@link jenkins.scm.api.SCMSourceObserver.ProjectObserver#addAttribute}
         * @return a new uninitialized multibranch project (do not configure its
         * {@link MultiBranchProject#getSourcesList} or call {@link MultiBranchProject#onCreatedFromScratch})
         */
        @NonNull
        protected abstract MultiBranchProject<?, ?> doCreateProject(@NonNull ItemGroup<?> parent, @NonNull String name,
                                                                    @NonNull Map<String, Object> attributes);

        /**
         * {@inheritDoc}
         */
        @Override
        public final MultiBranchProject<?, ?> createNewProject(@NonNull ItemGroup<?> parent, @NonNull String name,
                                                               @NonNull List<? extends SCMSource> scmSources,
                                                               @NonNull Map<String, Object> attributes,
                                                               @NonNull TaskListener listener)
                throws IOException, InterruptedException {
            return doCreateProject(parent, name, attributes);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean recognizes(@NonNull ItemGroup<?> parent, @NonNull String name,
                                  @NonNull List<? extends SCMSource> scmSources,
                                  @NonNull Map<String, Object> attributes,
                                  @NonNull TaskListener listener)
                throws IOException, InterruptedException {
            for (final SCMSource scmSource : scmSources) {
                if (scmSource.fetch(getSCMSourceCriteria(scmSource), SCMHeadObserver.any(), listener).getRevision()
                        != null) {
                    return true;
                }
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean recognizes(@NonNull ItemGroup<?> parent, @NonNull String name,
                                  @NonNull List<? extends SCMSource> scmSources,
                                  @NonNull Map<String, Object> attributes,
                                  @NonNull SCMHeadEvent<?> event, @NonNull TaskListener listener)
                throws IOException, InterruptedException {
            for (final SCMSource scmSource : scmSources) {
                if (scmSource.fetch(getSCMSourceCriteria(scmSource), SCMHeadObserver.any(), event, listener)
                        .getRevision() != null) {
                    return true;
                }
            }
            return false;
        }
    }

}
