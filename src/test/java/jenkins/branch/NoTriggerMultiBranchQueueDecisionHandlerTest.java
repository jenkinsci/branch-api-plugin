/*
 * The MIT License
 *
 * Copyright 2022 CloudBees, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cloudbees.hudson.plugins.folder.Folder;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Queue.Task;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.branch.NoTriggerMultiBranchQueueDecisionHandler.NoTriggerProperty;
import jenkins.branch.NoTriggerMultiBranchQueueDecisionHandler.SuppressionStrategy;
import jenkins.scm.api.SCMEvent;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class NoTriggerMultiBranchQueueDecisionHandlerTest {

    private static final String BRANCH_INDEXING_CAUSE_ID = "indexing";
    private static final String BRANCH_EVENT_CAUSE_ID = "event";

    @Test
    public void scheduleWhenNoJobIsPassed() {
        Task task = mock(Task.class);
        List<Action> actions = mock(List.class);

        boolean result = createHandler().shouldSchedule(task, actions);

        assertThat(result, is(Boolean.TRUE));
        verifyNoInteractions(actions);
    }

    @Test
    public void scheduleWhenNoMultiBranchIsPassed() {
        Job job = mock(JobImpl.class);
        Folder folder = mock(Folder.class);
        when(job.getParent()).thenReturn(folder);
        List<Action> actions = mock(List.class);

        boolean result = createHandler().shouldSchedule((Task) job, actions);

        assertThat(result, is(Boolean.TRUE));
        verifyNoInteractions(actions);
    }

    @Test
    public void scheduleWhenNoCauseActionsExist() {
        JobImpl job = mockMultiBranchJob();
        NoTriggerMultiBranchQueueDecisionHandler handler = spy(createHandler());
        List<Action> actions = Arrays.asList(mock(Action.class), mock(Action.class));

        boolean result = handler.shouldSchedule(job, actions);

        assertThat(result, is(Boolean.TRUE));
        verify(handler, never()).getBranchProperties((MultiBranchProject) job.getParent(), job);
    }

    @Test
    @Parameters({BRANCH_INDEXING_CAUSE_ID, BRANCH_EVENT_CAUSE_ID})
    public void scheduleWhenNoPropertiesAreSet(String causeTypeId) {
        JobImpl job = mockMultiBranchJob();
        NoTriggerMultiBranchQueueDecisionHandler handler = spy(createHandler());
        List<Action> actions = new ArrayList<>();
        actions.add(mock(Action.class));
        actions.add(mockCauseAction(mock(Cause.class), createCause(causeTypeId)));
        // no property is defined, so no need to check the second cause action
        CauseAction skippedAction = mockCauseAction();
        actions.add(skippedAction);

        boolean result = handler.shouldSchedule(job, actions);

        assertThat(result, is(Boolean.TRUE));
        verify(handler).getBranchProperties((MultiBranchProject) job.getParent(), job);
        verify(handler, never()).processAction(job, skippedAction);
    }

    @Test
    @Parameters({
        BRANCH_INDEXING_CAUSE_ID + ", true",
        BRANCH_INDEXING_CAUSE_ID + ", false",
        BRANCH_EVENT_CAUSE_ID + ", true",
        BRANCH_EVENT_CAUSE_ID + ", false"
    })
    public void overrideIndexTriggersJobPropertyTakesPrecedenceOverNoTriggerProperty(String causeTypeId, boolean override) {
        JobImpl job = mockMultiBranchJob();
        OverrideIndexTriggersJobProperty overrideTriggersProperty = new OverrideIndexTriggersJobProperty(override);
        when(job.getProperty(OverrideIndexTriggersJobProperty.class)).thenReturn(overrideTriggersProperty);
        NoTriggerProperty noTriggerProperty = mock(NoTriggerProperty.class);
        NoTriggerMultiBranchQueueDecisionHandler handler = spy(createHandler(noTriggerProperty));
        List<Action> actions = new ArrayList<>();
        actions.add(mock(Action.class));
        actions.add(mockCauseAction(mock(Cause.class), createCause(causeTypeId)));
        // the job properties are the same for all actions, so there is no need to check the second cause action
        CauseAction skippedAction = mockCauseAction();
        actions.add(skippedAction);

        boolean result = handler.shouldSchedule(job, actions);

        assertThat(result, is(override));
        verify(handler, never()).processAction(job, skippedAction);
        verifyNoInteractions(noTriggerProperty);
    }

    @Test
    @Parameters({BRANCH_INDEXING_CAUSE_ID, BRANCH_EVENT_CAUSE_ID})
    public void suppressWhenBranchNameDoesNotMatchTheRegex(String causeTypeId) {
        JobImpl job = mockMultiBranchJob("test/branch-123");
        NoTriggerProperty noTriggerProperty = mock(NoTriggerProperty.class);
        when(noTriggerProperty.getTriggeredBranchesRegex()).thenReturn("^main$");
        NoTriggerMultiBranchQueueDecisionHandler handler = createHandler(noTriggerProperty);
        List<Action> actions = Arrays.asList(mockCauseAction(createCause(causeTypeId), createCause(causeTypeId)));

        boolean result = handler.shouldSchedule(job, actions);

        assertThat(result, is(Boolean.FALSE));
        // executed only once because the job properties are the same for all actions (no need to check the second cause)
        verify(noTriggerProperty).getTriggeredBranchesRegex();
        // when branch name doesn't match, then it is always suppressed
        verify(noTriggerProperty, never()).getStrategy();
    }

    @Test
    @Parameters({BRANCH_INDEXING_CAUSE_ID, BRANCH_EVENT_CAUSE_ID})
    public void scheduleWhenBranchNameMatchesTheRegexAndNoneStrategyIsSet(String causeTypeId) {
        JobImpl job = mockMultiBranchJob("test/branch-123");
        NoTriggerProperty noTriggerProperty = spy(new NoTriggerPropertyImpl(".*", SuppressionStrategy.NONE));
        NoTriggerMultiBranchQueueDecisionHandler handler = createHandler(noTriggerProperty);
        List<Action> actions = Arrays.asList(mockCauseAction(createCause(causeTypeId), createCause(causeTypeId)));

        boolean result = handler.shouldSchedule(job, actions);

        assertThat(result, is(Boolean.TRUE));
        // both executed only once because the job properties are the same for all actions (no need to check the second cause)
        verify(noTriggerProperty).getTriggeredBranchesRegex();
        verify(noTriggerProperty).getStrategy();
    }

    @Test
    @Parameters({BRANCH_INDEXING_CAUSE_ID, BRANCH_EVENT_CAUSE_ID})
    public void suppressWhenBranchNameMatchesTheRegexAndStrategyShouldSuppressIt(String causeTypeId) {
        JobImpl job = mockMultiBranchJob("test");
        SuppressionStrategy strategy = BRANCH_INDEXING_CAUSE_ID.equals(causeTypeId) ? SuppressionStrategy.INDEXING : SuppressionStrategy.EVENTS;
        NoTriggerProperty noTriggerProperty = new NoTriggerPropertyImpl("test", strategy);
        NoTriggerMultiBranchQueueDecisionHandler handler = createHandler(noTriggerProperty);
        List<Action> actions = Arrays.asList(mockCauseAction(createCause(causeTypeId)));

        boolean result = handler.shouldSchedule(job, actions);

        assertThat(result, is(Boolean.FALSE));
    }

    @Test
    public void scheduleForEventCauseWhenBranchNameMatchesAndIndexingStrategyIsSet() {
        JobImpl job = mockMultiBranchJob("indexing");
        NoTriggerProperty noTriggerProperty = new NoTriggerPropertyImpl(".*", SuppressionStrategy.INDEXING);
        NoTriggerMultiBranchQueueDecisionHandler handler = createHandler(noTriggerProperty);
        List<Action> actions = Arrays.asList(mockCauseAction(createCause(BRANCH_EVENT_CAUSE_ID)));

        boolean result = handler.shouldSchedule(job, actions);

        assertThat(result, is(Boolean.TRUE));
    }

    @Test
    public void scheduleForIndexingCauseWhenBranchNameMatchesAndEventsStrategyIsSet() {
        JobImpl job = mockMultiBranchJob("event");
        NoTriggerProperty noTriggerProperty = new NoTriggerPropertyImpl(".*", SuppressionStrategy.EVENTS);
        NoTriggerMultiBranchQueueDecisionHandler handler = createHandler(noTriggerProperty);
        List<Action> actions = Arrays.asList(mockCauseAction(createCause(BRANCH_INDEXING_CAUSE_ID)));

        boolean result = handler.shouldSchedule(job, actions);

        assertThat(result, is(Boolean.TRUE));
    }

    private static NoTriggerMultiBranchQueueDecisionHandler createHandler() {
        return new NoTriggerMultiBranchQueueDecisionHandlerImpl(Collections.emptyList());
    }

    private static NoTriggerMultiBranchQueueDecisionHandler createHandler(NoTriggerProperty property) {
        return new NoTriggerMultiBranchQueueDecisionHandlerImpl(Arrays.asList(property));
    }

    private static JobImpl mockMultiBranchJob(String branchName) {
        JobImpl job = mock(JobImpl.class);
        MultiBranchProject project = mock(MultiBranchProject.class);
        when(job.getParent()).thenReturn(project);
        BranchProjectFactory factory = mock(BranchProjectFactory.class);
        when(project.getProjectFactory()).thenReturn(factory);
        Branch branch = mock(Branch.class);
        when(factory.getBranch(job)).thenReturn(branch);
        when(branch.getName()).thenReturn(branchName);
        return job;
    }

    private static JobImpl mockMultiBranchJob() {
        JobImpl job = mock(JobImpl.class);
        MultiBranchProject project = mock(MultiBranchProject.class);
        when(job.getParent()).thenReturn(project);
        return job;
    }

    private static CauseAction mockCauseAction(Cause... causes) {
        CauseAction action = mock(CauseAction.class);
        when(action.getCauses()).thenReturn(Arrays.stream(causes).collect(Collectors.toList()));
        return action;
    }

    private static Cause createCause(String causeTypeId) {
        if (BRANCH_INDEXING_CAUSE_ID.equals(causeTypeId)) {
            return new BranchIndexingCause();
        } else if (BRANCH_EVENT_CAUSE_ID.equals(causeTypeId)) {
            return new BranchEventCause(mock(SCMEvent.class), "description");
        } else {
            throw new UnsupportedOperationException("Type \"" + causeTypeId + "\" type is unsupported");
        }
    }

    private static class NoTriggerMultiBranchQueueDecisionHandlerImpl extends NoTriggerMultiBranchQueueDecisionHandler {

        private final List<Object> properties;

        NoTriggerMultiBranchQueueDecisionHandlerImpl(List<Object> properties) {
            this.properties = properties;
        }

        @NonNull
        @Override
        protected Iterable<? extends Object> getBranchProperties(MultiBranchProject project, Job job) {
            assertThat(project, notNullValue());
            assertThat(job, notNullValue());
            assertThat(job.getParent(), is(project));

            return properties;
        }
    }

    private static abstract class JobImpl extends Job implements Task {

        protected JobImpl(MultiBranchProject parent, String name) {
            super(parent, name);
        }

        @NonNull
        @Override
        public ItemGroup getParent() {
            throw new UnsupportedOperationException("Must be mocked");
        }
    }

    private static class NoTriggerPropertyImpl implements NoTriggerProperty {

        private final String triggeredBranchesRegex;
        private final SuppressionStrategy strategy;

        public NoTriggerPropertyImpl(@NonNull String triggeredBranchesRegex, @NonNull SuppressionStrategy strategy) {
            this.triggeredBranchesRegex = triggeredBranchesRegex;
            this.strategy = strategy;
        }

        @NonNull
        @Override
        public String getTriggeredBranchesRegex() {
            return triggeredBranchesRegex;
        }

        @NonNull
        @Override
        public SuppressionStrategy getStrategy() {
            return strategy;
        }
    }
}
