package jenkins.branch;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.BuildDiscarder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * @author Stephen Connolly
 */
public class BuildRetentionBranchProperty extends BranchProperty {

    private final BuildDiscarder buildDiscarder;

    @DataBoundConstructor
    public BuildRetentionBranchProperty(BuildDiscarder buildDiscarder) {
        this.buildDiscarder = buildDiscarder;
    }

    public BuildDiscarder getBuildDiscarder() {
        return buildDiscarder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <JobT extends Job<JobT, RunT>, RunT extends Run<JobT, RunT>> void configureJob(@NonNull Job<JobT, RunT> job) {
        BulkChange bc = new BulkChange(job);
        try {
            // HACK ALERT
            // ==========
            // A side-effect of setting the buildDiscarder is that it will try to save the job
            // we don't actually want to save the job in this method, so we turn off saving by
            // abusing BulkChange and the fact that BulkChange.abort does not revert the state
            // of the object.
            job.setBuildDiscarder(buildDiscarder);
        } catch (IOException e) {
            // ignore
        } finally {
            // we don't actually want to save the change
            bc.abort();
        }
    }

    /**
     * Our {@link hudson.model.Descriptor}.
     */
    @Extension
    @SuppressWarnings("unused") // instantiated by Jenkins
    public static class DescriptorImpl extends BranchPropertyDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Discard old builds";
        }
    }

}
