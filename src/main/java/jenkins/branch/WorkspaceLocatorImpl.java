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
import com.google.common.base.Charsets;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.listeners.ItemListener;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.slaves.ComputerListener;
import hudson.slaves.WorkspaceList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import jenkins.slaves.WorkspaceLocator;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Chooses manageable workspace names for branch projects.
 * @see "JENKINS-34564"
 * @see "JENKINS-2111"
 */
@Restricted(NoExternalUse.class)
@Extension(ordinal = -100)
public class WorkspaceLocatorImpl extends WorkspaceLocator {

    private static final Logger LOGGER = Logger.getLogger(WorkspaceLocatorImpl.class.getName());

    @Deprecated
    static final int PATH_MAX_DEFAULT = 80;
    /**
     * The most characters to allow in a workspace directory name, relative to the root.
     * Zero to disable altogether.
     * Not used for new workspaces; only for continuing to use workspaces created prior to {@link #MAX_LENGTH}.
     */
    @Deprecated
    static /* not final */ int PATH_MAX = Integer.getInteger(WorkspaceLocatorImpl.class.getName() + ".PATH_MAX", PATH_MAX_DEFAULT);

    static /* not final */ int MAX_LENGTH = Integer.getInteger(WorkspaceLocatorImpl.class.getName() + ".MAX_LENGTH", NameMangler.MAX_SAFE_LENGTH);

    static /* not final */ boolean MULTIBRANCH_ONLY = Boolean.parseBoolean(System.getProperty(WorkspaceLocatorImpl.class.getName() + ".MULTIBRANCH_ONLY", /* TBD */ "true"));

    /**
     * File containing pairs of lines tracking workspaces.
     * The first line in a pair is a {@link TopLevelItem#getFullName};
     * the second is a workspace-relative path.
     * Reads and writes to this file should be synchronized on the {@link Node}.
     */
    private static final String INDEX_FILE_NAME = "workspaces.txt";

    @Override
    public FilePath locate(TopLevelItem item, Node node) {
        return locate(item, node, true);
    }

    private static FilePath locate(TopLevelItem item, Node node, boolean create) {
        if (MULTIBRANCH_ONLY && !(item.getParent() instanceof MultiBranchProject)) {
            return null;
        }
        FilePath workspace = getWorkspaceRoot(node);
        if (workspace == null) {
            return null;
        }
        String fullName = item.getFullName();
        if (fullName.contains("\n") || fullName.equals(INDEX_FILE_NAME)) {
            throw new IllegalArgumentException(); // better not to mess around
        }
        try {
            synchronized (node) {
                Map<String, String> index = load(workspace);
                // Already listed:
                String path = index.get(fullName);
                if (path != null) {
                    return workspace.child(path);
                }
                // Old JENKINS-34564 implementation:
                if (PATH_MAX != 0 && item.getParent() instanceof MultiBranchProject) {
                    path = minimize(fullName);
                    FilePath dir = workspace.child(path);
                    if (dir.isDirectory()) {
                        index.put(fullName, path);
                        save(index, workspace);
                        return dir;
                    }
                }
                // Plain default:
                FilePath dir = workspace.child(fullName);
                if (dir.isDirectory()) {
                    index.put(fullName, fullName);
                    save(index, workspace);
                    return dir;
                }
                if (!create) {
                    return null;
                }
                // Allocate:
                String mnemonic = mnemonicOf(fullName);
                for (int i = 1; ; i++) {
                    path = StringUtils.right(i > 1 ? mnemonic + "_" + i : mnemonic, MAX_LENGTH);
                    if (!index.containsKey(path)) {
                        dir = workspace.child(path);
                        if (!dir.isDirectory()) {
                            index.put(fullName, path);
                            save(index, workspace);
                            return dir;
                        }
                    }
                }
            }
        } catch (IOException | InterruptedException x) {
            LOGGER.log(Level.WARNING, "could not manage workspaces on " + node, x);
            return null;
        }
    }
    
    private static Map<String, String> load(FilePath workspace) throws IOException, InterruptedException {
        Map<String, String> map = new HashMap<>();
        FilePath index = workspace.child(INDEX_FILE_NAME);
        if (index.exists()) {
            try (InputStream is = index.read(); Reader r = new InputStreamReader(is, StandardCharsets.UTF_8); BufferedReader br = new BufferedReader(r)) {
                while (true) {
                    String key = br.readLine();
                    if (key == null) {
                        break;
                    }
                    String value = br.readLine();
                    if (value == null) {
                        throw new IOException("malformed " + index);
                    }
                    map.put(key, value);
                }
            }
        }
        return map;
    }

    private static void save(Map<String, String> index, FilePath workspace) throws IOException, InterruptedException {
        FilePath tmp = workspace.child(INDEX_FILE_NAME + ".tmp");
        try (OutputStream os = tmp.write(); Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : index.entrySet()) {
                w.write(entry.getKey());
                w.write('\n');
                w.write(entry.getValue());
                w.write('\n');
            }
        }
        tmp.renameTo(workspace.child(INDEX_FILE_NAME));
    }

    private static final Pattern GOOD_RAW_WORKSPACE_DIR = Pattern.compile("[$][{]JENKINS_HOME[}]/(.+)/[$][{]ITEM_FULL_?NAME[}]");
    private static @CheckForNull FilePath getWorkspaceRoot(Node node) {
        if (node instanceof Jenkins) {
            Matcher m = GOOD_RAW_WORKSPACE_DIR.matcher(((Jenkins) node).getRawWorkspaceDir());
            if (m.matches()) {
                return node.getRootPath().child(m.group(1));
            } else {
                LOGGER.log(Level.WARNING, "JENKINS-2111 path sanitization ineffective when using legacy Workspace Root Directory ‘{0}’; switch to $'{'JENKINS_HOME'}'/workspace/$'{'ITEM_FULLNAME'}' as in JENKINS-8446 / JENKINS-21942", ((Jenkins) node).getRawWorkspaceDir());
                return null;
            }
        } else if (node instanceof Slave) {
            return ((Slave) node).getWorkspaceRoot();
        } else { // ?
            return null;
        }
    }

    @Deprecated
    private static String uniqueSuffix(String name) {
        // TODO still in beta: byte[] sha256 = Hashing.sha256().hashString(name).asBytes();
        byte[] sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256").digest(name.getBytes(Charsets.UTF_16LE));
        } catch (NoSuchAlgorithmException x) {
            throw new AssertionError("https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#MessageDigest", x);
        }
        return new Base32(0).encodeToString(sha256).replaceFirst("=+$", "");
    }

    private static String mnemonicOf(String name) {
        // Do not need the complexity of NameMangler here, since we uniquify as needed.
        return name.replaceAll("(%[0-9A-F]{2}|[^a-zA-Z0-9-_.])+", "_");
    }

    @Deprecated
    static String minimize(String name) {
        String mnemonic = mnemonicOf(name);
        int maxSuffix = 53; /* ceil(256 / lg(32)) + length("-") */
        int maxMnemonic = Math.max(PATH_MAX - maxSuffix, 1);
        if (maxSuffix + maxMnemonic > PATH_MAX) {
            // The whole suffix cannot be included in the path.  Trim the suffix
            // and the mnemonic to fit inside PATH_MAX.  The mnemonic always gets
            // at least one character.  The suffix always gets 10 characters plus
            // the "-".  The rest of PATH_MAX is split evenly between the two.
            LOGGER.log(Level.WARNING, "WorkspaceLocatorImpl.PATH_MAX is small enough that workspace path collisions are more likely to occur");
            final int minSuffix = 10 + /* length("-") */ 1;
            maxMnemonic = Math.max((int)((PATH_MAX - minSuffix) / 2), 1);
            maxSuffix = Math.max(PATH_MAX - maxMnemonic, minSuffix);
        }
        maxSuffix = maxSuffix - 1; // Remove the "-"
        String result = StringUtils.right(mnemonic, maxMnemonic) + "-" + uniqueSuffix(name).substring(0, maxSuffix);
        return result;
    }

    /**
     * Cleans up workspace when an orphaned branch project is deleted.
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
            Computer.threadPoolForRemoting.submit(new CleanupTask(tli, jenkins));
            for (Node node : jenkins.getNodes()) {
                Computer.threadPoolForRemoting.submit(new CleanupTask(tli, node));
            }
        }

        @Override
        public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            // TODO update index
        }

        /** Number of {@link CleanupTask} which have been scheduled but not yet completed. */
        private static int runningTasks;

        @VisibleForTesting
        static synchronized void waitForTasksToFinish() throws InterruptedException {
            while (runningTasks > 0) {
                Deleter.class.wait();
            }
        }

        private static synchronized void taskStarted() {
            runningTasks++;
        }

        private static synchronized void taskFinished() {
            runningTasks--;
            Deleter.class.notifyAll();
        }

        private static class CleanupTask implements Runnable {

            @NonNull
            private final TopLevelItem tli;

            @NonNull
            private final Node node;

            CleanupTask(TopLevelItem tli, Node node) {
                this.tli = tli;
                this.node = node;
                taskStarted();
            }

            @Override
            public void run() {
                Thread t = Thread.currentThread();
                String oldName = t.getName();
                String nodeName = node instanceof Jenkins ? "master" : node.getNodeName();
                try {
                    try (Timeout timeout = Timeout.limit(5, TimeUnit.MINUTES)) {
                        FilePath loc = locate(tli, node, false);
                        if (loc == null) {
                            return;
                        }
                        t.setName(oldName + ": deleting workspace in " + loc + " on " + nodeName);
                        String base = loc.getName();
                        FilePath parent = loc.getParent();
                        if (parent == null) { // unlikely but just in case
                            return;
                        }
                        List<FilePath> dirs = parent.listDirectories();
                        for (FilePath child : dirs) {
                            String childName = child.getName();
                            if (childName.equals(base) || childName.startsWith(base + /* COMBINATOR */ System.getProperty(WorkspaceList.class.getName(), "@"))) {
                                LOGGER.log(Level.INFO, "deleting obsolete workspace {0} on {1}", new Object[] {child, nodeName});
                                child.deleteRecursive();
                            }
                        }
                        FilePath workspace = getWorkspaceRoot(node);
                        if (workspace != null) {
                            synchronized (node) {
                                Map<String, String> index = load(workspace);
                                index.remove(tli.getFullName());
                                save(index, workspace);
                            }
                        }
                    } catch (IOException | InterruptedException x) {
                        LOGGER.log(Level.WARNING, "could not clean up workspace directory for " + tli.getFullName() + " on " + nodeName, x);
                    }
                } finally {
                    t.setName(oldName);
                    taskFinished();
                }
            }

        }

    }

    /**
     * Cleans up workspaces for apparently missing jobs when a node goes online.
     * This is a counterpart to {@link Deleter},
     * which only deletes those workspaces of a job being deleted which happen to be online at the time.
     */
    @Extension
    public static final class Collector extends ComputerListener {

        @Override
        public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
            Node node = c.getNode();
            if (node == null) {
                return;
            }
            FilePath workspace = getWorkspaceRoot(node);
            if (workspace == null) {
                return;
            }
            synchronized (node) {
                Map<String, String> index = load(workspace);
                boolean modified = false;
                try (ACLContext as = ACL.as(ACL.SYSTEM)) {
                    Iterator<Map.Entry<String, String>> it = index.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, String> entry = it.next();
                        String fullName = entry.getKey();
                        if (Jenkins.getActiveInstance().getItemByFullName(fullName, TopLevelItem.class) == null) {
                            String path = entry.getValue();
                            it.remove();
                            modified = true;
                            for (FilePath child : workspace.listDirectories()) {
                                String childName = child.getName();
                                if (childName.equals(path) || childName.startsWith(path + /* COMBINATOR */ System.getProperty(WorkspaceList.class.getName(), "@"))) {
                                    listener.getLogger().println("deleting obsolete workspace " + child);
                                    child.deleteRecursive();
                                }
                            }
                        }
                    }
                }
                if (modified) {
                    save(index, workspace);
                }
            }
        }

    }

}
