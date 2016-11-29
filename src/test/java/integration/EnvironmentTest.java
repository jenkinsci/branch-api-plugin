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

import hudson.model.TopLevelItem;
import integration.harness.BasicBranchProjectFactory;
import integration.harness.BasicDummyStepBranchProperty;
import integration.harness.BasicMultiBranchProject;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMSource;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

public class EnvironmentTest {

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
    public void given_multibranch_when_buildingABranch_then_environmentContainsBranchName() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.setProjectFactory(new BasicBranchProjectFactory());
            BranchSource source = new BranchSource(new MockSCMSource(null, c, "foo", true, false, false));
            source.setStrategy(
                    new DefaultBranchPropertyStrategy(new BranchProperty[]{new BasicDummyStepBranchProperty()}));
            prj.getSourcesList().add(source);
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(r.getLog(prj.getItem("master").getBuildByNumber(1)), containsString("BRANCH_NAME=master"));
        }
    }

    @Test
    public void given_multibranch_when_buildingAChangeRequest_then_environmentContainsBranchName() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.setProjectFactory(new BasicBranchProjectFactory());
            BranchSource source = new BranchSource(new MockSCMSource(null, c, "foo", false, false, true));
            source.setStrategy(
                    new DefaultBranchPropertyStrategy(new BranchProperty[]{new BasicDummyStepBranchProperty()}));
            prj.getSourcesList().add(source);
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(r.getLog(prj.getItem("CR-" + crNum).getBuildByNumber(1)),
                    containsString("BRANCH_NAME=CR-" + crNum));
        }
    }

    @Test
    public void given_multibranch_when_buildingAChangeRequest_then_environmentContainsChangeDetails() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            Integer crNum = c.openChangeRequest("foo", "master");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.setProjectFactory(new BasicBranchProjectFactory());
            BranchSource source = new BranchSource(new MockSCMSource(null, c, "foo", false, false, true));
            source.setStrategy(
                    new DefaultBranchPropertyStrategy(new BranchProperty[]{new BasicDummyStepBranchProperty()}));
            prj.getSourcesList().add(source);
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(r.getLog(prj.getItem("CR-" + crNum).getBuildByNumber(1)),
                    allOf(
                            containsString("CHANGE_ID=" + crNum),
                            containsString("CHANGE_URL=http://changes.example.com/" + crNum),
                            anyOf(
                                    containsString("CHANGE_TITLE='Change request #" + crNum + "'"), // unix
                                    containsString("CHANGE_TITLE=Change request #" + crNum + "") // win
                            ),
                            containsString("CHANGE_AUTHOR=bob"),
                            anyOf(
                                    containsString("CHANGE_AUTHOR_DISPLAY_NAME='Bob Smith'"), // unix
                                    containsString("CHANGE_AUTHOR_DISPLAY_NAME=Bob Smith") // win
                            ),
                            containsString("CHANGE_AUTHOR_EMAIL=bob@example.com"),
                            containsString("CHANGE_TARGET=master")
                    ));
        }
    }

}
