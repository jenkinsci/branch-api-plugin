/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.model.TopLevelItem;
import hudson.model.listeners.ItemListener;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.slaves.WorkspaceLocator;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Chooses manageable workspace names for branch projects.
 *
 * @see "JENKINS-34564"
 */
@Restricted(NoExternalUse.class)
@Extension(ordinal = -90)
public class WorkspaceLocatorV2Impl extends WorkspaceLocator {

    private static final Logger LOGGER = Logger.getLogger(WorkspaceLocatorV2Impl.class.getName());

    /**
     * The most characters to allow in a workspace directory name, relative to the root. Zero to disable altogether.
     */
    // TODO 2.4+ use SystemProperties
    static /* not final */ boolean ENABLED = Boolean.getBoolean(WorkspaceLocatorV2Impl.class.getName() + ".ENABLED");

    @Override
    public FilePath locate(TopLevelItem item, Node node) {
        if (!ENABLED) {
            return null;
        }
        if (!(item.getParent() instanceof MultiBranchProject)) {
            return null;
        }
        if (node instanceof Jenkins) {
            String workspaceDir = ((Jenkins) node).getRawWorkspaceDir();
            if (!workspaceDir.contains("ITEM_FULL")) {
                LOGGER.log(Level.WARNING,
                        "JENKINS-34564 path sanitization ineffective when using legacy Workspace Root Directory "
                                + "‘{0}’; switch to $'{'JENKINS_HOME'}'/workspace/$'{'ITEM_FULLNAME'}' as in "
                                + "JENKINS-8446 / JENKINS-21942",
                        workspaceDir);
            }
            return new FilePath(new File(expandVariablesForDirectory(workspaceDir,
                    NameMangler.apply(item.getFullName()), item.getRootDir().getPath())));
        } else if (node instanceof Slave) {
            FilePath root = ((Slave) node).getWorkspaceRoot();
            return root != null ? root.child(NameMangler.apply(item.getFullName())) : null;
        } else { // ?
            return null;
        }
    }

    /**
     * copied from {@link Jenkins}
     */
    static String expandVariablesForDirectory(String base, String itemFullName, String itemRootDir) {
        return Util.replaceMacro(base, ImmutableMap.of(
                "JENKINS_HOME", Jenkins.getActiveInstance().getRootDir().getPath(),
                "ITEM_ROOTDIR", itemRootDir,
                "ITEM_FULLNAME", itemFullName,   // legacy, deprecated
                "ITEM_FULL_NAME", itemFullName.replace(':', '$'))); // safe, see JENKINS-12251

    }

    /**
     * Cleans up workspace when an orphaned branch project is deleted.
     *
     * @see "JENKINS-2111"
     */
    @Extension
    public static class Deleter extends ItemListener {

        @Override
        public void onDeleted(Item item) {
            if (!(item instanceof TopLevelItem)) {
                return;
            }
            TopLevelItem tli = (TopLevelItem) item;
            Jenkins jenkins = Jenkins.getActiveInstance();
            FilePath masterLoc = new WorkspaceLocatorV2Impl().locate(tli, jenkins);
            if (masterLoc != null) {
                Computer.threadPoolForRemoting.submit(new CleanupTask(masterLoc, "master"));
                for (Node node : jenkins.getNodes()) {
                    FilePath slaveLoc = new WorkspaceLocatorV2Impl().locate(tli, node);
                    if (slaveLoc != null) {
                        Computer.threadPoolForRemoting.submit(new CleanupTask(slaveLoc, node.getNodeName()));
                    }
                }
            }
        }

        /**
         * Number of {@link WorkspaceLocatorV2Impl.Deleter.CleanupTask} which have been scheduled but not yet completed.
         */
        private static int runningTasks;

        @VisibleForTesting
        static synchronized void waitForTasksToFinish() throws InterruptedException {
            while (runningTasks > 0) {
                WorkspaceLocatorV2Impl.Deleter.class.wait();
            }
        }

        private static synchronized void taskStarted() {
            runningTasks++;
        }

        private static synchronized void taskFinished() {
            runningTasks--;
            WorkspaceLocatorV2Impl.Deleter.class.notifyAll();
        }

        private static class CleanupTask implements Runnable {

            @NonNull
            private final FilePath loc;

            @NonNull
            private final String nodeName;

            CleanupTask(FilePath loc, String nodeName) {
                this.loc = loc;
                this.nodeName = nodeName;
                taskStarted();
            }

            @Override
            public void run() {
                String base = loc.getName();
                FilePath parent = loc.getParent();
                if (parent == null) { // unlikely but just in case
                    return;
                }
                Thread t = Thread.currentThread();
                String oldName = t.getName();
                t.setName(oldName + ": deleting workspace in " + loc + " on " + nodeName);
                try {
                    try (Timeout timeout = Timeout.limit(5, TimeUnit.MINUTES)) {
                        List<FilePath> dirs = parent.listDirectories();
                        if (dirs == null) { // impossible as of https://github.com/jenkinsci/jenkins/pull/2914
                            return;
                        }
                        for (FilePath child : dirs) {
                            if (child.getName().startsWith(base)) {
                                LOGGER.log(Level.INFO, "deleting obsolete workspace {0} on {1}",
                                        new Object[] {child, nodeName});
                                child.deleteRecursive();
                            }
                        }
                    } catch (IOException | InterruptedException x) {
                        LOGGER.log(Level.WARNING, "could not clean up workspace directory " + loc + " on " + nodeName,
                                x);
                    }
                } finally {
                    t.setName(oldName);
                    taskFinished();
                }
            }

        }

    }

}
