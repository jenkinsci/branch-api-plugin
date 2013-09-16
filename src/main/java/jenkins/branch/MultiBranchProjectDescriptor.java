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
import hudson.model.Descriptor;
import hudson.model.TopLevelItemDescriptor;
import hudson.scm.SCMDescriptor;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link Descriptor} for {@link MultiBranchProject}s.
 *
 * @author Stephen Connolly
 */
public abstract class MultiBranchProjectDescriptor extends TopLevelItemDescriptor {

    /**
     * We have to extend {@link TopLevelItemDescriptor} but we want to be able to access {@link #clazz} as a
     * {@link MultiBranchProject} based type.
     *
     * @return the {@link #clazz}
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public Class<? extends MultiBranchProject> getClazz() {
        return (Class<? extends MultiBranchProject>) clazz;
    }

    /**
     * Gets the {@link SCMSourceDescriptor}s.
     *
     * @param onlyUserInstantiable {@code true} retains only those {@link jenkins.scm.api.SCMSource} types that
     *                             are instantiable by the user.
     * @return the list of {@link SCMSourceDescriptor}s.
     */
    @SuppressWarnings("unused") // used by stapler
    @NonNull
    public List<SCMSourceDescriptor> getSCMSourceDescriptors(boolean onlyUserInstantiable) {
        return SCMSourceDescriptor.forOwner(getClazz(), onlyUserInstantiable);
    }

    /**
     * Gets the {@link DeadBranchStrategyDescriptor}s.
     *
     * @return the {@link DeadBranchStrategyDescriptor}s.
     */
    @SuppressWarnings("unused") // used by stapler
    @NonNull
    public List<DeadBranchStrategyDescriptor> getDeadBranchStrategyDescriptors() {
        return DeadBranchStrategyDescriptor.forProject(getClazz());
    }

    /**
     * Gets the {@link SCMDescriptor}s, primarily used by {@link jenkins.scm.impl.SingleSCMSource}.
     *
     * @return the {@link SCMDescriptor}s.
     */
    @SuppressWarnings("unused") // used by stapler
    @NonNull
    public abstract List<SCMDescriptor<?>> getSCMDescriptors();

    /**
     * Returns the {@link BranchProjectFactoryDescriptor}s.
     *
     * @return the {@link BranchProjectFactoryDescriptor}s.
     */
    @SuppressWarnings("unused") // used by stapler
    @NonNull
    public List<BranchProjectFactoryDescriptor> getProjectFactoryDescriptors() {
        List<BranchProjectFactoryDescriptor> result = new ArrayList<BranchProjectFactoryDescriptor>();
        List<BranchProjectFactoryDescriptor> descriptorList =
                Jenkins.getInstance().getDescriptorList(BranchProjectFactory.class);
        for (BranchProjectFactoryDescriptor descriptor : descriptorList) {
            if (descriptor.isApplicable((Class<? extends MultiBranchProject>) clazz)) {
                result.add(descriptor);
            }
        }
        return descriptorList;
    }

    /**
     * Returns the {@link BranchSource.DescriptorImpl}.
     *
     * @return the {@link BranchSource.DescriptorImpl}.
     */
    @SuppressWarnings({"unused", "unchecked"}) // used by stapler
    @NonNull
    public Descriptor<BranchSource> getBranchSourceDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(BranchSource.class);
    }
}
