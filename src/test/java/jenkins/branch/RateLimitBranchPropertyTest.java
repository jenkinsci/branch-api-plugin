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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.TopLevelItem;
import hudson.model.queue.QueueTaskFuture;
import hudson.util.ListBoxModel;
import integration.harness.BasicMultiBranchProject;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMSource;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeThat;

public class RateLimitBranchPropertyTest {
    /**
     * How long to wait for a build that we expect will start. (Shouldn't affect test execution time)
     */
    private static final long BUILT_TO_START_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(30);
    /**
     * How long to wait for a build that should not start in order to test that it didn't. (Will directly affect test
     * execution time)
     */
    private static final long BUILD_TO_NOT_START_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(10);
    /**
     * All tests in this class only create items and do not affect other global configuration, thus we trade test
     * execution time for the restriction on only touching items.
     */
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @ClassRule
    public static LoggerRule loggerRule = new LoggerRule().record(RateLimitBranchProperty.class, Level.FINE);
    /**
     * Our logger.
     */
    private static Logger LOGGER = Logger.getLogger(RateLimitBranchPropertyTest.class.getName());

    @Before
    public void cleanOutAllItems() throws Exception {
        for (TopLevelItem i : r.getInstance().getItems()) {
            i.delete();
        }
    }

    @Test
    public void getCount() throws Exception {
        for (int i = 1; i < 1001; i++) {
            assertThat(new RateLimitBranchProperty(i, "hour", false).getCount(), is(i));
        }
    }

    @Test
    public void getCount_lowerBound() throws Exception {
        assertThat(new RateLimitBranchProperty(0, "hour", false).getCount(), is(1));
    }

    @Test
    public void getCount_upperBound() throws Exception {
        assertThat(new RateLimitBranchProperty(1001, "hour", false).getCount(), is(1000));
    }

    @Test
    public void getDurationName() throws Exception {
        assertThat(new RateLimitBranchProperty(10, "hour", false).getDurationName(), is("hour"));
        assertThat(new RateLimitBranchProperty(10, "year", false).getDurationName(), is("year"));
        assertThat(new RateLimitBranchProperty(10, "minute", false).getDurationName(), is("minute"));
        assertThat(new RateLimitBranchProperty(10, "second", false).getDurationName(), is("second"));
    }

    @Test
    public void checkDurationNameExists() throws Exception {
        ListBoxModel items = r.jenkins.getDescriptorByType(RateLimitBranchProperty.JobPropertyImpl.DescriptorImpl.class).doFillDurationNameItems();
        assertEquals(items.size(), 7);
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
    private void rateLimitsBlockBuilds(int rate) throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            BranchSource source = new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches()));
            source.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[]{
                    new RateLimitBranchProperty(rate, "hour", false)
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
                                    hasProperty("durationName", is("hour")),
                                    hasProperty("userBoost", is(false))
                            )
                    )
            );
            assertThat(master.isInQueue(), is(false));
            assertThat(master.getQueueItem(), nullValue());
            assertThat(master.getBuilds().getLastBuild(), notNullValue());
            long startTime = master.getBuilds().getLastBuild().getTimeInMillis();
            QueueTaskFuture<FreeStyleBuild> future = master.scheduleBuild2(0);

            // let the item get added to the queue
            while (!master.isInQueue()) {
                Thread.yield();
            }
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
            final long delay = (long)(60.f * 60.f / rate * 1000);
            assumeThat("At least the rate implied delay but no more than 500ms longer",
                    System.currentTimeMillis() - startTime,
                    allOf(
                            greaterThanOrEqualTo(delay - 200L),
                            lessThanOrEqualTo(delay + 500L)
                    )
            );
            future.get();
        }
    }

    @Test
    public void rateLimitsConcurrentBuilds() throws Exception {
        int rate = 1000;
        try (final MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            BranchSource source = new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches()));
            BasicParameterDefinitionBranchProperty p = new BasicParameterDefinitionBranchProperty();
            p.setParameterDefinitions(Collections.singletonList(new StringParameterDefinition("FOO", "BAR")));
            source.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[]{
                    new RateLimitBranchProperty(rate, "hour", false),
                    new ConcurrentBuildBranchProperty(),
                    p
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
            Thread.sleep(1);
            QueueTaskFuture<FreeStyleBuild> future2 = master.scheduleBuild2(0, (Cause) null,
                    new ParametersAction(
                            Collections.singletonList(new StringParameterValue("FOO", "MANCHU"))));
            assertThat(future, not(is(future2)));

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
            long midTime = startTime + 60*60/rate*1000L*5; // at least 5 times the expected delay
            while (!startCondition.isDone() && System.currentTimeMillis() < midTime) {
                Queue.getInstance().maintain();
                Thread.sleep(100);
            }
            assertThat(startCondition.isDone(), is(true));
            assertThat(master.isInQueue(), is(true));
            FreeStyleBuild firstBuild = startCondition.get();
            // now we wait for the start... invoking queue maintain every 100ms so that the queue
            // will pick up more responsively than the default 5s
            startCondition = future2.getStartCondition();
            long endTime = startTime + 60*60/rate*1000L*5; // at least 5 times the expected delay
            while (!startCondition.isDone() && System.currentTimeMillis() < endTime) {
                Queue.getInstance().maintain();
                Thread.sleep(100);
            }
            assertThat(startCondition.isDone(), is(true));
            assertThat(master.isInQueue(), is(false));
            FreeStyleBuild secondBuild = startCondition.get();
            // it can take more than the requested delay... that's ok, but it should not be
            // more than 500ms longer (i.e. 5 of our Queue.maintain loops above)
            final long delay = (long)(60.f * 60.f / rate * 1000);
            assumeThat("At least the rate implied delay but no more than 500ms longer",
                    secondBuild.getStartTimeInMillis() - firstBuild.getStartTimeInMillis(),
                    allOf(
                            greaterThanOrEqualTo(delay - 200L),
                            lessThanOrEqualTo(delay + 500L)
                    )
            );
            future.get();
        }

    }

    @Test
    public void rateLimitsUserBoost() throws Exception {
        try (final MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            BranchSource source = new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches()));
            BasicParameterDefinitionBranchProperty p = new BasicParameterDefinitionBranchProperty();
            p.setParameterDefinitions(Collections.singletonList(new StringParameterDefinition("FOO", "BAR")));
            source.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[]{
                    new RateLimitBranchProperty(1, "hour", true),
                    p
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
                                    hasProperty("count", is(1)),
                                    hasProperty("durationName", is("hour"))
                            )
                    )
            );
            assertThat(master.isInQueue(), is(false));
            assertThat(master.getQueueItem(), nullValue());

            // trigger first build... this should start as it is the first build
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
            LOGGER.info("Waiting for first build");
            Future<FreeStyleBuild> startCondition = future.getStartCondition();
            long midTime = startTime + BUILT_TO_START_DELAY_MILLIS;
            while (!startCondition.isDone() && System.currentTimeMillis() < midTime) {
                Queue.getInstance().maintain();
                Thread.sleep(100);
            }
            assertThat(startCondition.isDone(), is(false));
            assertThat(master.isInQueue(), is(true));

            // trigger second build, this should be blocked as it is not user caused
            LOGGER.info("Checking second build blocked");
            future = master.scheduleBuild2(0);
            // now we wait for the start... invoking queue maintain every 100ms so that the queue
            // will pick up more responsively than the default 5s
            startCondition = future.getStartCondition();
            // it's ok that the end time is short, we will re-verify the not-started at the end after waiting
            // for a request submitted after to have been built.
            long endTime = System.currentTimeMillis() + BUILD_TO_NOT_START_DELAY_MILLIS;
            while (!startCondition.isDone() && System.currentTimeMillis() < endTime) {
                Queue.getInstance().maintain();
                Thread.sleep(100);
            }
            assertThat(startCondition.isDone(), is(false));
            assertThat(master.isInQueue(), is(true));

            // now we trigger a user build... it should skip the queue
            QueueTaskFuture<FreeStyleBuild> future2 = master.scheduleBuild2(0, new Cause.UserIdCause(),
                    new ParametersAction(
                            Collections.singletonList(new StringParameterValue("FOO", "MANCHU"))));
            // now we wait for the start... invoking queue maintain every 100ms so that the queue
            // will pick up more responsively than the default 5s
            LOGGER.info("Checking user submitted build skips");
            startCondition = future2.getStartCondition();
            endTime = System.currentTimeMillis() + BUILT_TO_START_DELAY_MILLIS;
            while (!startCondition.isDone() && System.currentTimeMillis() < endTime) {
                Queue.getInstance().maintain();
                Thread.sleep(100);
            }
            assertThat("Non user triggered build still blocked", future.getStartCondition().isDone(), is(false));
            assertThat("User triggered build successful", future2.getStartCondition().isDone(), is(true));
            assertThat(master.isInQueue(), is(true));
            future.cancel(true);
        }

    }

    @Test
    public void configRoundtrip() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            BranchSource source = new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches()));
            source.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[]{
                    new RateLimitBranchProperty(10, "day", true)
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
            assertThat(property.isUserBoost(), is(true));
        }
    }

    public static class ConcurrentBuildBranchProperty extends BranchProperty {
        @Override
        public <P extends Job<P, B>, B extends Run<P, B>> JobDecorator<P, B> jobDecorator(
                Class<P> clazz) {
            if (FreeStyleProject.class.isAssignableFrom(clazz)) {
                return (JobDecorator<P, B>) new ProjectDecorator<FreeStyleProject, FreeStyleBuild>(){
                    @NonNull
                    @Override
                    public FreeStyleProject project(@NonNull FreeStyleProject project) {
                        try {
                            project.setConcurrentBuild(true);
                        } catch (IOException e) {
                            // ignore
                        }
                        return super.project(project);
                    }
                };
            }
            return null;
        }

        @TestExtension
        public static class DescriptorImpl extends BranchPropertyDescriptor {
            @Override
            protected boolean isApplicable(@NonNull MultiBranchProjectDescriptor projectDescriptor) {
                return projectDescriptor instanceof BasicMultiBranchProject.DescriptorImpl;
            }
        }
    }

    public static class BasicParameterDefinitionBranchProperty extends ParameterDefinitionBranchProperty {
        public BasicParameterDefinitionBranchProperty() {
        }

        @TestExtension
        public static class DescriptorImpl extends BranchPropertyDescriptor {
            @Override
            protected boolean isApplicable(@NonNull MultiBranchProjectDescriptor projectDescriptor) {
                return projectDescriptor instanceof BasicMultiBranchProject.DescriptorImpl;
            }
        }
    }
}
