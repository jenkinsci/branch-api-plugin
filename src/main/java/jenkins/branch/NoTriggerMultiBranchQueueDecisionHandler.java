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

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.model.Queue;
import java.util.List;
import java.util.Optional;
import org.jvnet.localizer.Localizable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public abstract class NoTriggerMultiBranchQueueDecisionHandler extends Queue.QueueDecisionHandler {

    @Override
    public boolean shouldSchedule(Queue.Task p, List<Action> actions) {
        if (!isMultiBranchJob(p)) {
            return true;
        }
        for (Action action : actions) {
            if (!(action instanceof CauseAction)) {
                continue;
            }
            Optional<Boolean> result = processAction((Job) p, (CauseAction) action);
            if (result.isPresent()) {
                return result.get();
            }
        }
        return true;
    }

    private boolean isMultiBranchJob(Queue.Task task) {
        if (!(task instanceof Job)) {
            return false;
        }
        Job<?, ?> job = (Job) task;
        return job.getParent() instanceof MultiBranchProject;
    }

    @VisibleForTesting
    Optional<Boolean> processAction(Job<?, ?> job, CauseAction action) {
        for (Cause cause : action.getCauses()) {
            if (!(cause instanceof BranchIndexingCause || cause instanceof BranchEventCause)) {
                continue;
            }
            Iterable<?> properties = getJobProperties((MultiBranchProject) job.getParent(), job);

            OverrideIndexTriggersJobProperty overrideTriggersProperty = findProperty(OverrideIndexTriggersJobProperty.class, properties);
            if (overrideTriggersProperty != null) {
                return Optional.of(overrideTriggersProperty.getEnableTriggers());
            }

            NoTriggerProperty noTriggerProperty = findProperty(NoTriggerProperty.class, properties);
            if (noTriggerProperty == null) {
                return Optional.of(Boolean.TRUE);
            }
            return Optional.of(!shouldSuppressBuild(job, cause, noTriggerProperty));
        }
        return Optional.empty();
    }

    /**
     * Returns all job properties.
     *
     * @param project the project to which the job belongs to.
     * @param job     the job of which the properties are returned.
     * @return the job properties.
     */
    @NonNull
    protected abstract Iterable<? extends Object> getJobProperties(MultiBranchProject project, Job job);

    private static <T> T findProperty(Class<T> clazz, Iterable<?> properties) {
        for (Object property : properties) {
            if (clazz.isAssignableFrom(property.getClass())) {
                return (T) property;
            }
        }
        return null;
    }

    private static boolean shouldSuppressBuild(Job<?, ?> job, Cause cause, NoTriggerProperty property) {
        if (!getBranchNameOf(job).matches(property.getTriggeredBranchesRegex())) {
            return true;
        }
        return property.getStrategy().shouldSuppress(cause);
    }

    private static String getBranchNameOf(Job<?, ?> job) {
        // Not necessarily the same as j.getName(), which may be encoded
        return ((MultiBranchProject) job.getParent()).getProjectFactory().getBranch(job).getName();
    }

    /**
     * Keeps configuration used to determine whether builds requested by {@link BranchIndexingCause}
     * or {@link BranchEventCause} should be suppressed.
     *
     * @see NoTriggerBranchProperty
     * @see NoTriggerOrganizationFolderProperty
     */
    public interface NoTriggerProperty {

        /**
         * Returns a regular expressions which determines which builds should be scheduled.
         *
         * @return the branch name regular expression.
         */
        @NonNull
        String getTriggeredBranchesRegex();

        /**
         * Returns a strategy which determines which builds should be suppressed.
         *
         * @return the suppression strategy.
         */
        @NonNull
        SuppressionStrategy getStrategy();
    }

    /**
     * Strategy which determines which builds should be suppressed.
     */
    public enum SuppressionStrategy {
        /**
         * Only builds triggered by {@link BranchIndexingCause} are suppressed.
         */
        INDEXING(Messages._NoTriggerProperty_strategy_indexing()),
        /**
         * Only builds triggered by {@link BranchEventCause} are suppressed.
         */
        EVENTS(Messages._NoTriggerProperty_strategy_events()),
        /**
         * All builds triggered by SCM are scheduled (nothing is suppressed).
         */
        NONE(Messages._NoTriggerProperty_strategy_none());

        private final Localizable displayName;

        SuppressionStrategy(Localizable displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName.toString();
        }

        public boolean shouldSuppress(Cause cause) {
            boolean indexing = cause instanceof BranchIndexingCause;
            boolean events = cause instanceof BranchEventCause;

            if (!indexing && !events) {
                String className = cause != null ? cause.getClass().getName() : "null";
                throw new IllegalArgumentException("Unsupported cause type: " + className);
            }

            return (indexing && this == INDEXING) || (events && this == EVENTS);
        }
    }
}
