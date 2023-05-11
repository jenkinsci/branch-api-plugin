package jenkins.branch;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.BuildDiscarder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;

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

    @Override
    public <P extends Job<P,B>,B extends Run<P,B>> JobDecorator<P,B> jobDecorator(Class<P> jobType) {
        return new JobDecorator<P, B>(){
            @NonNull
            @Override
            public P project(@NonNull final P project) {
                // HACK ALERT
                // ==========
                // The lesser evil hack is to set the field by reflection, so we try that first

                Boolean success = AccessController.doPrivileged((PrivilegedAction<Boolean>) () -> {
                    try {
                        Field logRotator = Job.class.getDeclaredField("logRotator");
                        boolean accessible = logRotator.isAccessible();
                        try {
                            if (!accessible) logRotator.setAccessible(true);
                            logRotator.set(project, buildDiscarder);
                            // now finally check our handywork in case the field gets renamed.
                            return project.getBuildDiscarder() == buildDiscarder;
                        } finally {
                            if (!accessible) logRotator.setAccessible(false);
                        }
                    } catch (IllegalAccessException | NoSuchFieldException e) {
                        return false;
                    }
                });
                if (!Boolean.TRUE.equals(success)) {
                    // ok that didn't work

                    BulkChange bc = new BulkChange(project);
                    try {
                        // HACK ALERT
                        // ==========
                        // A side-effect of setting the buildDiscarder is that it will try to save the job
                        // we don't actually want to save the job in this method, so we turn off saving by
                        // abusing BulkChange and the fact that BulkChange.abort does not revert the state
                        // of the object.
                        project.setBuildDiscarder(buildDiscarder);
                    } catch (IOException e) {
                        // ignore
                    } finally {
                        // we don't actually want to save the change
                        bc.abort();
                    }
                }
                return project;
            }
        };
    }

    /**
     * Our {@link hudson.model.Descriptor}.
     */
    @Symbol("buildRetention")
    @Extension
    @SuppressWarnings("unused") // instantiated by Jenkins
    public static class DescriptorImpl extends BranchPropertyDescriptor {
        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return "Discard old builds";
        }
    }

}
