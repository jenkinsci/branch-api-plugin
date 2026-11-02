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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
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
import hudson.util.ClassLoaderSanityThreadFactory;
import hudson.util.DaemonThreadFactory;
import hudson.util.ExceptionCatchingThreadFactory;
import hudson.util.NamingThreadFactory;
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
import java.util.concurrent.ExecutorService;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import jenkins.security.ImpersonatingExecutorService;
import jenkins.slaves.WorkspaceLocator;
import jenkins.util.AtmostOneTaskExecutor;
import jenkins.util.ContextResettingExecutorService;
import jenkins.util.ErrorLoggingExecutorService;
import jenkins.util.SystemProperties;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.StringUtils;
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

    /**
     * Whether this implementation handles workspaces of a project at all, according to {@link #MODE}.
     * @param branch whether the project is a branch project, that is a child of a {@link MultiBranchProject}
     */
    static boolean applicable(boolean branch) {
        switch (MODE) {
        case DISABLED:
            return false;
        case MULTIBRANCH_ONLY:
            return branch;
        case ENABLED:
            return true;
        default:
            throw new AssertionError();
        }
    }

    private static FilePath locate(TopLevelItem item, Node node, boolean create) {
        return locate(item, item.getFullName(), node, create);
    }

    @CheckForNull
    private static FilePath locate(TopLevelItem item, String fullName, Node node, boolean create) {
        return locate(item.getParent() instanceof MultiBranchProject, item, fullName, node, create);
    }

    /**
     * @param branch whether the item is a branch project, that is a child of a {@link MultiBranchProject}
     * @param item the item, or something which describes it: only used in log messages, so that a task
     *             which waits for a long time can do without keeping the item itself
     */
    @CheckForNull
    private static FilePath locate(boolean branch, Object item, String fullName, Node node, boolean create) {
        if (!applicable(branch)) {
            LOGGER.log(Level.FINE, "not handling {0} on {1} in mode {2}", new Object[] {item, node, MODE});
            return null;
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
                if (PATH_MAX != 0 && branch) {
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
                    if (index.containsValue(path)) {
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
        synchronized (_indexCache) {
            _indexCache.put(workspace.getChannel(), new IndexCacheEntry(workspace.getRemote(), map));
        }
        return map;
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
    private final LoadingCache<Node, Object> nodeLocks = Caffeine.newBuilder().weakKeys().build(node -> {
        // Avoiding new Object() to prepare for http://cr.openjdk.java.net/~briangoetz/valhalla/sov/02-object-model.html
        // Avoiding new String(…) because static analyzers complain
        // Could use anything but hoping that a future JVM enhances thread dumps to display monitors of type String
        return new StringBuilder("WorkspaceLocatorImpl lock for ").append(node.getNodeName()).toString();
    });
    /**
     * The monitor which guards the workspace index of a node.
     * Package-private so that tests can hold it to simulate a slow or hung node.
     */
    static Object lockFor(Node node) {
        return ExtensionList.lookupSingleton(WorkspaceLocatorImpl.class).nodeLocks.get(node);
    }

    private static String nodeName(Node node) {
        return node instanceof Jenkins ? "master" : node.getNodeName();
    }

    private static final Pattern GOOD_RAW_WORKSPACE_DIR = Pattern.compile("(.+)[/\\\\][$][{]ITEM_FULL_?NAME[}][/\\\\]?");
    static @CheckForNull FilePath getWorkspaceRoot(Node node) {
        if (node instanceof Jenkins) {
            String rawWorkspaceDir = ((Jenkins) node).getRawWorkspaceDir();
            Matcher m = GOOD_RAW_WORKSPACE_DIR.matcher(rawWorkspaceDir);
            if (m.matches()) {
                return new FilePath(new File(m.group(1).replace("${JENKINS_HOME}", ((Jenkins) node).getRootDir().getAbsolutePath())));
            } else {
                LOGGER.fine(() -> "JENKINS-2111 path sanitization ineffective when using Workspace Root Directory " + rawWorkspaceDir + "; stop overriding workspacesDir");
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
            sha256 = MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_16LE));
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
            maxMnemonic = Math.max((PATH_MAX - minSuffix) / 2, 1);
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

        /**
         * Maximum number of threads shared by all nodes for cleaning up or moving workspaces.
         * When unset (or not positive), the tasks of each node instead run one at a time on threads from
         * {@link Computer#threadPoolForRemoting}, so there are never more threads in use than nodes with pending work.
         * Either way, the tasks for a given node run one at a time, in the order in which items were deleted or moved.
         * (A node is told apart by its {@link Node} instance, so after an agent has been reconfigured
         * the new one gets a queue of its own, and the tasks still pending for the old one finish independently.)
         * @see "JENKINS-73208"
         */
        private static final int CLEANUP_THREAD_LIMIT = SystemProperties.getInteger(Deleter.class.getName() + ".CLEANUP_THREAD_LIMIT", 0);

        /** Shared by all nodes if {@link #CLEANUP_THREAD_LIMIT} is positive, else null. */
        @CheckForNull
        private static final ExecutorService SHARED_EXECUTOR =
            CLEANUP_THREAD_LIMIT > 0 ? sharedExecutor(CLEANUP_THREAD_LIMIT) : null;

        /**
         * Keyed by identity, as {@link #nodeLocks} is, so reconfiguring an agent gives it a new queue.
         * Values must not keep referring to their {@link Node} key when idle, or entries would never be collected:
         * only the tasks waiting in them do.
         */
        private static final LoadingCache<Node, NodeQueue> NODE_QUEUES = Caffeine.newBuilder().weakKeys().build(node ->
            new NodeQueue(nodeName(node), SHARED_EXECUTOR));

        /** Number of {@link Task}s which have been scheduled but not yet completed. */
        private static int runningTasks;

        private static ExecutorService sharedExecutor(int threads) {
            ThreadPoolExecutor tpe = new ThreadPoolExecutor(threads, threads, 10L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ExceptionCatchingThreadFactory(
                    new NamingThreadFactory(
                        new ClassLoaderSanityThreadFactory(new DaemonThreadFactory()),
                        "Deleter.cleanupTask")));
            // Use allowCoreThreadTimeOut to keep a lightweight ThreadPoolExecutor. The Thread pool grows to the
            // limit and then queue tasks. Thread will then be removed when idle
            tpe.allowCoreThreadTimeOut(true);
            return new ContextResettingExecutorService(
                new ImpersonatingExecutorService(
                    new ErrorLoggingExecutorService(tpe),
                        ACL.SYSTEM2));
        }

        /**
         * Pending {@link Task}s for one node, run one at a time.
         * Until the pending tasks have all been run there is at most one thread working on them,
         * so a node which is slow or hung cannot tie up more than that.
         *
         * @since FIXME
         */
        static final class NodeQueue {

            /** Tasks waiting for their turn, in the order in which they were given. */
            private final Queue<Task> pending = new ConcurrentLinkedQueue<>();

            /** Guards moving a task from {@link #pending} to {@link #running}, so it is always in one of them. */
            private final Object lock = new Object();

            /**
             * The task being run, if any: it has left {@link #pending} but may still wait, say for the node's lock.
             */
            private volatile Task running;

            /** Runs {@link #drain} on the base executor, never more than once at a time. */
            private final AtmostOneTaskExecutor<Void> executor;

            /** Only for log messages. */
            private final String nodeName;

            /**
             * @param nodeName used in log messages
             * @param shared the executor to run on, or null to use {@link Computer#threadPoolForRemoting}
             */
            NodeQueue(String nodeName, @CheckForNull ExecutorService shared) {
                this.nodeName = nodeName;
                ExecutorService base = shared != null ? shared : Computer.threadPoolForRemoting;
                executor = new AtmostOneTaskExecutor<>(base, this::drain);
            }

            /** Tells cleanups still waiting for a deleted item of this name that a new one has since been created. */
            void itemCreated(String fullName) {
                synchronized (lock) {
                    markIfCleanup(running, fullName);
                    for (Task task : pending) {
                        markIfCleanup(task, fullName);
                    }
                }
            }

            private static void markIfCleanup(@CheckForNull Task task, String fullName) {
                if (task instanceof CleanupTask) {
                    ((CleanupTask) task).obsoleteIfNamed(fullName);
                }
            }

            /**
             * Queues a task to run after any others.
             * @return false if it could not be scheduled, which in practice means that no thread could be created;
             *         then this and any other task still pending were cancelled, and this queue should be discarded
             *         since {@link AtmostOneTaskExecutor} cannot recover from such a failure.
             *         Only a failure to schedule a task given to an idle queue is reported this way:
             *         if {@link AtmostOneTaskExecutor} fails to reschedule itself after a task has completed,
             *         which can only happen for the same reason, nothing is reported and the tasks still pending
             *         for that node are left unrun until the queue is replaced (the agent being reconfigured) or
             *         Jenkins is restarted.
             */
            boolean submit(Task task) {
                pending.add(task);
                try {
                    // If a drain is already running it will see the task, or else it is rerun once it completes.
                    executor.submit();
                    return true;
                } catch (RuntimeException | Error x) {
                    LOGGER.log(Level.WARNING, "could not schedule a workspace task for " + nodeName, x);
                    Task t;
                    while ((t = pending.poll()) != null) {
                        t.cancel();
                    }
                    return false;
                }
            }

            /** Runs a single task, then reschedules itself if there are more: see {@link #CLEANUP_THREAD_LIMIT}. */
            private Void drain() {
                Task task;
                synchronized (lock) {
                    task = pending.poll();
                    running = task;
                }
                if (task != null) {
                    try {
                        task.run();
                    } catch (Throwable t) {
                        LOGGER.log(Level.WARNING, "workspace task failed", t);
                    } finally {
                        running = null;
                    }
                }
                if (!pending.isEmpty()) {
                    // Go to the back of the line, so that when the thread is shared with other nodes,
                    // one with a long (or hung) backlog does not keep it from the rest.
                    // This only records the request: the executor runs it once this call returns.
                    executor.submit();
                }
                return null;
            }

        }

        /** The controller, and any agent with a workspace root now (so not offline ones, left to {@link Collector}). */
        static List<Node> nodesToClean() {
            Jenkins jenkins = Jenkins.get();
            List<Node> nodes = new ArrayList<>();
            nodes.add(jenkins);
            nodes.addAll(jenkins.getNodes());
            nodes.removeIf(node -> getWorkspaceRoot(node) == null);
            return nodes;
        }

        /** Queues a task for a node, creating it only once the queue is at hand so that it is never left uncounted. */
        private static void submit(Node node, Function<Node, Task> task) {
            NodeQueue queue = NODE_QUEUES.get(node);
            if (!queue.submit(task.apply(node))) {
                NODE_QUEUES.asMap().remove(node, queue); // unless another thread has already replaced it
            }
        }

        /**
         * A cleanup waiting for its turn cannot tell, by the time it runs, whether the workspace it was meant for
         * now belongs to a new item which has taken the name of the one deleted.
         * Renaming an item does not call this, which is why an item renamed onto the name of a deleted one
         * does not stop that one's cleanup: it still has to make way for the move.
         */
        @Override
        public void onCreated(Item item) {
            if (item instanceof TopLevelItem) {
                String fullName = item.getFullName();
                for (NodeQueue queue : NODE_QUEUES.asMap().values()) {
                    queue.itemCreated(fullName);
                }
            }
        }

        @Override
        public void onDeleted(Item item) {
            if (!(item instanceof TopLevelItem)) {
                return;
            }
            TopLevelItem tli = (TopLevelItem) item;
            if (!applicable(tli.getParent() instanceof MultiBranchProject)) {
                return; // nothing for any node to do
            }
            for (Node node : nodesToClean()) {
                submit(node, n -> new CleanupTask(tli, n));
            }
        }

        @Override
        public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            if (!(item instanceof TopLevelItem)) {
                return;
            }
            boolean branch = item.getParent() instanceof MultiBranchProject;
            if (!applicable(branch)) {
                return; // nothing for any node to do
            }
            for (Node node : nodesToClean()) {
                submit(node, n -> new MoveTask(oldFullName, newFullName, branch, n));
            }
        }

        /**
         * Waits until every {@link Task} which has been given to a node has either run or been cancelled.
         * Only for tests.
         */
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

        /**
         * A unit of work for a node which is tracked by {@link #waitForTasksToFinish}.
         *
         * @since FIXME
         */
        abstract static class Task implements Runnable {

            Task() {
                taskStarted();
            }

            @Override
            public final void run() {
                try {
                    doRun();
                } finally {
                    taskFinished();
                }
            }

            /** What the task does when its turn comes. */
            abstract void doRun();

            /** Called instead of {@link #run} when the task will never run. */
            void cancel() {
                taskFinished();
            }

        }

        /** Deletes the workspaces of an item which has been deleted, on one node. */
        static class CleanupTask extends Task {

            /**
             * The name the item had when it was deleted: its parent may since have been renamed.
             * The item itself is not kept, since a task may wait a long time and there can be very many of them.
             */
            @NonNull
            private final String fullName;

            /** Whether the item was a child of a {@link MultiBranchProject}. */
            private final boolean branch;

            @NonNull
            private final Node node;

            /** Set when a new item has taken the name since, which now owns whatever workspace is there. */
            private volatile boolean obsolete;

            CleanupTask(TopLevelItem tli, Node node) {
                this.fullName = tli.getFullName();
                this.branch = tli.getParent() instanceof MultiBranchProject;
                this.node = node;
            }

            /** Called when an item has been created, whose full name may be that of the deleted one. */
            void obsoleteIfNamed(String newItemFullName) {
                if (fullName.equals(newItemFullName)) {
                    obsolete = true;
                }
            }

            @Override
            void doRun() {
                Thread t = Thread.currentThread();
                String oldName = t.getName();
                String nodeName = nodeName(node);
                try {
                    try (Timeout timeout = Timeout.limit(5, TimeUnit.MINUTES)) {
                        t.setName(oldName + ": possibly deleting workspace for " + fullName + " on " + nodeName);
                        FilePath loc = locate(branch, fullName, fullName, node, false);
                        // Checked after locate, which may have waited a while for the lock on the node.
                        if (loc == null || obsolete) {
                            return;
                        }
                        t.setName(oldName + ": deleting workspace for " + fullName + " in " + loc + " on " + nodeName);
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
                                index.remove(fullName);
                                save(index, workspace);
                            }
                        }
                    } catch (IOException | InterruptedException x) {
                        LOGGER.log(Level.WARNING,
                            "could not clean up workspace directory for " + fullName + " on " + nodeName, x);
                    }
                } finally {
                    t.setName(oldName);
                }
            }

        }

        /** Moves the workspaces of an item which has been renamed or moved, on one node. */
        static class MoveTask extends Task {

            @NonNull
            private final String oldFullName;

            @NonNull
            private final String newFullName;

            /** Whether the item was a child of a {@link MultiBranchProject}. */
            private final boolean branch;

            @NonNull
            private final Node node;

            /**
             * The item is not looked up when the task runs: by then it may have been renamed again, or deleted,
             * and the tasks queued after this one for the node, which run in order, take care of that.
             */
            MoveTask(String oldFullName, String newFullName, boolean branch, Node node) {
                this.oldFullName = oldFullName;
                this.newFullName = newFullName;
                this.branch = branch;
                this.node = node;
            }

            @Override
            void doRun() {
                String nodeName = nodeName(node);
                Thread t = Thread.currentThread();
                String oldName = t.getName();
                try {
                    try (Timeout timeout = Timeout.limit(5, TimeUnit.MINUTES)) {
                        FilePath from = locate(branch, newFullName, oldFullName, node, false);
                        if (from == null) {
                            return;
                        }
                        FilePath to = locate(branch, newFullName, newFullName, node, true);
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
                try (ACLContext as = ACL.as2(ACL.SYSTEM2)) {
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
