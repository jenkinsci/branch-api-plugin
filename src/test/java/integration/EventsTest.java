/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
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
import hudson.Util;
import hudson.model.FreeStyleProject;
import hudson.model.TopLevelItem;
import integration.harness.BasicMultiBranchProject;
import integration.harness.BasicMultiBranchProjectFactory;
import integration.harness.BasicSCMSourceCriteria;
import integration.harness.MockSCMController;
import integration.harness.MockSCMHeadEvent;
import integration.harness.MockSCMNavigator;
import integration.harness.MockSCMSource;
import integration.harness.MockSCMSourceEvent;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import jenkins.branch.BranchSource;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.OrganizationFolder;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMSourceEvent;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class EventsTest {

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
            assertThat("The tag was built", tag.getLastBuild(), notNullValue());
            assertThat("The tag was built", tag.getLastBuild().getNumber(), is(1));
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
            assertThat("The tag was built", tag.getLastBuild(), notNullValue());
            assertThat("The tag was built", tag.getLastBuild().getNumber(), is(1));
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
            assertThat("The tag was built", tag.getLastBuild(), notNullValue());
            assertThat("The tag was built", tag.getLastBuild().getNumber(), is(1));
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

    private void fire(MockSCMHeadEvent event) throws Exception {
        SCMHeadEvent.fireNow(event);
        Thread.sleep(100);
        r.waitUntilNoActivity();
    }

    private void fire(MockSCMSourceEvent event) throws Exception {
        SCMSourceEvent.fireNow(event);
        Thread.sleep(100);
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
