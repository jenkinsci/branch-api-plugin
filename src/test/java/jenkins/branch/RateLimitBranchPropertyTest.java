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

package jenkins.branch;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.model.TopLevelItem;
import hudson.model.queue.QueueTaskFuture;
import integration.harness.BasicMultiBranchProject;
import integration.harness.MockSCMController;
import integration.harness.MockSCMSource;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class RateLimitBranchPropertyTest {
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
    public void getCount() throws Exception {
        for (int i = 1; i < 1001; i++) {
            assertThat(new RateLimitBranchProperty(i, "hour").getCount(), is(i));
        }
    }

    @Test
    public void getCount_lowerBound() throws Exception {
        assertThat(new RateLimitBranchProperty(0, "hour").getCount(), is(1));
    }

    @Test
    public void getCount_upperBound() throws Exception {
        assertThat(new RateLimitBranchProperty(1001, "hour").getCount(), is(1000));
    }

    @Test
    public void getDurationName() throws Exception {
        assertThat(new RateLimitBranchProperty(10, "hour").getDurationName(), is("hour"));
        assertThat(new RateLimitBranchProperty(10, "year").getDurationName(), is("year"));

    }

    @Test
    public void rateLimitsBlockBuilds_maxRate() throws Exception {
        rateLimitsBlockBuilds(1000);
    }

    @Test
    public void rateLimitsBlockBuilds_medRate() throws Exception {
        rateLimitsBlockBuilds(500);
    }

    // we run this test at two rates which have more than the error margin (500ms) in expected delay to ensure
    // that the delay is doubled when the rate is halved and thus rule out a false positive where there is a general
    // delay on all builds of more than the expected delay.
    public void rateLimitsBlockBuilds(int rate) throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            BranchSource source = new BranchSource(new MockSCMSource(null, c, "foo", true, false, false));
            source.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[]{
                    new RateLimitBranchProperty(rate, "hour") // once every 6 seconds
            }));
            prj.getSourcesList().add(source);
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();

            FreeStyleProject master = prj.getItem("master");
            master.setQuietPeriod(0);
            assertThat(master.getProperties(),
                    hasEntry(
                            instanceOf(RateLimitBranchProperty.JobPropertyImpl.DescriptorImpl.class),
                            allOf(
                                    instanceOf(RateLimitBranchProperty.JobPropertyImpl.class),
                                    hasProperty("count", is(rate)),
                                    hasProperty("durationName", is("hour"))
                            )
                    )
            );
            assertThat(master.isInQueue(), is(false));
            assertThat(master.getQueueItem(), nullValue());
            QueueTaskFuture<FreeStyleBuild> future = master.scheduleBuild2(0);

            // let the item get added to the queue
            while (!master.isInQueue()) {
                Thread.yield();
            }
            long startTime = System.currentTimeMillis();
            assertThat(master.isInQueue(), is(true));

            // while it is in the queue, until queue maintenance takes place, it will not be flagged as blocked
            // since we cannot know when queue maintenance happens from the periodic task
            // we cannot assert any value of isBlocked() on this side of maintenance
            Queue.getInstance().maintain();
            assertThat(master.getQueueItem().isBlocked(), is(true));
            assertThat(master.getQueueItem().getCauseOfBlockage().getShortDescription().toLowerCase(),
 containsString("throttle"));

            // now we wait for the start... invoking queue maintain every 100ms so that the queue
            // will pick up more responsively than the default 5s
            Future<FreeStyleBuild> startCondition = future.getStartCondition();
            long endTime = startTime + 60*60/rate*1000L*5; // at least 5 times the expected delay
            while (!startCondition.isDone() && System.currentTimeMillis() < endTime) {
                Queue.getInstance().maintain();
                Thread.sleep(100);
            }
            assertThat(startCondition.isDone(), is(true));
            // it can take more than the requested delay... that's ok, but it should not be
            // more than 500ms longer (i.e. 5 of our Queue.maintain loops above)
            assertThat("At least the rate implied delay but no more than 500ms longer",
                    System.currentTimeMillis() - startTime,
                    allOf(
                            greaterThanOrEqualTo(60 * 60 / rate * 1000L),
                            lessThanOrEqualTo(60 * 60 / rate * 1000L * 500L)
                    )
            );
            future.get();
        }

    }

    @Test
    public void configRoundtrip() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            BranchSource source = new BranchSource(new MockSCMSource(null, c, "foo", true, false, false));
            source.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[]{
                    new RateLimitBranchProperty(10, "day")
            }));
            prj.getSourcesList().add(source);
            r.configRoundtrip(prj);
            assertThat(prj.getSources().get(0).getStrategy(), instanceOf(DefaultBranchPropertyStrategy.class));
            DefaultBranchPropertyStrategy strategy =
                    (DefaultBranchPropertyStrategy) prj.getSources().get(0).getStrategy();
            assertThat(strategy.getProps().get(0), instanceOf(RateLimitBranchProperty.class));
            RateLimitBranchProperty property = (RateLimitBranchProperty)strategy.getProps().get(0);
            assertThat(property.getCount(), is(10));
            assertThat(property.getDurationName(), is("day"));
        }
    }
}
