/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.util.FormValidation;
import java.util.Collections;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jenkins.branch.NoTriggerMultiBranchQueueDecisionHandler.NoTriggerProperty;
import jenkins.branch.NoTriggerMultiBranchQueueDecisionHandler.SuppressionStrategy;
import org.jenkinsci.Symbol;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Defines {@link NoTriggerBranchProperty} on selected branches.
 */
//@Restricted(NoExternalUse.class)
public class NoTriggerOrganizationFolderProperty extends AbstractFolderProperty<OrganizationFolder> implements NoTriggerProperty {

    private final String branches;
    private SuppressionStrategy strategy;

    @DataBoundConstructor
    public NoTriggerOrganizationFolderProperty(String branches) {
        this.branches = branches;
    }

    public String getBranches() {
        return branches;
    }

    @NonNull
    @Override
    public String getTriggeredBranchesRegex() {
        // backward compatibility, allow all by default
        if (branches == null) {
            return ".*";
        }
        return branches;
    }

    @NonNull
    @Override
    public SuppressionStrategy getStrategy() {
        if (strategy == null) {
            return SuppressionStrategy.NONE;
        }
        return strategy;
    }

    @DataBoundSetter
    public void setStrategy(NoTriggerMultiBranchQueueDecisionHandler.SuppressionStrategy strategy) {
        this.strategy = strategy;
    }

    @Extension
    @Symbol("suppressFolderAutomaticTriggering")
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.NoTriggerBranchProperty_suppress_automatic_scm_triggering();
        }

        public FormValidation doCheckBranches(@QueryParameter String value) {
            try {
                Pattern.compile(value);
                return FormValidation.ok();
            } catch (PatternSyntaxException x) {
                return FormValidation.error(x.getMessage());
            }
        }
    }

    @Extension
    public static class Dispatcher extends NoTriggerMultiBranchQueueDecisionHandler {

        @NonNull
        @Override
        protected Iterable<? extends Object> getJobProperties(MultiBranchProject project, Job job) {
            if (project.getParent() instanceof OrganizationFolder) {
                return ((OrganizationFolder) project.getParent()).getProperties();
            }
            return Collections.emptyList();
        }
    }

    @Extension
    public static class PropertyMigrationImpl extends PropertyMigration<OrganizationFolder, NoTriggerOrganizationFolderProperty> {

        public PropertyMigrationImpl() {
            super(OrganizationFolder.class, NoTriggerOrganizationFolderProperty.class, "basic-branch-build-strategies:1.1.0");
        }

        @Override
        public Localizable getDescription() {
            return Messages._NoTriggerOrganizationFolderProperty_PropertyMigrationWarning();
        }

        @Override
        public boolean isEnabled() {
            // Disabled due to impact discussed in https://issues.jenkins-ci.org/browse/JENKINS-54864.
            // Could be reenabled if we add some kind of guided migration that clearly explains what
            // manual changes might be required by the user.
            return false;
        }
    }
}
