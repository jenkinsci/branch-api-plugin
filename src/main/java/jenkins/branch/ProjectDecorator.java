package jenkins.branch;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Publisher;

import java.util.List;

/**
 * Something that can decorate a project. Decorations can include manipulating the list of {@link Publisher} instances,
 * the list of {@link BuildWrapper} instances and the list of {@link JobProperty}s of the project as well as custom
 * tweaks that are specific to the project instance type itself.
 *
 * @author Stephen Connolly
 * @since 0.2
 */
public class ProjectDecorator<P extends Job<P, B>, B extends Run<P, B>> {

    /**
     * This method is an extension point whereby a {@link ProjectDecorator} can filter or enhance the set of
     * {@link hudson.tasks.Publisher} to be used by the job.
     *
     * @param publishers the proposed {@link hudson.tasks.Publisher}s.
     * @return the resulting {@link hudson.tasks.Publisher}s.
     */
    @NonNull
    public List<Publisher> publishers(@NonNull List<Publisher> publishers) {
        return publishers;
    }

    /**
     * This method is an extension point whereby a {@link ProjectDecorator} can filter or enhance the set of
     * {@link hudson.tasks.BuildWrapper} to be used by the job.
     *
     * @param wrappers the proposed {@link hudson.tasks.BuildWrapper}s.
     * @return the resulting {@link hudson.tasks.BuildWrapper}s.
     */
    @NonNull
    public List<BuildWrapper> buildWrappers(@NonNull List<BuildWrapper> wrappers) {
        return wrappers;
    }

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
     * {@link #buildWrappers(List)} instead.</li>
     * <li>Don't try to manipulate the {@link Publisher} instances in the job, use {@link #publishers(java.util.List)}
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
