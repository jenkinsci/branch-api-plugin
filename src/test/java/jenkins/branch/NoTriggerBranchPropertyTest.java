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
import jenkins.branch.harness.MultiBranchImpl;
import jenkins.plugins.git.GitSCMSource;
import org.jenkinsci.plugins.workflow.steps.scm.GitSampleRepoRule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class NoTriggerBranchPropertyTest {

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
        BranchSource branchSource = new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false));
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

}
