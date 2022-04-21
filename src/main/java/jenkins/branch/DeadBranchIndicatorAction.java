/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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

import hudson.Extension;
import hudson.model.Action;
import hudson.model.BallColor;
import hudson.model.InvisibleAction;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.TransientActionFactory;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.Collections;

/**
 * An action that puts some css on job and run pages for jobs representing {@link Branch.Dead}.
 */
@SuppressWarnings("rawtypes")
public class DeadBranchIndicatorAction extends InvisibleAction {

    public String getDisabledColor() {
        return BallColor.DISABLED.getHtmlBaseColor();
    }

    @Extension
    public static class JobFactoryImpl extends TransientActionFactory<Job> {

        @Override
        public Class<Job> type() {
            return Job.class;
        }

        @NonNull
        @Override
        public Collection<? extends Action> createFor(@NonNull Job job) {
            if (job.getParent() instanceof MultiBranchProject) {
                MultiBranchProject p = (MultiBranchProject) job.getParent();
                BranchProjectFactory factory = p.getProjectFactory();
                if (factory.isProject(job)) {
                    Branch b = factory.getBranch(factory.asProject(job));
                    if (b instanceof Branch.Dead) {
                        return Collections.singleton(new DeadBranchIndicatorAction());
                    }
                }
            }
            return Collections.emptyList();
        }
    }

    @Extension
    public static class RunFactoryImpl extends TransientActionFactory<Run> {

        @Override
        public Class<Run> type() {
            return Run.class;
        }

        @NonNull
        @Override
        public Collection<? extends Action> createFor(@NonNull Run target) {
            final Job job = target.getParent();
            if (job.getParent() instanceof MultiBranchProject) {
                MultiBranchProject p = (MultiBranchProject) job.getParent();
                BranchProjectFactory factory = p.getProjectFactory();
                if (factory.isProject(job)) {
                    Branch b = factory.getBranch(factory.asProject(job));
                    if (b instanceof Branch.Dead) {
                        return Collections.singleton(new DeadBranchIndicatorAction());
                    }
                }
            }
            return Collections.emptyList();
        }
    }
}
