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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import jenkins.branch.WorkspaceLocatorImpl.Deleter.NodeQueue;
import jenkins.branch.WorkspaceLocatorImpl.Deleter.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

/**
 * Contract of {@link NodeQueue}, the part of {@link WorkspaceLocatorImpl.Deleter} which keeps the work for one node
 * on at most one thread, and in order, and which accounts for every task it is given.
 */
@Issue({"JENKINS-73208", "JENKINS-63399"})
class NodeQueueTest {

    private final List<ExecutorService> pools = new ArrayList<>();

    /** Latches which tasks may be blocked on, released when the test ends however it ends. */
    private final List<CountDownLatch> releases = new ArrayList<>();

    @AfterEach
    void tearDown() {
        // So that a test which failed while tasks were blocked does not leave them counted for the next tests.
        releases.forEach(CountDownLatch::countDown);
        try {
            finish();
        } finally {
            pools.forEach(ExecutorService::shutdownNow);
        }
    }

    /** A latch which tasks block on until the test counts it down, or else until the test ends. */
    private CountDownLatch release() {
        CountDownLatch release = new CountDownLatch(1);
        releases.add(release);
        return release;
    }

    private static Task task(Runnable body) {
        return new Task() {
            @Override
            void doRun() {
                body.run();
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS), "timed out");
        } catch (InterruptedException x) {
            throw new AssertionError(x);
        }
    }

    private static void finish() {
        assertTimeoutPreemptively(Duration.ofSeconds(60), WorkspaceLocatorImpl.Deleter::waitForTasksToFinish);
    }

    @Test
    void tasksRunInSubmissionOrderAndNeverConcurrently() {
        NodeQueue q = new NodeQueue("n", null);
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            int id = i;
            expected.add(i);
            q.submit(task(() -> {
                maxConcurrent.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
                order.add(id);
                concurrent.decrementAndGet();
            }));
        }
        finish();
        assertEquals(1, maxConcurrent.get());
        assertEquals(expected, order);
    }

    @Test
    void aQueueRunsOneTaskAtATimeAndItsWaitingTasksDoNotOccupyThreads() throws Exception {
        oneTaskAtATimeWithoutOccupyingThreads(null);
        ExecutorService shared = Executors.newFixedThreadPool(2);
        pools.add(shared);
        oneTaskAtATimeWithoutOccupyingThreads(shared);
    }

    private void oneTaskAtATimeWithoutOccupyingThreads(ExecutorService shared) throws InterruptedException {
        NodeQueue q = new NodeQueue("n", shared);
        CountDownLatch release = release();
        AtomicInteger started = new AtomicInteger();
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        for (int i = 0; i < 50; i++) {
            q.submit(task(() -> {
                boolean first = started.incrementAndGet() == 1;
                maxConcurrent.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
                if (first) {
                    await(release);
                }
                concurrent.decrementAndGet();
            }));
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (started.get() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, started.get(), "the first task must start");
        // Another queue on the same executor still gets a thread: the 49 tasks waiting behind the first take none.
        CountDownLatch otherRan = new CountDownLatch(1);
        new NodeQueue("other", shared).submit(task(otherRan::countDown));
        await(otherRan);
        for (int i = 0; i < 20; i++) { // and none of them starts while the first one is blocked
            assertEquals(1, started.get(), "only one task of a queue may run at once");
            Thread.sleep(10);
        }
        release.countDown();
        finish();
        assertEquals(50, started.get());
        assertEquals(1, maxConcurrent.get());
    }

    @Test
    void aBlockedQueueDoesNotBlockAnother() {
        NodeQueue hung = new NodeQueue("hung", null);
        NodeQueue healthy = new NodeQueue("healthy", null);
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = release();
        for (int i = 0; i < 20; i++) {
            hung.submit(task(() -> {
                blocked.countDown();
                await(release);
            }));
        }
        await(blocked);
        CountDownLatch done = new CountDownLatch(20);
        for (int i = 0; i < 20; i++) {
            healthy.submit(task(done::countDown));
        }
        await(done);
        release.countDown();
        finish();
    }

    @Test
    void aFailingTaskDoesNotStallTheQueueOrTheAccounting() {
        NodeQueue q = new NodeQueue("n", null);
        AtomicBoolean ran = new AtomicBoolean();
        CountDownLatch release = release();
        // The gate keeps the queue busy, so that both tasks below are pending before the failing one runs:
        // otherwise the next task could be submitted while the failing one is running, and be run anyway.
        q.submit(task(() -> await(release)));
        q.submit(task(() -> {
            throw new IllegalArgumentException("expected in test, like the one for a dangerous job name");
        }));
        q.submit(task(() -> ran.set(true)));
        release.countDown();
        finish();
        assertTrue(ran.get(), "a task after a failing one must still run");
    }

    @Test
    void aTaskSubmittedFromTheLastRunningTaskIsStillProcessed() {
        NodeQueue q = new NodeQueue("n", null);
        AtomicBoolean ranLater = new AtomicBoolean();
        q.submit(task(() -> q.submit(task(() -> ranLater.set(true)))));
        finish();
        assertTrue(ranLater.get());
    }

    @Test
    void tasksSubmittedConcurrentlyWhileDrainingAreAllProcessed() throws Exception {
        NodeQueue q = new NodeQueue("n", null);
        int threads = 4;
        int perThread = 2000;
        AtomicInteger ran = new AtomicInteger();
        ExecutorService submitters = Executors.newFixedThreadPool(threads);
        pools.add(submitters);
        CountDownLatch go = release();
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(submitters.submit(() -> {
                await(go);
                for (int i = 0; i < perThread; i++) {
                    q.submit(task(ran::incrementAndGet));
                }
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        finish();
        assertEquals(threads * perThread, ran.get());
    }

    @Test
    void tasksWhichCannotBeScheduledAreCancelledAndNotCounted() {
        ExecutorService shared = Executors.newFixedThreadPool(1);
        shared.shutdown(); // so it rejects everything, as when no thread can be created
        NodeQueue q = new NodeQueue("n", shared);
        AtomicBoolean ran = new AtomicBoolean();
        assertFalse(q.submit(task(() -> ran.set(true))), "the caller must learn that the queue is unusable");
        finish(); // would hang if the task stayed counted
        assertFalse(ran.get());
    }

    @Test
    void aSharedExecutorInterleavesNodesInsteadOfDrainingOneNodeAtATime() {
        ExecutorService shared = Executors.newFixedThreadPool(1);
        pools.add(shared);
        NodeQueue gate = new NodeQueue("gate", shared);
        NodeQueue a = new NodeQueue("a", shared);
        NodeQueue b = new NodeQueue("b", shared);
        CountDownLatch gateStarted = new CountDownLatch(1);
        CountDownLatch release = release();
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        gate.submit(task(() -> {
            gateStarted.countDown();
            await(release);
        }));
        await(gateStarted); // the only shared thread is busy, so everything below is queued before any of it runs
        for (int i = 0; i < 3; i++) {
            String name = "a" + i;
            a.submit(task(() -> log.add(name)));
        }
        b.submit(task(() -> log.add("b0")));
        release.countDown();
        finish();
        assertEquals(4, log.size(), log.toString());
        assertTrue(log.indexOf("b0") < log.indexOf("a2"),
            "a node with a long backlog must give up the shared thread between tasks, but ran: " + log);
    }

}
