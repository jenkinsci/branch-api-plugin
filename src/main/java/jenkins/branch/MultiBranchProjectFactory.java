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

import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceOwner;

/**
 * Creates {@link MultiBranchProject}s for repositories where recognized.
 * 
 * Please define a 'getting-started' view for a subclass, if you would like to provide specific information to the user
 * how to get started using the type of project factory. This view is displayed when there are no subfolders found.
 *
 * @since 0.2-beta-5
 * @see OrganizationFolder#getProjectFactories
 */
public abstract class MultiBranchProjectFactory extends AbstractDescribableImpl<MultiBranchProjectFactory> implements ExtensionPoint {

    /**
     * Determines whether this factory recognizes a given configuration.
     * @param parent a folder
     * @param name a project name
     * @param scmSources a set of SCM sources as added by {@link jenkins.scm.api.SCMSourceObserver.ProjectObserver#addSource}
     * @param attributes a set of metadata attributes as added by {@link jenkins.scm.api.SCMSourceObserver.ProjectObserver#addAttribute}
     * @param listener a way of reporting progress
     * @return true if recognized
     */
    public boolean recognizes(@Nonnull ItemGroup<?> parent, @Nonnull String name, @Nonnull List<? extends SCMSource> scmSources, @Nonnull Map<String,Object> attributes, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        if (Util.isOverridden(MultiBranchProjectFactory.class, getClass(), "createProject", ItemGroup.class, String.class, List.class, Map.class, TaskListener.class)) {
            return createProject(parent, name, scmSources, attributes, listener) != null;
        } else {
            throw new AbstractMethodError(getClass().getName() + " must override recognizes");
        }
    }

    /**
     * Creates a new multibranch project which matches {@link #recognizes}.
     * @param parent a folder
     * @param name a project name
     * @param scmSources a set of SCM sources as added by {@link jenkins.scm.api.SCMSourceObserver.ProjectObserver#addSource}
     * @param attributes a set of metadata attributes as added by {@link jenkins.scm.api.SCMSourceObserver.ProjectObserver#addAttribute}
     * @param listener a way of reporting progress
     * @return a new uninitialized multibranch project (do not configure its {@link MultiBranchProject#getSourcesList} or call {@link MultiBranchProject#onCreatedFromScratch})
     */
    @Nonnull
    public MultiBranchProject<?,?> createNewProject(@Nonnull ItemGroup<?> parent, @Nonnull String name, @Nonnull List<? extends SCMSource> scmSources, @Nonnull Map<String,Object> attributes, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        if (Util.isOverridden(MultiBranchProjectFactory.class, getClass(), "createProject", ItemGroup.class, String.class, List.class, Map.class, TaskListener.class)) {
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
     * @param project an existing project, perhaps created by this factory, perhaps not
     * @param attributes a set of metadata attributes as added by {@link jenkins.scm.api.SCMSourceObserver.ProjectObserver#addAttribute}
     * @param listener a way of reporting progress
     */
    public void updateExistingProject(MultiBranchProject<?,?> project, @Nonnull Map<String,Object> attributes, @Nonnull TaskListener listener) throws IOException, InterruptedException {}

    @Deprecated
    @CheckForNull
    public MultiBranchProject<?,?> createProject(@Nonnull ItemGroup<?> parent, @Nonnull String name, @Nonnull List<? extends SCMSource> scmSources, @Nonnull Map<String,Object> attributes, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        if (recognizes(parent, name, scmSources, attributes, listener)) {
            return createNewProject(parent, name, scmSources, attributes, listener);
        } else {
            return null;
        }
    }

    @Override
    public MultiBranchProjectFactoryDescriptor getDescriptor() {
        return (MultiBranchProjectFactoryDescriptor) super.getDescriptor();
    }

    // TODO we have no way with the current API of instructing an SCMSource to use a specific SCMSourceCriteria during retrieve; impls hardcode SCMSource.getCriteria, which relies on a parent SCMSourceOwner
    interface SCMSourceOwnerHack extends SCMSourceOwner {
        <T> T withSCMSourceCriteria(@Nonnull SCMSource source, @Nonnull SCMSourceCriteria criteria, @Nonnull Callable<T> body) throws Exception;
    }

    /**
     * Creates a particular kind of multibranch project insofar as at least one {@link SCMHead} satisfies a probe.
     */
    public static abstract class BySCMSourceCriteria extends MultiBranchProjectFactory {

        /**
         * Defines how to decide whether or not a given repository should host our type of project.
         * @param source a repository
         * @return criteria for treating its branches as a match
         */
        @Nonnull
        protected abstract SCMSourceCriteria getSCMSourceCriteria(@Nonnull SCMSource source);

        /**
         * Historical alias for {@link #createNewProject}.
         */
        @Nonnull
        protected abstract MultiBranchProject<?,?> doCreateProject(@Nonnull ItemGroup<?> parent, @Nonnull String name, @Nonnull Map<String,Object> attributes);

        @Override
        public final MultiBranchProject<?, ?> createNewProject(ItemGroup<?> parent, String name, List<? extends SCMSource> scmSources, Map<String, Object> attributes, TaskListener listener) throws IOException, InterruptedException {
            return doCreateProject(parent, name, attributes);
        }

        @Override
        public boolean recognizes(ItemGroup<?> parent, String name, List<? extends SCMSource> scmSources, Map<String, Object> attributes, final TaskListener listener) throws IOException, InterruptedException {
            for (final SCMSource scmSource : scmSources) {
                SCMSourceOwner owner = scmSource.getOwner();
                if (!(owner instanceof SCMSourceOwnerHack)) {
                    throw new IOException("cannot influence criteria on " + owner);
                }
                boolean empty;
                try {
                    empty = ((SCMSourceOwnerHack) owner).withSCMSourceCriteria(scmSource, getSCMSourceCriteria(scmSource), new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            return scmSource.fetch(listener).isEmpty();
                        }
                    });
                // This is ugly but lambdas do not seem to help at all: http://stackoverflow.com/q/18198176/12916
                } catch (IOException x) {
                    throw x;
                } catch (InterruptedException x) {
                    throw x;
                } catch (RuntimeException x) {
                    throw x;
                } catch (Exception x) {
                    throw new AssertionError(x);
                }
                if (!empty) {
                    return true;
                }
            }
            return false;
        }

    }

}
