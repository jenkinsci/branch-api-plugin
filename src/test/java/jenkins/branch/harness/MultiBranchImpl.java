/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import java.io.IOException;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.MultiBranchProjectDescriptor;

public class MultiBranchImpl extends MultiBranchProject<FreeStyleProject, FreeStyleBuild> {

    private static final Logger LOGGER = Logger.getLogger(MultiBranchImpl.class.getName());

    public MultiBranchImpl(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override
    protected BranchProjectFactory<FreeStyleProject, FreeStyleBuild> newProjectFactory() {
        return new BranchProjectFactoryImpl();
    }

    @Override
    public boolean scheduleBuild() {
        LOGGER.info("Indexing multibranch project: " + getDisplayName());
        return super.scheduleBuild();
    }

    public static class BranchProjectFactoryImpl extends BranchProjectFactory<FreeStyleProject, FreeStyleBuild> {

        @Override
        public FreeStyleProject newInstance(Branch branch) {
            FreeStyleProject job = new FreeStyleProject(getOwner(), branch.getName());
            setBranch(job, branch);
            return job;
        }

        @Override
        public Branch getBranch(FreeStyleProject project) {
            return project.getProperty(BranchProperty.class).getBranch();
        }

        @Override
        public FreeStyleProject setBranch(FreeStyleProject project, Branch branch) {
            BranchProperty property = project.getProperty(BranchProperty.class);

            try {
                if (property == null) {
                    project.addProperty(new BranchProperty(branch));
                } else if (!property.getBranch().equals(branch)) {
                    property.setBranch(branch);
                    project.save();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return project;
        }

        @Override
        public boolean isProject(Item item) {
            return item instanceof FreeStyleProject && ((FreeStyleProject) item).getProperty(BranchProperty.class) != null;
        }

    }

    @Extension
    public static class DescriptorImpl extends MultiBranchProjectDescriptor {

        @Override 
        public String getDisplayName() {
            return "Test Multibranch";
        }

        @Override 
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new MultiBranchImpl(parent, name);
        }
    }
}
