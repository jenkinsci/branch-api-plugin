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
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceOwner;

/**
 * Creates {@link MultiBranchProject}s for repositories where recognized.
 */
public abstract class MultiBranchProjectFactory extends AbstractDescribableImpl<MultiBranchProjectFactory> implements ExtensionPoint {

    /**
     * Creates a multibranch project for a given set of SCM sources if they seem compatible.
     * @param parent a folder
     * @param name a project name
     * @param scmSources a set of SCM sources as enumerated by {@link SCMNavigator#discoverSources}
     * @return a new uninitialized multibranch project (do not configure its {@link MultiBranchProject#getSourcesList} or call {@link MultiBranchProject#onCreatedFromScratch}), or null if unrecognized
     */
    public abstract @CheckForNull MultiBranchProject<?,?> createProject(@Nonnull ItemGroup<?> parent, @Nonnull String name, @Nonnull List<? extends SCMSource> scmSources, @Nonnull TaskListener listener) throws IOException, InterruptedException;
    
    @Override public MultiBranchProjectFactoryDescriptor getDescriptor() {
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
        protected abstract @Nonnull SCMSourceCriteria getSCMSourceCriteria(@Nonnull SCMSource source);

        /**
         * Creates a project given that there seems to be a match.
         * @param parent the folder
         * @param name the project name
         * @return a new project suitable for {@link #createProject}
         */
        protected abstract @Nonnull MultiBranchProject<?,?> doCreateProject(@Nonnull ItemGroup<?> parent, @Nonnull String name);

        @Override public final MultiBranchProject<?,?> createProject(ItemGroup<?> parent, String name, List<? extends SCMSource> scmSources, final TaskListener listener) throws IOException, InterruptedException {
            for (final SCMSource scmSource : scmSources) {
                SCMSourceOwner owner = scmSource.getOwner();
                if (!(owner instanceof SCMSourceOwnerHack)) {
                    throw new IOException("cannot influence criteria on " + owner);
                }
                boolean empty;
                try {
                    empty = ((SCMSourceOwnerHack) owner).withSCMSourceCriteria(scmSource, getSCMSourceCriteria(scmSource), new Callable<Boolean>() {
                        @Override public Boolean call() throws Exception {
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
                    return doCreateProject(parent, name);
                }
            }
            return null;
        }

    }

}
