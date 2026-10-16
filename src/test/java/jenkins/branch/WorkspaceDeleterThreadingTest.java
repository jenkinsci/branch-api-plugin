/*
 * The MIT License
 *
 * Copyright (c) 2026, Aitor Moya Lopez
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.model.AbstractItem;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Node;
import hudson.model.TopLevelItem;
import hudson.scm.NullSCM;
import hudson.slaves.DumbSlave;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jenkins.branch.harness.MultiBranchImpl;
import jenkins.scm.impl.SingleSCMSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Guards the behavior of {@link WorkspaceLocatorImpl.Deleter} when many items are deleted at once:
 * the number of cleanup threads must stay bounded (JENKINS-73208, JENKINS-63399),
 * a node that is slow or hung must not stop cleanup on the other nodes,
 * and every workspace and index entry must still be removed.
 * Slow or hung nodes are simulated by holding the per-node lock which every cleanup task needs.
 * See also {@link NodeQueueTest}.
 */
@WithJenkins
class WorkspaceDeleterThreadingTest {

    private JenkinsRule r;
    private WorkspaceLocatorImpl.Mode origMode;
    private final List<CountDownLatch> releases = new ArrayList<>();

    @SuppressWarnings("deprecation")
    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
        origMode = WorkspaceLocatorImpl.MODE;
        WorkspaceLocatorImpl.MODE = WorkspaceLocatorImpl.Mode.ENABLED;
    }

    @AfterEach
    void tearDown() {
        WorkspaceLocatorImpl.MODE = origMode;
        releases.forEach(CountDownLatch::countDown);
        // Deleter.runningTasks is static: a test which leaves tasks behind must fail here, loudly,
        // rather than have the counter silently reset (or driven negative) under the next one.
        finish();
    }

    /** Holds {@code lockFor(node)} on a daemon thread until the returned latch is released (or the test ends). */
    private CountDownLatch holdLock(Node node) throws Exception {
        Object lock = WorkspaceLocatorImpl.lockFor(node);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch held = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            synchronized (lock) {
                held.countDown();
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                }
            }
        }, "test lock holder for " + node.getNodeName());
        t.setDaemon(true);
        t.start();
        assertTrue(held.await(10, TimeUnit.SECONDS));
        releases.add(release);
        return release;
    }

    private List<Node> nodes(int agents) throws Exception {
        List<Node> nodes = new ArrayList<>();
        nodes.add(r.jenkins);
        for (int i = 0; i < agents; i++) {
            DumbSlave s = r.createOnlineSlave();
            nodes.add(s);
        }
        return nodes;
    }

    /** Registers a non-empty workspace for an item on every node. */
    private void withWorkspaces(AbstractItem p, List<Node> nodes) throws Exception {
        withWorkspaces(p, nodes, "x");
    }

    /** Registers a workspace for an item on every node, with a file {@code marker} in it containing the given text. */
    private void withWorkspaces(AbstractItem p, List<Node> nodes, String marker) throws Exception {
        WorkspaceLocatorImpl locator = ExtensionList.lookupSingleton(WorkspaceLocatorImpl.class);
        for (Node n : nodes) {
            FilePath ws = locator.locate((TopLevelItem) p, n);
            assertNotNull(ws, "workspace should be registered for " + p + " on " + n);
            ws.child("marker").write(marker, null);
        }
    }

    /** Creates {@code count} freestyle projects, each with a registered, non-empty workspace on every node. */
    private List<FreeStyleProject> projectsWithWorkspaces(int count, List<Node> nodes) throws Exception {
        List<FreeStyleProject> projects = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            FreeStyleProject p = r.createFreeStyleProject("job" + i);
            projects.add(p);
            withWorkspaces(p, nodes);
        }
        return projects;
    }

    private static int cleanupThreads() {
        int n = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().contains("deleting workspace for")) {
                n++;
            }
        }
        return n;
    }

    @FunctionalInterface
    private interface Condition {
        boolean holds() throws Exception;
    }

    private static void awaitCondition(String message, Condition condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!condition.holds()) {
            if (System.nanoTime() > end) {
                throw new AssertionError("timed out waiting for: " + message);
            }
            Thread.sleep(20);
        }
    }

    private static List<String> dirNames(Node node) throws Exception {
        FilePath root = WorkspaceLocatorImpl.getWorkspaceRoot(node);
        List<String> names = new ArrayList<>();
        if (root != null && root.isDirectory()) {
            for (FilePath d : root.listDirectories()) {
                names.add(d.getName());
            }
        }
        return names;
    }

    private static String index(Node node) throws Exception {
        FilePath f = WorkspaceLocatorImpl.getWorkspaceRoot(node).child(WorkspaceLocatorImpl.INDEX_FILE_NAME);
        return f.exists() ? f.readToString() : "";
    }

    private static void finish() {
        assertTimeoutPreemptively(Duration.ofSeconds(120), WorkspaceLocatorImpl.Deleter::waitForTasksToFinish);
    }

    @Issue({"JENKINS-73208", "JENKINS-63399"})
    @Test
    void massDeletionUsesAtMostOneCleanupThreadPerNode() throws Exception {
        List<Node> nodes = nodes(3);
        List<FreeStyleProject> projects = projectsWithWorkspaces(40, nodes);
        for (Node n : nodes) {
            holdLock(n); // every node is "slow": no cleanup task can complete
        }
        for (FreeStyleProject p : projects) {
            p.delete();
        }
        // Previously one thread per (deleted item x node), i.e. 160 here; now one per node, each parked on its lock.
        awaitCondition("a cleanup thread parked on each node", () -> cleanupThreads() == nodes.size());
        for (int i = 0; i < 10; i++) {
            assertEquals(nodes.size(), cleanupThreads(), "40 deletions on " + nodes.size() + " nodes");
            Thread.sleep(50);
        }
        releases.forEach(CountDownLatch::countDown);
        finish();
        for (Node n : nodes) {
            assertEquals(List.of(), dirNames(n), "all workspaces removed on " + n.getDisplayName());
            assertEquals("", index(n).trim(), "all index entries removed on " + n.getDisplayName());
        }
    }

    @Issue("JENKINS-73208")
    @Test
    void hungNodeDoesNotStopCleanupOnOtherNodes() throws Exception {
        List<Node> nodes = nodes(2);
        Node hung = nodes.get(1);
        Node healthy = nodes.get(2);
        List<FreeStyleProject> projects = projectsWithWorkspaces(20, nodes);
        CountDownLatch release = holdLock(hung);
        for (FreeStyleProject p : projects) {
            p.delete();
        }
        awaitCondition("cleanup of the other nodes", () -> {
            try {
                return dirNames(r.jenkins).isEmpty() && dirNames(healthy).isEmpty();
            } catch (Exception x) {
                throw new AssertionError(x);
            }
        });
        assertEquals(20, dirNames(hung).size(), "hung node cannot have been cleaned yet");
        release.countDown();
        finish();
        assertEquals(List.of(), dirNames(hung), "hung node cleaned once it recovers");
        assertEquals("", index(hung).trim());
    }

    @Issue("JENKINS-2111")
    @Test
    void survivorsKeepTheirWorkspaces() throws Exception {
        List<Node> nodes = nodes(1);
        List<FreeStyleProject> projects = projectsWithWorkspaces(10, nodes);
        FreeStyleProject survivor = projects.get(0);
        for (FreeStyleProject p : projects.subList(1, projects.size())) {
            p.delete();
        }
        finish();
        for (Node n : nodes) {
            assertEquals(List.of("job0"), dirNames(n));
            assertTrue(index(n).contains(survivor.getFullName()));
        }
    }

    @Issue("JENKINS-73208")
    @Test
    void concurrentDeletionsAreAllProcessed() throws Exception {
        List<Node> nodes = nodes(2);
        List<FreeStyleProject> projects = projectsWithWorkspaces(60, nodes);
        ExecutorService deleters = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (FreeStyleProject p : projects) {
                futures.add(deleters.submit(() -> {
                    p.delete();
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            deleters.shutdownNow();
        }
        finish();
        for (Node n : nodes) {
            assertEquals(List.of(), dirNames(n), "all workspaces removed on " + n.getDisplayName());
            assertEquals("", index(n).trim());
        }
    }

    @Issue("JENKINS-73208")
    @Test
    void failingCleanupOfOneItemDoesNotStopTheOthers() throws Exception {
        List<Node> nodes = nodes(1);
        FreeStyleProject gate = r.createFreeStyleProject("gate");
        withWorkspaces(gate, nodes);
        // WorkspaceLocatorImpl refuses to deal with a job whose name is that of its own index file.
        FreeStyleProject dangerous = r.createFreeStyleProject(WorkspaceLocatorImpl.INDEX_FILE_NAME);
        FreeStyleProject ordinary = r.createFreeStyleProject("ordinary");
        withWorkspaces(ordinary, nodes);
        for (Node n : nodes) {
            holdLock(n);
        }
        // The cleanup of gate runs first and parks on the lock of the node, so that the failing cleanup
        // and the next one are both pending behind it; the failing one throws before it takes the lock.
        gate.delete();
        dangerous.delete();
        ordinary.delete();
        releases.forEach(CountDownLatch::countDown);
        finish();
        for (Node n : nodes) {
            assertEquals(List.of(), dirNames(n),
                "the workspaces of the items before and after the failing one are cleaned on " + n.getDisplayName());
        }
    }

    @Issue("JENKINS-73208")
    @Test
    void workspaceOfAnItemRecreatedUnderADeletedNameIsKept() throws Exception {
        List<Node> nodes = nodes(1);
        FreeStyleProject old = r.createFreeStyleProject("reused");
        withWorkspaces(old, nodes, "fromOld");
        for (Node n : nodes) {
            holdLock(n); // so that the cleanup is still queued when the name is taken again
        }
        old.delete();
        r.createFreeStyleProject("reused"); // fires onCreated; renames do not
        releases.forEach(CountDownLatch::countDown);
        finish();
        for (Node n : nodes) {
            assertEquals(List.of("reused"), dirNames(n), "workspace of the new item kept on " + n.getDisplayName());
            assertTrue(index(n).contains("reused"), "and its index entry on " + n.getDisplayName());
        }
    }

    @Issue("JENKINS-73208")
    @Test
    void deletingAnItemAgainAfterItWasRecreatedStillCleansUp() throws Exception {
        List<Node> nodes = nodes(1);
        FreeStyleProject old = r.createFreeStyleProject("reused");
        withWorkspaces(old, nodes);
        for (Node n : nodes) {
            holdLock(n);
        }
        old.delete();
        FreeStyleProject recreated = r.createFreeStyleProject("reused");
        recreated.delete(); // so the workspace is no longer wanted by anyone
        releases.forEach(CountDownLatch::countDown);
        finish();
        for (Node n : nodes) {
            assertEquals(List.of(), dirNames(n), "cleaned up after the second deletion on " + n.getDisplayName());
            assertEquals("", index(n).trim());
        }
    }

    @Issue("JENKINS-73208")
    @Test
    void queuedCleanupsDoNotRetainTheDeletedItems() {
        // A node which hangs leaves its queue to drain for hours: it must not keep every deleted job alive.
        // Checked structurally, since a test relying on garbage collection would be unreliable.
        for (Field f : WorkspaceLocatorImpl.Deleter.CleanupTask.class.getDeclaredFields()) {
            assertFalse(Item.class.isAssignableFrom(f.getType()), "CleanupTask." + f.getName() + " retains an item");
        }
    }

    @Issue("JENKINS-73208")
    @Test
    void anItemRenamedOntoTheNameOfADeletedOneGetsItsOwnWorkspace() throws Exception {
        List<Node> nodes = nodes(1);
        FreeStyleProject x = r.createFreeStyleProject("X");
        FreeStyleProject y = r.createFreeStyleProject("Y");
        withWorkspaces(x, nodes, "fromX");
        withWorkspaces(y, nodes, "fromY");
        for (Node n : nodes) {
            holdLock(n); // so that the cleanup of X is still queued when Y takes its name
        }
        x.delete();
        y.renameTo("X");
        releases.forEach(CountDownLatch::countDown);
        finish();
        for (Node n : nodes) {
            assertEquals(List.of("X"), dirNames(n), "one workspace left on " + n.getDisplayName());
            assertEquals("fromY", WorkspaceLocatorImpl.getWorkspaceRoot(n).child("X/marker").readToString(),
                "the renamed item keeps its own workspace on " + n.getDisplayName());
            assertEquals("X\nX", index(n).trim().replace("\r", ""),
                "index entry for Y moved to X on " + n.getDisplayName());
        }
    }

    @Issue("JENKINS-73208")
    @Test
    void cleanupUsesTheNameTheItemHadWhenItWasDeleted() throws Exception {
        List<Node> nodes = nodes(1);
        Folder f = r.jenkins.createProject(Folder.class, "F");
        FreeStyleProject a = f.createProject(FreeStyleProject.class, "a");
        withWorkspaces(a, nodes);
        FreeStyleProject other = r.createFreeStyleProject("other");
        withWorkspaces(other, nodes);
        for (Node n : nodes) {
            holdLock(n); // parks the first task, so that the cleanup of F/a is still queued when its folder is renamed
        }
        other.delete();
        a.delete();
        f.renameTo("G");
        releases.forEach(CountDownLatch::countDown);
        finish();
        for (Node n : nodes) {
            assertEquals(List.of(), dirNames(n), "workspace of F/a removed on " + n.getDisplayName());
            assertEquals("", index(n).trim(), "index entry of F/a removed on " + n.getDisplayName());
        }
    }

    @Issue("JENKINS-2111")
    @Test
    void offlineAgentsAreLeftToTheCollector() throws Exception {
        DumbSlave offline = r.createOnlineSlave();
        offline.toComputer().disconnect(null).get();
        DumbSlave online = r.createOnlineSlave();
        List<Node> nodes = WorkspaceLocatorImpl.Deleter.nodesToClean();
        assertTrue(nodes.contains(r.jenkins));
        assertTrue(nodes.contains(online));
        assertFalse(nodes.contains(offline), "nothing can be done on a node which is not connected");
    }

    @Issue("JENKINS-2111")
    @Test
    void renamingAMultiBranchProjectMovesTheWorkspacesOfItsBranchesInTheDefaultMode() throws Exception {
        WorkspaceLocatorImpl.MODE = WorkspaceLocatorImpl.Mode.MULTIBRANCH_ONLY; // the default; other tests use ENABLED
        List<Node> nodes = nodes(1);
        MultiBranchImpl p = r.createProject(MultiBranchImpl.class, "p");
        p.getSourcesList().add(new BranchSource(new SingleSCMSource("master", new NullSCM())));
        p.scheduleBuild2(0).getFuture().get();
        r.waitUntilNoActivity();
        FreeStyleProject master = r.jenkins.getItemByFullName("p/master", FreeStyleProject.class);
        assertNotNull(master);
        withWorkspaces(master, nodes, "fromP");
        p.renameTo("q"); // tells the listener about every branch too
        finish();
        for (Node n : nodes) {
            assertEquals(List.of("q_master"), dirNames(n), "branch workspace moved on " + n.getDisplayName());
            assertEquals("fromP", WorkspaceLocatorImpl.getWorkspaceRoot(n).child("q_master/marker").readToString());
        }
    }

    @WithoutJenkins
    @Test
    void modeDecidesWhichItemsAreWorthQueueing() {
        WorkspaceLocatorImpl.MODE = WorkspaceLocatorImpl.Mode.DISABLED;
        assertFalse(WorkspaceLocatorImpl.applicable(true));
        assertFalse(WorkspaceLocatorImpl.applicable(false));
        WorkspaceLocatorImpl.MODE = WorkspaceLocatorImpl.Mode.MULTIBRANCH_ONLY;
        assertTrue(WorkspaceLocatorImpl.applicable(true));
        assertFalse(WorkspaceLocatorImpl.applicable(false));
        WorkspaceLocatorImpl.MODE = WorkspaceLocatorImpl.Mode.ENABLED;
        assertTrue(WorkspaceLocatorImpl.applicable(true));
        assertTrue(WorkspaceLocatorImpl.applicable(false));
    }

    @Issue("JENKINS-2111")
    @Test
    void renamesStillMoveWorkspacesOnEveryNode() throws Exception {
        List<Node> nodes = nodes(2);
        List<FreeStyleProject> projects = projectsWithWorkspaces(5, nodes);
        for (FreeStyleProject p : projects) {
            p.renameTo("renamed-" + p.getName());
        }
        finish();
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            expected.add("renamed-job" + i);
        }
        for (Node n : nodes) {
            List<String> actual = dirNames(n);
            actual.sort(null);
            assertEquals(expected, actual, "workspaces moved on " + n.getDisplayName());
        }
    }

    @Issue("JENKINS-73208")
    @Test
    void doubleRenameWhileQueuedMovesTheWorkspaceAllTheWay() throws Exception {
        List<Node> nodes = nodes(1);
        FreeStyleProject p = r.createFreeStyleProject("X");
        withWorkspaces(p, nodes, "fromX");
        FreeStyleProject other = r.createFreeStyleProject("other");
        withWorkspaces(other, nodes);
        for (Node n : nodes) {
            holdLock(n);
        }
        // The cleanup of other runs first and parks on the lock of the node,
        // so that both moves are still pending, not yet started, when the item gets its final name.
        other.delete();
        p.renameTo("Y");
        p.renameTo("Z");
        releases.forEach(CountDownLatch::countDown);
        finish();
        for (Node n : nodes) {
            assertEquals(List.of("Z"), dirNames(n), "workspace moved all the way on " + n.getDisplayName());
            assertEquals("fromX", WorkspaceLocatorImpl.getWorkspaceRoot(n).child("Z/marker").readToString(),
                "with its contents on " + n.getDisplayName());
            assertEquals("Z\nZ", index(n).trim().replace("\r", ""),
                "only the final name is indexed on " + n.getDisplayName());
        }
    }

    @Issue("JENKINS-73208")
    @Test
    void renameThenDeleteWhileQueuedLeavesNothingBehind() throws Exception {
        List<Node> nodes = nodes(1);
        FreeStyleProject p = r.createFreeStyleProject("X");
        withWorkspaces(p, nodes);
        FreeStyleProject other = r.createFreeStyleProject("other");
        withWorkspaces(other, nodes);
        for (Node n : nodes) {
            holdLock(n);
        }
        // The cleanup of other runs first and parks on the lock of the node,
        // so that the move is still pending, not yet started, when the item is deleted.
        other.delete();
        p.renameTo("Z");
        p.delete();
        releases.forEach(CountDownLatch::countDown);
        finish();
        for (Node n : nodes) {
            assertEquals(List.of(), dirNames(n), "workspace removed on " + n.getDisplayName());
            assertEquals("", index(n).trim(), "index entries removed on " + n.getDisplayName());
        }
    }

    @Issue("JENKINS-73208")
    @Test
    void obsoleteMarksPendingCleanupsNotOnlyTheRunningOne() throws Exception {
        List<Node> nodes = nodes(1);
        FreeStyleProject other = r.createFreeStyleProject("other");
        FreeStyleProject x = r.createFreeStyleProject("X");
        withWorkspaces(other, nodes);
        withWorkspaces(x, nodes);
        for (Node n : nodes) {
            holdLock(n);
        }
        other.delete(); // its cleanup is the running task, parked on the lock of the node
        x.delete(); // so this one is still pending behind it
        r.createFreeStyleProject("X"); // fires onCreated
        releases.forEach(CountDownLatch::countDown);
        finish();
        for (Node n : nodes) {
            assertEquals(List.of("X"), dirNames(n),
                "workspace of the new X kept and that of other removed on " + n.getDisplayName());
            assertTrue(index(n).contains("X"), "index entry of X kept on " + n.getDisplayName());
        }
    }

}
