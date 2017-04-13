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

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.FilePath;
import hudson.model.FreeStyleProject;
import hudson.scm.NullSCM;
import hudson.slaves.DumbSlave;
import hudson.slaves.WorkspaceList;
import java.io.File;
import java.util.Collections;
import static jenkins.branch.NoTriggerBranchPropertyTest.showComputation;
import jenkins.branch.harness.MultiBranchImpl;
import jenkins.scm.impl.SingleSCMSource;
import org.junit.Test;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.*;

public class WorkspaceLocatorImplTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @WithoutJenkins
    @Test
    public void minimize() {
        WorkspaceLocatorImpl.PATH_MAX = 80;
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
        
        // Reset back to 80 for other tests that assume it is 80.
        WorkspaceLocatorImpl.PATH_MAX = 80;
    }

    @Issue("JENKINS-34564")
    @Test
    public void locate() throws Exception {
        MultiBranchImpl stuff = r.createProject(MultiBranchImpl.class, "stuff");
        stuff.getSourcesList().add(new BranchSource(new SingleSCMSource(null, "dev/flow", new NullSCM())));
        stuff.scheduleBuild2(0).getFuture().get();
        r.waitUntilNoActivity();
        showComputation(stuff);
        FreeStyleProject master = r.jenkins.getItemByFullName("stuff/dev%2Fflow", FreeStyleProject.class);
        assertNotNull(master);
        assertEquals(r.jenkins.getRootPath().child("workspace/stuff_dev_flow-L5GKER67QGVMJ2UD3JCSGKEV2ACON2O4VO4RNUZ27HGUY32SYVXQ"), r.jenkins.getWorkspaceFor(master));
        DumbSlave slave = r.createOnlineSlave();
        assertEquals(slave.getWorkspaceRoot().child("stuff_dev_flow-L5GKER67QGVMJ2UD3JCSGKEV2ACON2O4VO4RNUZ27HGUY32SYVXQ"), slave.getWorkspaceFor(master));
        FreeStyleProject unrelated = r.createFreeStyleProject("100% crazy");
        assertEquals(r.jenkins.getRootPath().child("workspace/100% crazy"), r.jenkins.getWorkspaceFor(unrelated));
    }

    @Issue("JENKINS-38837")
    @Test
    public void locateCustom() throws Exception {
        TemporaryDirectoryAllocator tmp = new TemporaryDirectoryAllocator();
        try {
            File workspaces = tmp.allocate();
            WorkspaceLocatorImpl.JenkinsWorkspaceBaseDirConfiguration.get().setPath(workspaces.getAbsolutePath());
            MultiBranchImpl stuff = r.createProject(MultiBranchImpl.class, "stuff");
            stuff.getSourcesList().add(new BranchSource(new SingleSCMSource(null, "dev/flow", new NullSCM())));
            stuff.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            showComputation(stuff);
            FreeStyleProject master = r.jenkins.getItemByFullName("stuff/dev%2Fflow", FreeStyleProject.class);
            assertNotNull(master);
            final String expectedName = "stuff_dev_flow-L5GKER67QGVMJ2UD3JCSGKEV2ACON2O4VO4RNUZ27HGUY32SYVXQ";
            assertEquals(new FilePath(new File(workspaces, expectedName)), r.jenkins.getWorkspaceFor(master));

            FreeStyleProject unrelated = r.createFreeStyleProject("100% crazy");
            assertEquals(r.jenkins.getRootPath().child("workspace/100% crazy"), r.jenkins.getWorkspaceFor(unrelated));
        } finally {
            tmp.dispose();
        }
    }

    @Issue("JENKINS-38837")
    @Test
    public void locateCustomRelative() throws Exception {
        WorkspaceLocatorImpl.JenkinsWorkspaceBaseDirConfiguration.get().setPath("multipass");
        MultiBranchImpl stuff = r.createProject(MultiBranchImpl.class, "stuff");
        stuff.getSourcesList().add(new BranchSource(new SingleSCMSource(null, "dev/flow", new NullSCM())));
        stuff.scheduleBuild2(0).getFuture().get();
        r.waitUntilNoActivity();
        showComputation(stuff);
        FreeStyleProject master = r.jenkins.getItemByFullName("stuff/dev%2Fflow", FreeStyleProject.class);
        assertNotNull(master);
        assertEquals(r.jenkins.getRootPath().child("multipass/stuff_dev_flow-L5GKER67QGVMJ2UD3JCSGKEV2ACON2O4VO4RNUZ27HGUY32SYVXQ"), r.jenkins.getWorkspaceFor(master));
        FreeStyleProject unrelated = r.createFreeStyleProject("100% crazy");
        assertEquals(r.jenkins.getRootPath().child("workspace/100% crazy"), r.jenkins.getWorkspaceFor(unrelated));
    }

    @Issue("JENKINS-38837")
    @Test
    public void globalConfigRoundtrip() throws Exception {
        assertFalse("workspace should not be created by default", r.jenkins.getRootPath().child("workspace").exists());
        r.configRoundtrip();
        assertEquals(r.jenkins.getRootPath().child("workspace"), WorkspaceLocatorImpl.JenkinsWorkspaceBaseDirConfiguration.get().getFilePath());
        assertTrue(WorkspaceLocatorImpl.JenkinsWorkspaceBaseDirConfiguration.get().getFilePath().exists());

        WorkspaceLocatorImpl.JenkinsWorkspaceBaseDirConfiguration.get().setPath("multipass");
        final HtmlPage config = r.createWebClient().goTo("configure");
        assertThat(config.getBody().asText(), containsString(Messages.JenkinsWorkspaceBaseDirConfiguration_PathDoesNotExist("multipass")));

        r.configRoundtrip();
        assertEquals(r.jenkins.getRootPath().child("multipass"), WorkspaceLocatorImpl.JenkinsWorkspaceBaseDirConfiguration.get().getFilePath());

        TemporaryDirectoryAllocator tmp = new TemporaryDirectoryAllocator();
        try {
            File path = tmp.allocate();
            WorkspaceLocatorImpl.JenkinsWorkspaceBaseDirConfiguration.get().setPath(path.getAbsolutePath());
            r.configRoundtrip();
            assertEquals(new FilePath(path), WorkspaceLocatorImpl.JenkinsWorkspaceBaseDirConfiguration.get().getFilePath());
            path = tmp.allocate();
            assertTrue("Could not mark dir readonly", path.setReadOnly());
            WorkspaceLocatorImpl.JenkinsWorkspaceBaseDirConfiguration.get().setPath(path.getAbsolutePath());
            try {
                r.configRoundtrip();
                fail("The configuration should have failed.");
            } catch (FailingHttpStatusCodeException e) {
                assertThat(e.getResponse().getContentAsString(), containsString("MultiBranch workspace write test failed: " + path.getAbsolutePath()));
            }
            assertTrue(path.setWritable(true));

        } finally {
            tmp.dispose();
        }
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
        assertEquals(r.jenkins.getRootPath().child("workspace/p_master-NFABYX74Y6QHVCY2OKHXKUN4SSHQIWYYSJW7JE3FM65W5M5OSXMA"), r.jenkins.getWorkspaceFor(master));
        assertEquals(r.jenkins.getRootPath().child("workspace/p_PR-1-4FMSDR3M7ZFZIJ2EAYSJ4FQ5NYFE7ONABCE3ULVB2MF75EK665PA"), r.jenkins.getWorkspaceFor(pr1));
        // Do builds on an agent too.
        DumbSlave slave = r.createOnlineSlave();
        assertEquals(slave.getWorkspaceRoot().child("p_master-NFABYX74Y6QHVCY2OKHXKUN4SSHQIWYYSJW7JE3FM65W5M5OSXMA"), slave.getWorkspaceFor(master));
        assertEquals(slave.getWorkspaceRoot().child("p_PR-1-4FMSDR3M7ZFZIJ2EAYSJ4FQ5NYFE7ONABCE3ULVB2MF75EK665PA"), slave.getWorkspaceFor(pr1));
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
        assertEquals(Collections.singletonList(master), r.jenkins.getAllItems(FreeStyleProject.class));
        assertEquals(Collections.singletonList(r.jenkins.getRootPath().child("workspace/p_master-NFABYX74Y6QHVCY2OKHXKUN4SSHQIWYYSJW7JE3FM65W5M5OSXMA")), r.jenkins.getRootPath().child("workspace").listDirectories());
        assertEquals(Collections.singletonList(slave.getWorkspaceRoot().child("p_master-NFABYX74Y6QHVCY2OKHXKUN4SSHQIWYYSJW7JE3FM65W5M5OSXMA")), slave.getWorkspaceRoot().listDirectories());
        assertFalse(pr1Root.isDirectory());
    }

}
