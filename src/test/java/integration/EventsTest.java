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
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Functions;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.queue.QueueTaskFuture;
import integration.harness.BasicMultiBranchProject;
import integration.harness.BasicMultiBranchProjectFactory;
import integration.harness.BasicSCMSourceCriteria;
import java.io.IOException;
import java.lang.management.ThreadInfo;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import jenkins.branch.Branch;
import jenkins.branch.BranchBuildStrategy;
import jenkins.branch.BranchBuildStrategyDescriptor;
import jenkins.branch.BranchSource;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.NameEncoder;
import jenkins.branch.OrganizationFolder;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMEvents;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceEvent;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMRevision;
import jenkins.scm.impl.mock.MockChangeRequestFlags;
import jenkins.scm.impl.mock.MockFailure;
import jenkins.scm.impl.mock.MockLatency;
import jenkins.scm.impl.mock.MockRepositoryFlags;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMDiscoverChangeRequests;
import jenkins.scm.impl.mock.MockSCMDiscoverTags;
import jenkins.scm.impl.mock.MockSCMHeadEvent;
import jenkins.scm.impl.mock.MockSCMNavigator;
import jenkins.scm.impl.mock.MockSCMRevision;
import jenkins.scm.impl.mock.MockSCMSource;
import jenkins.scm.impl.mock.MockSCMSourceEvent;
import jenkins.scm.impl.mock.MockSCMSourceSaveListener;
import jenkins.scm.impl.trait.WildcardSCMSourceFilterTrait;
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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;
import org.junit.Ignore;

public class EventsTest {

    /**
     * A branch name that contains a slash
     */
    private static final String SLASHY_BRANCH_NAME = "feature/ux-1";
    /**
     * The encoded name of {@link #SLASHY_BRANCH_NAME}, see {@link NameEncoder#encode(String)}
     */
    private static final String SLASHY_JOB_NAME = "feature%2Fux-1";
    /**
     * A branch name with unicode characters that have a two canonical forms (Korean for New Features).
     * There are two normalize forms: {@code "\uc0c8\ub85c\uc6b4 \ud2b9\uc9d5"} and
     * {@code "\u1109\u1162\u1105\u1169\u110b\u116e\u11ab \u1110\u1173\u11a8\u110c\u1175\u11bc"}
     */
    private static final String I18N_BRANCH_NAME = "\uc0c8\ub85c\uc6b4 \ud2b9\uc9d5";
    /**
     * The encoded name of {@link #I18N_BRANCH_NAME}, see {@link NameEncoder#encode(String)}
     */
    private static final String I18N_JOB_NAME = "\uc0c8\ub85c\uc6b4 \ud2b9\uc9d5";
    /**
     * All tests in this class only create items and do not affect other global configuration, thus we trade test
     * execution time for the restriction on only touching items.
     */
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Before
    public void cleanOutAllItems() throws Exception {
        //Use item map to avoid the permissions check
        for (TopLevelItem i : r.getInstance().getItemMap().values()) {
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
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
            prj.setSourcesList(Collections.singletonList(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches()).withId("firstId"))));
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
                    Collections.singletonList(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches()).withId("secondId"))));
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
                    new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches()).withId("firstId")),
                    new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverChangeRequests()).withId("secondId")),
                    new BranchSource(new MockSCMSource(c, "bar", new MockSCMDiscoverBranches()).withId("thirdId"))
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
                    new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches()).withId("idFirst")),
                    new BranchSource(new MockSCMSource(c, "bar", new MockSCMDiscoverBranches()).withId("idThird")),
                    new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverChangeRequests()).withId("idSecond"))
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverTags())));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverChangeRequests())));
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
    public void
    given_multibranchWithUntrustedChangeRequestBuildStrategy_when_indexing_then_onlyTrustedChangeRequestsAreFoundAndBuilt()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo", MockRepositoryFlags.TRUST_AWARE);
            c.createTag("foo", "master", "master-1.0");
            Integer untrustedCrNum = c.openChangeRequest("foo", "master", MockChangeRequestFlags.UNTRUSTED);
            Integer trustedCrNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            BranchSource branchSource = new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverChangeRequests()));
            branchSource.setBuildStrategies(Collections.singletonList(new BuildTrustedChangeRequestsStrategyImpl()));
            prj.getSourcesList().add(branchSource);
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("We now have projects",
                    prj.getItems(), not(Matchers.<FreeStyleProject>empty()));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We have no master branch", master, nullValue());
            FreeStyleProject tag = prj.getItem("master-1.0");
            assertThat("We have no master-1.0 tag", tag, nullValue());
            FreeStyleProject trustedCr = prj.getItem("CR-" + trustedCrNum);
            FreeStyleProject untrustedCr = prj.getItem("CR-" + untrustedCrNum);
            assertThat("We now have the change request", trustedCr, notNullValue());
            assertThat("We have change request but no tags or branches",
                    prj.getItems(), containsInAnyOrder(trustedCr, untrustedCr));
            r.waitUntilNoActivity();
            assertThat("The trusted change request was built", trustedCr.getLastBuild(), notNullValue());
            assertThat("The change request was built", trustedCr.getLastBuild().getNumber(), is(1));
            assertThat("The untrusted change request was not built", untrustedCr.getLastBuild(), nullValue());
        }
    }

    @Test
    public void given_multibranchWithMergeableChangeRequests_when_indexing_then_mergableChangeRequestsBuilt()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createTag("foo", "master", "master-1.0");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            MockSCMSource source = new MockSCMSource(c, "foo",
                    new MockSCMDiscoverChangeRequests(
                            ChangeRequestCheckoutStrategy.HEAD,
                            ChangeRequestCheckoutStrategy.MERGE
                    )
            );
            prj.getSourcesList().add(new BranchSource(source));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("We now have projects",
                    prj.getItems(), not(Matchers.<FreeStyleProject>empty()));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We have no master branch", master, nullValue());
            FreeStyleProject tag = prj.getItem("master-1.0");
            assertThat("We have no master-1.0 tag", tag, nullValue());
            FreeStyleProject cr = prj.getItem("CR-" + crNum);
            assertThat("We have no plan CR", cr, nullValue());
            FreeStyleProject crMerge = prj.getItem("CR-" + crNum+"-merge");
            assertThat("We now have the merge change request", crMerge, notNullValue());
            FreeStyleProject crHead = prj.getItem("CR-" + crNum+"-head");
            assertThat("We now have the head change request", crHead, notNullValue());
            assertThat("We have change requests but no tags or branches",
                    prj.getItems(), containsInAnyOrder(crMerge, crHead));
            r.waitUntilNoActivity();
            assertThat("The merge change request was built", crMerge.getLastBuild(), notNullValue());
            assertThat("The merge change request was built", crMerge.getLastBuild().getNumber(), is(1));
            assertThat("The head change request was built", crHead.getLastBuild(), notNullValue());
            assertThat("The head change request was built", crHead.getLastBuild().getNumber(), is(1));
        }
    }

    @Test
    public void given_multibranchWithMergeableChangeRequests_when_reindexing_then_mergableChangeRequestsRebuilt()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createTag("foo", "master", "master-1.0");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            MockSCMSource source = new MockSCMSource(c, "foo", new MockSCMDiscoverChangeRequests(
                    ChangeRequestCheckoutStrategy.HEAD, ChangeRequestCheckoutStrategy.MERGE));
            prj.getSourcesList().add(new BranchSource(source));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assumeThat("We now have projects",
                    prj.getItems(), not(Matchers.<FreeStyleProject>empty()));
            FreeStyleProject master = prj.getItem("master");
            assumeThat("We have no master branch", master, nullValue());
            FreeStyleProject tag = prj.getItem("master-1.0");
            assumeThat("We have no master-1.0 tag", tag, nullValue());
            FreeStyleProject cr = prj.getItem("CR-" + crNum);
            assumeThat("We have no plan CR", cr, nullValue());
            FreeStyleProject crMerge = prj.getItem("CR-" + crNum+"-merge");
            assumeThat("We now have the merge change request", crMerge, notNullValue());
            FreeStyleProject crHead = prj.getItem("CR-" + crNum+"-head");
            assumeThat("We now have the head change request", crHead, notNullValue());
            assumeThat("We have change requests but no tags or branches",
                    prj.getItems(), containsInAnyOrder(crMerge, crHead));
            r.waitUntilNoActivity();
            assumeThat("The merge change request was built", crMerge.getLastBuild(), notNullValue());
            assumeThat("The merge change request was built", crMerge.getLastBuild().getNumber(), is(1));
            assumeThat("The head change request was built", crHead.getLastBuild(), notNullValue());
            assumeThat("The head change request was built", crHead.getLastBuild().getNumber(), is(1));
            c.addFile("foo", "master", "change the target", "file.txt", new byte[]{0});
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            master = prj.getItem("master");
            assertThat("We still have no master branch", master, nullValue());
            tag = prj.getItem("master-1.0");
            assertThat("We still have no master-1.0 tag", tag, nullValue());
            cr = prj.getItem("CR-" + crNum);
            assertThat("We still have no plan CR", cr, nullValue());
            crMerge = prj.getItem("CR-" + crNum + "-merge");
            assertThat("We still have the merge change request", crMerge, notNullValue());
            crHead = prj.getItem("CR-" + crNum + "-head");
            assertThat("We still  have the head change request", crHead, notNullValue());
            assertThat("We still have change requests but no tags or branches",
                    prj.getItems(), containsInAnyOrder(crMerge, crHead));
            r.waitUntilNoActivity();
            assertThat("The merge change request was rebuilt", crMerge.getLastBuild().getNumber(), is(2));
            assertThat("The head change request was not rebuilt", crHead.getLastBuild().getNumber(), is(1));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches(), new MockSCMDiscoverTags())));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches(), new MockSCMDiscoverTags(), new MockSCMDiscoverChangeRequests())));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches(),
                    new MockSCMDiscoverTags(), new MockSCMDiscoverChangeRequests())));
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
        public boolean isAutomaticBuild(@NonNull SCMSource source, @NonNull SCMHead head,
                                        @NonNull SCMRevision currRevision,
                                        SCMRevision lastBuiltRevision, SCMRevision lastSeenRevision,
                                        TaskListener listener) {
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
            BranchSource source = new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches(),
                    new MockSCMDiscoverTags(), new MockSCMDiscoverChangeRequests()));
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
        public boolean isAutomaticBuild(@NonNull SCMSource source, @NonNull SCMHead head,
                                        @NonNull SCMRevision currRevision,
                                        SCMRevision lastBuiltRevision, SCMRevision lastSeenRevision,
                                        @NonNull TaskListener listener) {
            return head instanceof ChangeRequestSCMHead;
        }

        @TestExtension(
                "given_multibranchWithSourcesWantingEverythingAndBuildingCRs_when_indexing_then_everythingIsFoundAndOnlyCRsBuilt")
        public static class DescriptorImpl extends BranchBuildStrategyDescriptor {

        }
    }

    public static class BuildTrustedChangeRequestsStrategyImpl extends BranchBuildStrategy {
        @Override
        public boolean isAutomaticBuild(@NonNull SCMSource source, @NonNull SCMHead head,
                                        @NonNull SCMRevision currRevision,
                                        SCMRevision lastBuiltRevision, SCMRevision lastSeenRevision,
                                        @NonNull TaskListener listener) {
            if (head instanceof ChangeRequestSCMHead) {
                try {
                    return currRevision.equals(source.getTrustedRevision(currRevision, listener));
                } catch (IOException | InterruptedException e) {
                    Functions.printThrowable(e);
                }
                return true;
            }
            return false;
        }

        @TestExtension(
                "given_multibranchWithUntrustedChangeRequestBuildStrategy_when_indexing_then_onlyTrustedChangeRequestsAreFoundAndBuilt")
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
            BranchSource source = new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches(),
                    new MockSCMDiscoverTags(), new MockSCMDiscoverChangeRequests()));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", SLASHY_BRANCH_NAME, "junkHash"));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", I18N_BRANCH_NAME, "junkHash"));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", "master", c.getRevision("foo", "master")));
            FreeStyleProject master = prj.getItem("master");
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            c.addFile("foo", "master", "adding file", "file", new byte[0]);
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "master", c.getRevision("foo", "master")));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", SLASHY_BRANCH_NAME, c.getRevision("foo",
                    SLASHY_BRANCH_NAME)));
            FreeStyleProject branch = prj.getItem(SLASHY_JOB_NAME);
            r.waitUntilNoActivity();
            assertThat("The "+SLASHY_BRANCH_NAME+" branch was built", branch.getLastBuild().getNumber(), is(1));
            c.addFile("foo", SLASHY_BRANCH_NAME, "adding file", "file", new byte[0]);
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo",
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", I18N_BRANCH_NAME, c.getRevision("foo",
                    I18N_BRANCH_NAME)));
            FreeStyleProject branch = prj.getItem(I18N_JOB_NAME);
            r.waitUntilNoActivity();
            assertThat("The " + I18N_BRANCH_NAME + " branch was built", branch.getLastBuild().getNumber(), is(1));
            c.addFile("foo", I18N_BRANCH_NAME, "adding file", "file", new byte[0]);
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo",
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", "master", c.getRevision("foo", "master")));
            FreeStyleProject master = prj.getItem("master");
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            String revision = c.getRevision("foo", "master");
            c.addFile("foo", "master", "adding file", "file", new byte[0]);
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "master", revision));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", SLASHY_BRANCH_NAME, c.getRevision("foo", SLASHY_BRANCH_NAME)));
            FreeStyleProject master = prj.getItem(SLASHY_JOB_NAME);
            r.waitUntilNoActivity();
            assertThat("The branch was built", master.getLastBuild().getNumber(), is(1));
            String revision = c.getRevision("foo", SLASHY_BRANCH_NAME);
            c.addFile("foo", SLASHY_BRANCH_NAME, "adding file", "file", new byte[0]);
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", SLASHY_BRANCH_NAME, revision));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", I18N_BRANCH_NAME, c.getRevision("foo", I18N_BRANCH_NAME)));
            FreeStyleProject master = prj.getItem(I18N_JOB_NAME);
            r.waitUntilNoActivity();
            assertThat("The branch was built", master.getLastBuild().getNumber(), is(1));
            String revision = c.getRevision("foo", I18N_BRANCH_NAME);
            c.addFile("foo", I18N_BRANCH_NAME, "adding file", "file", new byte[0]);
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", I18N_BRANCH_NAME, revision));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            prj.scheduleBuild2(0).getFuture().get();
            FreeStyleProject master = prj.getItem("master");
            r.waitUntilNoActivity();
            // we have not received any events so far, so in fact this should be 0L
            long lastModified = prj.getComputation().getEventsFile().lastModified();
            // we add a file so that the create event can contain a legitimate new revision
            // but the branch is one that we already have, so the event should be ignored by this project
            // and never even hit the events log
            c.addFile("foo", "master", "adding file", "file", new byte[0]);

            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", "master", c.getRevision("foo", "master")));
            r.waitUntilNoActivity();
            assertThat("The master branch was not built", master.getLastBuild().getNumber(), is(1));
            assertThat(prj.getComputation().getEventsFile().lastModified(), is(lastModified));
        }
    }

    @Test
    @Issue("JENKINS-42511")
    public void given_multibranchWithSources_when_createEventWhileIndexing_then_onlyOneBuildCreated() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));

            // Now we need some latency, this will ensure that the indexing and the event are interleaved during
            // processing
            c.withLatency(MockLatency.fixed(100, TimeUnit.MILLISECONDS));
            // we have not received any events so far, so in fact this should be 0L
            long lastModified = prj.getComputation().getEventsFile().lastModified();

            QueueTaskFuture<Queue.Executable> future = prj.scheduleBuild2(0).getFuture();
            SCMHeadEvent.fireNow(new MockSCMHeadEvent(
                    "given_multibranchWithSources_when_createEventWhileIndexing_then_onlyOneBuildCreated",
                    SCMEvent.Type.CREATED, c, "foo", "master", c.getRevision("foo", "master")
            ));
            future.get();
            FreeStyleProject master = prj.getItem("master");
            waitUntilNoActivityIgnoringThreadDeathUpTo(10000);

            assertThat(prj.getComputation().getEventsFile().lastModified(), greaterThan(lastModified));
            assertThat("The master branch was built once only", master.getLastBuild().getNumber(), is(1));
            assertThat(master.getLastBuild().getResult(), is(Result.SUCCESS));
        }
    }

    public static class BuildRevisionStrategyImpl extends BranchBuildStrategy {
        private final Set<String> approved;

        public BuildRevisionStrategyImpl(String... approved) {
            this.approved = new HashSet<>(Arrays.asList(approved));
        }

        @Override
        public boolean isAutomaticBuild(@NonNull SCMSource source, @NonNull SCMHead head,
                                        @NonNull SCMRevision currRevision,
                                        SCMRevision lastBuiltRevision, SCMRevision lastSeenRevision,
                                        @NonNull TaskListener listener) {
            return currRevision instanceof MockSCMRevision
                    && approved.contains(((MockSCMRevision) currRevision).getHash());
        }

        @TestExtension(
                "given_multibranchWithRevisionSpecificStrategy_when_indexing_then_everythingIsFoundButMagicRevisionOnlyBuilt")
        public static class DescriptorImpl extends BranchBuildStrategyDescriptor {

        }
    }

    @Test
    @Issue("JENKINS-47308")
    public void
    given_multibranchWithRevisionSpecificStrategy_when_indexing_then_everythingIsFoundButMagicRevisionOnlyBuilt()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createTag("foo", "master", "master-1.0");
            c.cloneBranch("foo", "master","stable");
            c.addFile("foo", "master", "new revision", "dummy.txt", "anything".getBytes(StandardCharsets.UTF_8));
            c.cloneBranch("foo", "master", "development");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            BranchSource source = new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches(),
                    new MockSCMDiscoverTags(), new MockSCMDiscoverChangeRequests()));
            source.setBuildStrategies(Arrays.<BranchBuildStrategy>asList(new BuildRevisionStrategyImpl(c.getRevision("foo", "master"))));
            prj.getSourcesList().add(source);
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("We now have projects",
                    prj.getItems(), not(Matchers.<FreeStyleProject>empty()));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We have master branch", master, notNullValue());
            FreeStyleProject stable = prj.getItem("stable");
            assertThat("We have stable branch", stable, notNullValue());
            FreeStyleProject development = prj.getItem("development");
            assertThat("We have development branch", development, notNullValue());
            FreeStyleProject tag = prj.getItem("master-1.0");
            assertThat("We have master-1.0 tag", tag, notNullValue());
            FreeStyleProject cr = prj.getItem("CR-" + crNum);
            assertThat("We now have the change request", cr, notNullValue());
            assertThat("We have only the expected items",
                    prj.getItems(), containsInAnyOrder(cr, master, stable, development, tag));
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The development branch was built", development.getLastBuild(), notNullValue());
            assertThat("The development branch was built", development.getLastBuild().getNumber(), is(1));
            assertThat("The stable branch was not built", stable.getLastBuild(), nullValue());
            assertThat("The tag was not built", tag.getLastBuild(), nullValue());
            assertThat("The change request was not built", cr.getLastBuild(), nullValue());
        }
    }

    public static class IgnoreTargetChangesStrategyImpl extends BranchBuildStrategy {

        public IgnoreTargetChangesStrategyImpl() {
        }

        @Override
        public boolean isAutomaticBuild(@NonNull SCMSource source, @NonNull SCMHead head,
                                        @NonNull SCMRevision currRevision,
                                        SCMRevision lastBuiltRevision, SCMRevision lastSeenRevision,
                                        @NonNull TaskListener listener) {
            if (currRevision instanceof ChangeRequestSCMRevision) {
                ChangeRequestSCMRevision<?> currCR = (ChangeRequestSCMRevision<?>) currRevision;
                if (lastBuiltRevision instanceof ChangeRequestSCMRevision) {
                    // if we have a previous, only build if the change is affecting the head not the target
                    return !currCR.equivalent((ChangeRequestSCMRevision<?>) lastBuiltRevision);
                } else {
                    // we don't have a previous, so build
                    return true;
                }
            }
            return false;
        }

        @TestExtension(
                "given_multibranchWithIgnoreTargetChangesStrategy_when_reindexing_then_onlyCRsWithHeadChangesRebuilt")
        public static class DescriptorImpl extends BranchBuildStrategyDescriptor {

        }
    }

    @Test
    @Issue("JENKINS-48535")
    public void
    given_multibranchWithIgnoreTargetChangesStrategy_when_reindexing_then_onlyCRsWithHeadChangesRebuilt()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.addFile("foo", "master", "new revision", "dummy.txt", "anything".getBytes(StandardCharsets.UTF_8));
            Integer cr1Num = c.openChangeRequest("foo", "master");
            Integer cr2Num = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            BranchSource source = new BranchSource(new MockSCMSource(
                    c,
                    "foo",
                    new MockSCMDiscoverBranches(),
                    new MockSCMDiscoverChangeRequests(
                            ChangeRequestCheckoutStrategy.HEAD,
                            ChangeRequestCheckoutStrategy.MERGE
                    )
            ));
            source.setBuildStrategies(Arrays.<BranchBuildStrategy>asList(new IgnoreTargetChangesStrategyImpl()));
            prj.getSourcesList().add(source);
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("We now have projects",
                    prj.getItems(), not(Matchers.<FreeStyleProject>empty()));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We have master branch", master, notNullValue());
            FreeStyleProject cr1Head = prj.getItem("CR-" + cr1Num + "-HEAD");
            assertThat("We now have the change request 1 HEAD", cr1Head, notNullValue());
            FreeStyleProject cr1Merge = prj.getItem("CR-" + cr1Num + "-MERGE");
            assertThat("We now have the change request 1 MERGE", cr1Merge, notNullValue());
            FreeStyleProject cr2Head = prj.getItem("CR-" + cr2Num + "-HEAD");
            assertThat("We now have the change request 2 HEAD", cr2Head, notNullValue());
            FreeStyleProject cr2Merge = prj.getItem("CR-" + cr2Num + "-MERGE");
            assertThat("We now have the change request 2 MERGE", cr2Merge, notNullValue());
            assertThat("We have only the expected items",
                    prj.getItems(), containsInAnyOrder(master, cr1Head, cr1Merge, cr2Head, cr2Merge));
            assertThat("The master branch was not built", master.getLastBuild(), nullValue());
            assertThat("The change request 1 HEAD was built", cr1Head.getLastBuild(), notNullValue());
            assertThat("The change request 1 HEAD was built", cr1Head.getLastBuild().getNumber(), is(1));
            assertThat("The change request 1 MERGE was built", cr1Merge.getLastBuild(), notNullValue());
            assertThat("The change request 1 MERGE was built", cr1Merge.getLastBuild().getNumber(), is(1));
            assertThat("The change request 2 HEAD was built", cr2Head.getLastBuild(), notNullValue());
            assertThat("The change request 2 HEAD was built", cr2Head.getLastBuild().getNumber(), is(1));
            assertThat("The change request 2 MERGE was built", cr2Merge.getLastBuild(), notNullValue());
            assertThat("The change request 2 MERGE was built", cr2Merge.getLastBuild().getNumber(), is(1));

            // now change the master baseline and one of the change requests
            c.addFile("foo", "master", "new revision", "dummy.txt", "anythingElse".getBytes(StandardCharsets.UTF_8));
            c.addFile("foo", "change-request/"+cr2Num, "new revision", "dummy.txt", "headChange".getBytes(StandardCharsets.UTF_8));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();

            assertThat("We have only the expected items",
                    prj.getItems(), containsInAnyOrder(master, cr1Head, cr1Merge, cr2Head, cr2Merge));
            assertThat("The master branch was not built", master.getLastBuild(), nullValue());
            assertThat("The change request 1 HEAD was not rebuilt", cr1Head.getLastBuild().getNumber(), is(1));
            assertThat("The change request 1 MERGE was not rebuilt", cr1Merge.getLastBuild().getNumber(), is(1));
            assertThat("The change request 2 HEAD was rebuilt", cr2Head.getLastBuild().getNumber(), is(2));
            assertThat("The change request 2 MERGE was rebuilt", cr2Merge.getLastBuild().getNumber(), is(2));
        }
    }

    /**
     * Waits until Hudson finishes building everything, including those in the queue, or fail the test
     * if the specified timeout milliseconds is
     */
    public void waitUntilNoActivityIgnoringThreadDeathUpTo(int timeout) throws Exception {
        long startTime = System.currentTimeMillis();
        int streak = 0;

        while (true) {
            Thread.sleep(10);
            if (isSomethingHappeningIgnoringThreadDeath())
                streak = 0;
            else
                streak++;

            if (streak > 5)   // the system is quiet for a while
                return;

            if (System.currentTimeMillis() - startTime > timeout) {
                List<Queue.Executable> building = new ArrayList<>();
                List<Throwable> deaths = new ArrayList<>();
                for (Computer c : r.jenkins.getComputers()) {
                    for (Executor e : c.getAllExecutors()) {
                        if (e.isBusy()) {
                            building.add(e.getCurrentExecutable());
                        }
                    }
                }
                ThreadInfo[] threadInfos = Functions.getThreadInfos();
                Functions.ThreadGroupMap m = Functions.sortThreadsAndGetGroupMap(threadInfos);
                for (ThreadInfo ti : threadInfos) {
                    System.err.println(Functions.dumpThreadInfo(ti, m));
                }
                throw new AssertionError(
                        String.format("Jenkins is still doing something after %dms: queue=%s building=%s deaths=%s",
                                timeout, Arrays.asList(r.jenkins.getQueue().getItems()), building, deaths));
            }
        }
    }

    /**
     * Returns true if Hudson is building something or going to build something.
     */
    public boolean isSomethingHappeningIgnoringThreadDeath() {
        if (!r.jenkins.getQueue().isEmpty()) {
            return true;
        }
        for (Computer n : r.jenkins.getComputers()) {
            for (Executor e: n.getAllExecutors()) {
                if (e.isBusy()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    public void given_multibranchWithSources_when_nonMatchingEvent_then_branchesAreNotFoundAndBuilt() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            assertThat("Adding sources doesn't trigger indexing",
                    prj.getItems(), is((Collection<FreeStyleProject>) Collections.<FreeStyleProject>emptyList()));
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "fork", "junkHash"));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("We now have branches",
                    prj.getItems(), not(is((Collection<FreeStyleProject>) Collections.<FreeStyleProject>emptyList())));
            FreeStyleProject master = prj.getItem("master");
            assertThat("We now have the master branch", master, notNullValue());
            FreeStyleProject feature = prj.getItem("feature");
            assertThat("We now have the feature branch", feature, notNullValue());
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
            assertThat("No events means no new builds in master branch", master.getLastBuild().getNumber(), is(1));
            assertThat("No events means no new builds in feature branch", feature.getLastBuild().getNumber(), is(1));

            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
            assertThat("The master branch was built from event", master.getLastBuild().getNumber(), is(2));
            assertThat("The feature branch was not built", feature.getLastBuild().getNumber(), is(1));

            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "feature", "junkHash"));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
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

            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
            assertThat("The master branch was built from event", master.getLastBuild().getNumber(), is(2));
            assertThat("The feature branch was not built", feature.getLastBuild().getNumber(), is(1));

            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "feature", "junkHash"));
            assertThat("The master branch was not built", master.getLastBuild().getNumber(), is(2));
            assertThat("The feature branch was built from event", feature.getLastBuild().getNumber(), is(2));

            // Now fire events that match but verification will show no change, so no rebuilt

            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
            assertThat("The master branch was not rebuilt", master.getLastBuild().getNumber(), is(2));
            assertThat("The feature branch was not built", feature.getLastBuild().getNumber(), is(2));

            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "feature", "junkHash"));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
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
            assertThat("CheckoutStrategy allows one branch to remain",
                    prj.getItems(), containsInAnyOrder(master, feature1, feature2, feature3));
            assertThat("Dead branches not deleted per retention strategy", feature1.isDisabled(), is(true));

            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("CheckoutStrategy allows one branch to remain",
                    prj.getItems(), containsInAnyOrder(master, feature1, feature2, feature3));
            assertThat("Dead branches not deleted per retention strategy", feature1.isDisabled(), is(true));

            c.deleteBranch("foo", "feature-3");

            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("Dead branches not deleted per retention strategy", feature3.isDisabled(), is(true));
            assertThat("CheckoutStrategy allows one branch to remain",
                    prj.getItems(), containsInAnyOrder(master, feature2, feature3));

            c.deleteBranch("foo", "feature-2");

            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("CheckoutStrategy allows one newest branch to remain",
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            FreeStyleProject feature = prj.getItem("feature");
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature branch was built", feature.getLastBuild(), notNullValue());
            assertThat("The feature branch was built", feature.getLastBuild().getNumber(), is(1));

            fire(new MockSCMHeadEvent(null, SCMEvent.Type.REMOVED, c, "foo", "feature", "junkHash"));
            assertThat("Events only validate the rumoured change", feature.isDisabled(), is(false));

            c.deleteBranch("foo", "feature");
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.REMOVED, c, "foo", "feature", "junkHash"));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            FreeStyleProject master = prj.getItem("master");
            FreeStyleProject feature = prj.getItem("feature");
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertThat("The feature branch was built", feature.getLastBuild(), notNullValue());
            assertThat("The feature branch was built", feature.getLastBuild().getNumber(), is(1));

            fire(new MockSCMHeadEvent(null, SCMEvent.Type.REMOVED, c, "foo", "feature", "junkHash"));

            c.deleteBranch("foo", "feature");
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.REMOVED, c, "foo", "feature", "junkHash"));

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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));

            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
            assertThat("Events only validate the rumoured change",
                    prj.getItems(), Matchers.<FreeStyleProject>empty());

            c.addFile("foo", "master", "Adding README.md", "README.md", "This is the readme".getBytes());
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));

            c.addFile("foo", "master", "adding marker file", "marker.txt", "This is the marker".getBytes());
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));

            c.addFile("foo", "master", "adding marker file", "marker.txt", "This is the marker".getBytes());
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
            FreeStyleProject master = prj.getItem("master");
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));

            c.cloneBranch("foo", "master", "feature-1");
            assertThat("No new branches without indexing or event",
                    prj.getItems(), is((Collection<FreeStyleProject>) Collections.singletonList(master)));

            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", "does-not-exist", "junkHash"));
            assertThat("Events only validate the rumoured change",
                    prj.getItems(), is((Collection<FreeStyleProject>) Collections.singletonList(master)));

            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", "feature-1", "junkHash"));
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
    public void given_multibranch_when_eventStorm_then_projectsCreated()
            throws Exception {
        List<String> branchNames = Arrays.asList( // top 20 names for boys and girls 2016 in case you are wondering
                "Sophia", "Jackson", "Emma", "Aiden", "Olivia", "Lucas", "Ava", "Liam", "Mia", "Noah", "Isabella",
                "Ethan", "Riley", "Mason", "Aria", "Caden", "Zoe", "Oliver", "Charlotte", "Elijah", "Lily", "Grayson",
                "Layla", "Jacob", "Amelia", "Michael", "Emily", "Benjamin", "Madelyn", "Carter", "Aubrey", "James",
                "Adalyn", "Jayden", "Madison", "Logan", "Chloe", "Alexander", "Harper", "Caleb"
        );
        try (MockSCMController c = MockSCMController.create().withLatency(MockLatency.fixed(5, TimeUnit.MILLISECONDS))) {
            c.createRepository("foo");
            for (String n: branchNames) {
                c.createBranch("foo", n);
            }

            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(new BasicSCMSourceCriteria("marker.txt"));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));

            for (String n : branchNames) {
                c.addFile("foo", n, "adding marker file", "marker.txt", "This is the marker".getBytes());
            }
            // now for the storm
            long watermark = SCMEvents.getWatermark();
            for (String n: branchNames) {
                SCMHeadEvent.fireNow(new MockSCMHeadEvent("test", SCMEvent.Type.UPDATED, c, "foo", n, c.getRevision("foo", n)));
                watermark = Math.max(watermark, SCMEvents.getWatermark());
            }
            SCMEvents.awaitAll(watermark);
            r.waitUntilNoActivity();

            List<FreeStyleProject> expected = new ArrayList<>();
            for (String n : branchNames) {
                FreeStyleProject branch = prj.getItem(n);
                assertThat("We have the " + n + " branch", branch, notNullValue());
                assertThat("The "+n+" branch was built", branch.getLastBuild(), notNullValue());
                assertThat("The "+n+" branch was built", branch.getLastBuild().getNumber(), is(1));
                expected.add(branch);
            }
            assertThat(prj.getItems(), containsInAnyOrder(expected.toArray(new FreeStyleProject[0])));
        }
    }

    @Test
    public void given_multibranch_when_eventStorm_then_eventsConcurrent()
            throws Exception {
        List<String> branchNames = Arrays.asList( // top 20 names for boys and girls 2016 in case you are wondering
                "Sophia", "Jackson", "Emma", "Aiden", "Olivia", "Lucas", "Ava", "Liam", "Mia", "Noah", "Isabella",
                "Ethan", "Riley", "Mason", "Aria", "Caden", "Zoe", "Oliver", "Charlotte", "Elijah", "Lily", "Grayson",
                "Layla", "Jacob", "Amelia", "Michael", "Emily", "Benjamin", "Madelyn", "Carter", "Aubrey", "James",
                "Adalyn", "Jayden", "Madison", "Logan", "Chloe", "Alexander", "Harper", "Caleb"
        );
        final AtomicInteger concurrentRequests = new AtomicInteger(0);
        final AtomicInteger maxInflight = new AtomicInteger(0);
        try (MockSCMController c = MockSCMController.create().withLatency(new MockLatency() {
            @Override
            public void apply() throws InterruptedException {
                int start = concurrentRequests.incrementAndGet();
                try {
                    Thread.sleep(1);
                } finally {
                    int end = concurrentRequests.getAndDecrement();
                    int max = Math.max(start, end);
                    int prevMax;
                    while (max > (prevMax = maxInflight.get())) {
                        maxInflight.compareAndSet(prevMax, max);
                    }
                }
            }
        })) {
            c.createRepository("foo");
            for (String n: branchNames) {
                c.createBranch("foo", n);
            }

            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(new BasicSCMSourceCriteria("marker.txt"));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));

            for (String n : branchNames) {
                c.addFile("foo", n, "adding marker file", "marker.txt", "This is the marker".getBytes());
            }
            // now for the storm
            long watermark = SCMEvents.getWatermark();
            for (String n: branchNames) {
                SCMHeadEvent.fireNow(new MockSCMHeadEvent("test", SCMEvent.Type.UPDATED, c, "foo", n, c.getRevision("foo", n)));
                watermark = Math.max(watermark, SCMEvents.getWatermark());
            }
            SCMEvents.awaitAll(watermark);
            r.waitUntilNoActivity();

            List<FreeStyleProject> expected = new ArrayList<>();
            for (String n : branchNames) {
                FreeStyleProject branch = prj.getItem(n);
                assertThat("We have the " + n + " branch", branch, notNullValue());
                assertThat("The " + n + " branch was built", branch.getLastBuild(), notNullValue());
                assertThat("The " + n + " branch was built", branch.getLastBuild().getNumber(), is(1));
                expected.add(branch);
            }
            assertThat(prj.getItems(), containsInAnyOrder(expected.toArray(new FreeStyleProject[0])));
        }
        assertThat("More than one event processed concurrently", maxInflight.get(), greaterThan(1));
    }

    @Ignore("TODO passes locally, but on CI often (and on Windows, always?) fails; seems to be a ClosedByInterruptException")
    @Test
    public void given_multibranch_when_oneEventBlocking_then_otherEventsProcessed() throws Exception {
        List<String> branchNames = Arrays.asList( // top 20 names for boys and girls 2016 in case you are wondering
                "Sophia", "Jackson", "Emma", "Aiden", "Olivia", "Lucas", "Ava", "Liam", "Mia", "Noah", "Isabella",
                "Ethan", "Riley", "Mason", "Aria", "Caden", "Zoe", "Oliver", "Charlotte", "Elijah", "Lily", "Grayson",
                "Layla", "Jacob", "Amelia", "Michael", "Emily", "Benjamin", "Madelyn", "Carter", "Aubrey", "James",
                "Adalyn", "Jayden", "Madison", "Logan", "Chloe", "Alexander", "Harper", "Caleb"
        );
        final CountDownLatch block = new CountDownLatch(1);
        final CountDownLatch ready = new CountDownLatch(1);
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            for (String n: branchNames) {
                c.createBranch("foo", n);
            }

            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(new BasicSCMSourceCriteria("marker.txt"));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            c.addFile("foo", "master", "adding marker file", "marker.txt", "This is the marker".getBytes());
            final AtomicBoolean tripActive = new AtomicBoolean(true);
            c.addFault(new MockFailure() {
                @Override
                public void check(@CheckForNull String repository, @CheckForNull String branchOrCR,
                                  @CheckForNull String revision,
                                  boolean actions) throws IOException {
                    if ("foo".equals(repository) && "master".equals(branchOrCR) && tripActive.get()) {
                        try {
                            ready.countDown();
                            block.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            throw new IOException(e);
                        }
                    }
                }
            });
            for (String n : branchNames) {
                c.addFile("foo", n, "adding marker file", "marker.txt", "This is the marker".getBytes());
            }
            SCMHeadEvent.fireNow(
                    new MockSCMHeadEvent("test", SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash")
            );
            ready.await(250, TimeUnit.MILLISECONDS);
            tripActive.set(false);

            // now for the other events
            for (String n: branchNames) {
                SCMHeadEvent.fireNow(new MockSCMHeadEvent("test", SCMEvent.Type.UPDATED, c, "foo", n, c.getRevision("foo", n)));
            }
            while (prj.getItems().size() < branchNames.size()) {
                Thread.sleep(50);
            }
            r.waitUntilNoActivity();
            assertThat("We don't have the master branch", prj.getItem("master"), nullValue());
            List<FreeStyleProject> expected = new ArrayList<>();
            for (String n : branchNames) {
                FreeStyleProject branch = prj.getItem(n);
                assertThat("We have the " + n + " branch", branch, notNullValue());
                assertThat("The " + n + " branch was built", branch.getLastBuild(), notNullValue());
                assertThat("The " + n + " branch was built", branch.getLastBuild().getNumber(), is(1));
                expected.add(branch);
            }
            assertThat(prj.getItems(), containsInAnyOrder(expected.toArray(new FreeStyleProject[0])));

            // release the block
            block.countDown();
            SCMEvents.awaitAll(SCMEvents.getWatermark());
            r.waitUntilNoActivity();

            FreeStyleProject master = prj.getItem("master");
            assertThat("We have the master branch", master, notNullValue());
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));

            expected.add(master);
            assertThat(prj.getItems(), containsInAnyOrder(expected.toArray(new FreeStyleProject[0])));
        } finally {
            block.countDown(); // release it just in case to ensure any background threads get to terminate promptly
        }
    }

    @Test
    public void given_multibranchWithSourcesCriteriaAndMatchingBranches_when_eventRemoveMatch_then_projectsDisables()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(new BasicSCMSourceCriteria("marker.txt"));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));

            c.addFile("foo", "master", "adding marker file", "marker.txt", "This is the marker".getBytes());
            c.cloneBranch("foo", "master", "feature-1");
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", "does-not-exist", "junkHash"));
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", "feature-1", "junkHash"));
            FreeStyleProject feature1 = prj.getItem("feature-1");
            r.waitUntilNoActivity();
            assertThat("The feature-1 branch was built", feature1.getLastBuild(), notNullValue());
            assertThat("The feature-1 branch was built", feature1.getLastBuild().getNumber(), is(1));

            fire(new MockSCMHeadEvent(null, SCMEvent.Type.REMOVED, c, "foo", "feature-1", "junkHash"));
            assertThat("Events only validate the rumoured change", feature1.isDisabled(), is(false));
            assertThat("The feature-1 branch was not built", feature1.getLastBuild(), notNullValue());
            assertThat("The feature-1 branch was not built", feature1.getLastBuild().getNumber(), is(1));

            c.rmFile("foo", "feature-1", "removing marker file", "marker.txt");
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "feature-1", "junkHash"));
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
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
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
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
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
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
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

    @Issue("JENKINS-48536")
    @Test
    public void given_orgFolder_when_someReposMatch_then_scanFiresSCMSourceAfterSave() throws Exception {
        final ConcurrentMap<SCMSourceOwner,MockSCMSource> saved = new ConcurrentHashMap<>();
        try (MockSCMController c = MockSCMController.create().withSaveListener(new MockSCMSourceSaveListener() {
            @Override
            public void afterSave(MockSCMSource source) {
                saved.putIfAbsent(source.getOwner(), source);
            }
        })) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.addFile("foo", "master", "adding marker", "marker.txt", "A marker".getBytes());
            assertThat(saved.isEmpty(), is(true));
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
            assertThat(saved, hasKey((SCMSourceOwner)foo));
        }
    }

    @Test
    public void given_orgFolderWithFilteringTrait_when_someReposMatch_then_scanCreatesMatchingProjects() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches(),
                    new WildcardSCMSourceFilterTrait("*", "fu")
            ));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("fu");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.addFile("foo", "master", "adding marker", "marker.txt", "A marker".getBytes());
            c.addFile("fu", "master", "adding marker", "marker.txt", "A marker".getBytes());
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
    @Issue("JENKINS-42000")
    public void given_orgFolder_when_navigatorIoErrorScanning_then_scanRecordedAsFailure() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.addFile("foo", "master", "adding marker", "marker.txt", "A marker".getBytes());
            c.addFile("bar", "master", "adding marker", "marker.txt", "A marker".getBytes());
            c.addFile("manchu", "master", "adding marker", "marker.txt", "A marker".getBytes());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getComputation().getResult(), is(Result.SUCCESS));
            assertThat("A scan picks up a newly qualified repo",
                    prj.getItems(),
                    not(is((Collection<MultiBranchProject<?, ?>>) Collections.<MultiBranchProject<?, ?>>emptyList())));
            BasicMultiBranchProject foo = (BasicMultiBranchProject) prj.getItem("foo");
            BasicMultiBranchProject bar = (BasicMultiBranchProject) prj.getItem("bar");
            BasicMultiBranchProject manchu = (BasicMultiBranchProject) prj.getItem("manchu");
            assertThat("We now have the `foo` project", foo, notNullValue());
            assertThat("We now have the `bar` project", bar, notNullValue());
            assertThat("We now have the `manchu` project", manchu, notNullValue());
            assertThat("We now have only the projects expected",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>containsInAnyOrder(foo, bar, manchu));
            c.addFault(new MockFailure() {
                @Override
                public void check(@CheckForNull String repository, @CheckForNull String branchOrCR,
                                  @CheckForNull String revision,
                                  boolean actions) throws IOException {
                    if ("bar".equals(repository) && branchOrCR == null && revision == null && !actions) {
                        throw new IOException("Boom Boom Boom!!!");
                    }
                }
            });
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getComputation().getResult(), is(Result.FAILURE));
            foo = (BasicMultiBranchProject) prj.getItem("foo");
            bar = (BasicMultiBranchProject) prj.getItem("bar");
            manchu = (BasicMultiBranchProject) prj.getItem("manchu");
            assertThat("We now have the `foo` project", foo, notNullValue());
            assertThat("We now have the `bar` project", bar, notNullValue());
            assertThat("We now have the `manchu` project", manchu, notNullValue());
            assertThat("We now have only the projects expected",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>containsInAnyOrder(foo, bar, manchu));
        }
    }

    @Test
    @Issue("JENKINS-42000")
    public void given_orgFolder_when_sourceIoErrorScanning_then_scanRecordedAsFailure() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.addFile("foo", "master", "adding marker", "marker.txt", "A marker".getBytes());
            c.addFile("bar", "master", "adding marker", "marker.txt", "A marker".getBytes());
            c.addFile("manchu", "master", "adding marker", "marker.txt", "A marker".getBytes());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("A scan picks up a newly qualified repo",
                    prj.getItems(),
                    not(is((Collection<MultiBranchProject<?, ?>>) Collections.<MultiBranchProject<?, ?>>emptyList())));
            BasicMultiBranchProject foo = (BasicMultiBranchProject) prj.getItem("foo");
            BasicMultiBranchProject bar = (BasicMultiBranchProject) prj.getItem("bar");
            BasicMultiBranchProject manchu = (BasicMultiBranchProject) prj.getItem("manchu");
            assertThat("We now have the `foo` project", foo, notNullValue());
            assertThat("We now have the `bar` project", bar, notNullValue());
            assertThat("We now have the `manchu` project", manchu, notNullValue());
            assertThat("We now have only the projects expected",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>containsInAnyOrder(foo, bar, manchu));
            c.addFault(new MockFailure() {
                @Override
                public void check(@CheckForNull String repository, @CheckForNull String branchOrCR,
                                  @CheckForNull String revision,
                                  boolean actions) throws IOException {
                    if ("bar".equals(repository) && branchOrCR != null && revision == null && !actions) {
                        throw new IOException("Boom Boom Boom!!!");
                    }
                }
            });
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getComputation().getResult(), is(Result.FAILURE));
            foo = (BasicMultiBranchProject) prj.getItem("foo");
            bar = (BasicMultiBranchProject) prj.getItem("bar");
            manchu = (BasicMultiBranchProject) prj.getItem("manchu");
            assertThat("We now have the `foo` project", foo, notNullValue());
            assertThat("We now have the `bar` project", bar, notNullValue());
            assertThat("We now have the `manchu` project", manchu, notNullValue());
            assertThat("We now have only the projects expected",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>containsInAnyOrder(foo, bar, manchu));
        }
    }

    @Test
    public void given_orgFolder_when_someReposMatch_then_eventCreatesMatchingProject() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.addFile("foo", "master", "adding marker", "marker.txt", "A marker".getBytes());
            c.addFile("bar", "master", "adding marker", "marker.txt", "A marker".getBytes());
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "bar", "master", "junkHash"));
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
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
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
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "bar", SLASHY_BRANCH_NAME, "junkHash"));
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
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
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
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "bar", I18N_BRANCH_NAME, "junkHash"));
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
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.addFile("foo", "master", "adding marker", "marker.txt", "A marker".getBytes());
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "bar", "master", "junkHash"));
            assertThat("Events only apply to the branch they refer to and are validated",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>empty());
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "bar", "master", "junkHash"));
            assertThat("Events only apply to the branch they refer to and are validated",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>empty());
        }
    }

    @Test
    public void given_orgFolder_when_eventUpdatesABranchToAMatch_then_projectIsCreated() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.addFile("foo", "master", "adding marker", "marker.txt", "A marker".getBytes());
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
            BasicMultiBranchProject foo = (BasicMultiBranchProject) prj.getItem("foo");
            assertThat("We now have the one project matching", foo, notNullValue());
            assertThat("We now have only the one project matching",
                    prj.getItems(),
                    Matchers.<MultiBranchProject<?, ?>>containsInAnyOrder(foo));
            assertThat("The matching branch exists", foo.getItem("master"), notNullValue());
        }
    }

    @Test
    @Issue("JENKINS-48214")
    public void given_orgFolderWithOnlyDeactivatedChildren_when_eventUpdatesABranchToRestoreAMatch_then_projectIsRestored() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            prj.setOrphanedItemStrategy(new DefaultOrphanedItemStrategy(true, "1", "2"));
            c.createRepository("foo");
            c.createRepository("bar");
            c.addFile("foo", "master", "adding marker", "marker.txt", "A marker".getBytes());
            c.addFile("bar", "master", "adding marker", "marker.txt", "A marker".getBytes());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            BasicMultiBranchProject foo = (BasicMultiBranchProject) prj.getItem("foo");
            BasicMultiBranchProject bar = (BasicMultiBranchProject) prj.getItem("bar");
            assertThat("We now have the two projects matching", foo, notNullValue());
            assertThat("We now have the two projects matching", bar, notNullValue());
            assertThat("we now have two enabled projects", foo.isBuildable(), is(true));
            assertThat("we now have two enabled projects", bar.isBuildable(), is(true));
            assertThat("The matching branch exists", foo.getItem("master"), notNullValue());
            assertThat("The matching branch was built", foo.getItem("master").getNextBuildNumber(), is(2));

            assertThat(c.stat("foo", "master", "marker.txt"), is(SCMFile.Type.REGULAR_FILE));
            c.rmFile("foo", "master", "removing marker", "marker.txt");
            assertThat(c.stat("foo", "master", "marker.txt"), is(SCMFile.Type.NONEXISTENT));
            c.rmFile("bar", "master", "removing marker", "marker.txt");
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            foo = (BasicMultiBranchProject) prj.getItem("foo");
            bar = (BasicMultiBranchProject) prj.getItem("bar");
            assertThat("We now have the two projects matching", foo, notNullValue());
            assertThat("We now have the two projects matching", bar, notNullValue());
            assertThat("we now have two disabled projects", foo.isBuildable(), is(false));
            assertThat("we now have two disabled projects", bar.isBuildable(), is(false));

            c.addFile("foo", "master", "adding marker", "marker.txt", "A marker".getBytes());
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "master", "junkHash"));
            r.waitUntilNoActivity();

            assertThat("we now have one enabled project", foo.isBuildable(), is(true));
            assertThat("we now have one disabled project", bar.isBuildable(), is(false));
            assertThat("The matching branch exists", foo.getItem("master"), notNullValue());
            assertThat("The matching branch was restored", foo.getItem("master").getNextBuildNumber(), is(3));
        }
    }

    @Test
    public void given_orgFolder_when_eventCreatesABranchWithAMatch_then_projectIsCreated() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.cloneBranch("foo", "master", "feature");
            c.addFile("foo", "feature", "adding marker", "marker.txt", "A marker".getBytes());
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", "feature", "junkHash"));
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
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.cloneBranch("foo", "master", SLASHY_BRANCH_NAME);
            c.addFile("foo", SLASHY_BRANCH_NAME, "adding marker", "marker.txt", "A marker".getBytes());
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", SLASHY_BRANCH_NAME, "junkHash"));
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
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.cloneBranch("foo", "master", I18N_BRANCH_NAME);
            c.addFile("foo", I18N_BRANCH_NAME, "adding marker", "marker.txt", "A marker".getBytes());
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", I18N_BRANCH_NAME, "junkHash"));
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
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.cloneBranch("foo", "master", "feature");
            c.addFile("foo", "feature", "adding marker", "marker.txt", "A marker".getBytes());
            fire(new MockSCMSourceEvent(null, SCMEvent.Type.CREATED, c, "foo"));
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
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.cloneBranch("foo", "master", "feature");
            c.addFile("foo", "feature", "adding marker", "marker.txt", "A marker".getBytes());
            c.cloneBranch("foo", "feature", "sustaining");
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "master", "ignored"));
            assertThat("Event specified branch does not match",
                    prj.getItem("foo"),
                    nullValue());
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "feature", "ignored"));
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
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.createRepository("bar");
            c.createRepository("manchu");
            c.cloneBranch("foo", "master", "feature");
            fire(new MockSCMSourceEvent(null, SCMEvent.Type.CREATED, c, "foo"));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches(),
                    new MockSCMDiscoverTags(), new MockSCMDiscoverChangeRequests())));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
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
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.REMOVED, c, "foo", "feature", "junkHash"));
            assertThat("Feature branch is disabled", feature.isDisabled(), is(true));
            assertThat("Feature branch is dead", prj.getProjectFactory().getBranch(feature), instanceOf(Branch.Dead.class));

            c.createBranch("foo", "feature");
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", "feature", "junkHash"));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
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
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "feature", "junkHash"));
            assertThat("Feature branch is disabled", feature.isDisabled(), is(true));

            c.addFile("foo", "feature", "marker", "marker.txt", "build me again".getBytes());
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "feature", "junkHash"));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())
                    .withId("foo:id")));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "bar", new MockSCMDiscoverBranches())
                    .withId("bar:id")));
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
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", "feature", "junkHash"));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())
                    .withId("foo:id")));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "bar", new MockSCMDiscoverBranches())
                    .withId("bar:id")));
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
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "bar", "feature", "junkHash"));
            assertThat("Feature branch not taken over by lower priority source",
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())
                    .withId("foo:id")));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "bar", new MockSCMDiscoverBranches())
                    .withId("bar:id")));
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
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.REMOVED, c, "foo", "feature", "junkHash"));
            assertThat("Feature branch is disabled", feature.isDisabled(), is(true));
            assertThat("Feature branch is dead", prj.getProjectFactory().getBranch(feature),
                    instanceOf(Branch.Dead.class));

            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "bar", "feature", "junkHash"));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())
                    .withId("foo:id")));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "bar", new MockSCMDiscoverBranches())
                    .withId("bar:id")));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())
                    .withId("foo:id")));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "bar", new MockSCMDiscoverBranches())
                    .withId("bar:id")));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())
                    .withId("foo:id")));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "bar", new MockSCMDiscoverBranches())
                    .withId("bar:id")));
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
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "feature", "junkHash"));
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())
                    .withId("foo:id")));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "bar", new MockSCMDiscoverBranches())
                    .withId("bar:id")));
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
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "bar", "feature", "junkHash"));
            assertThat("Feature branch not taken over by lower priority source",
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
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())
                    .withId("foo:id")));
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "bar", new MockSCMDiscoverBranches())
                    .withId("bar:id")));
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
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "feature", "junkHash"));
            assertThat("Feature branch is disabled", feature.isDisabled(), is(true));
            assertThat("Feature branch is dead", prj.getProjectFactory().getBranch(feature),
                    instanceOf(Branch.Dead.class));

            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "bar", "feature", "junkHash"));
            assertThat("Feature branch takeover by lower priority source",
                    prj.getProjectFactory().getBranch(feature).getSourceId(),
                    is("bar:id"));
            r.waitUntilNoActivity();
            assertThat("The feature branch was rebuilt", feature.getLastBuild().getNumber(), is(2));
        }
    }

    @Test
    public void given_multibranch_when_not_build_is_skipped_then_lastSeenRevision_is_equal_to_lastBuiltRevision() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "my-project");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", "master", c.getRevision("foo", "master")));
            FreeStyleProject master = prj.getItem("master");
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertEquals(prj.getProjectFactory().getLastSeenRevision(master), prj.getProjectFactory().getRevision(master));
            c.addFile("foo", "master", "adding file", "file", new byte[0]);
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "master", c.getRevision("foo", "master")));
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(2));
            assertEquals(prj.getProjectFactory().getLastSeenRevision(master), prj.getProjectFactory().getRevision(master));
        }
    }

    @Test
    public void given_multibranch_when_first_build_is_skipped_then_lastSeenRevision_is_different_to_lastBuiltRevision() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "my-project");
            prj.setCriteria(null);
            BranchSource source = new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches()));
            source.setBuildStrategies(Collections.<BranchBuildStrategy>singletonList(new SkipInitialBuildStrategyImpl()));
            prj.getSourcesList().add(source);
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", "master", c.getRevision("foo", "master")));
            FreeStyleProject master = prj.getItem("master");
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), nullValue());
            assertNotNull(prj.getProjectFactory().getLastSeenRevision(master));
            assertNull(prj.getProjectFactory().getRevision(master));
            c.addFile("foo", "master", "adding file", "file", new byte[0]);
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "master", c.getRevision("foo", "master")));
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch was built", master.getLastBuild().getNumber(), is(1));
            assertNotNull(prj.getProjectFactory().getLastSeenRevision(master));
            assertNotNull(prj.getProjectFactory().getRevision(master));
            assertThat(prj.getProjectFactory().getRevision(master), is(prj.getProjectFactory().getLastSeenRevision(master)));
        }
    }

    public static class SkipInitialBuildStrategyImpl extends BranchBuildStrategy {
        @Override
        public boolean isAutomaticBuild(@NonNull SCMSource source, @NonNull SCMHead head,
                                        @NonNull SCMRevision currRevision,
                                        SCMRevision lastBuiltRevision, SCMRevision lastSeenRevision,
                                        TaskListener listener) {
           if (lastSeenRevision != null) {
               return true;
           }
           return false;
        }

        @TestExtension(
                "given_multibranch_when_first_build_is_skipped_then_lastSeenRevision_is_different_to_lastBuiltRevision")
        public static class DescriptorImpl extends BranchBuildStrategyDescriptor {

        }
    }

    @Test
    public void given_multibranch_when__build_is_skipped_then_lastBuiltRevision_is_null() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "my-project");
            prj.setCriteria(null);
            BranchSource source = new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches()));
            source.setBuildStrategies(Collections.<BranchBuildStrategy>singletonList(new SkipIAllBuildStrategyImpl()));
            prj.getSourcesList().add(source);
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.CREATED, c, "foo", "master", c.getRevision("foo", "master")));
            FreeStyleProject master = prj.getItem("master");
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), nullValue());
            SCMRevision scmLastSeenRevision1 = prj.getProjectFactory().getLastSeenRevision(master);
            assertNotNull(scmLastSeenRevision1);
            assertNull(prj.getProjectFactory().getRevision(master));
            c.addFile("foo", "master", "adding file", "file", new byte[0]);
            fire(new MockSCMHeadEvent(null, SCMEvent.Type.UPDATED, c, "foo", "master", c.getRevision("foo", "master")));
            r.waitUntilNoActivity();
            assertThat("The master branch was built", master.getLastBuild(), nullValue());
            SCMRevision scmLastSeenRevision2 = prj.getProjectFactory().getLastSeenRevision(master);
            assertNotNull(scmLastSeenRevision2);
            assertNull(prj.getProjectFactory().getRevision(master));
            assertThat(scmLastSeenRevision1, not(scmLastSeenRevision2));
        }
    }

    public static class SkipIAllBuildStrategyImpl extends BranchBuildStrategy {
        @Override
        public boolean isAutomaticBuild(@NonNull SCMSource source, @NonNull SCMHead head,
                                        @NonNull SCMRevision currRevision,
                                        SCMRevision lastBuiltRevision, SCMRevision lastSeenRevision,
                                        TaskListener listener) {
            return false;
        }

        @TestExtension(
                "given_multibranch_when__build_is_skipped_then_lastBuiltRevision_is_null")
        public static class DescriptorImpl extends BranchBuildStrategyDescriptor {

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
            System.out.printf("    * Branches%n");
            for (String branch : c.listBranches(name)) {
                System.out.printf("      - %s%n", branch);
                MockSCMController.LogEntry e = c.log(name, branch).get(0);
                System.out
                        .printf("          %s %tc %s%n", e.getHash().substring(0, 7), e.getTimestamp(), e.getMessage());
            }
            System.out.printf("    * Tags%n");
            for (String tag : c.listTags(name)) {
                System.out.printf("      - %s%n", tag);
                MockSCMController.LogEntry e = c.log(name, tag).get(0);
                System.out
                        .printf("          %s %tc %s%n", e.getHash().substring(0, 7), e.getTimestamp(), e.getMessage());
            }
            System.out.printf("    * Change requests%n");
            for (Integer crNum : c.listChangeRequests(name)) {
                System.out.printf("      - #%d%n", crNum);
                MockSCMController.LogEntry e = c.log(name, "change-request/" + crNum).get(0);
                System.out
                        .printf("          %s %tc %s%n", e.getHash().substring(0, 7), e.getTimestamp(), e.getMessage());
            }
        }
    }
}
