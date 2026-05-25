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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.MultiBranchProjectFactory;
import jenkins.branch.MultiBranchProjectFactoryDescriptor;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;

public class BasicMultiBranchProjectFactory extends MultiBranchProjectFactory.BySCMSourceCriteria {

    private final SCMSourceCriteria criteria;

    public BasicMultiBranchProjectFactory(SCMSourceCriteria criteria) {
        this.criteria = criteria;
    }

    @CheckForNull
    @Override
    protected SCMSourceCriteria getSCMSourceCriteria(@NonNull SCMSource source) {
        return criteria;
    }

    @NonNull
    @Override
    protected MultiBranchProject<?, ?> doCreateProject(@NonNull ItemGroup<?> parent, @NonNull String name,
                                                       @NonNull Map<String, Object> attributes) {
        BasicMultiBranchProject project = new BasicMultiBranchProject(parent, name);
        project.setCriteria(criteria);
        return project;
    }

    @Override
    public void updateExistingProject(@NonNull MultiBranchProject<?, ?> project,
                                      @NonNull Map<String, Object> attributes, @NonNull TaskListener listener) {
        if (project instanceof BasicMultiBranchProject) {
            SCMSourceCriteria criteria = ((BasicMultiBranchProject) project).getCriteria();
            if (!Objects.equals(this.criteria, criteria)) {
                ((BasicMultiBranchProject) project).setCriteria(this.criteria);
            }
        }
    }

    @Extension
    public static class DescriptorImpl extends MultiBranchProjectFactoryDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return "BasicMultiBranchProjectFactory";
        }

        @Override
        public MultiBranchProjectFactory newInstance() {
            return null;
        }
    }
}
