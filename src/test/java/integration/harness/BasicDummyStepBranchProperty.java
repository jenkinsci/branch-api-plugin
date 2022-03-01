/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
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
 *
 */

package integration.harness;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.Run;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import java.util.List;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchPropertyDescriptor;
import jenkins.branch.JobDecorator;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.MultiBranchProjectDescriptor;
import jenkins.branch.ProjectDecorator;
import org.kohsuke.stapler.DataBoundConstructor;

public class BasicDummyStepBranchProperty extends BranchProperty {
    @DataBoundConstructor
    public BasicDummyStepBranchProperty() {
    }

    @Override
    public <P extends Job<P, B>, B extends Run<P, B>> JobDecorator<P, B> jobDecorator(Class<P> clazz) {
        if (FreeStyleProject.class.isAssignableFrom(clazz)) {
            return (JobDecorator<P, B>) new ProjectDecorator<FreeStyleProject, FreeStyleBuild>() {
                @NonNull
                @Override
                public List<JobProperty<? super FreeStyleProject>> jobProperties(
                        @NonNull List<JobProperty<? super FreeStyleProject>> jobProperties) {
                    return super.jobProperties(jobProperties);
                }

                @NonNull
                @Override
                public FreeStyleProject project(@NonNull FreeStyleProject project) {
                    project.getBuildersList().add(
                            Functions.isWindows() ? new BatchFile("set") : new Shell("env")
                    );
                    return super.project(project);
                }
            };
        }
        return null;
    }

    @Extension
    public static class DescriptorImpl extends BranchPropertyDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return "BasicDummyStepBranchProperty";
        }

        @Override
        public boolean isApplicable(@NonNull MultiBranchProject project) {
            return project instanceof BasicMultiBranchProject;
        }

        @Override
        protected boolean isApplicable(@NonNull MultiBranchProjectDescriptor projectDescriptor) {
            return projectDescriptor instanceof BasicMultiBranchProject.DescriptorImpl;
        }
    }
}
