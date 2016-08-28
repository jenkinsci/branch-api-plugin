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
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Queue;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.util.List;

/**
 * Allows overriding indexing triggers for an individual job - either by enabling when the multibranch or org is set to
 * suppress them, or disabling if they're otherwise enabled.
 */
public class OverrideIndexTriggersJobProperty extends JobProperty<Job<?,?>> {
    private final boolean enableTriggers;

    @DataBoundConstructor
    public OverrideIndexTriggersJobProperty(boolean enableTriggers) {
        this.enableTriggers = enableTriggers;
    }

    public boolean getEnableTriggers() {
        return enableTriggers;
    }

    @Extension
    @Symbol("overrideIndexTriggers")
    public static class DescriptorImpl extends JobPropertyDescriptor {

        public boolean isOwnerMultibranch(Item item) {
            return item instanceof MultiBranchProject || item instanceof OrganizationFolder || item.getParent() instanceof MultiBranchProject;
        }

        @Override public String getDisplayName() {
            return "Override multibranch or organization branch indexing triggers.";
        }

        @Override
        public JobProperty<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return formData.optBoolean("specified") ? super.newInstance(req, formData) : null;
        }
    }

    @Extension
    public static class Dispatcher extends Queue.QueueDecisionHandler {

        @SuppressWarnings({"unchecked", "rawtypes"}) // untypable
        @Override
        public boolean shouldSchedule(Queue.Task p, List<Action> actions) {
            for (Action action : actions) {
                if (action instanceof CauseAction) {
                    for (Cause c : ((CauseAction) action).getCauses()) {
                        if (c instanceof BranchIndexingCause) {
                            if (p instanceof Job) {
                                Job<?,?> j = (Job) p;
                                OverrideIndexTriggersJobProperty overrideProp = j.getProperty(OverrideIndexTriggersJobProperty.class);
                                if (overrideProp != null) {
                                    return overrideProp.getEnableTriggers();
                                } else {
                                    return true;
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