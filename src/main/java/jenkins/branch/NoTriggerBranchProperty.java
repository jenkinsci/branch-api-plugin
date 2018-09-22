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

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Suppresses builds due to either {@link BranchIndexingCause} or {@link BranchEventCause}.
 * The purpose of this property is to prevent triggering builds resulting from the <em>detection</em>
 * of changes in the underlying SCM.
 */
@Restricted(NoExternalUse.class)
public class NoTriggerBranchProperty extends BranchProperty {
    private final Boolean disableBranchIndexingCause;
    private final Boolean disableBranchEventCause;

    public Boolean getDisableBranchIndexingCause() {
        return disableBranchIndexingCause;
    }

    public Boolean getDisableBranchEventCause() {
        return disableBranchEventCause;
    }

    @DataBoundConstructor
    public NoTriggerBranchProperty(Boolean disableBranchIndexingCause, Boolean disableBranchEventCause) {
        this.disableBranchEventCause = disableBranchEventCause;
        this.disableBranchIndexingCause = disableBranchIndexingCause;
    }

    @Override
    public <P extends Job<P, B>, B extends Run<P, B>> JobDecorator<P, B> jobDecorator(Class<P> clazz) {
        return null;
    }


    @Extension
    public static class DescriptorImpl extends BranchPropertyDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.NoTriggerBranchProperty_suppress_automatic_scm_triggering();
        }

    }

    @Extension
    public static class Dispatcher extends Queue.QueueDecisionHandler {

        @SuppressWarnings({"unchecked", "rawtypes"}) // untypable
        @Override
        public boolean shouldSchedule(Queue.Task p, List<Action> actions) {
            for (Action action :  actions) {
                if (action instanceof CauseAction) {
                    for (Cause c : ((CauseAction) action).getCauses()) {
                        if (c instanceof BranchIndexingCause || c instanceof BranchEventCause) {
                            if (p instanceof Job) {
                                Job<?,?> j = (Job) p;
                                if (j.getParent() instanceof MultiBranchProject) {

                                    OverrideIndexTriggersJobProperty overrideProp = j.getProperty(OverrideIndexTriggersJobProperty.class);
                                    if (overrideProp != null) {
                                        return overrideProp.getEnableTriggers();
                                    } else {
                                        for (BranchProperty prop : ((MultiBranchProject) j.getParent()).getProjectFactory().getBranch(j).getProperties()) {
                                            if (prop instanceof NoTriggerBranchProperty) {
                                                if((c instanceof BranchIndexingCause && ((NoTriggerBranchProperty) prop).getDisableBranchIndexingCause()) ||
                                                        (c instanceof  BranchEventCause && ((NoTriggerBranchProperty) prop).getDisableBranchEventCause())) {
                                                    return false;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return true;
        }

    }

}
