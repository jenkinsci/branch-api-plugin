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

package jenkins.branch.harness;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import java.io.IOException;
import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.BranchProjectFactoryDescriptor;
import jenkins.branch.MultiBranchProject;
import org.kohsuke.stapler.DataBoundConstructor;

public class BranchProjectFactoryImpl extends BranchProjectFactory<FreeStyleProject, FreeStyleBuild> {

    @DataBoundConstructor
    public BranchProjectFactoryImpl() {}

    @Override
    public FreeStyleProject newInstance(Branch branch) {
        FreeStyleProject job = new FreeStyleProject(getOwner(), branch.getEncodedName());
        setBranch(job, branch);
        return job;
    }

    @NonNull
    @Override
    public Branch getBranch(@NonNull FreeStyleProject project) {
        return project.getProperty(BranchProperty.class).getBranch();
    }

    @NonNull
    @Override
    public FreeStyleProject setBranch(@NonNull FreeStyleProject project, @NonNull Branch branch) {
        try {
            project.addProperty(new BranchProperty(branch));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return project;
    }

    @Override
    public boolean isProject(Item item) {
        return item instanceof FreeStyleProject && ((FreeStyleProject) item).getProperty(BranchProperty.class) != null;
    }

    @Extension
    public static class DescriptorImpl extends BranchProjectFactoryDescriptor {

        @Override
        public boolean isApplicable(Class<? extends MultiBranchProject> clazz) {
            return MultiBranchProject.class.isAssignableFrom(clazz);
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "BranchProjectFactoryImpl";
        }

    }

}
