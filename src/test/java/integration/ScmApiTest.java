/*
 * The MIT License
 *
 * Copyright (c) 2016-2017 CloudBees, Inc.
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

package integration;

import hudson.model.FreeStyleProject;
import hudson.model.TopLevelItem;
import integration.harness.BasicMultiBranchProject;
import integration.harness.BasicMultiBranchProjectFactory;
import integration.harness.BasicSCMSourceCriteria;
import java.util.Collections;
import jenkins.branch.BranchSource;
import jenkins.branch.OrganizationFolder;
import jenkins.scm.api.SCMHead;
import jenkins.scm.impl.mock.MockChangeRequestSCMHead;
import jenkins.scm.impl.mock.MockSCM;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMHead;
import jenkins.scm.impl.mock.MockSCMNavigator;
import jenkins.scm.impl.mock.MockSCMRevision;
import jenkins.scm.impl.mock.MockSCMSource;
import jenkins.scm.impl.mock.MockTagSCMHead;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class ScmApiTest {

    /**
     * All tests in this class only create items and do not affect other global configuration, thus we trade test
     * execution time for the restriction on only touching items.
     */
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Before
    public void cleanOutAllItems() throws Exception {
        for (TopLevelItem i : r.getInstance().getItems()) {
            i.delete();
        }
    }

    @Test
    public void given_multibranchJob_when_scmHeadHeadByItemFindHead_then_headReturned() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            long timestamp = c.createTag("foo", "master", "1.0");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, true, true)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(SCMHead.HeadByItem.findHead(prj.getItem("master")),
                    is((SCMHead) new MockSCMHead("master")));
            assertThat(SCMHead.HeadByItem.findHead(prj.getItem("1.0")), is((SCMHead) new MockTagSCMHead("1.0",
                    timestamp)));
            assertThat(SCMHead.HeadByItem.findHead(prj.getItem("CR-" + crNum)),
                    is((SCMHead) new MockChangeRequestSCMHead(crNum, "master")));
        }
    }

    @Test
    public void given_multibranch_when_scmHeadHeadByItemFindHead_then_noHeadReturned() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createTag("foo", "master", "1.0");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, true, true)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(SCMHead.HeadByItem.findHead(prj), nullValue());
        }
    }

    @Test
    public void given_orgFolder_when_scmHeadHeadByItemFindHead_then_noHeadReturned() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(SCMHead.HeadByItem.findHead(prj), nullValue());
        }
    }

    @Test
    public void given_freestyle_when_scmHeadHeadByItemFindHead_then_noHeadReturned() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            FreeStyleProject prj = r.createFreeStyleProject();
            MockSCMHead head = new MockSCMHead("master");
            prj.setScm(new MockSCM(c, "foo", head, new MockSCMRevision(head, c.getRevision("foo", "master"))));
            r.buildAndAssertSuccess(prj);
            // we rely on there being no implementation of HeadByItem provided by MockSCM to
            // extract the head from a freestyle project's SCM. If SCM API were to add a clever
            // implementation to extract this information generically then the following assumption
            // would be invalid. What we are trying to verify is that the BranchAPI implementation
            // of SCMHead.HeadByItem doesn't blow up when given a "near match"
            assertThat(SCMHead.HeadByItem.findHead(prj), nullValue());
        }
    }

}
