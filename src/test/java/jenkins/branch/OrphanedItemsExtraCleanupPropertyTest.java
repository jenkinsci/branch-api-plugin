/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import integration.harness.BasicMultiBranchProject;
import jenkins.branch.harness.BranchProperty;
import jenkins.branch.harness.MultiBranchImpl;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMDiscoverChangeRequests;
import jenkins.scm.impl.mock.MockSCMDiscoverTags;
import jenkins.scm.impl.mock.MockSCMNavigator;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;

import static jenkins.branch.NoTriggerBranchPropertyTest.showComputation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OrphanedItemsExtraCleanupPropertyTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

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
        sampleRepo.git("checkout", "-b", "anotherrelease", "master");
        sampleRepo.write("server", "thecorp.com");
        sampleRepo.git("add", "server");
        sampleRepo.git("commit", "--all", "--message=anotherrelease");
        MultiBranchImpl stuff = r.jenkins.createProject(MultiBranchImpl.class, "stuff");
        stuff.getProperties().add(new OrphanedItemsExtraCleanupProperty("1h"));
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(Collections.singletonList(new BranchDiscoveryTrait()));
        BranchSource branchSource = new BranchSource(source);
        stuff.getSourcesList().add(branchSource);
        stuff = r.configRoundtrip(stuff);
        stuff.scheduleBuild2(0).getFuture().get();
        r.waitUntilNoActivity();
        showComputation(stuff);
        FreeStyleProject master = r.jenkins.getItemByFullName("stuff/master", FreeStyleProject.class);
        assertNotNull(master);
        FreeStyleProject release = r.jenkins.getItemByFullName("stuff/release", FreeStyleProject.class);
        assertNotNull(release);
        FreeStyleProject newfeature = r.jenkins.getItemByFullName("stuff/newfeature", FreeStyleProject.class);
        assertNotNull(newfeature);
        FreeStyleProject anotherrelease = r.jenkins.getItemByFullName("stuff/anotherrelease", FreeStyleProject.class);
        assertNotNull(anotherrelease);

        BranchProjectFactory<FreeStyleProject, FreeStyleBuild> factory = stuff.getProjectFactory();
        assertTrue(factory.isProject(newfeature));
        assertTrue(factory.isProject(anotherrelease));

        BranchProperty bp = newfeature.removeProperty(BranchProperty.class);
        //There are multiple branch properties in the project, unclear why.
        while (newfeature.removeProperty(BranchProperty.class) != null) { System.out.println("More to remove"); }
        factory.setBranch(newfeature, new Branch.Dead(bp.getBranch()));
        bp = anotherrelease.removeProperty(BranchProperty.class);
        while (anotherrelease.removeProperty(BranchProperty.class) != null) { System.out.println("More to remove"); }
        factory.setBranch(anotherrelease, new Branch.Dead(bp.getBranch()));

        OrphanedItemsExtraCleanupProperty property = stuff.getProperties().get(OrphanedItemsExtraCleanupProperty.class);
        Jenkins.get().getQueue().schedule(property,
                0, new CauseAction(new Cause.UserIdCause()));

        r.waitUntilNoActivity();
        System.out.println("---%<--- " + property.getCleaning().getUrl());
        property.getCleaning().writeWholeLogTo(System.out);
        System.out.println("---%<--- ");

        newfeature = r.jenkins.getItemByFullName("stuff/newfeature", FreeStyleProject.class);
        assertNull(newfeature);
        anotherrelease = r.jenkins.getItemByFullName("stuff/anotherrelease", FreeStyleProject.class);
        assertNull(anotherrelease);
    }

    @Test
    public void orgFolder() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("stuff");
            c.createBranch("stuff", "m");
            OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
            top.getProjectFactories().add(new OrganizationFolderTest.MockFactory());
            top.getNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches(), new MockSCMDiscoverTags(), new MockSCMDiscoverChangeRequests()));
            top.getProperties().add(new OrphanedItemsExtraCleanupProperty.OrganizationChildProperty("5m"));
            top.scheduleBuild(0);
            r.waitUntilNoActivity();
            showComputation(top);
            MultiBranchProject<?, ?> stuff = top.getItem("stuff");
            assertThat("Top level has been scanned", stuff, notNullValue());
            OrphanedItemsExtraCleanupProperty property = stuff.getProperties().get(OrphanedItemsExtraCleanupProperty.class);
            assertThat("Property has been created", property, notNullValue());
            assertEquals(property.getInterval(), "5m");
        }
    }
}
