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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.FormValidation;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jenkins.branch.NoTriggerMultiBranchQueueDecisionHandler.NoTriggerProperty;
import jenkins.branch.NoTriggerMultiBranchQueueDecisionHandler.SuppressionStrategy;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Suppresses builds due to either {@link BranchIndexingCause} or {@link BranchEventCause}.
 * The purpose of this property is to prevent triggering builds resulting from the <em>detection</em>
 * of changes in the underlying SCM.
 */
@Restricted(NoExternalUse.class)
public class NoTriggerBranchProperty extends BranchProperty implements NoTriggerProperty {

    private String triggeredBranchesRegex;
    private SuppressionStrategy strategy;

    @DataBoundConstructor
    public NoTriggerBranchProperty() {
        // empty
    }

    @NonNull
    @Override
    public String getTriggeredBranchesRegex() {
        // backward compatibility, skip all by default
        if (triggeredBranchesRegex == null) {
            return "^$";
        }
        return triggeredBranchesRegex;
    }

    @DataBoundSetter
    public void setTriggeredBranchesRegex(String triggeredBranchesRegex) {
        this.triggeredBranchesRegex = triggeredBranchesRegex;
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
    public void setStrategy(SuppressionStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public <P extends Job<P, B>, B extends Run<P, B>> JobDecorator<P, B> jobDecorator(Class<P> clazz) {
        return null;
    }

    @Extension
    @Symbol("suppressAutomaticTriggering")
    public static class DescriptorImpl extends BranchPropertyDescriptor {

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
            return project.getProjectFactory().getBranch(job).getProperties();
        }
    }
}
