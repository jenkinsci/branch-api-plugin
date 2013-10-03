package jenkins.branch;

import hudson.Extension;
import jenkins.model.BuildDiscarder;
import org.kohsuke.stapler.DataBoundConstructor;

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
