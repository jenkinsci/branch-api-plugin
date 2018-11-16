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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import jenkins.scm.api.SCMSourceDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * The base class for {@link Descriptor}s or {@link BranchPropertyStrategy} instances.
 *
 * @author Stephen Connolly
 */
public abstract class BranchPropertyStrategyDescriptor extends Descriptor<BranchPropertyStrategy> {

    /**
     * A branch property strategy may not be appropriate for every type of source, this method lets a strategy
     * opt out of being selectable for a specific source type. When this method is called (via stapler) we do not have
     * an instance of the source so this needs to be hooked
     *
     * @param sourceDescriptor the source descriptor.
     * @return {@code} true iff this property strategy is relevant with this source.
     */
    public boolean isApplicable(@NonNull SCMSourceDescriptor sourceDescriptor) {
        return true;
    }

    /**
     * A branch property strategy may not be appropriate for every project, this method lets a strategy
     * opt out of being selectable for a specific project.
     * <p>By default it checks {@link #isApplicable(MultiBranchProjectDescriptor)},
     * and whether {@link BranchPropertyDescriptor#all(MultiBranchProject)} is nonempty
     * when filtered by {@link DescriptorVisibilityFilter} on the project,
     * which due to {@link jenkins.branch.BranchPropertyDescriptor.Visibility}
     * also calls {@link BranchPropertyDescriptor#isApplicable(MultiBranchProject)}.
     * @param project the project.
     * @return {@code} true iff this property strategy is relevant with this project instance.
     */
    public boolean isApplicable(@NonNull MultiBranchProject project) {
        return isApplicable(project.getDescriptor()) &&
            !DescriptorVisibilityFilter.apply(project, BranchPropertyDescriptor.all()).isEmpty();
    }

    /**
     * Usually a branch property strategy is more concerned with the specific type of project than the specifics of
     * the project instance.
     *
     * @param projectDescriptor the project type.
     * @return {@code} true iff this property strategy is relevant with this project type.
     */
    protected boolean isApplicable(@NonNull MultiBranchProjectDescriptor projectDescriptor) {
        return true;
    }

    /**
     * Gets all the {@link BranchPropertyStrategyDescriptor} instances.
     *
     * @return all the {@link BranchPropertyStrategyDescriptor} instances.
     */
    public static List<BranchPropertyStrategyDescriptor> all() {
        return ExtensionList.lookup(BranchPropertyStrategyDescriptor.class);
    }

    /**
     * Gets all the {@link BranchPropertyStrategyDescriptor} instances applicable to the specified project and source.
     *
     * @param project          the project
     * @param sourceDescriptor the source.
     * @return all the {@link BranchPropertyStrategyDescriptor} instances  applicable to the specified project and
     *         source.
     */
    public static List<BranchPropertyStrategyDescriptor> all(
            @NonNull MultiBranchProject project, @NonNull SCMSourceDescriptor sourceDescriptor) {
        List<BranchPropertyStrategyDescriptor> result = new ArrayList<>();
        for (BranchPropertyStrategyDescriptor d : all()) {
            if (d.isApplicable(project) && d.isApplicable(sourceDescriptor)) {
                result.add(d);
            }
        }
        return result;
    }

}
