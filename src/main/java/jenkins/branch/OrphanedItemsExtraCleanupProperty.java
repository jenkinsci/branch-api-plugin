package jenkins.branch;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.PeriodicWork;
import hudson.scheduler.CronTabList;
import hudson.util.DescribableList;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.ObjectStreamException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OrphanedItemsExtraCleanupProperty extends AbstractFolderProperty {
    static final Logger LOGGER = Logger.getLogger(OrphanedItemsExtraCleanupProperty.class.getName());

    private long interval;
    private transient String cronTabSpec;
    private transient CronTabList tabs;

    @DataBoundConstructor
    public OrphanedItemsExtraCleanupProperty(String interval) {
        long intervalMillis = toIntervalMillis(interval);
        this.interval = intervalMillis;
        this.cronTabSpec = toCrontab(intervalMillis);
        this.tabs = CronTabList.create(cronTabSpec);
    }

    /**
     * Returns the interval between indexing.
     * Code borrowed from {@link PeriodicFolderTrigger}
     * to mimic the same behavior as when configuring multibranch scanning.
     *
     * @return the interval between indexing.
     */
    @SuppressWarnings("unused") // used by Jelly EL
    public String getInterval() {
        if (interval < TimeUnit.SECONDS.toMillis(1)) {
            return Long.toString(interval) + "ms";
        }
        if (interval < TimeUnit.MINUTES.toMillis(1)) {
            return Long.toString(TimeUnit.MILLISECONDS.toSeconds(interval)) + "s";
        }
        if (interval < TimeUnit.HOURS.toMillis(1)) {
            return Long.toString(TimeUnit.MILLISECONDS.toMinutes(interval)) + "m";
        }
        if (interval < TimeUnit.DAYS.toMillis(1)) {
            return Long.toString(TimeUnit.MILLISECONDS.toHours(interval)) + "h";
        }
        return Long.toString(TimeUnit.MILLISECONDS.toDays(interval)) + "d";
    }

    public CronTabList getTabs() {
        return tabs;
    }

    /**
     * Called when object has been deserialized from a stream.
     *
     * @return {@code this}, or a replacement for {@code this}.
     * @throws ObjectStreamException if the object cannot be restored.
     * @see <a href="http://download.oracle.com/javase/1.3/docs/guide/serialization/spec/input.doc6.html">The Java Object Serialization Specification</a>
     */
    private Object readResolve() throws ObjectStreamException {
        this.cronTabSpec = toCrontab(interval);
        this.tabs = CronTabList.create(cronTabSpec);
        return this;
    }

    @Extension @Symbol("orphanedItemsCleanup")
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Periodic Orphaned Items cleanup";
        }

        @Override
        public AbstractFolderProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return formData.optBoolean("specified") ? super.newInstance(req, formData) : null;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractFolder> containerType) {
            return MultiBranchProject.class.isAssignableFrom(containerType);
        }

        @SuppressWarnings("unused") // used by Jelly
        public ListBoxModel doFillIntervalItems() {
            PeriodicFolderTrigger.DescriptorImpl descriptor = ExtensionList.lookupSingleton(PeriodicFolderTrigger.DescriptorImpl.class);
            return descriptor.doFillIntervalItems();
        }
    }

    /**
     * Turns an interval into a suitable crontab.
     * Code borrowed from {@link PeriodicFolderTrigger}
     * to mimic the same behavior as when configuring multibranch scanning.
     *
     * @param millis the interval.
     * @return the crontab.
     */
    private static String toCrontab(long millis) {
        // we want to ensure the crontab wakes us excessively
        if (millis <= TimeUnit.MINUTES.toMillis(5)) {
            return "* * * * *"; // 0-5min: check every minute
        }
        if (millis <= TimeUnit.MINUTES.toMillis(30)) {
            return "H/5 * * * *"; // 11-30min: check every 5 minutes
        }
        if (millis <= TimeUnit.HOURS.toMillis(1)) {
            return "H/15 * * * *"; // 30-60min: check every 15 minutes
        }
        if (millis <= TimeUnit.HOURS.toMillis(8)) {
            return "H/30 * * * *"; // 61min-8hr: check every 30 minutes
        }
        if (millis <= TimeUnit.DAYS.toMillis(1)) {
            return "H H/4 * * *"; // 8hr-24h: check every 4 hours
        }
        if (millis <= TimeUnit.DAYS.toMillis(2)) {
            return "H H/12 * * *"; // 24h-2d: check every 12 hours
        }
        return "H H * * *"; // check once per day
    }

    /**
     * Turns an interval into milliseconds./
     * Code borrowed from {@link PeriodicFolderTrigger}
     * to mimic the same behavior as when configuring multibranch scanning.
     *
     * @param interval the interval.
     * @return the milliseconds.
     */
    private static long toIntervalMillis(String interval) {
        TimeUnit units = TimeUnit.MINUTES;
        interval = interval.toLowerCase();
        if (interval.endsWith("h")) {
            units = TimeUnit.HOURS;
            interval = StringUtils.removeEnd(interval, "h");
        }
        if (interval.endsWith("m")) {
            interval = StringUtils.removeEnd(interval, "m");
        } else if (interval.endsWith("d")) {
            units = TimeUnit.DAYS;
            interval = StringUtils.removeEnd(interval, "d");
        } else if (interval.endsWith("ms")) {
            units = TimeUnit.SECONDS;
            interval = StringUtils.removeEnd(interval, "ms");
        } else if (interval.endsWith("s")) {
            units = TimeUnit.SECONDS;
            interval = StringUtils.removeEnd(interval, "s");
        }
        long value = 0;
        try {
            value = Long.parseLong(interval);
        } catch (NumberFormatException e) {
            value = 1;
        }
        return Math.min(TimeUnit.DAYS.toMillis(30),
                Math.max(TimeUnit.MINUTES.toMillis(1), units.toMillis(value)));
    }

    @Extension
    public static class Cron extends PeriodicWork {
        private final Calendar cal = new GregorianCalendar();

        public Cron() {
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        }

        @Override
        public long getRecurrencePeriod() {
            return MIN;
        }

        @Override
        public long getInitialDelay() {
            return MIN - TimeUnit.SECONDS.toMillis(Calendar.getInstance().get(Calendar.SECOND));
        }

        @Override
        public void doRun() {
            while (new Date().getTime() >= cal.getTimeInMillis()) {
                LOGGER.log(Level.FINE, "cron checking {0}", cal.getTime());
                try {
                    checkAllCleanups(cal);
                } catch (Throwable e) {
                    LOGGER.log(Level.WARNING, "Cron thread throw an exception", e);
                    // SafeTimerTask.run would also catch this, but be sure to increment cal too.
                }

                cal.add(Calendar.MINUTE, 1);
            }
        }

        private void checkAllCleanups(Calendar cal) {
            Jenkins jenkins = Jenkins.get();
            jenkins.allItems(MultiBranchProject.class, multiBranchProject -> {
                DescribableList<AbstractFolderProperty<?>, AbstractFolderPropertyDescriptor> properties = multiBranchProject.getProperties();
                return properties != null && properties.get(OrphanedItemsExtraCleanupProperty.class) != null;
            }).forEach(multiBranchProject -> {
                DescribableList<AbstractFolderProperty<?>, AbstractFolderPropertyDescriptor> properties = multiBranchProject.getProperties();
                OrphanedItemsExtraCleanupProperty property = properties.get(OrphanedItemsExtraCleanupProperty.class);
                CronTabList tabs = property.getTabs();
                if (tabs.check(cal)) {
                    //TODO property.getCalculation()
                }
            });

        }
    }
}
