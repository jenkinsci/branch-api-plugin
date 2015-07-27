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
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;

import java.util.ArrayList;
import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * {@link Descriptor} for {@link BranchProperty}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class BranchPropertyDescriptor extends Descriptor<BranchProperty> {

    /**
     * A branch property may not be appropriate for every project, this method lets a property
     * opt out of being selectable for a specific project.
     *
     * @param project the project.
     * @return {@code} true iff this property is relevant with this project instance.
     */
    @SuppressWarnings("unused") // by stapler
    public boolean isApplicable(@NonNull MultiBranchProject project) {
        return isApplicable(project.getDescriptor());
    }

    /**
     * Usually a branch property is more concerned with the specific type of project than the specifics of
     * the project instance.
     *
     * @param projectDescriptor the project type.
     * @return {@code} true iff this property is relevant with this project type.
     */
    protected boolean isApplicable(@NonNull MultiBranchProjectDescriptor projectDescriptor) {
        return true;
    }

    /**
     * All the registered {@link BranchPropertyDescriptor}s.
     * Probably unused.
     */
    public static List<BranchPropertyDescriptor> all() {
        return ExtensionList.lookup(BranchPropertyDescriptor.class);
    }

    /**
     * Gets all the {@link BranchPropertyDescriptor} instances applicable to the specified project.
     * Probably unused.
     * @param project the project
     * @return all the {@link BranchPropertyDescriptor} instances  applicable to the specified project.
     */
    public static List<BranchPropertyDescriptor> all(@NonNull MultiBranchProject project) {
        List<BranchPropertyDescriptor> result = new ArrayList<BranchPropertyDescriptor>();
        for (BranchPropertyDescriptor d : all()) {
            if (d.isApplicable(project)) {
                result.add(d);
            }
        }
        return result;
    }

    /**
     * Ensures that the configuration screen of (for example) {@link DefaultBranchPropertyStrategy} shows only appropriate descriptors.
     * TODO this trick does not work for {@link NamedExceptionsBranchPropertyStrategy} on initial configuration (i.e., when {@link DefaultBranchPropertyStrategy} was initially selected);
     * seems {@code filterDescriptors} is not called when {@code instance == null} perhaps?
     * Or perhaps {@code it == null} when {@code l:renderOnDemand} is in use (supposed to work due to {@code capture} attribute, but…)?
     */
    @Restricted(DoNotUse.class)
    @Extension
    public static final class Visibility extends DescriptorVisibilityFilter {

        @SuppressWarnings("rawtypes")
        @Override
        public boolean filter(Object context, Descriptor descriptor) {
            if (context instanceof MultiBranchProject && descriptor instanceof BranchPropertyDescriptor) {
                return ((BranchPropertyDescriptor) descriptor).isApplicable((MultiBranchProject) context);
            } else {
                return true;
            }
        }

    }

}
