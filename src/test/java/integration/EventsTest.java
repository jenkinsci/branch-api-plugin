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

import com.cloudbees.hudson.plugins.folder.computed.DefaultOrphanedItemStrategy;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.AbortException;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.TopLevelItem;
import integration.harness.BasicMultiBranchProject;
import integration.harness.BasicMultiBranchProjectFactory;
import integration.harness.BasicSCMSourceCriteria;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import jenkins.branch.Branch;
import jenkins.branch.BranchBuildStrategy;
import jenkins.branch.BranchBuildStrategyDescriptor;
import jenkins.branch.BranchSource;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.OrganizationFolder;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMEvents;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceEvent;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import jenkins.scm.impl.mock.MockFailure;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMHeadEvent;
import jenkins.scm.impl.mock.MockSCMNavigator;
import jenkins.scm.impl.mock.MockSCMSource;
import jenkins.scm.impl.mock.MockSCMSourceEvent;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class EventsTest {

    /**
     * A branch name that contains a slash
     */
    private static final String SLASHY_BRANCH_NAME = "feature/ux-1";
    /**
     * The mangled name of {@link #SLASHY_BRANCH_NAME}
     */
    private static final String SLASHY_JOB_NAME = "feature-ux-1.0gorp7";
    /**
     * A branch name with unicode characters that have a two canonical forms (Korean for New Features).
     * There are two normalize forms: {@code "\uc0c8\ub85c\uc6b4 \ud2b9\uc9d5"} and
     * {@code "\u1109\u1162\u1105\u1169\u110b\u116e\u11ab \u1110\u1173\u11a8\u110c\u1175\u11bc"}
     */
    private static final String I18N_BRANCH_NAME = "\uc0c8\ub85c\uc6b4 \ud2b9\uc9d5";
    /**
     * The mangled name of {@link #I18N_BRANCH_NAME}
     */
    private static final String I18N_JOB_NAME = "0_c0_c8_b8_5.ns0v4p._d2_b9_c9_d5";
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
    public void given_multibranch_when_inspectingProjectFactory_then_ProjectTypeCorrectlyInferred() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            assertThat(prj.getProjectFactory().getProjectClass(), equalTo((Class)FreeStyleProject.class));
        }
    }

    @Test
    public void given_multibranch_when_inspectingProject_then_ProjectTypeCorrectlyInferred() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            assertThat(prj.getProjectClass(), equalTo((Class)FreeStyleProject.class));
        }
    }

    @Test
    public void given_multibranch_when_noSources_then_noBranchProjects() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            assertThat("No sources means no items",
                    prj.getItems(), is((Collection<FreeStyleProject>) Collections.<FreeStyleProject>emptyList()));
            assertThat("Not buildable without sources", prj.scheduleBuild2(0), nullValue());
        }
    }

    @Test
    public void given_multibranch_when_addingCriteriaWithoutSources_then_noBranchProjects() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            assertThat("No sources means no items",
                    prj.getItems(), is((Collection<FreeStyleProject>) Collections.<FreeStyleProject>emptyList()));

            prj.setCriteria(null);
            assertThat("Changing the criteria doesn't affect the items when we have none",
                    prj.getItems(), is((Collection<FreeStyleProject>) Collections.<FreeStyleProject>emptyList()));
            assertThat("No sources means no items",
                    prj.getItems(), is((Collection<FreeStyleProject>) Collections.<FreeStyleProject>emptyList()));
            assertThat("Not buildable without sources", prj.scheduleBuild2(0), nullValue());
        }
    }

    @Test
    public void given_multibranch_when_addingSources_then_noIndexingIsTriggered() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            r.waitUntilNoActivity();
            assertThat("Adding sources doesn't trigger indexing",
                    prj.getItems(), is((Collection<FreeStyleProject>) Collections.<FreeStyleProject>emptyList()));
        }
    }

    @Test
    public void given_multibranchWithSources_when_indexing_then_branchesAreFoundAndBuilt() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("We now have branches",
                    prj.getItems(), not(is((Collection<FreeStyleProject>) Collections.<FreeStyleProject>emptyList())));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We now have the master branch", master, notNullValue());
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    @Issue("JENKINS-41255")
    public void given_multibranchWithSource_when_replacingSource_then_noBuildStorm() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            // set the first list
            prj.setSourcesList(Collections.singletonList(new BranchSource(new MockSCMSource("firstId", c, "foo", true, false, false))));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("We now have branches",
                    prj.getItems(), not(is((Collection<FreeStyleProject>) Collections.<FreeStyleProject>emptyList())));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We now have the master branch", master, notNullValue());
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            Branch branch = prj.getProjectFactory().getBranch(master);
            assertThat("The branch source is the configured source", branch.getSourceId(), is("firstId"));
            prj.setSourcesList(
                    Collections.singletonList(new BranchSource(new MockSCMSource("secondId", c, "foo", true, false, false))));
            Branch updated = prj.getProjectFactory().getBranch(master);
            assertThat("The branch source is new configured source", updated.getSourceId(), is("secondId"));
            prj.scheduleBuild2(0).getFuture().get();
            assertThat("The master branch was not rebuilt", master.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    @Issue("JENKINS-41255")
    public void given_multibranchWithSources_when_replacingSources_then_noBuildStorm() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            Integer id = c.openChangeRequest("foo", "master");
            c.createRepository("bar");
            c.createBranch("bar", "fun");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            // set the first list
            prj.setSourcesList(Arrays.asList(
                    new BranchSource(new MockSCMSource("firstId", c, "foo", true, false, false)),
                    new BranchSource(new MockSCMSource("secondId", c, "foo", false, false, true)),
                    new BranchSource(new MockSCMSource("thirdId", c, "bar", true, false, false))
            ));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("We now have branches",
                    prj.getItems(), not(is((Collection<FreeStyleProject>) Collections.<FreeStyleProject>emptyList())));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We now have the master branch", master, notNullValue());
            FreeStyleProject change = prj.getItem("CR-" + id);
            assertThat("We now have the change request", change, notNullValue());
            FreeStyleProject fun = prj.getItem("fun");
            assertThat("We now have the fun branch", fun, notNullValue());
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The change request was built", change.getLastBuild(), notNullValue());
            assertThat("The change request was built", change.getLastBuild().getNumber(), is(1));
            assertThat("The fun branch was built", fun.getLastBuild(), notNullValue());
            assertThat("The fun branch was built", fun.getLastBuild().getNumber(), is(1));
            assertThat("The master branch source is the configured source",
                    prj.getProjectFactory().getBranch(master).getSourceId(), is("firstId"));
            assertThat("The change request source is the configured source",
                    prj.getProjectFactory().getBranch(change).getSourceId(), is("secondId"));
            assertThat("The fun branch source is the configured source",
                    prj.getProjectFactory().getBranch(fun).getSourceId(), is("thirdId"));
            prj.setSourcesList(Arrays.asList(
                    new BranchSource(new MockSCMSource("idFirst", c, "foo", true, false, false)),
                    new BranchSource(new MockSCMSource("idThird", c, "bar", true, false, false)),
                    new BranchSource(new MockSCMSource("idSecond", c, "foo", false, false, true))
            ));
            Branch updated = prj.getProjectFactory().getBranch(master);
            assertThat("The master branch source is new configured source",
                    prj.getProjectFactory().getBranch(master).getSourceId(), is("idFirst"));
            assertThat("The change request source is new configured source",
                    prj.getProjectFactory().getBranch(change).getSourceId(), is("idSecond"));
            assertThat("The fun branch source is new configured source",
                    prj.getProjectFactory().getBranch(fun).getSourceId(), is("idThird"));
            prj.scheduleBuild2(0).getFuture().get();
            assertThat("The master branch was not rebuilt", master.getLastBuild().getNumber(), is(1));
            assertThat("The change request was not rebuilt", change.getLastBuild().getNumber(), is(1));
            assertThat("The fun branch was not rebuilt", master.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    public void given_multibranchWithSourcesWantingBranchesOnly_when_indexing_then_onlyBranchesAreFoundAndBuilt()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createTag("foo", "master", "master-1.0");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("We now have projects",
                    prj.getItems(), not(empty()));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We have master branch", master, notNullValue());
            FreeStyleProject tag = prj.getItem("master-1.0");
            assertThat("We have no master-1.0 tag", tag, nullValue());
            FreeStyleProject cr = prj.getItem("CR-" + crNum);
            assertThat("We have no change request", cr, nullValue());
            assertThat("We have branch but no tags or change requests",
                    prj.getItems(), containsInAnyOrder(master));
            r.waitUntilNoActivity();
            assertThat("The branch was built", master.getLastBuild(), notNullValue());
            assertThat("The branch was built", master.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    public void given_multibranchWithSourcesWantingTagsOnly_when_indexing_then_onlyTagsAreFoundAndBuilt()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createTag("foo", "master", "master-1.0");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", false, true, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("We now have projects",
                    prj.getItems(), not(Matchers.<FreeStyleProject>empty()));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We have no master branch", master, nullValue());
            FreeStyleProject tag = prj.getItem("master-1.0");
            assertThat("We have master-1.0 tag", tag, notNullValue());
            FreeStyleProject cr = prj.getItem("CR-" + crNum);
            assertThat("We have no change request", cr, nullValue());
            assertThat("We have change request but no tags or branches",
                    prj.getItems(), containsInAnyOrder(tag));
            r.waitUntilNoActivity();
            assertThat("The tag was not built", tag.getLastBuild(), nullValue());
        }
    }

    @Test
    public void
    given_multibranchWithSourcesWantingChangeRequestsOnly_when_indexing_then_onlyChangeRequestsAreFoundAndBuilt()

            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createTag("foo", "master", "master-1.0");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", false, false, true)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("We now have projects",
                    prj.getItems(), not(Matchers.<FreeStyleProject>empty()));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We have no master branch", master, nullValue());
            FreeStyleProject tag = prj.getItem("master-1.0");
            assertThat("We have no master-1.0 tag", tag, nullValue());
            FreeStyleProject cr = prj.getItem("CR-" + crNum);
            assertThat("We now have the change request", cr, notNullValue());
            assertThat("We have change request but no tags or branches",
                    prj.getItems(), containsInAnyOrder(cr));
            r.waitUntilNoActivity();
            assertThat("The change request was built", cr.getLastBuild(), notNullValue());
            assertThat("The change request was built", cr.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    public void given_multibranchWithSourcesWantingBranchesAndTags_when_indexing_then_branchesAndTagsAreFoundAndBuilt()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createTag("foo", "master", "master-1.0");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, true, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("We now have projects",
                    prj.getItems(), not(Matchers.<FreeStyleProject>empty()));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We now have the master branch", master, notNullValue());
            FreeStyleProject tag = prj.getItem("master-1.0");
            assertThat("We now have the master-1.0 tag", tag, notNullValue());
            FreeStyleProject cr = prj.getItem("CR-" + crNum);
            assertThat("We have no change request", cr, nullValue());
            assertThat("We have tags and branches but no change request",
                    prj.getItems(), containsInAnyOrder(master, tag));
            r.waitUntilNoActivity();
            assertThat("The branch was built", master.getLastBuild(), notNullValue());
            assertThat("The branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The tag was not built", tag.getLastBuild(), nullValue());
        }
    }

    @Test
    public void given_indexedMultibranch_when_indexingFails_then_previouslyIndexedBranchesAreNotDeleted()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createTag("foo", "master", "master-1.0");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, true, true)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            FreeStyleProject tag = prj.getItem("master-1.0");
            FreeStyleProject cr = prj.getItem("CR-" + crNum);
            assertThat("We have tags and branches and change request",
                    prj.getItems(), containsInAnyOrder(master, tag, cr));
            r.waitUntilNoActivity();
            c.addFault(new MockFailure() {
                @Override
                public void check(@CheckForNull String repository, @CheckForNull String branchOrCR,
                                  @CheckForNull String revision, boolean actions)
                        throws IOException {
                    if (!actions && "foo".equals(repository)) {
                        throw new AbortException("FAULT");
                    }
                }
            });
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getComputation().getResult(), is(Result.FAILURE));
            assertThat("We have tags and branches and change request",
                    prj.getItems(), containsInAnyOrder(master, tag, cr));
        }
    }

    @Test
    public void given_multibranchWithSourcesWantingEverything_when_indexing_then_everythingIsFoundAndBuilt()

            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createTag("foo", "master", "master-1.0");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, true, true)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("We now have projects",
                    prj.getItems(), not(Matchers.<FreeStyleProject>empty()));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We have master branch", master, notNullValue());
            FreeStyleProject tag = prj.getItem("master-1.0");
            assertThat("We have master-1.0 tag", tag, notNullValue());
            FreeStyleProject cr = prj.getItem("CR-" + crNum);
            assertThat("We now have the change request", cr, notNullValue());
            assertThat("We have change request but no tags or branches",
                    prj.getItems(), containsInAnyOrder(cr, master, tag));
            r.waitUntilNoActivity();
            assertThat("The branch was built", master.getLastBuild(), notNullValue());
            assertThat("The branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The tag was not built", tag.getLastBuild(), nullValue());
            assertThat("The change request was built", cr.getLastBuild(), notNullValue());
            assertThat("The change request was built", cr.getLastBuild().getNumber(), is(1));
        }
    }

    public static class BuildEverythingStrategyImpl extends BranchBuildStrategy {
        @Override
        public boolean isAutomaticBuild(SCMSource source, SCMHead head) {
            return true;
        }

        @TestExtension(
                "given_multibranchWithSourcesWantingEverythingAndBuildingTags_when_indexing_then_everythingIsFoundAndBuiltEvenTags")
        public static class DescriptorImpl extends BranchBuildStrategyDescriptor {

        }
    }

    @Test
    public void given_multibranchWithSourcesWantingEverythingAndBuildingTags_when_indexing_then_everythingIsFoundAndBuiltEvenTags()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createTag("foo", "master", "master-1.0");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            BranchSource source = new BranchSource(new MockSCMSource(null, c, "foo", true, true, true));
            source.setBuildStrategies(Arrays.<BranchBuildStrategy>asList(new BuildEverythingStrategyImpl()));
            prj.getSourcesList().add(source);
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("We now have projects",
                    prj.getItems(), not(Matchers.<FreeStyleProject>empty()));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We have master branch", master, notNullValue());
            FreeStyleProject tag = prj.getItem("master-1.0");
            assertThat("We have master-1.0 tag", tag, notNullValue());
            FreeStyleProject cr = prj.getItem("CR-" + crNum);
            assertThat("We now have the change request", cr, notNullValue());
            assertThat("We have change request but no tags or branches",
                    prj.getItems(), containsInAnyOrder(cr, master, tag));
            r.waitUntilNoActivity();
            assertThat("The branch was built", master.getLastBuild(), notNullValue());
            assertThat("The branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The tag was built", tag.getLastBuild(), notNullValue());
            assertThat("The tag was built", tag.getLastBuild().getNumber(), is(1));
            assertThat("The change request was built", cr.getLastBuild(), notNullValue());
            assertThat("The change request was built", cr.getLastBuild().getNumber(), is(1));
        }
    }

    public static class BuildChangeRequestsStrategyImpl extends BranchBuildStrategy {
        @Override
        public boolean isAutomaticBuild(SCMSource source, SCMHead head) {
            return head instanceof ChangeRequestSCMHead;
        }

        @TestExtension(
                "given_multibranchWithSourcesWantingEverythingAndBuildingCRs_when_indexing_then_everythingIsFoundAndOnlyCRsBuilt")
        public static class DescriptorImpl extends BranchBuildStrategyDescriptor {

        }
    }

    @Test
    public void given_multibranchWithSourcesWantingEverythingAndBuildingCRs_when_indexing_then_everythingIsFoundAndOnlyCRsBuilt()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createTag("foo", "master", "master-1.0");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            BranchSource source = new BranchSource(new MockSCMSource(null, c, "foo", true, true, true));
            source.setBuildStrategies(Arrays.<BranchBuildStrategy>asList(new BuildChangeRequestsStrategyImpl()));
            prj.getSourcesList().add(source);
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("We now have projects",
                    prj.getItems(), not(Matchers.<FreeStyleProject>empty()));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We have master branch", master, notNullValue());
            FreeStyleProject tag = prj.getItem("master-1.0");
            assertThat("We have master-1.0 tag", tag, notNullValue());
            FreeStyleProject cr = prj.getItem("CR-" + crNum);
            assertThat("We now have the change request", cr, notNullValue());
            assertThat("We have change request but no tags or branches",
                    prj.getItems(), containsInAnyOrder(cr, master, tag));
            r.waitUntilNoActivity();
            assertThat("The branch was not built", master.getLastBuild(), nullValue());
            assertThat("The tag was not built", tag.getLastBuild(), nullValue());
            assertThat("The change request was built", cr.getLastBuild(), notNullValue());
            assertThat("The change request was built", cr.getLastBuild().getNumber(), is(1));
        }
    }


    @Test
    public void given_multibranchWithSources_when_matchingEvent_then_branchesAreFoundAndBuilt() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
            assertThat("We now have branches",
                    prj.getItems(), not(is((Collection<FreeStyleProject>) Collections.<FreeStyleProject>emptyList())));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We now have the master branch", master, notNullValue());
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    public void given_multibranchWithSlashySources_when_matchingEvent_then_branchesAreFoundAndBuilt() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", SLASHY_BRANCH_NAME);
            c.deleteBranch("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", SLASHY_BRANCH_NAME, "junkHash"));
            assertThat("We now have branches",
                    prj.getItems(), not(is((Collection<FreeStyleProject>) Collections.<FreeStyleProject>emptyList())));
            FreeStyleProject branch = prj.getItem(SLASHY_JOB_NAME);
            assertThat("We now have the "+SLASHY_BRANCH_NAME+" branch", branch, notNullValue());
            assertThat("We now have the "+SLASHY_BRANCH_NAME+" branch", branch.getName(), is(SLASHY_JOB_NAME));
            assertThat("We now have the "+SLASHY_BRANCH_NAME+" branch", branch.getDisplayName(), is(SLASHY_BRANCH_NAME));
            r.waitUntilNoActivity();
            assertThat("The "+SLASHY_BRANCH_NAME+" branch was built", branch.getLastBuild(), notNullValue());
            assertThat("The " + SLASHY_BRANCH_NAME + " branch was built", branch.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    public void given_multibranchWithI18nSources_when_matchingEvent_then_branchesAreFoundAndBuilt() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", I18N_BRANCH_NAME);
            c.deleteBranch("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", I18N_BRANCH_NAME, "junkHash"));
            assertThat("We now have branches",
                    prj.getItems(), not(is((Collection<FreeStyleProject>) Collections.<FreeStyleProject>emptyList())));
            FreeStyleProject branch = prj.getItem(I18N_JOB_NAME);
            assertThat("We now have the "+ I18N_BRANCH_NAME+" branch", branch, notNullValue());
            assertThat("We now have the "+ I18N_BRANCH_NAME+" branch", branch.getName(), is(I18N_JOB_NAME));
            assertThat("We now have the "+ I18N_BRANCH_NAME+"branch", branch.getDisplayName(), is(I18N_BRANCH_NAME));
            r.waitUntilNoActivity();
            assertThat("The master branch was built", branch.getLastBuild(), notNullValue());
            assertThat("The master branch was built", branch.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    public void given_multibranchWithSources_when_matchingEventWithMatchingRevision_then_branchesAreBuilt() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "foo", "master", c.getRevision("foo", "master")));
            FreeStyleProject master = prj.getItem("master");
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            c.addFile("foo", "master", "adding file", "file", new byte[0]);
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "master", c.getRevision("foo", "master")));
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(2));
        }
    }

    @Test
    public void given_multibranchWithSlashySources_when_matchingEventWithMatchingRevision_then_branchesAreBuilt() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", SLASHY_BRANCH_NAME);
            c.deleteBranch("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "foo", SLASHY_BRANCH_NAME, c.getRevision("foo",
                    SLASHY_BRANCH_NAME)));
            FreeStyleProject branch = prj.getItem(SLASHY_JOB_NAME);
            r.waitUntilNoActivity();
            assertThat("The "+SLASHY_BRANCH_NAME+" branch was built", branch.getLastBuild().getNumber(), is(1));
            c.addFile("foo", SLASHY_BRANCH_NAME, "adding file", "file", new byte[0]);
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo",
                    SLASHY_BRANCH_NAME, c.getRevision("foo", SLASHY_BRANCH_NAME)));
            r.waitUntilNoActivity();
            assertThat("The " + SLASHY_BRANCH_NAME + " branch was built", branch.getLastBuild(), notNullValue());
            assertThat("The " + SLASHY_BRANCH_NAME + " branch was built", branch.getLastBuild().getNumber(), is(2));
        }
    }

    @Test
    public void given_multibranchWithI18nSources_when_matchingEventWithMatchingRevision_then_branchesAreBuilt() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", I18N_BRANCH_NAME);
            c.deleteBranch("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "foo", I18N_BRANCH_NAME, c.getRevision("foo",
                    I18N_BRANCH_NAME)));
            FreeStyleProject branch = prj.getItem(I18N_JOB_NAME);
            r.waitUntilNoActivity();
            assertThat("The " + I18N_BRANCH_NAME + " branch was built", branch.getLastBuild().getNumber(), is(1));
            c.addFile("foo", I18N_BRANCH_NAME, "adding file", "file", new byte[0]);
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo",
                    I18N_BRANCH_NAME, c.getRevision("foo", I18N_BRANCH_NAME)));
            r.waitUntilNoActivity();
            assertThat("The " + I18N_BRANCH_NAME + " branch was built", branch.getLastBuild(), notNullValue());
            assertThat("The " + I18N_BRANCH_NAME + " branch was built", branch.getLastBuild().getNumber(), is(2));
        }
    }

    @Test
    public void given_multibranchWithSources_when_matchingEventWithMatchingPreviousRevision_then_branchesAreNotBuilt() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "foo", "master", c.getRevision("foo", "master")));
            FreeStyleProject master = prj.getItem("master");
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            String revision = c.getRevision("foo", "master");
            c.addFile("foo", "master", "adding file", "file", new byte[0]);
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "master", revision));
            r.waitUntilNoActivity();
            assertThat("The master branch was not built", master.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    public void given_multibranchWithSlashySources_when_matchingEventWithMatchingPreviousRevision_then_branchesAreNotBuilt() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", SLASHY_BRANCH_NAME);
            c.deleteBranch("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "foo", SLASHY_BRANCH_NAME, c.getRevision("foo", SLASHY_BRANCH_NAME)));
            FreeStyleProject master = prj.getItem(SLASHY_JOB_NAME);
            r.waitUntilNoActivity();
            assertThat("The branch was built", master.getLastBuild().getNumber(), is(1));
            String revision = c.getRevision("foo", SLASHY_BRANCH_NAME);
            c.addFile("foo", SLASHY_BRANCH_NAME, "adding file", "file", new byte[0]);
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", SLASHY_BRANCH_NAME, revision));
            r.waitUntilNoActivity();
            assertThat("The branch was not built", master.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    public void given_multibranchWithI18nSources_when_matchingEventWithMatchingPreviousRevision_then_branchesAreNotBuilt() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", I18N_BRANCH_NAME);
            c.deleteBranch("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "foo", I18N_BRANCH_NAME, c.getRevision("foo", I18N_BRANCH_NAME)));
            FreeStyleProject master = prj.getItem(I18N_JOB_NAME);
            r.waitUntilNoActivity();
            assertThat("The branch was built", master.getLastBuild().getNumber(), is(1));
            String revision = c.getRevision("foo", I18N_BRANCH_NAME);
            c.addFile("foo", I18N_BRANCH_NAME, "adding file", "file", new byte[0]);
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", I18N_BRANCH_NAME, revision));
            r.waitUntilNoActivity();
            assertThat("The branch was not built", master.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    public void given_multibranchWithSources_when_createEventForExistingBranch_then_eventIgnored() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            FreeStyleProject master = prj.getItem("master");
            r.waitUntilNoActivity();
            // we have not received any events so far, so in fact this should be 0L
            long lastModified = prj.getComputation().getEventsFile().lastModified();
            // we add a file so that the create event can contain a legitimate new revision
            // but the branch is one that we already have, so the event should be ignored by this project
            // and never even hit the events log
            c.addFile("foo", "master", "adding file", "file", new byte[0]);

            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "foo", "master", c.getRevision("foo", "master")));
            r.waitUntilNoActivity();
            assertThat("The master branch was not built", master.getLastBuild().getNumber(), is(1));
            assertThat(prj.getComputation().getEventsFile().lastModified(), is(lastModified));
        }
    }

    @Test
    public void given_multibranchWithSources_when_nonMatchingEvent_then_branchesAreNotFoundAndBuilt() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            assertThat("Adding sources doesn't trigger indexing",
                    prj.getItems(), is((Collection<FreeStyleProject>) Collections.<FreeStyleProject>emptyList()));
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "fork", "junkHash"));
            assertThat("Events only trigger the branch they mention",
                    prj.getItems(), is((Collection<FreeStyleProject>) Collections.<FreeStyleProject>emptyList()));
        }
    }

    @Test
    public void given_multibranchWithSources_when_branchChangeEvent_then_branchFromEventIsRebuilt() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", "feature");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("We now have branches",
                    prj.getItems(), not(is((Collection<FreeStyleProject>) Collections.<FreeStyleProject>emptyList())));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We now have the master branch", master, notNullValue());
            FreeStyleProject feature = prj.getItem("feature");
            assertThat("We now have the feature branch", master, notNullValue());
            assertThat("We only have the master and feature branches",
                    prj.getItems(), containsInAnyOrder(master, feature));
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature branch was built", feature.getLastBuild(), notNullValue());
            assertThat("The feature branch was built", feature.getLastBuild().getNumber(), is(1));

            c.addFile("foo", "master", "Adding README.md", "README.md", "This is the readme".getBytes());
            c.addFile("foo", "feature", "Adding README.adoc", "README.adoc", "This is the readme".getBytes());
            r.waitUntilNoActivity();
            assertThat("No events means no new builds", master.getLastBuild().getNumber(), is(1));

            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
            assertThat("The master branch was built from event", master.getLastBuild().getNumber(), is(2));
            assertThat("The feature branch was not built", feature.getLastBuild().getNumber(), is(1));

            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "feature", "junkHash"));
            assertThat("The master branch was not built", master.getLastBuild().getNumber(), is(2));
            assertThat("The feature branch was built from event", feature.getLastBuild().getNumber(), is(2));
        }
    }

    @Test
    public void given_multibranchWithSources_when_lateBranchChangeEvent_then_branchIsNotRebuilt() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", "feature");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            FreeStyleProject feature = prj.getItem("feature");
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature branch was built", feature.getLastBuild(), notNullValue());
            assertThat("The feature branch was built", feature.getLastBuild().getNumber(), is(1));

            c.addFile("foo", "master", "Adding README.md", "README.md", "This is the readme".getBytes());
            c.addFile("foo", "feature", "Adding README.adoc", "README.adoc", "This is the readme".getBytes());

            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
            assertThat("The master branch was built from event", master.getLastBuild().getNumber(), is(2));
            assertThat("The feature branch was not built", feature.getLastBuild().getNumber(), is(1));

            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "feature", "junkHash"));
            assertThat("The master branch was not built", master.getLastBuild().getNumber(), is(2));
            assertThat("The feature branch was built from event", feature.getLastBuild().getNumber(), is(2));

            // Now fire events that match but verification will show no change, so no rebuilt

            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
            assertThat("The master branch was not rebuilt", master.getLastBuild().getNumber(), is(2));
            assertThat("The feature branch was not built", feature.getLastBuild().getNumber(), is(2));

            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "feature", "junkHash"));
            assertThat("The master branch was not built", master.getLastBuild().getNumber(), is(2));
            assertThat("The feature branch was not rebuilt", feature.getLastBuild().getNumber(), is(2));
        }
    }

    @Test
    public void given_multibranchWithSourcesAndTwoBranches_when_indexing_then_deadBranchIsDeleted() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", "feature");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            FreeStyleProject feature = prj.getItem("feature");
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature branch was built", feature.getLastBuild(), notNullValue());
            assertThat("The feature branch was built", feature.getLastBuild().getNumber(), is(1));

            c.deleteBranch("foo", "feature");

            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("Indexing applies dead branch cleanup",
                    prj.getItems(), containsInAnyOrder(master));

        }
    }

    @Test
    public void
    given_multibranchWithSourcesAndSomeRetentionOfDeadBranches_when_indexing_then_oldestDeadBranchIsDeleted()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", "feature-1");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            prj.setOrphanedItemStrategy(new DefaultOrphanedItemStrategy(true, null, "1"));
            prj.scheduleBuild2(0).getFuture().get();
            // eed to force the build time stamps
            r.waitUntilNoActivity();
            c.createBranch("foo", "feature-2");
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            c.createBranch("foo", "feature-3");
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            FreeStyleProject feature1 = prj.getItem("feature-1");
            FreeStyleProject feature2 = prj.getItem("feature-2");
            FreeStyleProject feature3 = prj.getItem("feature-3");
            assertThat("We have all three branches",
                    prj.getItems(), containsInAnyOrder(master, feature1, feature2, feature3));
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature-1 branch was built", feature1.getLastBuild(), notNullValue());
            assertThat("The feature-1 branch was built", feature1.getLastBuild().getNumber(), is(1));
            assertThat("The feature-2 branch was built", feature2.getLastBuild(), notNullValue());
            assertThat("The feature-2 branch was built", feature2.getLastBuild().getNumber(), is(1));

            c.deleteBranch("foo", "feature-1");

            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("Strategy allows one branch to remain",
                    prj.getItems(), containsInAnyOrder(master, feature1, feature2, feature3));
            assertThat("Dead branches not deleted per retention strategy", feature1.isDisabled(), is(true));

            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("Strategy allows one branch to remain",
                    prj.getItems(), containsInAnyOrder(master, feature1, feature2, feature3));
            assertThat("Dead branches not deleted per retention strategy", feature1.isDisabled(), is(true));

            c.deleteBranch("foo", "feature-3");

            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("Dead branches not deleted per retention strategy", feature3.isDisabled(), is(true));
            assertThat("Strategy allows one branch to remain",
                    prj.getItems(), containsInAnyOrder(master, feature2, feature3));

            c.deleteBranch("foo", "feature-2");

            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("Strategy allows one newest branch to remain",
                    prj.getItems(), containsInAnyOrder(master, feature3));

        }
    }

    @Test
    public void given_multibranchWithSourcesAndTwoBranches_when_eventForBranchRemoval_then_branchIsDisabled()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", "feature");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            FreeStyleProject feature = prj.getItem("feature");
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature branch was built", feature.getLastBuild(), notNullValue());
            assertThat("The feature branch was built", feature.getLastBuild().getNumber(), is(1));

            fire(new MockSCMHeadEvent(SCMEvent.Type.REMOVED, c, "foo", "feature", "junkHash"));
            assertThat("Events only validate the rumoured change", feature.isDisabled(), is(false));

            c.deleteBranch("foo", "feature");
            fire(new MockSCMHeadEvent(SCMEvent.Type.REMOVED, c, "foo", "feature", "junkHash"));
            assertThat("Events do not delete items", prj.getItem("feature"), is(feature));
            assertThat("Events only validate the rumoured change", feature.isDisabled(), is(true));
        }
    }

    @Test
    public void given_multibranchWithSourcesAndOneDeadBranches_when_indexing_then_branchIsDeleted() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", "feature");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            FreeStyleProject feature = prj.getItem("feature");
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature branch was built", feature.getLastBuild(), notNullValue());
            assertThat("The feature branch was built", feature.getLastBuild().getNumber(), is(1));

            fire(new MockSCMHeadEvent(SCMEvent.Type.REMOVED, c, "foo", "feature", "junkHash"));

            c.deleteBranch("foo", "feature");
            fire(new MockSCMHeadEvent(SCMEvent.Type.REMOVED, c, "foo", "feature", "junkHash"));

            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("Indexing applies dead branch cleanup",
                    prj.getItems(), is((Collection<FreeStyleProject>) Collections.singletonList(master)));
        }
    }

    @Test
    public void given_multibranchWithSourcesAndCriteria_when_indexingAndNoMatchingBranches_then_noProjectsCreated()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(new BasicSCMSourceCriteria("marker.txt"));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();

            assertThat("No branches matching criteria means no items",
                    prj.getItems(), Matchers.<FreeStyleProject>empty());
        }
    }

    @Test
    public void
    given_multibranchWithSourcesCriteriaAndNoMatchingBranches_when_eventDoesntAddMatch_then_noProjectsCreated()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(new BasicSCMSourceCriteria("marker.txt"));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));

            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
            assertThat("Events only validate the rumoured change",
                    prj.getItems(), Matchers.<FreeStyleProject>empty());

            c.addFile("foo", "master", "Adding README.md", "README.md", "This is the readme".getBytes());
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
            assertThat("The criteria must be met to create the branch job",
                    prj.getItems(), Matchers.<FreeStyleProject>empty());

        }
    }

    @Test
    public void given_multibranchWithSourcesCriteriaAndNoMatchingBranches_when_eventAddsMatch_then_projectsCreated()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(new BasicSCMSourceCriteria("marker.txt"));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));

            c.addFile("foo", "master", "adding marker file", "marker.txt", "This is the marker".getBytes());
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
            assertThat("We now have branches",
                    prj.getItems(), not(is((Collection<FreeStyleProject>) Collections.<FreeStyleProject>emptyList())));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We now have the master branch", master, notNullValue());
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    public void given_multibranchWithSourcesCriteriaAndMatchingBranches_when_eventAddsMatch_then_projectsCreated()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(new BasicSCMSourceCriteria("marker.txt"));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));

            c.addFile("foo", "master", "adding marker file", "marker.txt", "This is the marker".getBytes());
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
            FreeStyleProject master = prj.getItem("master");
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));

            c.cloneBranch("foo", "master", "feature-1");
            assertThat("No new branches without indexing or event",
                    prj.getItems(), is((Collection<FreeStyleProject>) Collections.singletonList(master)));

            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "foo", "does-not-exist", "junkHash"));
            assertThat("Events only validate the rumoured change",
                    prj.getItems(), is((Collection<FreeStyleProject>) Collections.singletonList(master)));

            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "foo", "feature-1", "junkHash"));
            assertThat("Events add the new branch",
                    prj.getItems(), not(is((Collection<FreeStyleProject>) Collections.singletonList(master))));

            FreeStyleProject feature1 = prj.getItem("feature-1");
            assertThat("We now have the feature-1 branch", feature1, notNullValue());
            r.waitUntilNoActivity();
            assertThat("The feature-1 branch was built", feature1.getLastBuild(), notNullValue());
            assertThat("The feature-1 branch was built", feature1.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    public void given_multibranchWithSourcesCriteriaAndMatchingBranches_when_eventRemoveMatch_then_projectsDisables()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(new BasicSCMSourceCriteria("marker.txt"));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));

            c.addFile("foo", "master", "adding marker file", "marker.txt", "This is the marker".getBytes());
            c.cloneBranch("foo", "master", "feature-1");
            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "foo", "does-not-exist", "junkHash"));
            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "foo", "feature-1", "junkHash"));
            FreeStyleProject feature1 = prj.getItem("feature-1");
            r.waitUntilNoActivity();
            assertThat("The feature-1 branch was built", feature1.getLastBuild(), notNullValue());
            assertThat("The feature-1 branch was built", feature1.getLastBuild().getNumber(), is(1));

            fire(new MockSCMHeadEvent(SCMEvent.Type.REMOVED, c, "foo", "feature-1", "junkHash"));
            assertThat("Events only validate the rumoured change", feature1.isDisabled(), is(false));
            assertThat("The feature-1 branch was not built", feature1.getLastBuild(), notNullValue());
            assertThat("The feature-1 branch was not built", feature1.getLastBuild().getNumber(), is(1));

            c.rmFile("foo", "feature-1", "removing marker file", "marker.txt");
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "feature-1", "junkHash"));
            assertThat("Events do not delete items", prj.getItem("feature-1"), is(feature1));
            assertThat("Events only validate the rumoured change", feature1.isDisabled(), is(true));
            assertThat("The feature-1 branch was not built", feature1.getLastBuild(), notNullValue());
            assertThat("The feature-1 branch was not built", feature1.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    public void given_orgFolder_when_noRepos_then_scanCreatesNoProjects() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            assertThat("No repos means no items",
                    prj.getItems(), Matchers.<MultiBranchProject<?, ?>>empty());

            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("Scheduling a scan makes no difference when we have no repos",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>empty());

        }
    }

    @Test
    public void given_orgFolder_when_noMatchingRepos_then_scanCreatesNoProjects() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");

            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("A scan makes no difference when we have no repos meeting criteria",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>empty());
        }
    }

    @Test
    public void given_orgFolder_when_someReposMatch_then_scanCreatesMatchingProjects() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.addFile("foo", "master", "adding marker", "marker.txt", "A marker".getBytes());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("A scan picks up a newly qualified repo",
                    prj.getItems(),
                    not(is((Collection<MultiBranchProject<?, ?>>) Collections.<MultiBranchProject<?, ?>>emptyList())));
            BasicMultiBranchProject foo = (BasicMultiBranchProject) prj.getItem("foo");
            assertThat("We now have the one project matching", foo, notNullValue());
            assertThat("We now have only the one project matching",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>containsInAnyOrder(foo));
            assertThat("The matching branch exists", foo.getItem("master"), notNullValue());

        }
    }

    @Test
    public void given_orgFolder_when_someReposMatch_then_eventCreatesMatchingProject() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.addFile("foo", "master", "adding marker", "marker.txt", "A marker".getBytes());
            c.addFile("bar", "master", "adding marker", "marker.txt", "A marker".getBytes());
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "bar", "master", "junkHash"));
            BasicMultiBranchProject bar = (BasicMultiBranchProject) prj.getItem("bar");
            assertThat("We now have the second project matching", bar, notNullValue());
            assertThat("We now have only the two projects matching",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>containsInAnyOrder(bar));
            assertThat("The matching branch exists", bar.getItem("master"), notNullValue());
        }
    }

    @Test
    public void given_orgFolder_when_someSlashyReposMatch_then_eventCreatesMatchingProject() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.createBranch("foo", SLASHY_BRANCH_NAME);
            c.deleteBranch("foo", "master");
            c.createBranch("bar", SLASHY_BRANCH_NAME);
            c.deleteBranch("bar", "master");
            c.addFile("foo", SLASHY_BRANCH_NAME, "adding marker", "marker.txt", "A marker".getBytes());
            c.addFile("bar", SLASHY_BRANCH_NAME, "adding marker", "marker.txt", "A marker".getBytes());
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "bar", SLASHY_BRANCH_NAME, "junkHash"));
            BasicMultiBranchProject bar = (BasicMultiBranchProject) prj.getItem("bar");
            assertThat("We now have the second project matching", bar, notNullValue());
            assertThat("We now have only the two projects matching",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>containsInAnyOrder(bar));
            assertThat("The matching branch exists", bar.getItem(SLASHY_JOB_NAME), notNullValue());
        }
    }

    @Test
    public void given_orgFolder_when_someI18nReposMatch_then_eventCreatesMatchingProject() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.createBranch("foo", I18N_BRANCH_NAME);
            c.deleteBranch("foo", "master");
            c.createBranch("bar", I18N_BRANCH_NAME);
            c.deleteBranch("bar", "master");
            c.addFile("foo", I18N_BRANCH_NAME, "adding marker", "marker.txt", "A marker".getBytes());
            c.addFile("bar", I18N_BRANCH_NAME, "adding marker", "marker.txt", "A marker".getBytes());
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "bar", I18N_BRANCH_NAME, "junkHash"));
            BasicMultiBranchProject bar = (BasicMultiBranchProject) prj.getItem("bar");
            assertThat("We now have the second project matching", bar, notNullValue());
            assertThat("We now have only the two projects matching",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>containsInAnyOrder(bar));
            assertThat("The matching branch exists", bar.getItem(I18N_JOB_NAME), notNullValue());
        }
    }

    @Test
    public void given_orgFolder_when_someReposMatch_then_eventsAreValidated() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.addFile("foo", "master", "adding marker", "marker.txt", "A marker".getBytes());
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "bar", "master", "junkHash"));
            assertThat("Events only apply to the branch they refer to and are validated",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>empty());
            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "bar", "master", "junkHash"));
            assertThat("Events only apply to the branch they refer to and are validated",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>empty());
        }
    }

    @Test
    public void given_orgFolder_when_eventUpdatesABranchToAMatch_then_projectIsCreated() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.addFile("foo", "master", "adding marker", "marker.txt", "A marker".getBytes());
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
            BasicMultiBranchProject foo = (BasicMultiBranchProject) prj.getItem("foo");
            assertThat("We now have the one project matching", foo, notNullValue());
            assertThat("We now have only the one project matching",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>containsInAnyOrder(foo));
            assertThat("The matching branch exists", foo.getItem("master"), notNullValue());
        }
    }

    @Test
    public void given_orgFolder_when_eventCreatesABranchWithAMatch_then_projectIsCreated() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.cloneBranch("foo", "master", "feature");
            c.addFile("foo", "feature", "adding marker", "marker.txt", "A marker".getBytes());
            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "foo", "feature", "junkHash"));
            BasicMultiBranchProject foo = (BasicMultiBranchProject) prj.getItem("foo");
            assertThat("We now have the one project matching", foo, notNullValue());
            assertThat("We now have only the one project matching",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>containsInAnyOrder(foo));
            assertThat("The matching branch exists", foo.getItem("feature"), notNullValue());
            assertThat("The non-matching branch does not exists", foo.getItem("master"), nullValue());
        }
    }

    @Test
    public void given_orgFolder_when_eventCreatesASlashyBranchWithAMatch_then_projectIsCreated() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.cloneBranch("foo", "master", SLASHY_BRANCH_NAME);
            c.addFile("foo", SLASHY_BRANCH_NAME, "adding marker", "marker.txt", "A marker".getBytes());
            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "foo", SLASHY_BRANCH_NAME, "junkHash"));
            BasicMultiBranchProject foo = (BasicMultiBranchProject) prj.getItem("foo");
            assertThat("We now have the one project matching", foo, notNullValue());
            assertThat("We now have only the one project matching",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>containsInAnyOrder(foo));
            assertThat("The matching branch exists", foo.getItem(SLASHY_JOB_NAME), notNullValue());
            assertThat("The non-matching branch does not exists", foo.getItem("master"), nullValue());
        }
    }

    @Test
    public void given_orgFolder_when_eventCreatesAI18nBranchWithAMatch_then_projectIsCreated() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.cloneBranch("foo", "master", I18N_BRANCH_NAME);
            c.addFile("foo", I18N_BRANCH_NAME, "adding marker", "marker.txt", "A marker".getBytes());
            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "foo", I18N_BRANCH_NAME, "junkHash"));
            BasicMultiBranchProject foo = (BasicMultiBranchProject) prj.getItem("foo");
            assertThat("We now have the one project matching", foo, notNullValue());
            assertThat("We now have only the one project matching",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>containsInAnyOrder(foo));
            assertThat("The matching branch exists", foo.getItem(I18N_JOB_NAME), notNullValue());
            assertThat("The non-matching branch does not exists", foo.getItem("master"), nullValue());
        }
    }

    @Test
    public void given_orgFolder_when_eventCreatesARepoWithAMatch_then_projectIsCreated() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.cloneBranch("foo", "master", "feature");
            c.addFile("foo", "feature", "adding marker", "marker.txt", "A marker".getBytes());
            fire(new MockSCMSourceEvent(SCMEvent.Type.CREATED, c, "foo"));
            BasicMultiBranchProject foo = (BasicMultiBranchProject) prj.getItem("foo");
            assertThat("We now have the one project matching", foo, notNullValue());
            assertThat("We now have only the one project matching",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>containsInAnyOrder(foo));
            assertThat("The matching branch exists", foo.getItem("feature"), notNullValue());
            assertThat("The non-matching branch does not exists", foo.getItem("master"), nullValue());
        }
    }

    @Test
    public void given_orgFolder_when_eventTouchesADifferentBranchInAnUndiscoveredRepoWithAMatch_then_noProjectIsCreated() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.cloneBranch("foo", "master", "feature");
            c.addFile("foo", "feature", "adding marker", "marker.txt", "A marker".getBytes());
            c.cloneBranch("foo", "feature", "sustaining");
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "master", "ignored"));
            assertThat("Event specified branch does not match",
                    prj.getItem("foo"),
                    nullValue());
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "feature", "ignored"));
            BasicMultiBranchProject foo = (BasicMultiBranchProject) prj.getItem("foo");
            assertThat("We now have the one project matching", foo, notNullValue());
            assertThat("We now have only the one project matching",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>containsInAnyOrder(foo));
            assertThat("The matching branch exists", foo.getItem("feature"), notNullValue());
            assertThat("A full index occurred when adding the repo", foo.getItem("sustaining"), notNullValue());
            assertThat("The non-matching branch does not exists", foo.getItem("master"), nullValue());
        }
    }

    @Test
    public void given_orgFolder_when_eventCreatesARepoWithoutAMatch_then_noProjectIsCreated() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.cloneBranch("foo", "master", "feature");
            fire(new MockSCMSourceEvent(SCMEvent.Type.CREATED, c, "foo"));
            assertThat("No matching branches",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>empty());
        }
    }

    @Test
    public void given_multibranch_when_sourcesManipulatedProgrammatically_then_configureTriggersIndex()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, true, true)));
            assertThat(prj.getItems(),
                    empty()
            );
            r.configRoundtrip(prj);
            r.waitUntilNoActivity();
            assertThat(prj.getItems(),
                    contains(
                            hasProperty("name", is("master"))
                    )
            );
        }
    }

    @Test
    public void given_multibranchWithSourcesAndDeadBranch_when_eventForBranchResurrection_then_branchIsBuilt()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", "feature");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            FreeStyleProject feature = prj.getItem("feature");
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature branch was built", feature.getLastBuild(), notNullValue());
            assertThat("The feature branch was built", feature.getLastBuild().getNumber(), is(1));

            c.deleteBranch("foo", "feature");
            fire(new MockSCMHeadEvent(SCMEvent.Type.REMOVED, c, "foo", "feature", "junkHash"));
            assertThat("Feature branch is disabled", feature.isDisabled(), is(true));
            assertThat("Feature branch is dead", prj.getProjectFactory().getBranch(feature), instanceOf(Branch.Dead.class));

            c.createBranch("foo", "feature");
            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "foo", "feature", "junkHash"));
            assertThat("Feature branch is resurrected", feature.isDisabled(), is(false));
            assertThat("Feature branch is resurrected", prj.getProjectFactory().getBranch(feature),
                    not(instanceOf(Branch.Dead.class)));
            r.waitUntilNoActivity();
            assertThat("The feature branch was rebuilt", feature.getLastBuild().getNumber(), is(2));
        }
    }

    @Test
    public void given_multibranchWithSourcesCriteriaAndDeadBranch_when_eventForBranchResurrection_then_branchIsBuilt()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.addFile("foo", "master", "marker", "marker.txt", "build me".getBytes());
            c.cloneBranch("foo", "master", "feature");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(new BasicSCMSourceCriteria("marker.txt"));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            FreeStyleProject feature = prj.getItem("feature");
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature branch was built", feature.getLastBuild(), notNullValue());
            assertThat("The feature branch was built", feature.getLastBuild().getNumber(), is(1));

            c.rmFile("foo", "feature", "remove marker", "marker.txt");
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "feature", "junkHash"));
            assertThat("Feature branch is disabled", feature.isDisabled(), is(true));

            c.addFile("foo", "feature", "marker", "marker.txt", "build me again".getBytes());
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "feature", "junkHash"));
            assertThat("Feature branch is resurrected", feature.isDisabled(), is(false));
            r.waitUntilNoActivity();
            assertThat("The feature branch was rebuilt", feature.getLastBuild().getNumber(), is(2));
        }
    }

    @Test
    public void given_multibranchWith2Sources_when_eventForBranchOnHigherSource_then_branchTakeover()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createRepository("bar");
            c.cloneBranch("bar", "master", "feature");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource("foo:id", c, "foo", true, false, false)));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource("bar:id", c, "bar", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            assertThat("Master branch is from first source",
                    prj.getProjectFactory().getBranch(master).getSourceId(),
                    is("foo:id"));
            FreeStyleProject feature = prj.getItem("feature");
            assertThat("Feature branch is from second source",
                    prj.getProjectFactory().getBranch(feature).getSourceId(),
                    is("bar:id"));
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature branch was built", feature.getLastBuild(), notNullValue());
            assertThat("The feature branch was built", feature.getLastBuild().getNumber(), is(1));

            c.createBranch("foo", "feature");
            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "foo", "feature", "junkHash"));
            assertThat("Feature branch takeover by higher priority source",
                    prj.getProjectFactory().getBranch(feature).getSourceId(),
                    is("foo:id"));
            r.waitUntilNoActivity();
            assertThat("The feature branch was rebuilt", feature.getLastBuild().getNumber(), is(2));
        }
    }

    @Test
    public void given_multibranchWith2Sources_when_eventForBranchOnLowerSource_then_eventIgnored()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createRepository("bar");
            c.cloneBranch("foo", "master", "feature");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource("foo:id", c, "foo", true, false, false)));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource("bar:id", c, "bar", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            assertThat("Master branch is from first source",
                    prj.getProjectFactory().getBranch(master).getSourceId(),
                    is("foo:id"));
            FreeStyleProject feature = prj.getItem("feature");
            assertThat("Feature branch is from first source",
                    prj.getProjectFactory().getBranch(feature).getSourceId(),
                    is("foo:id"));
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature branch was built", feature.getLastBuild(), notNullValue());
            assertThat("The feature branch was built", feature.getLastBuild().getNumber(), is(1));

            c.cloneBranch("bar", "master", "feature");
            fire(new MockSCMHeadEvent(SCMEvent.Type.CREATED, c, "bar", "feature", "junkHash"));
            assertThat("Feature branch not takenover by lower priority source",
                    prj.getProjectFactory().getBranch(feature).getSourceId(),
                    is("foo:id"));
            r.waitUntilNoActivity();
            assertThat("The feature branch was not rebuilt", feature.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    public void given_multibranchWith2Sources_when_eventForBranchOnHigherSourceOpensTakeover_then_branchDeadUntilLowerEvent()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createRepository("bar");
            c.cloneBranch("foo", "master", "feature");
            c.cloneBranch("bar", "master", "feature");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource("foo:id", c, "foo", true, false, false)));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource("bar:id", c, "bar", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            assertThat("Master branch is from first source",
                    prj.getProjectFactory().getBranch(master).getSourceId(),
                    is("foo:id"));
            FreeStyleProject feature = prj.getItem("feature");
            assertThat("Feature branch is from first source",
                    prj.getProjectFactory().getBranch(feature).getSourceId(),
                    is("foo:id"));
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature branch was built", feature.getLastBuild(), notNullValue());
            assertThat("The feature branch was built", feature.getLastBuild().getNumber(), is(1));

            c.deleteBranch("foo", "feature");
            fire(new MockSCMHeadEvent(SCMEvent.Type.REMOVED, c, "foo", "feature", "junkHash"));
            assertThat("Feature branch is disabled", feature.isDisabled(), is(true));
            assertThat("Feature branch is dead", prj.getProjectFactory().getBranch(feature),
                    instanceOf(Branch.Dead.class));

            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "bar", "feature", "junkHash"));
            assertThat("Feature branch takeover by lower priority source",
                    prj.getProjectFactory().getBranch(feature).getSourceId(),
                    is("bar:id"));
            r.waitUntilNoActivity();
            assertThat("The feature branch was rebuilt", feature.getLastBuild().getNumber(), is(2));
        }
    }

    @Test
    public void given_multibranchWith2SourcesAndCriteria_when_firstSourceDoesntHaveBranchAndSecondSourceHasMatch_then_branchPresent()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createRepository("bar");
            c.addFile("foo", "master", "marker", "marker.txt", "build me".getBytes());
            c.addFile("bar", "master", "marker", "marker.txt", "build me".getBytes());
            c.cloneBranch("bar", "master","feature");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(new BasicSCMSourceCriteria("marker.txt"));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource("foo:id", c, "foo", true, false, false)));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource("bar:id", c, "bar", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            assertThat("Master branch is from first source",
                    prj.getProjectFactory().getBranch(master).getSourceId(),
                    is("foo:id"));
            FreeStyleProject feature = prj.getItem("feature");
            assertThat("Feature branch is from second source",
                    prj.getProjectFactory().getBranch(feature).getSourceId(),
                    is("bar:id"));
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature branch was built", feature.getLastBuild(), notNullValue());
            assertThat("The feature branch was built", feature.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    public void given_multibranchWith2SourcesAndCriteria_when_firstSourceHasBranchWithoutMatchAndSecondSourceHasMatch_then_branchFromSecondSource()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createRepository("bar");
            c.cloneBranch("foo", "master", "feature");
            c.addFile("foo", "master", "marker", "marker.txt", "build me".getBytes());
            c.addFile("bar", "master", "marker", "marker.txt", "build me".getBytes());
            c.cloneBranch("bar", "master", "feature");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(new BasicSCMSourceCriteria("marker.txt"));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource("foo:id", c, "foo", true, false, false)));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource("bar:id", c, "bar", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            assertThat("Master branch is from first source",
                    prj.getProjectFactory().getBranch(master).getSourceId(),
                    is("foo:id"));
            FreeStyleProject feature = prj.getItem("feature");
            assertThat("Feature branch is from second source",
                    prj.getProjectFactory().getBranch(feature).getSourceId(),
                    is("bar:id"));
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature branch was built", feature.getLastBuild(), notNullValue());
            assertThat("The feature branch was built", feature.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    public void given_multibranchWith2SourcesAndCriteria_when_eventForBranchOnHigherSource_then_branchTakeover()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createRepository("bar");
            c.addFile("foo", "master", "marker", "marker.txt", "build me".getBytes());
            c.addFile("bar", "master", "marker", "marker.txt", "build me".getBytes());
            c.cloneBranch("bar", "master", "feature");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(new BasicSCMSourceCriteria("marker.txt"));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource("foo:id", c, "foo", true, false, false)));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource("bar:id", c, "bar", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            assertThat("Master branch is from first source",
                    prj.getProjectFactory().getBranch(master).getSourceId(),
                    is("foo:id"));
            FreeStyleProject feature = prj.getItem("feature");
            assertThat("Feature branch is from second source",
                    prj.getProjectFactory().getBranch(feature).getSourceId(),
                    is("bar:id"));
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature branch was built", feature.getLastBuild(), notNullValue());
            assertThat("The feature branch was built", feature.getLastBuild().getNumber(), is(1));

            c.cloneBranch("foo", "master", "feature");
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "feature", "junkHash"));
            assertThat("Feature branch takeover by higher priority source",
                    prj.getProjectFactory().getBranch(feature).getSourceId(),
                    is("foo:id"));
            r.waitUntilNoActivity();
            assertThat("The feature branch was rebuilt", feature.getLastBuild().getNumber(), is(2));
        }
    }

    @Test
    public void given_multibranchWith2SourcesAndCriteria_when_eventForBranchOnLowerSource_then_eventIgnored()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createRepository("bar");
            c.addFile("foo", "master", "marker", "marker.txt", "build me".getBytes());
            c.cloneBranch("foo", "master", "feature");
            c.addFile("bar", "master", "marker", "marker.txt", "build me".getBytes());
            c.cloneBranch("bar", "master", "feature");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(new BasicSCMSourceCriteria("marker.txt"));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource("foo:id", c, "foo", true, false, false)));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource("bar:id", c, "bar", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            assertThat("Master branch is from first source",
                    prj.getProjectFactory().getBranch(master).getSourceId(),
                    is("foo:id"));
            FreeStyleProject feature = prj.getItem("feature");
            assertThat("Feature branch is from first source",
                    prj.getProjectFactory().getBranch(feature).getSourceId(),
                    is("foo:id"));
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature branch was built", feature.getLastBuild(), notNullValue());
            assertThat("The feature branch was built", feature.getLastBuild().getNumber(), is(1));

            c.addFile("bar", "feature", "change", "marker.txt", "ignore this".getBytes());
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "bar", "feature", "junkHash"));
            assertThat("Feature branch not takenover by lower priority source",
                    prj.getProjectFactory().getBranch(feature).getSourceId(),
                    is("foo:id"));
            r.waitUntilNoActivity();
            assertThat("The feature branch was not rebuilt", feature.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    public void given_multibranchWith2SourcesAndCriteria_when_eventForBranchOnHigherSourceOpensTakeover_then_branchDeadUntilLowerEvent()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createRepository("bar");
            c.addFile("foo", "master", "marker", "marker.txt", "build me".getBytes());
            c.addFile("bar", "master", "marker", "marker.txt", "build me".getBytes());
            c.cloneBranch("foo", "master", "feature");
            c.cloneBranch("bar", "master", "feature");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(new BasicSCMSourceCriteria("marker.txt"));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource("foo:id", c, "foo", true, false, false)));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource("bar:id", c, "bar", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            assertThat("Master branch is from first source",
                    prj.getProjectFactory().getBranch(master).getSourceId(),
                    is("foo:id"));
            FreeStyleProject feature = prj.getItem("feature");
            assertThat("Feature branch is from first source",
                    prj.getProjectFactory().getBranch(feature).getSourceId(),
                    is("foo:id"));
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature branch was built", feature.getLastBuild(), notNullValue());
            assertThat("The feature branch was built", feature.getLastBuild().getNumber(), is(1));

            c.rmFile("foo", "feature", "allow bar to take over", "marker.txt");
            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "foo", "feature", "junkHash"));
            assertThat("Feature branch is disabled", feature.isDisabled(), is(true));
            assertThat("Feature branch is dead", prj.getProjectFactory().getBranch(feature),
                    instanceOf(Branch.Dead.class));

            fire(new MockSCMHeadEvent(SCMEvent.Type.UPDATED, c, "bar", "feature", "junkHash"));
            assertThat("Feature branch takeover by lower priority source",
                    prj.getProjectFactory().getBranch(feature).getSourceId(),
                    is("bar:id"));
            r.waitUntilNoActivity();
            assertThat("The feature branch was rebuilt", feature.getLastBuild().getNumber(), is(2));
        }
    }

    private void fire(MockSCMHeadEvent event) throws Exception {
        long watermark = SCMEvents.getWatermark();
        SCMHeadEvent.fireNow(event);
        SCMEvents.awaitAll(watermark);
        r.waitUntilNoActivity();
    }

    private void fire(MockSCMSourceEvent event) throws Exception {
        long watermark = SCMEvents.getWatermark();
        SCMSourceEvent.fireNow(event);
        SCMEvents.awaitAll(watermark);
        r.waitUntilNoActivity();
    }

    private void dump(MockSCMController c) throws IOException {
        System.out.println("Mock SCM");
        System.out.println("========");
        System.out.println();
        for (String name : c.listRepositories()) {
            System.out.printf("  * %s%n", name);
            System.out.printf("    * Branches%n", name);
            for (String branch : c.listBranches(name)) {
                System.out.printf("      - %s%n", branch);
                MockSCMController.LogEntry e = c.log(name, branch).get(0);
                System.out
                        .printf("          %s %tc %s%n", e.getHash().substring(0, 7), e.getTimestamp(), e.getMessage());
            }
            System.out.printf("    * Tags%n", name);
            for (String tag : c.listTags(name)) {
                System.out.printf("      - %s%n", tag);
                MockSCMController.LogEntry e = c.log(name, tag).get(0);
                System.out
                        .printf("          %s %tc %s%n", e.getHash().substring(0, 7), e.getTimestamp(), e.getMessage());
            }
            System.out.printf("    * Change requests%n", name);
            for (Integer crNum : c.listChangeRequests(name)) {
                System.out.printf("      - #%d%n", crNum);
                MockSCMController.LogEntry e = c.log(name, "change-request/" + crNum).get(0);
                System.out
                        .printf("          %s %tc %s%n", e.getHash().substring(0, 7), e.getTimestamp(), e.getMessage());
            }
        }
    }
}
