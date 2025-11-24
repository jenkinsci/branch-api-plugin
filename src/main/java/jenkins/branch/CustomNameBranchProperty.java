package jenkins.branch;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.FormValidation;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

/**
 * @author Frédéric Laugier
 */
public class CustomNameBranchProperty extends BranchProperty {

    private final String pattern;

    @DataBoundConstructor
    public CustomNameBranchProperty(String pattern) {
        super();

        if(!checkValidPattern(pattern)) {
            throw new IllegalArgumentException(Messages.CustomNameBranchProperty_InvalidPattern());
        }

        this.pattern = StringUtils.trimToNull(pattern);
    }


    public String getPattern() {
        return this.pattern;
    }

    @Override
    public <P extends Job<P, B>, B extends Run<P, B>> JobDecorator<P, B> jobDecorator(Class<P> clazz) {
        return null;
    }

    private static boolean checkValidPattern(String pattern) {
        String value = StringUtils.trimToNull(pattern);
        return value == null || value.contains("{}");
    }

    String generateName(String name) {
        return this.pattern != null ? this.pattern.replaceAll("\\{\\}", name) : name;
    }

    @Extension
    public static class DescriptorImpl extends BranchPropertyDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.CustomNameBranchProperty_DisplayName();
        }

        @POST
        public FormValidation doCheckPattern(StaplerRequest request) {
            return checkValidPattern(request.getParameter("value"))
                ? FormValidation.ok()
                : FormValidation.error(Messages.CustomNameBranchProperty_InvalidPattern());
        }
    }
}
