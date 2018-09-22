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

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.queue.QueueTaskFuture;
import java.util.Collections;
import static jenkins.branch.NoTriggerBranchPropertyTest.showComputation;
import jenkins.branch.harness.MultiBranchImpl;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.api.SCMSource;
import jenkins.scm.impl.SingleSCMNavigator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class OverrideIndexTriggersJobPropertyTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Issue("JENKINS-37219")
    @Test
    public void overridesDisabledBranch() throws Exception {
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
        BranchSource branchSource = new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false));
        branchSource.setStrategy(new NamedExceptionsBranchPropertyStrategy(new BranchProperty[0], new NamedExceptionsBranchPropertyStrategy.Named[] {
            new NamedExceptionsBranchPropertyStrategy.Named("release*", new BranchProperty[] {new NoTriggerBranchProperty(true, true)})
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

        // Now add the property and make sure a build will get triggered.
        release.addProperty(new OverrideIndexTriggersJobProperty(true));
        sampleRepo.git("checkout", "release");
        sampleRepo.write("file", "even more");
        sampleRepo.git("commit", "--all", "--message=release-3");
        sampleRepo.notifyCommit(r);
        showComputation(stuff);

        FreeStyleBuild releaseBuild = release.getBuildByNumber(1);
        assertNotNull("release branch build was not triggered by commit", releaseBuild);
        assertEquals(1, releaseBuild.getNumber());
        assertEquals(2, release.getNextBuildNumber());
    }

    @Issue("JENKINS-37219")
    @Test
    public void overridesDisabledOrg() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "newfeature");
        sampleRepo.write("stuff", "content");
        sampleRepo.git("add", "stuff");
        sampleRepo.git("commit", "--all", "--message=newfeature");
        sampleRepo.git("checkout", "-b", "release", "master");
        sampleRepo.write("server", "mycorp.com");
        sampleRepo.git("add", "server");
        sampleRepo.git("commit", "--all", "--message=release");
        OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
        r.configRoundtrip(top);
        NoTriggerOrganizationFolderProperty prop = top.getProperties().get(NoTriggerOrganizationFolderProperty.class);
        assertNotNull(prop);
        assertEquals(".*", prop.getBranches());
        top.getProperties().replace(new NoTriggerOrganizationFolderProperty("(?!release.*).*"));
        top.getProjectFactories().add(new OrganizationFolderTest.MockFactory());
        top.getNavigators().add(new SingleSCMNavigator("stuff", Collections.<SCMSource>singletonList(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false))));
        r.configRoundtrip(top);
        prop = top.getProperties().get(NoTriggerOrganizationFolderProperty.class);
        assertNotNull(prop);
        assertEquals("(?!release.*).*", prop.getBranches());
        assertEquals(1, top.getProperties().getAll(NoTriggerOrganizationFolderProperty.class).size());
        top.scheduleBuild2(0).getFuture().get();
        r.waitUntilNoActivity();
        showComputation(top);
        MultiBranchImpl stuff = r.jenkins.getItemByFullName("top/stuff", MultiBranchImpl.class);
        assertNotNull(stuff);
        showComputation(stuff);
        FreeStyleProject master = r.jenkins.getItemByFullName("top/stuff/master", FreeStyleProject.class);
        assertNotNull(master);
        assertEquals(2, master.getNextBuildNumber());
        FreeStyleProject release = r.jenkins.getItemByFullName("top/stuff/release", FreeStyleProject.class);
        assertNotNull(release);
        assertEquals(1, release.getNextBuildNumber());
        FreeStyleProject newfeature = r.jenkins.getItemByFullName("top/stuff/newfeature", FreeStyleProject.class);
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
        showComputation(top);
        showComputation(stuff);
        assertEquals(3, master.getNextBuildNumber());
        assertEquals(1, release.getNextBuildNumber());
        assertEquals(3, newfeature.getNextBuildNumber());

        // Now add the property and make sure a build will get triggered.
        release.addProperty(new OverrideIndexTriggersJobProperty(true));
        sampleRepo.git("checkout", "release");
        sampleRepo.write("file", "even more");
        sampleRepo.git("commit", "--all", "--message=release-3");
        sampleRepo.notifyCommit(r);
        showComputation(top);
        showComputation(stuff);

        FreeStyleBuild releaseBuild = release.getBuildByNumber(1);
        assertNotNull("release branch build was not triggered by commit", releaseBuild);
        assertEquals(1, releaseBuild.getNumber());
        assertEquals(2, release.getNextBuildNumber());
    }

    @Issue("JENKINS-37219")
    @Test
    public void overridesEnabled() throws Exception {
        sampleRepo.init();
        sampleRepo.write("stuff", "content");
        sampleRepo.git("add", "stuff");
        sampleRepo.git("commit", "--all", "--message=master-1");
        MultiBranchImpl stuff = r.jenkins.createProject(MultiBranchImpl.class, "stuff");
        BranchSource branchSource = new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false));
        stuff.getSourcesList().add(branchSource);
        r.configRoundtrip(stuff);
        stuff.scheduleBuild2(0).getFuture().get();
        r.waitUntilNoActivity();
        showComputation(stuff);
        FreeStyleProject master = r.jenkins.getItemByFullName("stuff/master", FreeStyleProject.class);
        assertNotNull(master);
        assertEquals(2, master.getNextBuildNumber());

        master.addProperty(new OverrideIndexTriggersJobProperty(false));
        sampleRepo.git("checkout", "master");
        sampleRepo.write("file", "more");
        sampleRepo.git("commit", "--all", "--message=master-2");
        sampleRepo.notifyCommit(r);
        showComputation(stuff);
        assertEquals(2, master.getNextBuildNumber());


        QueueTaskFuture<FreeStyleBuild> masterBuild = master.scheduleBuild2(0);
        assertNotNull("was not able to schedule a manual build of the master branch", masterBuild);
        assertEquals(2, masterBuild.get().getNumber());
        assertEquals(3, master.getNextBuildNumber());
    }

    @TestExtension
    public static class ConfigRoundTripDescriptor extends OrganizationFolderTest.MockFactoryDescriptor {}

}
