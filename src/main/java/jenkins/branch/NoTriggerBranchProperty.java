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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import java.util.List;
import org.jenkinsci.Symbol;
import org.jvnet.localizer.Localizable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Suppresses builds due to either {@link BranchIndexingCause} or {@link BranchEventCause}.
 * The purpose of this property is to prevent triggering builds resulting from the <em>detection</em>
 * of changes in the underlying SCM.
 */
@Restricted(NoExternalUse.class)
public class NoTriggerBranchProperty extends BranchProperty {

    private SuppressionStrategy strategy;

    @DataBoundConstructor
    public NoTriggerBranchProperty() {}

    /**
     * Gets the strategy which determines which builds should be suppressed.
     *
     * @return the suppression strategy.
     * @since 2.7.1
     */
    @NonNull
    public SuppressionStrategy getStrategy() {
        if (strategy == null) {
            return SuppressionStrategy.ALL;
        }
        return strategy;
    }

    @DataBoundSetter
    public void setStrategy(SuppressionStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public <P extends Job<P, B>, B extends Run<P, B>> JobDecorator<P, B> jobDecorator(Class<P> clazz) {
        return null;
    }

    @Symbol("suppressAutomaticTriggering")
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
                        boolean indexingCause = c instanceof BranchIndexingCause;
                        boolean eventCause = c instanceof BranchEventCause;
                        if (indexingCause || eventCause) {
                            if (p instanceof Job) {
                                Job<?,?> j = (Job) p;
                                if (j.getParent() instanceof MultiBranchProject) {

                                    OverrideIndexTriggersJobProperty overrideProp = j.getProperty(OverrideIndexTriggersJobProperty.class);
                                    if (overrideProp != null) {
                                        return overrideProp.getEnableTriggers();
                                    } else {
                                        for (BranchProperty prop : ((MultiBranchProject) j.getParent()).getProjectFactory().getBranch(j).getProperties()) {
                                            if (prop instanceof NoTriggerBranchProperty) {
                                                NoTriggerBranchProperty triggerProperty = (NoTriggerBranchProperty) prop;
                                                boolean suppressIndexing = SuppressionStrategy.EVENTS != triggerProperty.getStrategy();
                                                boolean suppressEvents = SuppressionStrategy.INDEXING != triggerProperty.getStrategy();
                                                if ((indexingCause && suppressIndexing) || (eventCause && suppressEvents)) {
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

    /**
     * Strategy which determines which builds should be suppressed.
     * @since 2.7.1
     */
    public enum SuppressionStrategy {
        /**
         * All builds triggered by SCM are suppressed.
         *
         * @since 2.7.1
         */
        ALL(Messages._NoTriggerBranchProperty_strategy_all()),
        /**
         * Only builds triggered by {@link BranchIndexingCause} are suppressed.
         *
         * @since 2.7.1
         */
        INDEXING(Messages._NoTriggerBranchProperty_strategy_indexing()),
        /**
         * Only builds triggered by {@link BranchEventCause} are suppressed.
         *
         * @since 2.7.1
         */
        EVENTS(Messages._NoTriggerBranchProperty_strategy_events());

        private final Localizable displayName;

        SuppressionStrategy(Localizable displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName.toString();
        }
    }
}
