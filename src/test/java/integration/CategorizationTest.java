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

import hudson.model.Item;
import hudson.model.TopLevelItem;
import integration.harness.BasicMultiBranchProject;
import jenkins.branch.BranchSource;
import jenkins.branch.MultiBranchProjectEmptyView;
import jenkins.branch.MultiBranchProjectViewHolder;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.TagSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMDiscoverChangeRequests;
import jenkins.scm.impl.mock.MockSCMDiscoverTags;
import jenkins.scm.impl.mock.MockSCMSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

@WithJenkins
class CategorizationTest {

    /**
     * All tests in this class only create items and do not affect other global configuration, thus we trade test
     * execution time for the restriction on only touching items.
     */
    private static JenkinsRule r;

    @BeforeAll
    static void setUp(JenkinsRule rule) {
        r = rule;
    }

    @BeforeEach
    void setUp() throws Exception {
        for (TopLevelItem i : r.getInstance().getItems()) {
            i.delete();
        }
    }

    @Test
    void given_multibranch_when_noSourcesDefined_then_welcomeViewPresent() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            assertThat("We have no items", prj.getItems(), empty());
            assertThat("We have the empty view when no sources", prj.getViews(),
                    contains(instanceOf(MultiBranchProjectEmptyView.class)));
        }
    }

    @Test
    void given_multibranch_when_atLeastOneSourceDefinedButNoItems_then_welcomeViewPresent()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            assertThat("We have no items", prj.getItems(), empty());
            assertThat("We have the empty view when no items", prj.getViews(),
                    contains(instanceOf(MultiBranchProjectEmptyView.class)));
        }
    }

    @Test
    void given_multibranch_when_onlyUncategorizedCategory_then_onlyUncategorizedViewPresent() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createTag("foo", "master", "master-1.0");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getItems(),
                    contains(hasProperty("displayName", is("master")))
            );
            assertThat(prj.getViews(),
                    contains(
                            allOf(
                                    instanceOf(MultiBranchProjectViewHolder.ViewImpl.class),
                                    hasProperty("viewName", is(UncategorizedSCMHeadCategory.DEFAULT.getName())),
                                    hasProperty("items", contains(hasProperty("displayName", is("master"))))
                            )
                    )
            );
        }
    }

    @Test
    void given_multibranch_when_changeRequestsWanted_then_onlyUncategorizedAndChangeRequetsViewsPresent()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createTag("foo", "master", "master-1.0");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches(), new MockSCMDiscoverChangeRequests())));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getItems(),
                    containsInAnyOrder(
                            hasProperty("displayName", is("master")),
                            hasProperty("displayName", is("Change request #1 (CR-1)"))
                    )
            );
            assertThat(prj.getViews(),
                    containsInAnyOrder(
                            allOf(
                                    instanceOf(MultiBranchProjectViewHolder.ViewImpl.class),
                                    hasProperty("viewName", is(UncategorizedSCMHeadCategory.DEFAULT.getName())),
                                    hasProperty("items", contains(hasProperty("name", is("master"))))
                            ),
                            allOf(
                                    instanceOf(MultiBranchProjectViewHolder.ViewImpl.class),
                                    hasProperty("viewName", is(ChangeRequestSCMHeadCategory.DEFAULT.getName())),
                                    hasProperty("items", contains(hasProperty("name", is("CR-" + crNum))))
                            )
                    ));
        }
    }

    @Test
    void given_multibranch_when_tagsWanted_then_onlyUncategorizedAndTagsViewsPresent()
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
            assertThat(prj.getItems(),
                    containsInAnyOrder(
                            hasProperty("displayName", is("master")),
                            hasProperty("displayName", is("master-1.0"))
                    )
            );
            assertThat(prj.getViews(),
                    containsInAnyOrder(
                            allOf(
                                    instanceOf(MultiBranchProjectViewHolder.ViewImpl.class),
                                    hasProperty("viewName", is(UncategorizedSCMHeadCategory.DEFAULT.getName())),
                                    hasProperty("items", contains(hasProperty("displayName", is("master"))))
                            ),
                            allOf(
                                    instanceOf(MultiBranchProjectViewHolder.ViewImpl.class),
                                    hasProperty("viewName", is(TagSCMHeadCategory.DEFAULT.getName())),
                                    hasProperty("items", contains(hasProperty("displayName", is("master-1.0"))))
                            )
                    ));
        }
    }

    @Test
    void given_multibranch_when_noBranchesWanted_then_uncategorizedViewPresentButEmpty()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createTag("foo", "master", "master-1.0");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverTags(), new MockSCMDiscoverChangeRequests())));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getItems(),
                    containsInAnyOrder(
                            hasProperty("displayName", is("master-1.0")),
                            hasProperty("displayName", is("Change request #1 (CR-1)"))
                    )
            );
            assertThat(prj.getViews(),
                    containsInAnyOrder(
                            allOf(
                                    instanceOf(MultiBranchProjectViewHolder.ViewImpl.class),
                                    hasProperty("viewName", is(UncategorizedSCMHeadCategory.DEFAULT.getName())),
                                    hasProperty("items", emptyCollectionOf(Item.class))
                            ),
                            allOf(
                                    instanceOf(MultiBranchProjectViewHolder.ViewImpl.class),
                                    hasProperty("viewName", is(TagSCMHeadCategory.DEFAULT.getName())),
                                    hasProperty("items", contains(hasProperty("displayName", is("master-1.0"))))
                            ),
                            allOf(
                                    instanceOf(MultiBranchProjectViewHolder.ViewImpl.class),
                                    hasProperty("viewName", is(ChangeRequestSCMHeadCategory.DEFAULT.getName())),
                                    hasProperty("items", contains(hasProperty("displayName", is("Change request #1 (CR-1)"))))
                            )
                    ));
        }
    }

    @Test
    void given_multibranch_when_wantsEverything_then_hasEverything()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.cloneBranch("foo", "master", "feature");
            c.addFile("foo", "feature", "add new feature", "FEATURE", "new".getBytes());
            c.createTag("foo", "master", "master-1.0");
            c.addFile("foo", "master", "prepare for version 1.1", "VERSION", "1.1".getBytes());
            c.createTag("foo", "master", "master-1.1");
            Integer crNum1 = c.openChangeRequest("foo", "master");
            c.addFile("foo", "change-request/" + crNum1, "propose change", "CHANGE", "proposed".getBytes());
            Integer crNum2 = c.openChangeRequest("foo", "feature");
            c.addFile("foo", "change-request/" + crNum2, "propose change", "CHANGE", "proposed".getBytes());
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches(), new MockSCMDiscoverTags(), new MockSCMDiscoverChangeRequests())));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getItems(),
                    containsInAnyOrder(
                            hasProperty("displayName", is("master")),
                            hasProperty("displayName", is("feature")),
                            hasProperty("displayName", is("master-1.0")),
                            hasProperty("displayName", is("master-1.1")),
                            hasProperty("displayName", is("Change request #1 (CR-1)")),
                            hasProperty("displayName", is("Change request #2 (CR-2)"))
                    )
            );
            assertThat(prj.getViews(),
                    containsInAnyOrder(
                            allOf(
                                    instanceOf(MultiBranchProjectViewHolder.ViewImpl.class),
                                    hasProperty("viewName", is(UncategorizedSCMHeadCategory.DEFAULT.getName())),
                                    hasProperty("items", containsInAnyOrder(
                                            hasProperty("displayName", is("master")),
                                            hasProperty("displayName", is("feature"))
                                    ))
                            ),
                            allOf(
                                    instanceOf(MultiBranchProjectViewHolder.ViewImpl.class),
                                    hasProperty("viewName", is(TagSCMHeadCategory.DEFAULT.getName())),
                                    hasProperty("items", containsInAnyOrder(
                                            hasProperty("displayName", is("master-1.0")),
                                            hasProperty("displayName", is("master-1.1"))
                                    ))
                            ),
                            allOf(
                                    instanceOf(MultiBranchProjectViewHolder.ViewImpl.class),
                                    hasProperty("viewName", is(ChangeRequestSCMHeadCategory.DEFAULT.getName())),
                                    hasProperty("items", containsInAnyOrder(
                                            hasProperty("displayName", is("Change request #1 (CR-1)")),
                                            hasProperty("displayName", is("Change request #2 (CR-2)"))
                                    ))
                            )
                    ));
        }
    }

}
