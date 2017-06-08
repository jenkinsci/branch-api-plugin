/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.Timer;

// TODO copied from org.jenkinsci.plugins.workflow.support.concurrent pending JENKINS-44785
class Timeout implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(Timeout.class.getName());

    private final ScheduledFuture<?> task;

    private Timeout(ScheduledFuture<?> task) {
        this.task = task;
    }

    @Override public void close() {
        task.cancel(false);
    }

    public static Timeout limit(final long time, final TimeUnit unit) {
        final Thread thread = Thread.currentThread();
        return new Timeout(Timer.get().schedule(new Runnable() {
            @Override public void run() {
                if (LOGGER.isLoggable(Level.FINE)) {
                    Throwable t = new Throwable();
                    t.setStackTrace(thread.getStackTrace());
                    LOGGER.log(Level.FINE, "Interrupting " + thread + " after " + time + " " + unit, t);
                }
                thread.interrupt();
            }
        }, time, unit));
    }

}
