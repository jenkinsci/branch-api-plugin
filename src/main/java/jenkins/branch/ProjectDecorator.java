package jenkins.branch;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Build;
import hudson.model.Project;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Publisher;

import java.util.List;

/**
 * Something that can decorate a project.
 * Decorations can include manipulating the list of {@link Publisher} instances,
 * the list of {@link BuildWrapper} instances,
 * and things specified in the more generic {@link JobDecorator}.
 *
 * @author Stephen Connolly
 * @since 0.2
 */
public class ProjectDecorator<P extends Project<P, B>, B extends Build<P, B>> extends JobDecorator<P, B> {

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

}
