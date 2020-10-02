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
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.WorkspaceCleanupThread;
import hudson.model.listeners.ItemListener;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.slaves.ComputerListener;
import hudson.slaves.WorkspaceList;
import hudson.util.TextFile;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import jenkins.slaves.WorkspaceLocator;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Chooses manageable workspace names for (especially branch) projects.
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

    enum Mode {
        DISABLED,
        MULTIBRANCH_ONLY,
        ENABLED
    }
    /**
     * On which projects to use this extension.
     * Could be set to {@link Mode#ENABLED} to turn on the smarter behavior for all projects,
     * including standalone Pipeline and even freestyle, matrix, etc.
     * On the one hand I do not like having installation of what is otherwise a library plugin affect behavior of core functionality.
     * On the other hand, the name shortening is very useful for projects in deep folder hierarchies;
     * the character sanitization is useful for all sorts of project generation systems other than multibranch;
     * and the automatic cleanup is useful for pretty much everyone, since {@link WorkspaceCleanupThread} is unreliable.
     */
    static /* not final */ Mode MODE = Mode.valueOf(System.getProperty(WorkspaceLocatorImpl.class.getName() + ".MODE", Mode.MULTIBRANCH_ONLY.name()));

    /**
     * File containing pairs of lines tracking workspaces.
     * The first line in a pair is a {@link TopLevelItem#getFullName};
     * the second is a workspace-relative path.
     * Reads and writes to this file should be synchronized on {@link #lockFor}.
     */
    static final String INDEX_FILE_NAME = "workspaces.txt";

    /** Same as {@link WorkspaceList#COMBINATOR}. */
    private static final String COMBINATOR = System.getProperty(WorkspaceList.class.getName(), "@");

    /**
     * @see #indexCache()
     * @see #load
     * @see #save
     */
    private final Map<VirtualChannel, IndexCacheEntry> indexCache = new WeakHashMap<>();
    private static final class IndexCacheEntry {
        final String workspaceRoot;
        final Map<String, String> index;
        IndexCacheEntry(String workspaceRoot, Map<String, String> index) {
            this.workspaceRoot = workspaceRoot;
            this.index = index;
        }
    }

    @Override
    public FilePath locate(TopLevelItem item, Node node) {
        return locate(item, node, true);
    }

    private static FilePath locate(TopLevelItem item, Node node, boolean create) {
        return locate(item, item.getFullName(), node, create);
    }

    @CheckForNull
    private static FilePath locate(TopLevelItem item, String fullName, Node node, boolean create) {
        switch (MODE) {
        case DISABLED:
            LOGGER.log(Level.FINE, "disabled, skipping for {0} on {1}", new Object[] {item, node});
            return null;
        case MULTIBRANCH_ONLY:
            if (!(item.getParent() instanceof MultiBranchProject)) {
                LOGGER.log(Level.FINE, "ignoring non-branch project {0} on {1}", new Object[] {item, node});
                return null;
            }
            break;
        case ENABLED:
            break;
        default:
            throw new AssertionError();
        }
        FilePath workspace = getWorkspaceRoot(node);
        if (workspace == null) {
            LOGGER.log(Level.FINE, "no available workspace root for {0} so skipping {1}", new Object[] {node, item});
            return null;
        }
        if (fullName.contains("\n") || fullName.equals(INDEX_FILE_NAME)) {
            throw new IllegalArgumentException("Dangerous job name `" + fullName + "`"); // better not to mess around
        }
        try {
            synchronized (lockFor(node)) {
                Map<String, String> index = load(workspace);
                // Already listed:
                String path = index.get(fullName);
                if (path != null) {
                    FilePath dir = workspace.child(path);
                    LOGGER.log(Level.FINER, "index already lists {0} for {1} on {2}", new Object[] {dir, item, node});
                    return dir;
                }
                // Old JENKINS-34564 implementation:
                if (PATH_MAX != 0 && item.getParent() instanceof MultiBranchProject) {
                    path = minimize(fullName);
                    FilePath dir = workspace.child(path);
                    if (dir.isDirectory()) {
                        index.put(fullName, path);
                        save(index, workspace);
                        LOGGER.log(Level.FINE, "detected existing workspace {0} under old naming scheme for {1} on {2}", new Object[] {dir, item, node});
                        return dir;
                    }
                }
                // Plain default:
                FilePath dir = workspace.child(fullName);
                if (dir.isDirectory()) {
                    index.put(fullName, fullName);
                    save(index, workspace);
                    LOGGER.log(Level.FINE, "using plain default location {0} for {1} on {2}", new Object[] {dir, item, node});
                    return dir;
                }
                if (!create) {
                    LOGGER.log(Level.FINE, "not creating a new workspace for {0} on {1} since {2} does not exist", new Object[] {item, node, dir});
                    return null;
                }
                // Allocate:
                String mnemonic = mnemonicOf(fullName);
                for (int i = 1; ; i++) {
                    path = StringUtils.right(i > 1 ? mnemonic + "_" + i : mnemonic, MAX_LENGTH);
                    path = replaceLeadingHyphen(path);
                    if (index.values().contains(path)) {
                        LOGGER.log(Level.FINER, "index collision on {0} for {1} on {2}", new Object[] {path, item, node});
                    } else {
                        dir = workspace.child(path);
                        if (dir.isDirectory()) {
                            LOGGER.log(Level.FINER, "directory collision on {0} for {1} on {2}", new Object[] {path, item, node});
                        } else {
                            index.put(fullName, path);
                            save(index, workspace);
                            LOGGER.log(Level.FINE, "allocating {0} for {1} on {2}", new Object[] {dir, item, node});
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

    private static Map<VirtualChannel, IndexCacheEntry> indexCache() {
        return ExtensionList.lookupSingleton(WorkspaceLocatorImpl.class).indexCache;
    }

    private static Map<String, String> load(FilePath workspace) throws IOException, InterruptedException {
        Map<VirtualChannel, IndexCacheEntry> _indexCache = indexCache();
        IndexCacheEntry entry;
        synchronized (_indexCache) {
            entry = _indexCache.get(workspace.getChannel());
        }
        if (entry != null && entry.workspaceRoot.equals(workspace.getRemote())) {
            LOGGER.log(Level.FINER, "cache hit on {0}", workspace);
            return entry.index;
        }
        LOGGER.log(Level.FINER, "cache miss on {0}", workspace);
        Map<String, String> map = new TreeMap<>();
        FilePath index = workspace.child(INDEX_FILE_NAME);
        if (index.exists()) {
            try (InputStream is = readFilePath(index);  Reader r = new InputStreamReader(is, StandardCharsets.UTF_8); BufferedReader br = new BufferedReader(r)) {
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
        synchronized (_indexCache) {
            _indexCache.put(workspace.getChannel(), new IndexCacheEntry(workspace.getRemote(), map));
        }
        return map;
    }

    // Workaround spotbugs false-positive redundant null checking for try blocks in java 11
    // https://github.com/spotbugs/spotbugs/issues/756
    @NonNull
    private static InputStream readFilePath(@NonNull FilePath index) throws IOException, InterruptedException {
        InputStream stream = index.read();
        assert stream != null;
        return stream;
    }

    private static void save(Map<String, String> index, FilePath workspace) throws IOException, InterruptedException {
        // FilePath.renameTo does not support REPLACE_EXISTING, and FilePath.write(String, String) is not atomic.
        // So we use TextFile, which wraps AtomicFileWriter (in UTF-8 encoding), but which does not have any built-in remote overload.
        // Note that we are synchronizing access to this file so the only potential problem with a non-atomic write is half-written content.
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> entry : index.entrySet()) {
            b.append(entry.getKey()).append('\n').append(entry.getValue()).append('\n');
        }
        workspace.child(INDEX_FILE_NAME).act(new WriteAtomic(b.toString()));
        LOGGER.log(Level.FINER, "cache update on {0}", workspace);
        Map<VirtualChannel, IndexCacheEntry> _indexCache = indexCache();
        synchronized (_indexCache) {
            _indexCache.put(workspace.getChannel(), new IndexCacheEntry(workspace.getRemote(), index));
        }
    }
    private static final class WriteAtomic extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1;
        private final String text;
        WriteAtomic(String text) {
            this.text = text;
        }
        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            new TextFile(f).write(text);
            return null;
        }
    }

    // Avoiding WeakHashMap<Node, T> since Slave overrides hashCode/equals
    private final LoadingCache<Node, Object> nodeLocks = CacheBuilder.newBuilder().weakKeys().build(new CacheLoader<Node, Object>() {
        @Override
        public Object load(Node node) throws Exception {
            // Avoiding new Object() to prepare for http://cr.openjdk.java.net/~briangoetz/valhalla/sov/02-object-model.html
            // Avoiding new String(…) because static analyzers complain
            // Could use anything but hoping that a future JVM enhances thread dumps to display monitors of type String
            return new StringBuilder("WorkspaceLocatorImpl lock for ").append(node.getNodeName()).toString();
        }
    });
    private static Object lockFor(Node node) {
        try {
            return ExtensionList.lookupSingleton(WorkspaceLocatorImpl.class).nodeLocks.get(node);
        } catch (ExecutionException x) {
            throw new AssertionError(x);
        }
    }

    private static final Pattern GOOD_RAW_WORKSPACE_DIR = Pattern.compile("(.+)[/\\\\][$][{]ITEM_FULL_?NAME[}][/\\\\]?");
    static @CheckForNull FilePath getWorkspaceRoot(Node node) {
        if (node instanceof Jenkins) {
            Matcher m = GOOD_RAW_WORKSPACE_DIR.matcher(((Jenkins) node).getRawWorkspaceDir());
            if (m.matches()) {
                return new FilePath(new File(m.group(1).replace("${JENKINS_HOME}", ((Jenkins) node).getRootDir().getAbsolutePath())));
            } else {
                LOGGER.log(Level.WARNING, "JENKINS-2111 path sanitization ineffective when using legacy Workspace Root Directory ‘{0}’; switch to ‘$'{'JENKINS_HOME'}'/workspace/$'{'ITEM_FULL_NAME'}'’ as in JENKINS-8446 / JENKINS-21942", ((Jenkins) node).getRawWorkspaceDir());
                return null;
            }
        } else if (node instanceof Slave) {
            return ((Slave) node).getWorkspaceRoot();
        } else {
            LOGGER.log(Level.WARNING, "Unrecognized node {0} of {1}", new Object[] {node, node.getClass()});
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

    private static String replaceLeadingHyphen(String name) {
        //On linux - or -- (hyphen) are interpreted as an option passed to commands and require special handling. So a leading - is replaced with _.
        return name.replaceAll("^-", "_");
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
     * Cleans up workspace when an orphaned project is deleted.
     * Also moves workspace when it is renamed or moved.
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
            Jenkins jenkins = Jenkins.get();
            Computer.threadPoolForRemoting.submit(new CleanupTask(tli, jenkins));
            // Starts provisioner Thread which is tasked with only starting cleanup Threads is there is sufficient Memory.
            new CleanupTaskProvisioner(tli, jenkins.getNodes()).run();
        }

        @Override
        public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            if (!(item instanceof TopLevelItem)) {
                return;
            }
            Jenkins jenkins = Jenkins.get();
            Computer.threadPoolForRemoting.submit(new MoveTask(oldFullName, newFullName, jenkins));
            for (Node node : jenkins.getNodes()) {    
                Computer.threadPoolForRemoting.submit(new MoveTask(oldFullName, newFullName, node));
            }
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
        
        private static class CleanupTaskProvisioner implements Runnable{
              
            private final TopLevelItem tli;
            
            private final Queue<Node> nodes;
            
            private static final double MEMORYSATURATIONLIMIT = 85.00;
            
            public CleanupTaskProvisioner(TopLevelItem tli, List<Node> nodes) {
                this.tli = tli;
                this.nodes = new LinkedList<>(nodes);
            }
            
            @Override
            public void run() {
                try {
                    boolean isRunning = true;
                    double usedMemoryPercent = calculateUsedMemoryPercentage();
                    while(isRunning){                   
                        // Get current used memory as Percentages
                        usedMemoryPercent = calculateUsedMemoryPercentage();
                        // While the maximum usage of Memory is below 85% And Queue isn't empty - Keep spawning threads
                        // If memory limit is exceeded break While Loop - Check if Queue is empty if not - retry
                        while(usedMemoryPercent <= this.MEMORYSATURATIONLIMIT && !nodes.isEmpty()){
                            Computer.threadPoolForRemoting.submit(new CleanupTask(tli, nodes.poll()));
                            usedMemoryPercent = calculateUsedMemoryPercentage();
                            LOGGER.log(Level.INFO, "Current used Memory in Percent: {0}%", usedMemoryPercent);
                        }
                    // If the Queue is empty it will set isRunning to False which will terminated the Loop
                    isRunning = !nodes.isEmpty();
                }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, e.getMessage());
                }
            }
        }
        
        private static double calculateUsedMemoryPercentage(){
            Runtime instance = Runtime.getRuntime();
            long totalMemory = instance.totalMemory();
            long freeMemory = instance.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            return (double)usedMemory / totalMemory * 100;
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
                        t.setName(oldName + ": possibly deleting workspace for " + tli.getFullName() + " on " + nodeName);
                        FilePath loc = locate(tli, node, false);
                        if (loc == null) {
                            return;
                        }
                        t.setName(oldName + ": deleting workspace for " + tli.getFullName() + " in " + loc + " on " + nodeName);
                        String base = loc.getName();
                        FilePath parent = loc.getParent();
                        if (parent == null) { // unlikely but just in case
                            return;
                        }
                        List<FilePath> dirs = parent.listDirectories();
                        for (FilePath child : dirs) {
                            String childName = child.getName();
                            if (childName.equals(base) || childName.startsWith(base + COMBINATOR)) {
                                LOGGER.log(Level.INFO, "deleting obsolete workspace {0} on {1}", new Object[] {child, nodeName});
                                child.deleteRecursive();
                            }
                        }
                        FilePath workspace = getWorkspaceRoot(node);
                        if (workspace != null) {
                            synchronized (lockFor(node)) {
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

        private static class MoveTask implements Runnable {

            @NonNull
            private final String oldFullName;

            @NonNull
            private final String newFullName;

            @NonNull
            private final Node node;

            MoveTask(String oldFullName, String newFullName, Node node) {
                this.oldFullName = oldFullName;
                this.newFullName = newFullName;
                this.node = node;
                taskStarted();
            }

            @Override
            public void run() {
                String nodeName = node instanceof Jenkins ? "master" : node.getNodeName();
                TopLevelItem tli = Jenkins.get().getItemByFullName(newFullName, TopLevelItem.class);
                if (tli == null) { // race condition after multiple renames, perhaps
                    LOGGER.warning(newFullName + " no longer exists so cannot process rename from " + oldFullName + " in " + nodeName);
                    return;
                }
                Thread t = Thread.currentThread();
                String oldName = t.getName();
                try {
                    try (Timeout timeout = Timeout.limit(5, TimeUnit.MINUTES)) {
                        FilePath from = locate(tli, oldFullName, node, false);
                        if (from == null) {
                            return;
                        }
                        FilePath to = locate(tli, newFullName, node, true);
                        if (to == null) {
                            return;
                        }
                        t.setName(oldName + ": moving workspace from " + from + " to " + to + " on " + nodeName);
                        String base = from.getName();
                        FilePath parent = from.getParent();
                        if (parent == null) {
                            return;
                        }
                        assert parent.equals(to.getParent());
                        List<FilePath> dirs = parent.listDirectories();
                        for (FilePath child : dirs) {
                            String childName = child.getName();
                            if (childName.equals(base) || childName.startsWith(base + COMBINATOR)) {
                                FilePath target = to.withSuffix(childName.substring(base.length()));
                                LOGGER.log(Level.INFO, "moving workspace {0} to {1} on {2}", new Object[] {child, target, nodeName});
                                child.renameTo(target);
                            }
                        }
                        FilePath workspace = getWorkspaceRoot(node);
                        if (workspace != null) {
                            synchronized (lockFor(node)) {
                                Map<String, String> index = load(workspace);
                                index.remove(oldFullName);
                                assert index.containsKey(newFullName); // locate(…, true) should have added it
                                save(index, workspace);
                            }
                        }
                    } catch (IOException | InterruptedException x) {
                        LOGGER.log(Level.WARNING, "could not move workspace directory from " + oldFullName + " on " + nodeName, x);
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
            synchronized (lockFor(node)) {
                Map<String, String> index = load(workspace);
                boolean modified = false;
                try (ACLContext as = ACL.as(ACL.SYSTEM)) {
                    Iterator<Map.Entry<String, String>> it = index.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, String> entry = it.next();
                        String fullName = entry.getKey();
                        if (Jenkins.get().getItemByFullName(fullName, TopLevelItem.class) == null) {
                            String path = entry.getValue();
                            it.remove();
                            modified = true;
                            for (FilePath child : workspace.listDirectories()) {
                                String childName = child.getName();
                                if (childName.equals(path) || childName.startsWith(path + COMBINATOR)) {
                                    listener.getLogger().println("deleting obsolete workspace " + child);
                                    try {
                                        child.deleteRecursive();
                                    } catch (IOException x) {
                                        //Prevent flooding logs if recursive delete fails
                                        if (x.getSuppressed().length != 0) {
                                            IOException e = new IOException(x.getMessage(), x.getCause());
                                            e.setStackTrace(x.getStackTrace());
                                            LOGGER.log(Level.WARNING, "could not delete workspace " + child + " on " + node.getNodeName() + " check finer logs for more information", e);
                                            LOGGER.log(Level.FINE, "could not delete workspace " + child + " on " + node.getNodeName() , x);
                                        } else {
                                            LOGGER.log(Level.WARNING, "could not delete workspace " + child + " on " + node.getNodeName(), x);
                                        }
                                        listener.getLogger().println("could not delete workspace " + child  + " on " + node.getNodeName()
                                                                        + " , wrong file ownership? Review exception in jenkins log and manually remove the directory");
                                    }
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
