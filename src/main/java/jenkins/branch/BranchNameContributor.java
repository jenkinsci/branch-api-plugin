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

package jenkins.branch;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Date;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.metadata.ContributorMetadataAction;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;
import jenkins.scm.api.mixin.TagSCMHead;

/**
 * Defines the environment variable {@code BRANCH_NAME} and {@code BRANCH_IS_PRIMARY} for multibranch builds.
 * Also defines {@code CHANGE_*} variables for {@link ChangeRequestSCMHead} instances and 
 * {@code TAG_*} variables for {@link TagSCMHead} instances.
 */
@Extension
public class BranchNameContributor extends EnvironmentContributor {

    /** {@inheritDoc} */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void buildEnvironmentFor(Job j, EnvVars envs, TaskListener listener) throws IOException, InterruptedException {
        ItemGroup parent = j.getParent();
        if (parent instanceof MultiBranchProject) {
            BranchProjectFactory projectFactory = ((MultiBranchProject) parent).getProjectFactory();
            if (projectFactory.isProject(j)) {
                Branch branch = projectFactory.getBranch(j);
                SCMHead head = branch.getHead();
                // Note: not using Branch.name, since in the future that could be something different
                // than SCMHead.name, which is what we really want here.
                envs.put("FOLDER_NAME", parent.getFullName());
                envs.put("BRANCH_NAME", head.getName());
                if (branch.getAction(PrimaryInstanceMetadataAction.class) != null) {
                    envs.put("BRANCH_IS_PRIMARY", "true");
                }
                if (head instanceof ChangeRequestSCMHead) {
                    envs.putIfNotNull("CHANGE_ID", ((ChangeRequestSCMHead) head).getId());
                    SCMHead target = ((ChangeRequestSCMHead) head).getTarget();
                    envs.putIfNotNull("CHANGE_TARGET", target.getName());
                    if (head instanceof ChangeRequestSCMHead2) {
                        envs.putIfNotNull("CHANGE_BRANCH", ((ChangeRequestSCMHead2) head).getOriginName());
                    }
                    SCMHeadOrigin origin = head.getOrigin();
                    if (origin instanceof SCMHeadOrigin.Fork) {
                        envs.putIfNotNull("CHANGE_FORK", ((SCMHeadOrigin.Fork) origin).getName());
                    }
                    ObjectMetadataAction oma = branch.getAction(ObjectMetadataAction.class);
                    if (oma != null) {
                        envs.putIfNotNull("CHANGE_URL", oma.getObjectUrl());
                        envs.putIfNotNull("CHANGE_TITLE", oma.getObjectDisplayName());
                    }
                    ContributorMetadataAction cma = branch.getAction(ContributorMetadataAction.class);
                    if (cma != null) {
                        envs.putIfNotNull("CHANGE_AUTHOR", cma.getContributor());
                        envs.putIfNotNull("CHANGE_AUTHOR_DISPLAY_NAME", cma.getContributorDisplayName());
                        envs.putIfNotNull("CHANGE_AUTHOR_EMAIL", cma.getContributorEmail());
                    }
                }
                if (head instanceof TagSCMHead) {
                    envs.put("TAG_NAME", head.getName());
                    envs.putIfNotNull("TAG_TIMESTAMP", Long.toString(((TagSCMHead) head).getTimestamp()));
                    envs.putIfNotNull("TAG_UNIXTIME", Long.toString(((TagSCMHead) head).getTimestamp()/1000L));
                    envs.putIfNotNull("TAG_DATE", new Date(((TagSCMHead) head).getTimestamp()).toString());
                }
            }
        }
    }

}
