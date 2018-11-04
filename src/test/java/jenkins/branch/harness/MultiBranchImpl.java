/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

package jenkins.branch.harness;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import hudson.Extension;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.MultiBranchProjectDescriptor;

public class MultiBranchImpl extends MultiBranchProject<FreeStyleProject, FreeStyleBuild> {

    private static final Logger LOGGER = Logger.getLogger(MultiBranchImpl.class.getName());
    private static WeakHashMap<MultiBranchImpl, Long> lastIndex = new WeakHashMap<>();
    private static Lock lastIndexLock = new ReentrantLock();
    private static Condition lastIndexUpdated = lastIndexLock.newCondition();

    /**
     * Awaits a fresh indexing where the index scheduling happens after the start of this method.
     */
    public static boolean awaitIndexed(ItemGroup parent, String displayName, long timeout, TimeUnit units)
            throws InterruptedException {
        long nanos = units.toNanos(timeout);

        lastIndexLock.lock();
        try {
            MultiBranchImpl target = null;
            Long timestamp = null;
            for (Map.Entry<MultiBranchImpl, Long> entry : lastIndex.entrySet()) {
                if (entry.getKey().getParent() == parent && displayName.equals(entry.getKey().getDisplayName())) {
                    target = entry.getKey();
                    timestamp = entry.getValue();
                    break;
                }
            }
            while (true) {
                if (target != null) {
                    if (timestamp == null ? lastIndex.get(target) != null : !timestamp.equals(lastIndex.get(target))) {
                        return true;
                    }
                } else {
                    for (Map.Entry<MultiBranchImpl, Long> entry : lastIndex.entrySet()) {
                        if (entry.getKey().getParent() == parent && displayName
                                .equals(entry.getKey().getDisplayName())) {
                            return true;
                        }
                    }
                }
                if (nanos < 0L) {
                    return false;
                }
                nanos = lastIndexUpdated.awaitNanos(nanos);
            }
        } finally {
            lastIndexLock.unlock();
        }
    }

    public MultiBranchImpl(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override
    protected BranchProjectFactory<FreeStyleProject, FreeStyleBuild> newProjectFactory() {
        return new BranchProjectFactoryImpl();
    }

    @Override
    public boolean scheduleBuild() {
        lastIndexLock.lock();
        try {
            lastIndex.put(this, System.currentTimeMillis());
            lastIndexUpdated.signalAll();
        } finally {
            lastIndexLock.unlock();
        }
        LOGGER.info("Indexing multibranch project: " + getDisplayName());
        return super.scheduleBuild();
    }

    @Extension
    public static class DescriptorImpl extends MultiBranchProjectDescriptor {

        @Override 
        public String getDisplayName() {
            return "Test Multibranch";
        }

        @Override 
        public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new MultiBranchImpl(parent, name);
        }
    }
}
