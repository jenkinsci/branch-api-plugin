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
import java.util.Collections;
import static jenkins.branch.NoTriggerBranchPropertyTest.showComputation;
import jenkins.branch.harness.MultiBranchImpl;
import jenkins.scm.impl.SingleSCMSource;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

public class WorkspaceLocatorImplTest {
    
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @WithoutJenkins
    @Test
    public void minimize() {
        assertEquals("stuff_dev_flow-X0yiR9.BqsTqg9pFIyiV0ATm6dyruRbTOvnNTG9SxW8", WorkspaceLocatorImpl.minimize("stuff/dev%2Fflow"));
        assertEquals("some_longish_name_here_master-ftMhCUghiC48kxZS4Zw6.QohYNA35xl611Xy3HUNe.M", WorkspaceLocatorImpl.minimize("some longish name here/master"));
        assertEquals("o_much_to_fit_in_a_short_path_at_all-cH_mwM17JKzXFOunEJLQFV6iV4B5V.S2Wi1AC3bM6K0", WorkspaceLocatorImpl.minimize("really way too much to fit in a short path at all"));
        assertEquals("LIQOQHEADljz_nbflhsfliafdzbclllhdfla-Uj_ZXj._0tE7db2I1CxWsNPBcPjy0saTTJ0QT8iGAxQ", WorkspaceLocatorImpl.minimize("abc!@#$%^&*()[]{}|hdjlaiuehiuehbn,znxbbLUHLHULIQOQHEADljz,nbflhsfliafdzbclllhdfla"));
        assertEquals("llhdflaabcd_hdjlaiuehiuehbn_znxbbLUH-ahHH_tuKAYgION2gQVf2YnyMDSDX306fkceD914r_b0", WorkspaceLocatorImpl.minimize("LHULIQOQHEADljz,nbflhsfliafdzbclllhdflaabcd!@#$%^&*()[]{}|hdjlaiuehiuehbn,znxbbLUH"));
        assertEquals("lhdflaabcde_hdjlaiuehiuehbn_znxbbLUH-uPkP5l4k9eSBkVcNaracGXcXUnPfgADiTa7PVyWXa0w", WorkspaceLocatorImpl.minimize("LHULIQOQHEADljz,nbflhsfliafdzbclllhdflaabcde!@#$%^&*()[]{}|hdjlaiuehiuehbn,znxbbLUH"));
        assertEquals("hdflaabcdef_hdjlaiuehiuehbn_znxbbLUH-q8tDA_HG4zGluLyc80K9po3VXYHxIETkXoZqI0.lDDE", WorkspaceLocatorImpl.minimize("LHULIQOQHEADljz,nbflhsfliafdzbclllhdflaabcdef!@#$%^&*()[]{}|hdjlaiuehiuehbn,znxbbLUH"));
        assertEquals("dflaabcdefg_hdjlaiuehiuehbn_znxbbLUH-F00a2zf8qKtFgFO3WdCsvgWzY9SCgmjjnNJuLfbSans", WorkspaceLocatorImpl.minimize("LHULIQOQHEADljz,nbflhsfliafdzbclllhdflaabcdefg!@#$%^&*()[]{}|hdjlaiuehiuehbn,znxbbLUH"));
        assertEquals("a_b_c_d-oy2E86mHig3a7JI3gjl1h1_pFxZuvFiQUZFDQ1B6_jo", WorkspaceLocatorImpl.minimize("a/b/c/d"));
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
        assertEquals(r.jenkins.getRootPath().child("workspace/stuff_dev_flow-X0yiR9.BqsTqg9pFIyiV0ATm6dyruRbTOvnNTG9SxW8"), r.jenkins.getWorkspaceFor(master));
        DumbSlave slave = r.createOnlineSlave();
        assertEquals(slave.getWorkspaceRoot().child("stuff_dev_flow-X0yiR9.BqsTqg9pFIyiV0ATm6dyruRbTOvnNTG9SxW8"), slave.getWorkspaceFor(master));
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
        assertEquals(r.jenkins.getRootPath().child("workspace/p_master-aUAcX_zHoHqLGnKPdVG8lI8EWxiSbfSTZWe7brOuldg"), r.jenkins.getWorkspaceFor(master));
        assertEquals(r.jenkins.getRootPath().child("workspace/p_PR-1-4Vkhx2z.S5QnRAYknhYdbgpPuaAIibouodML_pFe914"), r.jenkins.getWorkspaceFor(pr1));
        // Do builds on an agent too.
        DumbSlave slave = r.createOnlineSlave();
        assertEquals(slave.getWorkspaceRoot().child("p_master-aUAcX_zHoHqLGnKPdVG8lI8EWxiSbfSTZWe7brOuldg"), slave.getWorkspaceFor(master));
        assertEquals(slave.getWorkspaceRoot().child("p_PR-1-4Vkhx2z.S5QnRAYknhYdbgpPuaAIibouodML_pFe914"), slave.getWorkspaceFor(pr1));
        master.setAssignedNode(slave);
        assertEquals(2, r.buildAndAssertSuccess(master).getNumber());
        pr1.setAssignedNode(slave);
        assertEquals(2, r.buildAndAssertSuccess(pr1).getNumber());
        // Now delete PR-1 and make sure its workspaces are deleted too.
        p.getSourcesList().remove(pr1Source);
        p.scheduleBuild2(0).getFuture().get();
        r.waitUntilNoActivity();
        showComputation(p);
        assertEquals(Collections.singletonList(master), r.jenkins.getAllItems(FreeStyleProject.class));
        assertEquals(Collections.singletonList(r.jenkins.getRootPath().child("workspace/p_master-aUAcX_zHoHqLGnKPdVG8lI8EWxiSbfSTZWe7brOuldg")), r.jenkins.getRootPath().child("workspace").listDirectories());
        assertEquals(Collections.singletonList(slave.getWorkspaceRoot().child("p_master-aUAcX_zHoHqLGnKPdVG8lI8EWxiSbfSTZWe7brOuldg")), slave.getWorkspaceRoot().listDirectories());
    }

}
