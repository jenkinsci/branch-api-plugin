/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import hudson.EnvVars;
import hudson.model.EnvironmentContributor;
import hudson.model.FreeStyleProject;
import hudson.model.TopLevelItem;
import hudson.util.LogTaskListener;
import integration.harness.BasicMultiBranchProject;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.impl.mock.MockChangeRequestFlags;
import jenkins.scm.impl.mock.MockRepositoryFlags;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMDiscoverChangeRequests;
import jenkins.scm.impl.mock.MockSCMDiscoverTags;
import jenkins.scm.impl.mock.MockSCMSource;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

public class BranchNameContributorTest {
    private static final Logger LOGGER = Logger.getLogger(BranchNameContributorTest.class.getName());
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
    public void buildEnvironmentFor() throws Exception {
        BranchNameContributor instance =
                r.jenkins.getExtensionList(EnvironmentContributor.class).get(BranchNameContributor.class);
        assertThat("The extension is registered", instance, notNullValue());
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo", MockRepositoryFlags.FORKABLE);
            c.setPrimaryBranch("foo", "main");
            Integer cr1Num = c.openChangeRequest("foo", "master");
            Integer cr2Num = c.openChangeRequest("foo", "master", MockChangeRequestFlags.FORK);
            c.createTag("foo", "master", "v1.0");
            c.createBranch("foo", "main");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches(), new MockSCMDiscoverTags(), new MockSCMDiscoverChangeRequests())));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat("We now have branches",
                    prj.getItems(), not(is(Collections.<FreeStyleProject>emptyList())));
            FreeStyleProject master = prj.getItem("master");
            FreeStyleProject cr1 = prj.getItem("CR-" + cr1Num);
            FreeStyleProject cr2 = prj.getItem("CR-" + cr2Num);
            FreeStyleProject tag = prj.getItem("v1.0");
            FreeStyleProject primaryBranch = prj.getItem("main");
            assertThat("We now have the master branch", master, notNullValue());
            assertThat("We now have the origin CR branch", cr1, notNullValue());
            assertThat("We now have the form CR branch", cr2, notNullValue());
            assertThat("We now have the tag branch", tag, notNullValue());
            assertThat("We now have the primary branch", primaryBranch, notNullValue());
            EnvVars env = new EnvVars();
            instance.buildEnvironmentFor(master, env, new LogTaskListener(LOGGER, Level.FINE));
            assertThat(env.keySet(), contains(is("BRANCH_NAME"), is("FOLDER_NAME")));
            assertThat(env.get("BRANCH_NAME"), is("master"));
            assertThat(env.keySet(), not(contains(is("BRANCH_IS_PRIMARY"))));

            env = new EnvVars();
            instance.buildEnvironmentFor(cr1, env, new LogTaskListener(LOGGER, Level.FINE));
            assertThat(env.keySet(), containsInAnyOrder(
                    is("FOLDER_NAME"),
                    is("BRANCH_NAME"),
                    is("CHANGE_ID"),
                    is("CHANGE_TARGET"),
                    is("CHANGE_TITLE"),
                    is("CHANGE_URL"),
                    is("CHANGE_BRANCH"),
                    is("CHANGE_AUTHOR"),
                    is("CHANGE_AUTHOR_EMAIL"),
                    is("CHANGE_AUTHOR_DISPLAY_NAME")
            ));
            assertThat(env.get("BRANCH_NAME"), is("CR-" + cr1Num));
            assertThat(env.keySet(), not(contains(is("BRANCH_IS_PRIMARY"))));
            assertThat(env.get("CHANGE_ID"), is(cr1Num.toString()));
            assertThat(env.get("CHANGE_TARGET"), is("master"));
            assertThat(env.get("CHANGE_BRANCH"), is("CR-" + cr1Num));
            assertThat(env.get("CHANGE_TITLE"), is("Change request #" + cr1Num));
            assertThat(env.get("CHANGE_URL"), is("http://changes.example.com/" + cr1Num));
            assertThat(env.get("CHANGE_AUTHOR"), is("bob"));
            assertThat(env.get("CHANGE_AUTHOR_EMAIL"), is("bob@example.com"));
            assertThat(env.get("CHANGE_AUTHOR_DISPLAY_NAME"), is("Bob Smith"));

            env = new EnvVars();
            instance.buildEnvironmentFor(cr2, env, new LogTaskListener(LOGGER, Level.FINE));
            assertThat(env.keySet(), containsInAnyOrder(
                    is("FOLDER_NAME"),
                    is("BRANCH_NAME"),
                    is("CHANGE_ID"),
                    is("CHANGE_TARGET"),
                    is("CHANGE_TITLE"),
                    is("CHANGE_URL"),
                    is("CHANGE_BRANCH"),
                    is("CHANGE_FORK"),
                    is("CHANGE_AUTHOR"),
                    is("CHANGE_AUTHOR_EMAIL"),
                    is("CHANGE_AUTHOR_DISPLAY_NAME")
            ));
            assertThat(env.get("BRANCH_NAME"), is("CR-" + cr2Num));
            assertThat(env.keySet(), not(contains(is("BRANCH_IS_PRIMARY"))));
            assertThat(env.get("CHANGE_ID"), is(cr2Num.toString()));
            assertThat(env.get("CHANGE_TARGET"), is("master"));
            assertThat(env.get("CHANGE_BRANCH"), is("CR-" + cr2Num));
            assertThat(env.get("CHANGE_FORK"), is("fork"));
            assertThat(env.get("CHANGE_TITLE"), is("Change request #" + cr2Num));
            assertThat(env.get("CHANGE_URL"), is("http://changes.example.com/" + cr2Num));
            assertThat(env.get("CHANGE_AUTHOR"), is("bob"));
            assertThat(env.get("CHANGE_AUTHOR_EMAIL"), is("bob@example.com"));
            assertThat(env.get("CHANGE_AUTHOR_DISPLAY_NAME"), is("Bob Smith"));

            env = new EnvVars();
            instance.buildEnvironmentFor(tag, env, new LogTaskListener(LOGGER, Level.FINE));
            assertThat(env.keySet(), containsInAnyOrder(
                    is("FOLDER_NAME"),
                    is("BRANCH_NAME"),
                    is("TAG_NAME"),
                    is("TAG_TIMESTAMP"),
                    is("TAG_UNIXTIME"),
                    is("TAG_DATE")
            ));
            assertThat(env.get("BRANCH_NAME"), is("v1.0"));
            assertThat(env.keySet(), not(contains(is("BRANCH_IS_PRIMARY"))));
            assertThat(env.get("TAG_NAME"), is("v1.0"));
            assertThat(env.get("TAG_TIMESTAMP"), not(is("")));
            assertThat(env.get("TAG_UNIXTIME"), not(is("")));
            assertThat(env.get("TAG_DATE"), not(is("")));

            env = new EnvVars();
            instance.buildEnvironmentFor(primaryBranch, env, new LogTaskListener(LOGGER, Level.FINE));
            assertThat(env.keySet(), containsInAnyOrder(
                    is("FOLDER_NAME"),
                    is("BRANCH_NAME"),
                    is("BRANCH_IS_PRIMARY")
            ));
            assertThat(env.get("BRANCH_NAME"), is("main"));
            assertThat(env.get("BRANCH_IS_PRIMARY"), is("true"));
        }
    }

}
