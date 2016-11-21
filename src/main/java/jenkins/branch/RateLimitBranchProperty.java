/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.TimeUnit2;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * A branch property that limits how often a specific branch can be built.
 *
 * @author Stephen Connolly
 */
@SuppressWarnings("unused") // instantiated by stapler
public class RateLimitBranchProperty extends BranchProperty {

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(RateLimitBranchProperty.class.getName());

    /**
     * The durations that we know about.
     */
    private static final Map<String, Long> DURATIONS = createDurations();

    /**
     * Initializer for {@link #DURATIONS}
     *
     * @return initial value.
     */
    private static Map<String, Long> createDurations() {
        Map<String, Long> result = new LinkedHashMap<String, Long>();
        result.put("hour", TimeUnit2.HOURS.toMillis(1));
        result.put("day", TimeUnit2.DAYS.toMillis(1));
        result.put("week", TimeUnit2.DAYS.toMillis(7));
        result.put("month", TimeUnit2.DAYS.toMillis(31));
        result.put("year", TimeUnit2.DAYS.toMillis(365));
        return Collections.unmodifiableMap(result);
    }

    /**
     * The name of the duration.
     */
    private final String durationName;

    /**
     * The maximum builds within the duration.
     */
    private final int count;

    /**
     * Constructor for stapler.
     */
    @DataBoundConstructor
    @SuppressWarnings("unused") // instantiated by stapler
    public RateLimitBranchProperty(int count, String durationName) {
        this.count = Math.min(Math.max(1, count), 1000);
        this.durationName = durationName == null || !DURATIONS.containsKey(durationName) ? "hour" : durationName;
    }

    /**
     * Gets the maximum builds within the duration.
     *
     * @return the maximum builds within the duration.
     */
    @SuppressWarnings("unused") // invoked by jelly EL
    public int getCount() {
        return count;
    }

    /**
     * Gets the duration.
     *
     * @return the duration.
     */
    @SuppressWarnings("unused") // invoked by jelly EL
    public String getDurationName() {
        return durationName;
    }

    @Override
    public <P extends Job<P, B>, B extends Run<P, B>> JobDecorator<P, B> jobDecorator(
            Class<P> jobType) {
        return new JobDecorator<P, B>() {
            /**
             * {@inheritDoc}
             */
            @NonNull
            @Override
            public List<JobProperty<? super P>> jobProperties(
                    @NonNull List<JobProperty<? super P>> properties) {
                List<JobProperty<? super P>> result = asArrayList(properties);
                result.add(new JobPropertyImpl(count == 0 ? null : new Throttle(count, durationName)));
                return result;
            }
        };
    }

    /**
     * Our descriptor
     */
    @Extension
    @SuppressWarnings("unused") // instantiated by jenkins
    public static class DescriptorImpl extends BranchPropertyDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.RateLimitBranchProperty_DisplayName();
        }

        /**
         * Fill the duration names
         */
        @SuppressWarnings("unused") // by stapler
        public ListBoxModel doFillDurationNameItems() {
            return Jenkins.getActiveInstance().getDescriptorByType(JobPropertyImpl.DescriptorImpl.class)
                    .doFillDurationNameItems();
        }

        /**
         * Check the count
         */
        public FormValidation doCheckCount(@QueryParameter int value, @QueryParameter String durationName) {
            return Jenkins.getActiveInstance().getDescriptorByType(JobPropertyImpl.DescriptorImpl.class)
                    .doCheckCount(value, durationName);
        }
    }

    /**
     * This class is to work around some annoying "features" of f:optionalBlock
     */
    public static class Throttle {

        /**
         * The name of the duration.
         */
        private final String durationName;

        /**
         * The maximum builds within the duration.
         */
        private final int count;

        @DataBoundConstructor
        public Throttle(int count, String durationName) {
            this.count = Math.min(Math.max(0, count), 1000);
            this.durationName = durationName == null || !DURATIONS.containsKey(durationName) ? "hour" : durationName;
        }

        /**
         * Gets the maximum builds within the duration.
         *
         * @return the maximum builds within the duration.
         */
        public int getCount() {
            return count;
        }

        /**
         * Gets the duration.
         *
         * @return the duration.
         */
        public String getDurationName() {
            return durationName;
        }

    }

    public static class JobPropertyImpl extends JobProperty<Job<?, ?>> {

        /**
         * The name of the duration.
         */
        private final String durationName;

        /**
         * The maximum builds within the duration.
         */
        private final int count;

        /**
         * The milliseconds of the duration.
         */
        private transient long duration;

        /**
         * The throttle (to save having to keep creating this a lot).
         */
        private transient Throttle throttle;

        @DataBoundConstructor
        public JobPropertyImpl(Throttle throttle) {
            this.throttle = throttle;
            this.count = throttle == null ? 0 : Math.min(Math.max(0, throttle.getCount()), 1000);
            this.durationName = throttle == null ? "hour" : throttle.getDurationName();
        }

        /**
         * Gets the maximum builds within the duration.
         *
         * @return the maximum builds within the duration.
         */
        public int getCount() {
            return count;
        }

        /**
         * Gets the duration name.
         *
         * @return the duration name.
         */
        public String getDurationName() {
            return durationName;
        }

        /**
         * Gets the duration.
         *
         * @return the duration.
         */
        public long getDuration() {
            if (duration < 1) {
                final String durationName = getDurationName();
                duration =
                        DURATIONS.containsKey(durationName) ? DURATIONS.get(durationName) : TimeUnit2.HOURS.toMillis(1);
            }
            return duration;
        }

        /**
         * Returns the {@link Throttle}.
         *
         * @return the {@link Throttle} or {@code null} if there is none.
         */
        @SuppressWarnings("unused") // invoked by Jelly EL
        public Throttle getThrottle() {
            if (count == 0) {
                return null;
            }
            if (throttle == null) {
                throttle = new Throttle(count, durationName);
            }
            return throttle;
        }

        /**
         * Returns the minimum time between builds required to enforce the throttle.
         *
         * @return the minimum time between builds required to enforce the throttle.
         */
        public long getMillisecondsBetweenBuilds() {
            return getCount() == 0 ? 0 : getDuration() / (Math.max(1, getCount()));
        }

        /**
         * Our descriptor.
         */
        @Extension
        @SuppressWarnings("unused") // instantiated by jenkins
        public static class DescriptorImpl extends JobPropertyDescriptor {

            @Override
            public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
                JobPropertyImpl prop = (JobPropertyImpl) super.newInstance(req, formData);
                return prop.getThrottle() != null ? prop : null;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.RateLimitBranchProperty_DisplayName();
            }

            /**
             * Fill the duration names
             */
            @SuppressWarnings("unused") // by stapler
            public ListBoxModel doFillDurationNameItems() {
                ListBoxModel result = new ListBoxModel();
                for (String unit : DURATIONS.keySet()) {
                    result.add(
                            ResourceBundleHolder.get(Messages.class).format("RateLimitBranchProperty.duration." + unit),
                            unit);
                }
                return result;
            }

            /**
             * Check the count
             */
            public FormValidation doCheckCount(@QueryParameter int value, @QueryParameter String durationName) {
                long duration =
                        DURATIONS.containsKey(durationName) ? DURATIONS.get(durationName) : TimeUnit2.HOURS.toMillis(1);
                if (value == 0) {
                    return FormValidation.ok();
                }
                long interval = duration / Math.max(1, value);
                if (interval < TimeUnit2.SECONDS.toMillis(1)) {
                    return FormValidation.ok();
                }
                if (interval < TimeUnit2.MINUTES.toMillis(1)) {
                    return FormValidation.ok(
                            Messages.RateLimitBranchProperty_ApproxSecsBetweenBuilds(
                                            TimeUnit2.MILLISECONDS.toSeconds(interval)));
                }
                if (interval < TimeUnit2.HOURS.toMillis(2)) {
                    return FormValidation.ok(
                            Messages.RateLimitBranchProperty_ApproxMinsBetweenBuilds(
                                            TimeUnit2.MILLISECONDS.toMinutes(interval)));
                }
                if (interval < TimeUnit2.DAYS.toMillis(2)) {
                    return FormValidation.ok(
                            Messages.RateLimitBranchProperty_ApproxHoursBetweenBuilds(
                                            TimeUnit2.MILLISECONDS.toHours(interval)));
                }
                if (interval < TimeUnit2.DAYS.toMillis(14)) {
                    return FormValidation.ok(
                                    Messages.RateLimitBranchProperty_ApproxDaysBetweenBuilds(
                                            TimeUnit2.MILLISECONDS.toDays(interval)));
                }
                return FormValidation.ok(Messages.RateLimitBranchProperty_ApproxWeeksBetweenBuilds(
                                TimeUnit2.MILLISECONDS.toDays(interval) / 7));
            }
        }
    }

    /**
     * This does the work of blocking builds while the throttle is enforced.
     */
    @Extension
    @SuppressWarnings("unused") // instantiated by Jenkins
    public static class QueueTaskDispatcherImpl extends QueueTaskDispatcher {
        /**
         * {@inheritDoc}
         */
        @Override
        public CauseOfBlockage canRun(Queue.Item item) {
            if (item.task instanceof Job) {
                Job<?,?> job = (Job) item.task;
                JobPropertyImpl property = job.getProperty(JobPropertyImpl.class);
                if (property != null) {
                    LOGGER.log(Level.FINER, "{0} has a rate limit of {1} builds per {2}", new Object[]{job.getFullName(), property.getCount(), property.getDurationName()});
                    Run lastBuild = job.getLastBuild();
                    if (lastBuild != null) {
                        long timeSinceLastBuild = System.currentTimeMillis() - lastBuild.getTimeInMillis();
                        long betweenBuilds = property.getMillisecondsBetweenBuilds();
                        if (timeSinceLastBuild < betweenBuilds) {
                            LOGGER.log(Level.FINE, "{0} will be delayed for another {1}ms as it is {2}ms since last build and ideal rate is {3}ms between builds", new Object[]{job.getFullName(), betweenBuilds - timeSinceLastBuild, timeSinceLastBuild, betweenBuilds});
                            return CauseOfBlockage.fromMessage(Messages._RateLimitBranchProperty_BuildBlocked(
                                    new Date(lastBuild.getTimeInMillis() + betweenBuilds))
                            );
                        }
                    }
                }
            }
            return null;
        }
    }
}
