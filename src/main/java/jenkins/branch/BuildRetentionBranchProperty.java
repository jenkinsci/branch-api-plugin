package jenkins.branch;

import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Stephen Connolly
 */
public class BuildRetentionBranchProperty extends BranchProperty {

    @DataBoundConstructor
    public BuildRetentionBranchProperty() {
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
