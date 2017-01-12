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

import hudson.model.FreeStyleProject;
import hudson.scm.NullSCM;
import hudson.slaves.DumbSlave;
import hudson.slaves.WorkspaceList;
import java.util.Collections;
import static jenkins.branch.NoTriggerBranchPropertyTest.showComputation;
import jenkins.branch.harness.MultiBranchImpl;
import jenkins.scm.impl.SingleSCMSource;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

public class WorkspaceLocatorImplTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @WithoutJenkins
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
    }

    @Issue("JENKINS-34564")
    @Test
    public void locate() throws Exception {
        MultiBranchImpl stuff = r.createProject(MultiBranchImpl.class, "stuff");
        stuff.getSourcesList().add(new BranchSource(new SingleSCMSource(null, "dev/flow", new NullSCM())));
        stuff.scheduleBuild2(0).getFuture().get();
        r.waitUntilNoActivity();
        showComputation(stuff);
        FreeStyleProject master = r.jenkins.getItemByFullName("stuff/"+NameMangler.apply("dev/flow"), FreeStyleProject.class);
        assertNotNull(master);
        assertEquals(r.jenkins.getRootPath().child("workspace/stuff_dev-flow.bn29c1-7W3ZTDHY5QN5BTMYZEFLCFP56UUPM32ZI6RUODUT4HZONWY7MLFA"), r.jenkins.getWorkspaceFor(master));
        DumbSlave slave = r.createOnlineSlave();
        assertEquals(slave.getWorkspaceRoot().child("stuff_dev-flow.bn29c1-7W3ZTDHY5QN5BTMYZEFLCFP56UUPM32ZI6RUODUT4HZONWY7MLFA"), slave.getWorkspaceFor(master));
        FreeStyleProject unrelated = r.createFreeStyleProject("100% crazy");
        assertEquals(r.jenkins.getRootPath().child("workspace/100% crazy"), r.jenkins.getWorkspaceFor(unrelated));
    }

    @Issue("JENKINS-2111")
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
        // Now delete PR-1 and make sure its workspaces are deleted too.
        p.getSourcesList().remove(pr1Source);
        p.scheduleBuild2(0).getFuture().get();
        r.waitUntilNoActivity();
        showComputation(p);
        assertEquals(Collections.singletonList(master), r.jenkins.getAllItems(FreeStyleProject.class));
        assertEquals(Collections.singletonList(r.jenkins.getRootPath().child("workspace/p_master-NFABYX74Y6QHVCY2OKHXKUN4SSHQIWYYSJW7JE3FM65W5M5OSXMA")), r.jenkins.getRootPath().child("workspace").listDirectories());
        assertEquals(Collections.singletonList(slave.getWorkspaceRoot().child("p_master-NFABYX74Y6QHVCY2OKHXKUN4SSHQIWYYSJW7JE3FM65W5M5OSXMA")), slave.getWorkspaceRoot().listDirectories());
    }

}
