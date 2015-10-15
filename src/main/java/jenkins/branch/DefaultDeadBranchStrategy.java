/*
 * The MIT License
 *
 * Copyright (c) 2011-2013, CloudBees, Inc., Stephen Connolly.
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.tasks.LogRotator;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;

/**
 * The default {@link DeadBranchStrategy}, which trims off jobs for dead branches
 * by the # of days or the # of jobs (much like {@link LogRotator} works.
 *
 * @author Stephen Connolly
 */
public class DefaultDeadBranchStrategy extends DeadBranchStrategy {

    /**
     * Our logger
     */
    private static final Logger LOGGER = Logger.getLogger(DefaultDeadBranchStrategy.class.getName());

    /**
     * Should old branches be removed at all
     */
    private final boolean pruneDeadBranches;

    /**
     * If not -1, dead branches are only kept up to this days.
     */
    private final int daysToKeep;

    /**
     * If not -1, only this number of dead branches are kept.
     */
    private final int numToKeep;

    /**
     * Default constructor for new {@link MultiBranchProject} instances.
     */
    public DefaultDeadBranchStrategy() {
        this(false, null, null);
    }

    /**
     * Stapler's constructor.
     *
     * @param pruneDeadBranches remove dead branches.
     * @param daysToKeepStr     how old a branch must be to remove.
     * @param numToKeepStr      how many branches to keep.
     */
    @DataBoundConstructor
    public DefaultDeadBranchStrategy(boolean pruneDeadBranches, @CheckForNull String daysToKeepStr,
                                     @CheckForNull String numToKeepStr) {
        this.pruneDeadBranches = pruneDeadBranches;
        // TODO in lieu of DeadBranchCleanupThread, introduce a form warning if daysToKeep < IndexAtLeastTrigger.interval
        this.daysToKeep = pruneDeadBranches ? fromString(daysToKeepStr) : -1;
        this.numToKeep = pruneDeadBranches ? fromString(numToKeepStr) : -1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOwner(MultiBranchProject<?, ?> owner) {
        super.setOwner(owner);
    }

    /**
     * Gets the number of days to keep dead branches.
     *
     * @return the number of days to keep dead branches.
     */
    @SuppressWarnings("unused") // used by Jelly EL
    public int getDaysToKeep() {
        return daysToKeep;
    }

    /**
     * Gets the number of dead branches to keep.
     *
     * @return the number of dead branches to keep.
     */
    @SuppressWarnings("unused") // used by Jelly EL
    public int getNumToKeep() {
        return numToKeep;
    }

    /**
     * Returns {@code true} if dead branches should be removed.
     *
     * @return {@code true} if dead branches should be removed.
     */
    @SuppressWarnings("unused") // used by Jelly EL
    public boolean isPruneDeadBranches() {
        return pruneDeadBranches;
    }

    /**
     * Returns the number of days to keep dead branches.
     *
     * @return the number of days to keep dead branches.
     */
    @SuppressWarnings("unused") // used by Jelly EL
    @NonNull
    public String getDaysToKeepStr() {
        return toString(daysToKeep);
    }

    /**
     * Gets the number of dead branches to keep.
     *
     * @return the number of dead branches to keep.
     */
    @SuppressWarnings("unused") // used by Jelly EL
    @NonNull
    public String getNumToKeepStr() {
        return toString(numToKeep);
    }

    /**
     * Helper to turn a int into a string where {@code -1} correspond to the empty string.
     *
     * @param i the possibly {@code null} {@link Integer}
     * @return the {@link String} representation of the int.
     */
    @NonNull
    private static String toString(int i) {
        if (i == -1) {
            return "";
        }
        return String.valueOf(i);
    }

    /**
     * Inverse of {@link #toString(int)}.
     *
     * @param s the string.
     * @return the int.
     */
    private static int fromString(@CheckForNull String s) {
        if (StringUtils.isBlank(s)) {
            return -1;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * {@inheritDoc}
     */
    public <P extends Job<P, R> & TopLevelItem,
            R extends Run<P, R>> Collection<P> runDeadBranchCleanup(Collection<P> deadBranches, TaskListener listener)
            throws IOException, InterruptedException {
        List<P> toRemove = new ArrayList<P>();
        final MultiBranchProject<P, R> owner = (MultiBranchProject<P, R>) getOwner();
        LOGGER.log(FINE, "Running the dead branch cleanup for " + owner.getFullDisplayName());
        if (pruneDeadBranches && (numToKeep != -1 || daysToKeep != -1)) {
            List<P> candidates = new ArrayList<P>(deadBranches);
            Collections.sort(candidates, new Comparator<P>() {
                public int compare(P o1, P o2) {
                    // most recent build first
                    R lb1 = o1.getLastBuild();
                    R lb2 = o2.getLastBuild();
                    if (lb1 == null) {
                        if (lb2 == null) {
                            return 0;
                        }
                        return 1;
                    }
                    if (lb2 == null) {
                        return -1;
                    }
                    long ms1 = lb1.getTimeInMillis();
                    long ms2 = lb2.getTimeInMillis();
                    return ms1 == ms2 ? 0 : (ms1 > ms2 ? -1 : 1);
                }
            });
            int count = 0;
            if (numToKeep != -1) {
                for (Iterator<P> iterator = candidates.iterator(); iterator.hasNext(); ) {
                    P project = iterator.next();
                    count++;
                    // todo keepers
                    if (count <= numToKeep) {
                        continue;
                    }
                    LOGGER.log(FINER, project.getFullDisplayName() + " is to be removed");
                    toRemove.add(project);
                    iterator.remove();
                }
            }
            if (daysToKeep != -1) {
                Calendar cal = new GregorianCalendar();
                cal.add(Calendar.DAY_OF_YEAR, -daysToKeep);
                for (Iterator<P> iterator = candidates.iterator(); iterator.hasNext(); ) {
                    P project = iterator.next();
                    R r = project.getLastBuild();
                    // todo keepers
                    if (r != null && !r.getTimestamp().before(cal)) {
                        LOGGER.log(FINER, project.getFullDisplayName() + " is not GC-ed because it's still new");
                        continue;
                    }
                    LOGGER.log(FINER, project.getFullDisplayName() + " is to be removed");
                    toRemove.add(project);
                    iterator.remove(); // not actually necessary here, could use a simple for-loop
                }
            }
        }
        return toRemove;
    }

    /**
     * Our {@link hudson.model.Descriptor}
     */
    @Extension
    @SuppressWarnings("unused") // instantiated by Jenkins
    public static class DescriptorImpl extends DeadBranchStrategyDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Default";
        }
    }

}
