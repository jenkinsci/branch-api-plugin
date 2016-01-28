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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Publisher;
import java.util.List;

/**
 * Something that can decorate a job.
 * Decorations can include manipulating the list of {@link JobProperty}s of the project as well as custom
 * tweaks that are specific to the project instance type itself.
 */
public class JobDecorator<P extends Job<P, B>, B extends Run<P, B>> {

    /**
     * This method is an extension point whereby a {@link BranchProperty} can filter or enhance the set of
     * {@link hudson.model.JobProperty} to be used by the branch specific project.
     *
     * @param properties the proposed {@link hudson.model.JobProperty}s.
     * @return the resulting {@link hudson.model.JobProperty}s.
     */
    @NonNull
    public List<JobProperty<? super P>> jobProperties(@NonNull List<JobProperty<? super P>> properties) {
        return properties;
    }

    /**
     * This method is an extension point whereby a {@link BranchProperty} can apply final tweaks to the project
     * for the branch specific project. Implementations should try to obey the following rules:
     * <ul>
     * <li>Don't trigger a save of the job</li>
     * <li>Don't try to manipulate the {@link JobProperty} instances in the job, use
     * {@link #jobProperties(List)} instead.</li>
     * <li>Don't try to manipulate the {@link BuildWrapper} instances in the job, use
     * {@link ProjectDecorator#buildWrappers(List)} instead.</li>
     * <li>Don't try to manipulate the {@link Publisher} instances in the job, use {@link ProjectDecorator#publishers(java.util.List)}
     * instead.</li>
     * </ul>
     * In general, this method should be seen as a final hook for use in those cases where the existing hooks
     * prove insufficient.
     *
     * @param project the project.
     */
    @NonNull
    public P project(@NonNull P project) {
        return project;
    }

}
