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

import static org.junit.Assume.assumeFalse;

import hudson.FilePath;
import hudson.Functions;
import hudson.model.FreeStyleProject;
import hudson.scm.NullSCM;
import hudson.slaves.DumbSlave;
import hudson.slaves.WorkspaceList;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.logging.Level;
import java.util.stream.Collectors;
import static jenkins.branch.NoTriggerBranchPropertyTest.showComputation;
import jenkins.branch.harness.MultiBranchImpl;
import jenkins.model.Jenkins;
import jenkins.scm.impl.SingleSCMSource;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.WithoutJenkins;

public class WorkspaceLocatorImplTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public LoggerRule logging = new LoggerRule().record(WorkspaceLocatorImpl.class, Level.FINER);
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @SuppressWarnings("deprecation")
    @Before
    public void defaultPathMax() {
        WorkspaceLocatorImpl.PATH_MAX = WorkspaceLocatorImpl.PATH_MAX_DEFAULT;
    }

    WorkspaceLocatorImpl.Mode origMode;
    @Before
    public void saveMode() {
        origMode = WorkspaceLocatorImpl.MODE;
    }
    @After
    public void restoreMode() {
        WorkspaceLocatorImpl.MODE = origMode;
    }

    @WithoutJenkins
    @SuppressWarnings("deprecation")
    @Test
    public void minimize() {
        assertEquals("a_b-NX345YSMOYT4QUL4OO7V6EGKM57BBNSYVIXGXHCE4KAEVPV5KZYQ", WorkspaceLocatorImpl.minimize("a/b"));
        assertEquals("a_b_c_d-UMWYJ45JQ6FA3WXMSI3YEOLVQ5P6SFYWN26FRECRSFBUGUD27Y5A", WorkspaceLocatorImpl.minimize("a/b/c/d"));
        assertEquals("stuff_dev_flow-L5GKER67QGVMJ2UD3JCSGKEV2ACON2O4VO4RNUZ27HGUY32SYVXQ", WorkspaceLocatorImpl.minimize("stuff/dev%2Fflow"));
        assertEquals("me_longish_name_here_master-P3JSCCKIEGEC4PETCZJODHB27EFCCYGQG7TRS6WXKXZNY5INPPRQ", WorkspaceLocatorImpl.minimize("some longish name here/master"));
        assertEquals("_fit_in_a_short_path_at_all-OB76NQGNPMSKZVYU5OTRBEWQCVPKEV4APFL6JNS2FVAAW5WM5CWQ", WorkspaceLocatorImpl.minimize("really way too much to fit in a short path at all"));
        assertEquals("abc_esky_-XHOKB7XHQS32PT7KIXIDYFSRSU4SBSGHX3K5O36BMZE2CSLSOVQA", WorkspaceLocatorImpl.minimize("abc!@#$%^&*()[]{}|česky™"));
        assertEquals("lahblahblahblahblahblahblah-PKYGNQW7EX27MNOU63BZF4FUBUTNK3HIBC37PR673KBZYLRZAQLA", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblah"));
        assertEquals("ahblahblahblahblahblahblahX-B4K3CPB6GP6JRCDBAKDJNRGX42AYU55PGVSOX2UWB5SVLUW42NCA", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahX"));
        assertEquals("hblahblahblahblahblahblahXY-ZGH2VVOGO2FEM72MZYA7CWRLPBOJK2HZ4YV7IFVDLZPCNVE3CXNQ", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXY"));
        assertEquals("blahblahblahblahblahblahXYZ-I7NHG3VUEWUH3IFAPUM3HGRL7O4EFAE3FXCXPL35CWOOQSZR6S4Q", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXYZ"));
        assertEquals("lahblahblahblahblahblahXYZW-LRVIZHY37BWI3PKRF7WSERGRN3NGPY4T74VWKWNFRMR4IWGXJPQA", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXYZW"));
        assertEquals("ahblahblahblahblahblahXYZWV-KLYOGWEJODAVXII3MEM2SLNMRPE7HF6IADTBQ5MP66V3RYCL2LAA", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXYZWV"));
        assertEquals("hblahblahblahblahblahXYZWVU-OSF24EPB4C42KAUXYHPP66XDQHOHKWPHGKZLIWREKOGZDZ46T2PQ", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXYZWVU"));
        
        WorkspaceLocatorImpl.PATH_MAX = 40;
        assertEquals("a_b-NX345YSMOYT4QUL4OO7V6EGKM", WorkspaceLocatorImpl.minimize("a/b"));
        assertEquals("a_b_c_d-UMWYJ45JQ6FA3WXMSI3YEOLVQ", WorkspaceLocatorImpl.minimize("a/b/c/d"));
        assertEquals("stuff_dev_flow-L5GKER67QGVMJ2UD3JCSGKEV2", WorkspaceLocatorImpl.minimize("stuff/dev%2Fflow"));
        assertEquals("me_here_master-P3JSCCKIEGEC4PETCZJODHB27", WorkspaceLocatorImpl.minimize("some longish name here/master"));
        assertEquals("rt_path_at_all-OB76NQGNPMSKZVYU5OTRBEWQC", WorkspaceLocatorImpl.minimize("really way too much to fit in a short path at all"));
        assertEquals("abc_esky_-XHOKB7XHQS32PT7KIXIDYFSRS", WorkspaceLocatorImpl.minimize("abc!@#$%^&*()[]{}|česky™"));
        assertEquals("ahblahblahblah-PKYGNQW7EX27MNOU63BZF4FUB", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblah"));
        assertEquals("hblahblahblahX-B4K3CPB6GP6JRCDBAKDJNRGX4", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahX"));
        assertEquals("blahblahblahXY-ZGH2VVOGO2FEM72MZYA7CWRLP", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXY"));
        assertEquals("lahblahblahXYZ-I7NHG3VUEWUH3IFAPUM3HGRL7", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXYZ"));
        assertEquals("ahblahblahXYZW-LRVIZHY37BWI3PKRF7WSERGRN", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXYZW"));
        assertEquals("hblahblahXYZWV-KLYOGWEJODAVXII3MEM2SLNMR", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXYZWV"));
        assertEquals("blahblahXYZWVU-OSF24EPB4C42KAUXYHPP66XDQ", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXYZWVU"));
        
        WorkspaceLocatorImpl.PATH_MAX = 20;
        assertEquals("a_b-NX345YSMOYT4QUL", WorkspaceLocatorImpl.minimize("a/b"));
        assertEquals("_c_d-UMWYJ45JQ6FA3WX", WorkspaceLocatorImpl.minimize("a/b/c/d"));
        assertEquals("flow-L5GKER67QGVMJ2U", WorkspaceLocatorImpl.minimize("stuff/dev%2Fflow"));
        assertEquals("ster-P3JSCCKIEGEC4PE", WorkspaceLocatorImpl.minimize("some longish name here/master"));
        assertEquals("_all-OB76NQGNPMSKZVY", WorkspaceLocatorImpl.minimize("really way too much to fit in a short path at all"));
        assertEquals("sky_-XHOKB7XHQS32PT7", WorkspaceLocatorImpl.minimize("abc!@#$%^&*()[]{}|česky™"));
        assertEquals("blah-PKYGNQW7EX27MNO", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblah"));
        assertEquals("lahX-B4K3CPB6GP6JRCD", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahX"));
        assertEquals("ahXY-ZGH2VVOGO2FEM72", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXY"));
        assertEquals("hXYZ-I7NHG3VUEWUH3IF", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXYZ"));
        assertEquals("XYZW-LRVIZHY37BWI3PK", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXYZW"));
        assertEquals("YZWV-KLYOGWEJODAVXII", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXYZWV"));
        assertEquals("ZWVU-OSF24EPB4C42KAU", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXYZWVU"));
        
        WorkspaceLocatorImpl.PATH_MAX = 1;
        assertEquals("b-NX345YSMOY", WorkspaceLocatorImpl.minimize("a/b"));
        assertEquals("d-UMWYJ45JQ6", WorkspaceLocatorImpl.minimize("a/b/c/d"));
        assertEquals("w-L5GKER67QG", WorkspaceLocatorImpl.minimize("stuff/dev%2Fflow"));
        assertEquals("r-P3JSCCKIEG", WorkspaceLocatorImpl.minimize("some longish name here/master"));
        assertEquals("l-OB76NQGNPM", WorkspaceLocatorImpl.minimize("really way too much to fit in a short path at all"));
        assertEquals("_-XHOKB7XHQS", WorkspaceLocatorImpl.minimize("abc!@#$%^&*()[]{}|česky™"));
        assertEquals("h-PKYGNQW7EX", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblah"));
        assertEquals("X-B4K3CPB6GP", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahX"));
        assertEquals("Y-ZGH2VVOGO2", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXY"));
        assertEquals("Z-I7NHG3VUEW", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXYZ"));
        assertEquals("W-LRVIZHY37B", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXYZW"));
        assertEquals("V-KLYOGWEJOD", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXYZWV"));
        assertEquals("U-OSF24EPB4C", WorkspaceLocatorImpl.minimize("blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahXYZWVU"));
    }

    @Issue({"JENKINS-34564", "JENKINS-38837", "JENKINS-2111"})
    @Test
    public void locate() throws Exception {
        assertEquals("${JENKINS_HOME}/workspace/${ITEM_FULL_NAME}", r.jenkins.getRawWorkspaceDir());
        MultiBranchImpl stuff = r.createProject(MultiBranchImpl.class, "stuff");
        stuff.getSourcesList().add(new BranchSource(new SingleSCMSource("dev/flow", new NullSCM())));
        stuff.scheduleBuild2(0).getFuture().get();
        r.waitUntilNoActivity();
        showComputation(stuff);
        FreeStyleProject master = r.jenkins.getItemByFullName("stuff/dev%2Fflow", FreeStyleProject.class);
        assertNotNull(master);
        assertEquals(r.jenkins.getRootPath().child("workspace/stuff_dev_flow"), r.jenkins.getWorkspaceFor(master));
        DumbSlave slave = r.createOnlineSlave();
        assertEquals(slave.getWorkspaceRoot().child("stuff_dev_flow"), slave.getWorkspaceFor(master));
        FreeStyleProject unrelated = r.createFreeStyleProject("100's of problems");
        assertEquals(r.jenkins.getRootPath().child("workspace/100's of problems"), r.jenkins.getWorkspaceFor(unrelated));
        // Checking other values of workspaceDir.
        Field workspaceDir = Jenkins.class.getDeclaredField("workspaceDir"); // currently settable only by Jenkins.doConfigSubmit
        workspaceDir.setAccessible(true);
        // Poor historical default, and as per JENKINS-21942 even possible after some startup scenarios:
        workspaceDir.set(r.jenkins, "${ITEM_ROOTDIR}/workspace");
        assertEquals("JENKINS-34564 inactive in this case", new FilePath(master.getRootDir()).child("workspace"), r.jenkins.getWorkspaceFor(master));
        // JENKINS-38837: customized root.
        workspaceDir.set(r.jenkins, "${JENKINS_HOME}/ws/${ITEM_FULLNAME}");
        assertEquals("ITEM_FULLNAME interpreted a little differently", r.jenkins.getRootPath().child("ws/stuff_dev_flow"), r.jenkins.getWorkspaceFor(master));
    }

    @Issue({"JENKINS-2111", "JENKINS-41068"})
    @Test
    public void delete() throws Exception {
        MultiBranchImpl p = r.createProject(MultiBranchImpl.class, "p");
        p.getSourcesList().add(new BranchSource(new SingleSCMSource("master", new NullSCM())));
        BranchSource pr1Source = new BranchSource(new SingleSCMSource("PR-1", new NullSCM()));
        p.getSourcesList().add(pr1Source);
        p.scheduleBuild2(0).getFuture().get();
        r.waitUntilNoActivity();
        showComputation(p);
        FreeStyleProject master = r.jenkins.getItemByFullName("p/master", FreeStyleProject.class);
        assertNotNull(master);
        FreeStyleProject pr1 = r.jenkins.getItemByFullName("p/PR-1", FreeStyleProject.class);
        assertNotNull(pr1);
        assertEquals(r.jenkins.getRootPath().child("workspace/p_master"), r.jenkins.getWorkspaceFor(master));
        assertEquals(r.jenkins.getRootPath().child("workspace/p_PR-1"), r.jenkins.getWorkspaceFor(pr1));
        // Do builds on an agent too.
        DumbSlave slave = r.createOnlineSlave();
        assertEquals(slave.getWorkspaceRoot().child("p_master"), slave.getWorkspaceFor(master));
        assertEquals(slave.getWorkspaceRoot().child("p_PR-1"), slave.getWorkspaceFor(pr1));
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
        WorkspaceLocatorImpl.Deleter.waitForTasksToFinish();
        assertEquals(Collections.singletonList(master), r.jenkins.getAllItems(FreeStyleProject.class));
        assertEquals(Collections.singletonList(r.jenkins.getRootPath().child("workspace/p_master")), r.jenkins.getRootPath().child("workspace").listDirectories());
        assertEquals(Collections.singletonList(slave.getWorkspaceRoot().child("p_master")), slave.getWorkspaceRoot().listDirectories());
        assertFalse(pr1Root.isDirectory());
    }

    @Issue("JENKINS-2111")
    @Test
    public void deleteOffline() throws Exception {
        assumeFalse("TODO: Fails on Windows CI runs", Functions.isWindows() && System.getenv("CI") != null);
        WorkspaceLocatorImpl.MODE = WorkspaceLocatorImpl.Mode.ENABLED;
        FreeStyleProject p = r.createFreeStyleProject("a'b");
        DumbSlave s = r.createSlave("remote", null, null);
        p.setAssignedNode(s);
        assertEquals(s, r.buildAndAssertSuccess(p).getBuiltOn());
        assertEquals(Collections.singletonList("a_b"), s.getWorkspaceRoot().listDirectories().stream().map(FilePath::getName).collect(Collectors.toList()));
        s.getWorkspaceRoot().child(WorkspaceLocatorImpl.INDEX_FILE_NAME).copyTo(System.out);
        s.toComputer().disconnect(null);
        p.delete();
        s = (DumbSlave) r.jenkins.getNode("remote");
        s.toComputer().connect(true).get();
        assertEquals(Collections.emptyList(), s.getWorkspaceRoot().listDirectories());
        s.toComputer().getLogText().writeLogTo(0, System.out);
    }

    @Issue({"JENKINS-2111", "JENKINS-58177"})
    @Test
    public void uniquification() throws Exception {
        WorkspaceLocatorImpl.MODE = WorkspaceLocatorImpl.Mode.ENABLED;
        assertEquals("a_b", r.buildAndAssertSuccess(r.createFreeStyleProject("a'b")).getWorkspace().getName());
        assertEquals("a_b_2", r.buildAndAssertSuccess(r.createFreeStyleProject("a(b")).getWorkspace().getName());
        assertEquals("ch_to_fit_in_a_short_path_at_all", r.buildAndAssertSuccess(r.createFreeStyleProject("way too much to fit in a short path at all")).getWorkspace().getName());
        assertEquals("_to_fit_in_a_short_path_at_all_2", r.buildAndAssertSuccess(r.createFreeStyleProject("really way too much to fit in a short path at all")).getWorkspace().getName());
        assertEquals("_to_fit_in_a_short_path_at_all_3", r.buildAndAssertSuccess(r.createFreeStyleProject("way, way, way too much to fit in a short path at all")).getWorkspace().getName());
        assertEquals("_-__that_would_start_with_hyphen", r.buildAndAssertSuccess(r.createFreeStyleProject("mnemonic--__that_would_start_with_hyphen")).getWorkspace().getName());
        r.jenkins.getRootPath().child("workspace/" + WorkspaceLocatorImpl.INDEX_FILE_NAME).copyTo(System.out);
    }

    @Issue("JENKINS-2111")
    @Test
    public void move() throws Exception {
        WorkspaceLocatorImpl.MODE = WorkspaceLocatorImpl.Mode.ENABLED;
        FreeStyleProject p = r.createFreeStyleProject("old");
        DumbSlave s = r.createSlave("remote", null, null);
        p.setAssignedNode(s);
        FilePath workspace = r.buildAndAssertSuccess(p).getWorkspace();
        assertEquals("old", workspace.getName());
        assertEquals(Collections.singletonList("old"), s.getWorkspaceRoot().listDirectories().stream().map(FilePath::getName).collect(Collectors.toList()));
        workspace.child("something").write("", null);
        p.renameTo("new");
        WorkspaceLocatorImpl.Deleter.waitForTasksToFinish();
        assertEquals(Collections.singletonList("new"), s.getWorkspaceRoot().listDirectories().stream().map(FilePath::getName).collect(Collectors.toList()));
        workspace = r.buildAndAssertSuccess(p).getWorkspace();
        assertEquals("new", workspace.getName());
        assertTrue(workspace.child("something").exists());
        s.getWorkspaceRoot().child(WorkspaceLocatorImpl.INDEX_FILE_NAME).copyTo(System.out);
        // Note that we do not try to do anything for offline workspaces when a project is moved:
        // that is treated like a delete and recreate.
    }

    @Issue("JENKINS-54640")
    @Test
    public void collisions() throws Exception {
        WorkspaceLocatorImpl.MODE = WorkspaceLocatorImpl.Mode.ENABLED;
        FilePath firstWs = r.buildAndAssertSuccess(r.createFreeStyleProject("first-project-with-a-rather-long-name")).getWorkspace();
        assertEquals("_project-with-a-rather-long-name", firstWs.getName());
        firstWs.deleteRecursive();
        assertEquals("roject-with-a-rather-long-name_2", r.buildAndAssertSuccess(r.createFreeStyleProject("second-project-with-a-rather-long-name")).getWorkspace().getName());
    }

    @Issue({"JENKINS-54654", "JENKINS-54968"})
    @Test
    public void getWorkspaceRoot() throws Exception {
        File top = tmp.getRoot();
        Field workspaceDir = Jenkins.class.getDeclaredField("workspaceDir");
        workspaceDir.setAccessible(true);
        workspaceDir.set(r.jenkins, "${ITEM_ROOTDIR}/workspace");
        assertNull("old default", WorkspaceLocatorImpl.getWorkspaceRoot(r.jenkins));
        workspaceDir.set(r.jenkins, "${JENKINS_HOME}/workspace/${ITEM_FULL_NAME}");
        assertEquals("new default", r.jenkins.getRootPath().child("workspace"), WorkspaceLocatorImpl.getWorkspaceRoot(r.jenkins));
        workspaceDir.set(r.jenkins, "${JENKINS_HOME}/somewhere/else/${ITEM_FULLNAME}");
        assertEquals("something else using ${JENKINS_HOME} and also deprecated ${ITEM_FULLNAME}", r.jenkins.getRootPath().child("somewhere/else"), WorkspaceLocatorImpl.getWorkspaceRoot(r.jenkins));
        workspaceDir.set(r.jenkins, top + File.separator + "${ITEM_FULL_NAME}");
        assertEquals("different location altogether", new FilePath(top), WorkspaceLocatorImpl.getWorkspaceRoot(r.jenkins));
        workspaceDir.set(r.jenkins, top + File.separator + "${ITEM_FULL_NAME}" + File.separator);
        assertEquals("different location altogether (with slash)", new FilePath(top), WorkspaceLocatorImpl.getWorkspaceRoot(r.jenkins));
    }

}
