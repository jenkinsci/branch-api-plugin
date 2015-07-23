/*
 * The MIT License
 *
 * Copyright (c) 2014, CloudBees, Inc.
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
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import java.util.Iterator;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.export.Exported;

/**
 * Support for build parameters.
 * Left abstract (not registered by default for all projects) so that concrete subclasses can decide which project types they should apply to.
 */
public abstract class ParameterDefinitionBranchProperty extends BranchProperty {

    /** Subclasses should have a {@link DataBoundConstructor}. */
    protected ParameterDefinitionBranchProperty() {}

    private List<ParameterDefinition> parameterDefinitions;

    @Exported
    public final List<ParameterDefinition> getParameterDefinitions() {
        return parameterDefinitions;
    }

    @DataBoundSetter public final void setParameterDefinitions(List<ParameterDefinition> parameterDefinitions) {
        this.parameterDefinitions = parameterDefinitions;
    }

    /** Not to be confused with {@link BranchPropertyDescriptor#isApplicable(MultiBranchProjectDescriptor)}, this checks applicability for the child job type. */
    protected <P extends Job<P, B>, B extends Run<P, B>> boolean isApplicable(Class<P> clazz) {
        return true;
    }

    @Override public final <P extends Job<P, B>, B extends Run<P, B>> JobDecorator<P, B> jobDecorator(Class<P> clazz) {
        if (!isApplicable(clazz)) {
            return null;
        }
        return new JobDecorator<P, B>() {
            @NonNull
            @Override
            public List<JobProperty<? super P>> jobProperties(
                    @NonNull List<JobProperty<? super P>> jobProperties) {
                List<JobProperty<? super P>> result = asArrayList(jobProperties);
                for (Iterator<JobProperty<? super P>> iterator = result.iterator();
                     iterator.hasNext(); ) {
                    JobProperty<? super P> p = iterator.next();
                    if (p instanceof ParametersDefinitionProperty) {
                        iterator.remove();
                    }
                }
                if (parameterDefinitions != null && !parameterDefinitions.isEmpty()) {
                    result.add(new ParametersDefinitionProperty(parameterDefinitions));
                }
                return result;
            }
        };
    }

}
