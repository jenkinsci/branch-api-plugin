/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
 * Copyright 2017 Rocket Science Group
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
import hudson.ExtensionPoint;
import hudson.Extension;
import hudson.ExtensionList;
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
import jenkins.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Suppresses builds due to either {@link BranchIndexingCause} or {@link BranchEventCause}.
 * The purpose of this property is to prevent triggering builds resulting from the <em>detection</em>
 * of changes in the underlying SCM.
 */
public abstract class OverrideTriggerProperty<J extends Job<?,?>> extends JobProperty<Job<?,?>> {

    public abstract Boolean shouldScheduleProperty( Action a );

    public static Boolean shouldSchedule(Job<?,?> j, Action a){
        Boolean propertyShouldSchedule = null;
        ArrayList<OverrideTriggerProperty> props = OverrideTriggerProperty.all(j);

        for (OverrideTriggerProperty potentialProp : props) {
                Boolean potentialShouldSchedule = potentialProp.shouldScheduleProperty(a);
                if( potentialShouldSchedule != null ){
                    if(propertyShouldSchedule == null){
                        propertyShouldSchedule = true;
                    }
                    propertyShouldSchedule = propertyShouldSchedule && potentialShouldSchedule;
                }
        }

        return propertyShouldSchedule;
    }

    public static ArrayList<OverrideTriggerProperty> all(Job<?,?> j) {
        ArrayList<OverrideTriggerProperty> list = new ArrayList<OverrideTriggerProperty>();
        for ( JobProperty param : j.getAllProperties() ){   
            if(param instanceof OverrideTriggerProperty){
                list.add((OverrideTriggerProperty) param);
            }
        }

        return list;
    }

    @Override
    public JobPropertyDescriptor getDescriptor() {
        return (JobPropertyDescriptor) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    @Extension
    public static class Dispatcher extends Queue.QueueDecisionHandler {

        @SuppressWarnings({"unchecked", "rawtypes"}) // untypable
        @Override
        public boolean shouldSchedule(Queue.Task p, List<Action> actions) {
            for (Action action : actions) {
                if (p instanceof Job) {
                    Job<?,?> j = (Job) p;
                    Boolean isOverrided = OverrideTriggerProperty.shouldSchedule(j, action);
                    if( isOverrided != null) {
                        return isOverrided;
                    }
                }
            }
            return true;
        }
    }

}
