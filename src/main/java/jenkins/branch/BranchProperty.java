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
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Publisher;

import java.util.List;
import java.util.Map;

/**
 * Additional information associated with {@link Branch}.
 * <p/>
 * {@link jenkins.scm.api.SCMSource}s can use properties to convey additional implementation/SCM specific
 * information that's not captured in the base {@link Branch} class.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class BranchProperty extends AbstractDescribableImpl<BranchProperty> {

    /**
     * This method is an extension point whereby a {@link BranchProperty} can filter or enhance the set of
     * {@link Publisher} to be used by the branch specific project.
     *
     * @param publishers the proposed {@link Publisher}s.
     * @return the resulting {@link Publisher}s.
     */
    @NonNull
    public Map<Descriptor<Publisher>, Publisher> configurePublishers(
            @NonNull Map<Descriptor<Publisher>, Publisher> publishers) {
        return publishers;
    }

    /**
     * This method is an extension point whereby a {@link BranchProperty} can filter or enhance the set of
     * {@link BuildWrapper} to be used by the branch specific project.
     *
     * @param wrappers the proposed {@link BuildWrapper}s.
     * @return the resulting {@link BuildWrapper}s.
     */
    @NonNull
    public Map<Descriptor<BuildWrapper>, BuildWrapper> configureBuildWrappers(
            @NonNull Map<Descriptor<BuildWrapper>, BuildWrapper> wrappers) {
        return wrappers;
    }

    /**
     * This method is an extension point whereby a {@link BranchProperty} can filter or enhance the set of
     * {@link JobProperty} to be used by the branch specific project.
     *
     * @param properties the proposed {@link JobProperty}s.
     * @return the resulting {@link JobProperty}s.
     */
    @NonNull
    public <JobT extends Job<?, ?>> List<JobProperty<? super JobT>> configureJobProperties(
            @NonNull List<JobProperty<? super JobT>> properties) {
        return properties;
    }

    /**
     * This method is an extension point whereby a {@link BranchProperty} can apply final tweaks to the job
     * for the branch specific project. Implementations should try to obey the following rules:
     * <ul>
     * <li>Don't trigger a save of the job</li>
     * <li>Don't try to manipulate the {@link JobProperty} instances in the job, use
     * {@link #configureJobProperties(List)} instead.</li>
     * <li>Don't try to manipulate the {@link BuildWrapper} instances in the job, use
     * {@link #configureBuildWrappers(Map)} instead.</li>
     * <li>Don't try to manipulate the {@link Publisher} instances in the job, use {@link #configurePublishers(Map)}}
     * instead.</li>
     * </ul>
     * In general, this method should be seen as a final hook for use in those cases where the existing hooks
     * prove insufficient.
     *
     * @param job    the job.
     * @param <JobT> the type of job.
     */
    public <JobT extends Job<JobT, RunT>, RunT extends Run<JobT, RunT>> void configureJob(
            @NonNull Job<JobT, RunT> job) {
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public BranchPropertyDescriptor getDescriptor() {
        return (BranchPropertyDescriptor) super.getDescriptor();
    }
}
