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

import hudson.FilePath;
import hudson.model.FreeStyleProject;
import hudson.scm.NullSCM;
import hudson.slaves.DumbSlave;
import hudson.slaves.WorkspaceList;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import jenkins.branch.harness.MultiBranchImpl;
import jenkins.model.Jenkins;
import jenkins.scm.impl.SingleSCMSource;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

import static jenkins.branch.NoTriggerBranchPropertyTest.showComputation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WorkspaceLocatorV2ImplTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Before
    public void defaultEnabled() {
        WorkspaceLocatorV2Impl.ENABLED = true;
    }

    @Issue({"JENKINS-34564", "JENKINS-38837"})
    @Test
    public void locate() throws Exception {
        assertEquals("${JENKINS_HOME}/workspace/${ITEM_FULLNAME}", r.jenkins.getRawWorkspaceDir());
        MultiBranchImpl stuff = r.createProject(MultiBranchImpl.class, "stuff");
        stuff.getSourcesList().add(new BranchSource(new SingleSCMSource(null, "dev/flow", new NullSCM())));
        stuff.scheduleBuild2(0).getFuture().get();
        r.waitUntilNoActivity();
        showComputation(stuff);
        FreeStyleProject master = r.jenkins.getItemByFullName("stuff/dev%2Fflow", FreeStyleProject.class);
        assertNotNull(master);
        assertEquals(r.jenkins.getRootPath().child("workspace/stuff-dev_252Fflow.3btcj0"), r.jenkins.getWorkspaceFor(master));
        DumbSlave slave = r.createOnlineSlave();
        assertEquals(slave.getWorkspaceRoot().child("stuff-dev_252Fflow.3btcj0"), slave.getWorkspaceFor(master));
        FreeStyleProject unrelated = r.createFreeStyleProject("100% crazy");
        assertEquals(r.jenkins.getRootPath().child("workspace/100% crazy"), r.jenkins.getWorkspaceFor(unrelated));
        // Checking other values of workspaceDir.
        Field workspaceDir = Jenkins.class.getDeclaredField("workspaceDir"); // currently settable only by Jenkins.doConfigSubmit
        workspaceDir.setAccessible(true);
        // Poor historical default, and as per JENKINS-21942 even possible after some startup scenarios:
        workspaceDir.set(r.jenkins, "${ITEM_ROOTDIR}/workspace");
        assertEquals("JENKINS-34564 inactive in this case", new FilePath(master.getRootDir()).child("workspace"), r.jenkins.getWorkspaceFor(master));
        // JENKINS-38837: customized root.
        workspaceDir.set(r.jenkins, "${JENKINS_HOME}/ws/${ITEM_FULLNAME}");
        assertEquals("ITEM_FULLNAME interpreted a little differently", r.jenkins.getRootPath().child("ws/stuff-dev_252Fflow.3btcj0"), r.jenkins.getWorkspaceFor(master));
    }

    @Issue({"JENKINS-2111", "JENKINS-41068"})
    @Test
    public void delete() throws Exception {
        MultiBranchImpl p = r.createProject(MultiBranchImpl.class, "p");
        p.getSourcesList().add(new BranchSource(new SingleSCMSource(null, "master", new NullSCM())));
        BranchSource pr1Source = new BranchSource(new SingleSCMSource(null, "PR-1", new NullSCM()));
        p.getSourcesList().add(pr1Source);
        p.scheduleBuild2(0).getFuture().get();
        r.waitUntilNoActivity();
        showComputation(p);
        FreeStyleProject master = r.jenkins.getItemByFullName("p/master", FreeStyleProject.class);
        assertNotNull(master);
        FreeStyleProject pr1 = r.jenkins.getItemByFullName("p/PR-1", FreeStyleProject.class);
        assertNotNull(pr1);
        assertEquals(r.jenkins.getRootPath().child("workspace/p-master.on5lru"), r.jenkins.getWorkspaceFor(master));
        assertEquals(r.jenkins.getRootPath().child("workspace/p-PR-1.b6l857"), r.jenkins.getWorkspaceFor(pr1));
        // Do builds on an agent too.
        DumbSlave slave = r.createOnlineSlave();
        assertEquals(slave.getWorkspaceRoot().child("p-master.on5lru"), slave.getWorkspaceFor(master));
        assertEquals(slave.getWorkspaceRoot().child("p-PR-1.b6l857"), slave.getWorkspaceFor(pr1));
        master.setAssignedNode(slave);
        assertEquals(2, r.buildAndAssertSuccess(master).getNumber());
        pr1.setAssignedNode(slave);
        assertEquals(2, r.buildAndAssertSuccess(pr1).getNumber());
        // Also make sure we are testing alternate workspaces.
        try (WorkspaceList.Lease lease = slave.toComputer().getWorkspaceList().acquire(slave.getWorkspaceFor(pr1))) {
            assertEquals(3, r.buildAndAssertSuccess(pr1).getNumber());
        }
        File pr1Root = pr1.getRootDir();
        assertTrue(pr1Root.isDirectory());
        // Now delete PR-1 and make sure its workspaces are deleted too.
        p.getSourcesList().remove(pr1Source);
        p.scheduleBuild2(0).getFuture().get();
        r.waitUntilNoActivity();
        showComputation(p);
        WorkspaceLocatorV2Impl.Deleter.waitForTasksToFinish();
        assertEquals(Collections.singletonList(master), r.jenkins.getAllItems(FreeStyleProject.class));
        assertEquals(Collections.singletonList(r.jenkins.getRootPath().child("workspace/p-master.on5lru")), r.jenkins.getRootPath().child("workspace").listDirectories());
        assertEquals(Collections.singletonList(slave.getWorkspaceRoot().child("p-master.on5lru")), slave.getWorkspaceRoot().listDirectories());
        assertFalse(pr1Root.isDirectory());
    }

}
