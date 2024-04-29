/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;
import com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.PeriodicWork;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.scheduler.CronTabList;
import hudson.triggers.TimerTrigger;
import hudson.util.DescribableList;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import jenkins.security.stapler.StaplerDispatchable;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.File;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OrphanedItemsExtraCleanupProperty<P extends Job<P, R> & TopLevelItem,
        R extends Run<P, R>> extends AbstractFolderProperty<MultiBranchProject<?,?>> implements Queue.FlyweightTask {
    static final Logger LOGGER = Logger.getLogger(OrphanedItemsExtraCleanupProperty.class.getName());

    private final long interval;
    private transient String cronTabSpec;
    private transient CronTabList tabs;
    private transient CleanupComputation computation;

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

    @Override
    public Collection<?> getItemContainerOverrides() {
        return Collections.singleton(this);
    }

    @StaplerDispatchable
    public synchronized CleanupComputation getCleaning() {
        if (computation == null) {
            computation = new CleanupComputation(this, (ComputedFolder<MultiBranchProject<?, ?>>) this.owner, null);
        }
        return computation;
    }

    @Override
    public synchronized Queue.Executable createExecutable() throws IOException {
        computation = new CleanupComputation(this, (ComputedFolder<MultiBranchProject<?, ?>>) this.owner, computation);
        return computation;
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

    @Override
    protected void setOwner(@NonNull MultiBranchProject<?, ?> owner) {
        super.setOwner(owner);

        synchronized (this) {
            if (computation == null) {
                try {
                    FileUtils.forceMkdir(getComputationDir());
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, null, x);
                }
                XmlFile file = getCleaning().getDataFile(); //creates the instance
                if (file.exists()) {
                    try {
                        file.unmarshal(computation);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to load " + file, e);
                    }
                }
            }
        }
    }

    @NonNull
    public File getComputationDir() {
        if (owner != null) {
            return new File(owner.getRootDir(), "cleaning");
        }
        try {
            return Util.createTempDir();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return "cleaning";
    }

    @Override
    public String getFullDisplayName() {
        return getDisplayName();
    }

    @Override
    public String getUrl() {
        return "cleaning";
    }

    @Override
    public String getDisplayName() {
        return getDescriptor().getDisplayName() + " of " + owner.getDisplayName();
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
                    Jenkins.get().getQueue().schedule(property, 0, new CauseAction(new TimerTrigger.TimerTriggerCause()));
                }
            });
        }
    }

    @Extension
    public static class ActionFactory extends TransientActionFactory<MultiBranchProject> {

        @Override
        public Class<MultiBranchProject> type() {
            return MultiBranchProject.class;
        }

        @NonNull
        @Override
        public Collection<? extends Action> createFor(@NonNull MultiBranchProject target) {
            DescribableList<AbstractFolderProperty<?>, AbstractFolderPropertyDescriptor> properties = target.getProperties();
            if (properties != null) {
                OrphanedItemsExtraCleanupProperty property = properties.get(OrphanedItemsExtraCleanupProperty.class);
                if (property != null) {
                    return List.of(new CleaningAction(), new RunCleaningAction(property));
                }
            }
            return Collections.emptyList();
        }
    }

    public static class CleaningAction implements Action {

        @Override
        public String getIconFileName() {
            return "icon-terminal";
        }

        @Override
        public String getDisplayName() {
            return ExtensionList.lookupSingleton(DescriptorImpl.class).getDisplayName();
        }

        @Override
        public String getUrlName() {
            return "cleaning";
        }
    }

    public static class RunCleaningAction implements Action {

        OrphanedItemsExtraCleanupProperty property;

        public RunCleaningAction(OrphanedItemsExtraCleanupProperty property) {
            this.property = property;
        }

        @Override
        public String getIconFileName() {
            return "icon-clock";
        }

        @Override
        public String getDisplayName() {
            return "Run " + ExtensionList.lookupSingleton(DescriptorImpl.class).getDisplayName() + " now";
        }

        @Override
        public String getUrlName() {
            return "performCleaning";
        }

        public void doIndex(StaplerResponse response) throws IOException {
            Jenkins.get().getQueue().schedule(property, 0, new CauseAction(new Cause.UserIdCause()));
            response.sendRedirect2("../cleaning/console");
        }
    }

    static class CleanupComputation extends FolderComputation<MultiBranchProject<?,?>> {
        private transient OrphanedItemsExtraCleanupProperty property;

        protected CleanupComputation(OrphanedItemsExtraCleanupProperty property, @NonNull ComputedFolder<MultiBranchProject<?,?>> folder, FolderComputation<MultiBranchProject<?,?>> previous) {
            super(folder, previous);
            this.property = property;
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        protected XmlFile getDataFile() {
            return new XmlFile(Items.XSTREAM, new File(property.getComputationDir(), "cleaning.xml"));
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public File getLogFile() {
            return new File(property.getComputationDir(), "cleaning.log");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.OrphanedItemsExtraCleanupProperty_displayName(((MultiBranchProject)getParent()).getSourcePronoun());
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getUrl() {
            return getParent().getUrl() + "/cleaning";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getSearchUrl() {
            return "cleaning/";
        }

        @Override
        protected void doRun(StreamBuildListener listener) throws IOException, InterruptedException {
            property.cleanDeadBranches(listener);
        }
    }

    void cleanDeadBranches(TaskListener listener) throws InterruptedException, IOException {
        PrintStream log = listener.getLogger();
        log.println("Searching for orphaned items...");
        BranchProjectFactory factory = this.owner.getProjectFactory();
        List<Job> deadBranches = new ArrayList<>();

        for (Job item : this.owner.getItems()) {
            if (factory.isProject(item)) {
                log.printf("Examining '%s'... ", item.getName());
                if (factory.getBranch(item) instanceof Branch.Dead) {
                    log.println("Is marked dead...");
                    deadBranches.add(item);
                } else {
                    log.println("Is alive...");
                }
            }
        }

        Collection<P> filteredOrphaned = ((MultiBranchProject) this.owner).orphanedItems(deadBranches, listener);
        for (Job orph : filteredOrphaned) {
            log.printf("Deleting orphaned item: %s%n", orph.getName());
            orph.delete();
        }
    }

    public static class OrganizationChildProperty extends OrganizationFolderProperty<OrganizationFolder> {

        private final String interval;

        @DataBoundConstructor
        public OrganizationChildProperty(String interval) {
            this.interval = interval;
        }

        @Override
        protected void decorate(@NonNull MultiBranchProject<?, ?> child, @NonNull TaskListener listener) throws IOException {
            if (StringUtils.isNotEmpty(interval)) {
                child.addProperty(new OrphanedItemsExtraCleanupProperty(interval));
            }
        }

        @Extension @Symbol("orphanedItemsCleanupChildren")
        public static class DescriptorImpl extends OrganizationFolderPropertyDescriptor {

            public DescriptorImpl() {
                addHelpFileRedirect("interval", OrphanedItemsExtraCleanupProperty.class, "interval");
            }

            @NonNull
            @Override
            public String getDisplayName() {
                return Messages.OrganizationChildOrphanedItemsExtraCleanupProperty_DisplayName();
            }

            @Override
            public AbstractFolderProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
                return formData.optBoolean("specified") ? super.newInstance(req, formData) : null;
            }

            @SuppressWarnings("unused") // used by Jelly
            public ListBoxModel doFillIntervalItems() {
                OrphanedItemsExtraCleanupProperty.DescriptorImpl descriptor = ExtensionList.lookupSingleton(OrphanedItemsExtraCleanupProperty.DescriptorImpl.class);
                return descriptor.doFillIntervalItems();
            }

            @Override
            public String getHelpFile() {
                return ExtensionList.lookupSingleton(OrphanedItemsExtraCleanupProperty.DescriptorImpl.class).getHelpFile();
            }
        }
    }
}
