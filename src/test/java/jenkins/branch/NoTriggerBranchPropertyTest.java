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

import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.queue.QueueTaskFuture;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.branch.NoTriggerBranchProperty.SuppressionStrategy;
import jenkins.branch.harness.MultiBranchImpl;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMEvents;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMSource;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMHeadEvent;
import jenkins.scm.impl.mock.MockSCMSource;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NoTriggerBranchPropertyTest {

    private static final SuppressionStrategy DONT_SUPPRESS_ANYTHING = null;

    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Issue("JENKINS-32396")
    @Test
    public void smokes() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "newfeature");
        sampleRepo.write("stuff", "content");
        sampleRepo.git("add", "stuff");
        sampleRepo.git("commit", "--all", "--message=newfeature");
        sampleRepo.git("checkout", "-b", "release", "master");
        sampleRepo.write("server", "mycorp.com");
        sampleRepo.git("add", "server");
        sampleRepo.git("commit", "--all", "--message=release");
        MultiBranchImpl stuff = r.jenkins.createProject(MultiBranchImpl.class, "stuff");
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(Collections.singletonList(new BranchDiscoveryTrait()));
        BranchSource branchSource = new BranchSource(source);
        branchSource.setStrategy(new NamedExceptionsBranchPropertyStrategy(new BranchProperty[0], new NamedExceptionsBranchPropertyStrategy.Named[] {
            new NamedExceptionsBranchPropertyStrategy.Named("release*", new BranchProperty[] {new NoTriggerBranchProperty()})
        }));
        stuff.getSourcesList().add(branchSource);
        r.configRoundtrip(stuff);
        stuff.scheduleBuild2(0).getFuture().get();
        r.waitUntilNoActivity();
        showComputation(stuff);
        FreeStyleProject master = r.jenkins.getItemByFullName("stuff/master", FreeStyleProject.class);
        assertNotNull(master);
        assertEquals(2, master.getNextBuildNumber());
        FreeStyleProject release = r.jenkins.getItemByFullName("stuff/release", FreeStyleProject.class);
        assertNotNull(release);
        assertEquals(1, release.getNextBuildNumber());
        FreeStyleProject newfeature = r.jenkins.getItemByFullName("stuff/newfeature", FreeStyleProject.class);
        assertNotNull(newfeature);
        assertEquals(2, newfeature.getNextBuildNumber());
        sampleRepo.git("checkout", "master");
        sampleRepo.write("file", "more");
        sampleRepo.git("commit", "--all", "--message=master-2");
        sampleRepo.git("checkout", "newfeature");
        sampleRepo.write("file", "more");
        sampleRepo.git("commit", "--all", "--message=newfeature-2");
        sampleRepo.git("checkout", "release");
        sampleRepo.write("file", "more");
        sampleRepo.git("commit", "--all", "--message=release-2");
        sampleRepo.notifyCommit(r);
        showComputation(stuff);
        assertEquals(3, master.getNextBuildNumber());
        assertEquals(1, release.getNextBuildNumber());
        assertEquals(3, newfeature.getNextBuildNumber());
        QueueTaskFuture<FreeStyleBuild> releaseBuild = release.scheduleBuild2(0);
        assertNotNull("was able to schedule a manual build of the release branch", releaseBuild);
        assertEquals(1, releaseBuild.get().getNumber());
        assertEquals(2, release.getNextBuildNumber());
    }

    static void showComputation(@NonNull ComputedFolder<?> d) throws Exception {
        FolderComputation<?> computation = d.getComputation();
        System.out.println("---%<--- " + computation.getUrl());
        computation.writeWholeLogTo(System.out);
        System.out.println("---%<--- ");
    }

    @Issue("JENKINS-63673")
    @Test
    public void suppressNothing() throws Exception {
        try (MockSCMController controller = MockSCMController.create()) {
            TestSuppressionProject project = new TestSuppressionProject(controller, DONT_SUPPRESS_ANYTHING);
            assertThat(project.getNextBuildNumber(), is(1));
            project.triggerIndexing();
            assertThat(project.getNextBuildNumber(), is(2));
            project.triggerEvent();
            assertThat(project.getNextBuildNumber(), is(3));
            project.triggerEvent();
            assertThat(project.getNextBuildNumber(), is(4));
            project.triggerIndexing();
            assertThat(project.getNextBuildNumber(), is(5));
        }
    }

    @Issue("JENKINS-63673")
    @Test
    public void suppressAll() throws Exception {
        try (MockSCMController controller = MockSCMController.create()) {
            TestSuppressionProject project = new TestSuppressionProject(controller, SuppressionStrategy.ALL);
            assertThat(project.getNextBuildNumber(), is(1));
            project.triggerIndexing();
            assertThat(project.getNextBuildNumber(), is(1));
            project.triggerEvent();
            assertThat(project.getNextBuildNumber(), is(1));
            project.triggerEvent();
            assertThat(project.getNextBuildNumber(), is(1));
            project.triggerIndexing();
            assertThat(project.getNextBuildNumber(), is(1));
        }
    }

    @Issue("JENKINS-63673")
    @Test
    public void suppressIndexing() throws Exception {
        try (MockSCMController controller = MockSCMController.create()) {
            TestSuppressionProject project = new TestSuppressionProject(controller, SuppressionStrategy.INDEXING);
            assertThat(project.getNextBuildNumber(), is(1));
            project.triggerIndexing();
            assertThat(project.getNextBuildNumber(), is(1));
            project.triggerEvent();
            assertThat(project.getNextBuildNumber(), is(2));
            project.triggerEvent();
            assertThat(project.getNextBuildNumber(), is(3));
            project.triggerIndexing();
            assertThat(project.getNextBuildNumber(), is(3));
        }
    }

    @Issue("JENKINS-63673")
    @Test
    public void suppressEvents() throws Exception {
        try (MockSCMController controller = MockSCMController.create()) {
            TestSuppressionProject project = new TestSuppressionProject(controller, SuppressionStrategy.EVENTS);
            assertThat(project.getNextBuildNumber(), is(1));
            project.triggerIndexing();
            assertThat(project.getNextBuildNumber(), is(2));
            project.triggerEvent();
            assertThat(project.getNextBuildNumber(), is(2));
            project.triggerEvent();
            assertThat(project.getNextBuildNumber(), is(2));
            project.triggerIndexing();
            assertThat(project.getNextBuildNumber(), is(3));
        }
    }

    private class TestSuppressionProject {

        private static final String REPOSITORY = "verify-suppression";
        private static final String BRANCH_NAME = "master";

        private final MockSCMController controller;
        private final MultiBranchImpl project;

        public TestSuppressionProject(MockSCMController controller, SuppressionStrategy strategy) throws Exception {
            this.controller = controller;
            controller.createRepository(REPOSITORY);
            project = r.jenkins.createProject(MultiBranchImpl.class, "verifySuppressionJob");
            SCMSource source = new MockSCMSource(controller, REPOSITORY, new MockSCMDiscoverBranches());
            BranchSource branchSource = new BranchSource(source);
            List<BranchProperty> branchProperties = new ArrayList<>();
            if (DONT_SUPPRESS_ANYTHING != strategy) {
                NoTriggerBranchProperty property = new NoTriggerBranchProperty();
                property.setStrategy(strategy);
                branchProperties.add(property);
            }
            branchSource.setStrategy(new DefaultBranchPropertyStrategy(branchProperties.toArray(new BranchProperty[0])));
            project.getSourcesList().add(branchSource);
            r.configRoundtrip(project);
        }

        public int getNextBuildNumber() {
            return project.getItem(BRANCH_NAME).getNextBuildNumber();
        }

        public void triggerIndexing() throws Exception {
            bumpHeadRevision();
            project.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            showComputation(project);
        }

        public void triggerEvent() throws Exception {
            long watermark = SCMEvents.getWatermark();
            String revision = bumpHeadRevision();
            SCMHeadEvent.fireNow(new MockSCMHeadEvent("test", SCMEvent.Type.UPDATED, controller, REPOSITORY, BRANCH_NAME, revision));
            SCMEvents.awaitAll(watermark);
            r.waitUntilNoActivity();
        }

        private String bumpHeadRevision() throws UnsupportedEncodingException, IOException {
            controller.addFile(REPOSITORY, BRANCH_NAME, "add new file", Long.toString(System.currentTimeMillis()), "text".getBytes("UTF-8"));
            return controller.getRevision(REPOSITORY, BRANCH_NAME);
        }
    }
}
