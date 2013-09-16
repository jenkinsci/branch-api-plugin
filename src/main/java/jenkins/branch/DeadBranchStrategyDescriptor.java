/*
 * The MIT License
 *
 * Copyright (c) 2011-2013, CloudBees, Inc., Stephen Connolly.
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

import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.List;

/**
 * The base class {@link Descriptor} for {@link DeadBranchStrategy} implementations.
 */
public abstract class DeadBranchStrategyDescriptor extends Descriptor<DeadBranchStrategy> {

    /**
     * Returns true if this build source is applicable to the given project.
     *
     * @param jobType the type of project.
     * @return true to allow user to select and configure this build source.
     */
    public boolean isApplicable(java.lang.Class<? extends MultiBranchProject> jobType) {
        return true;
    }

    /**
     * Returns the {@link DeadBranchStrategyDescriptor}s that are applicable to the specific {@link MultiBranchProject}
     *
     * @param jobType the type of {@link MultiBranchProject}
     * @return the list of {@link DeadBranchStrategyDescriptor}
     */
    @SuppressWarnings("unused") // used by stapler
    public static List<DeadBranchStrategyDescriptor> forProject(Class<? extends MultiBranchProject> jobType) {
        List<DeadBranchStrategyDescriptor> result = new ArrayList<DeadBranchStrategyDescriptor>();
        for (Descriptor<DeadBranchStrategy> d : Jenkins.getInstance().getDescriptorList(DeadBranchStrategy.class)) {
            if (d instanceof DeadBranchStrategyDescriptor) {
                DeadBranchStrategyDescriptor descriptor = (DeadBranchStrategyDescriptor) d;
                if (descriptor.isApplicable(jobType)) {
                    result.add(descriptor);
                }
            }
        }
        return result;
    }

}
