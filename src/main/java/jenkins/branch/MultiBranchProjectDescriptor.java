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

import com.cloudbees.hudson.plugins.folder.AbstractFolderDescriptor;
import com.cloudbees.hudson.plugins.folder.FolderIconDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.model.TopLevelItemDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceDescriptor;

/**
 * <p>The {@link Descriptor} for {@link MultiBranchProject}s.</p>
 *
 * <p>Compatible {@link hudson.scm.SCM}s displayed by {@link jenkins.scm.impl.SingleSCMSource} (via their
 * {@link hudson.scm.SCMDescriptor}) can be defined by overriding {@link #isApplicable(Descriptor)}:</p>
 * <pre>
 * &#64;Override
 * public boolean isApplicable(Descriptor descriptor) {
 *     if (descriptor instanceof SCMDescriptor) {
 *         SCMDescriptor d = (SCMDescriptor) descriptor;
 *         // Your logic
 *     }
 *     return super.isApplicable(descriptor);
 * }
 * </pre>
 *
 * @author Stephen Connolly
 */
public abstract class MultiBranchProjectDescriptor extends AbstractFolderDescriptor {

    /**
     * We have to extend {@link TopLevelItemDescriptor} but we want to be able to access {@link #clazz} as a
     * {@link MultiBranchProject} based type.
     *
     * @return the {@link #clazz}
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public Class<? extends MultiBranchProject> getClazz() {
        return clazz.asSubclass(MultiBranchProject.class);
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
     * Returns the {@link BranchProjectFactoryDescriptor}s.
     *
     * @return the {@link BranchProjectFactoryDescriptor}s.
     */
    @SuppressWarnings("unused") // used by stapler
    @NonNull
    public List<BranchProjectFactoryDescriptor> getProjectFactoryDescriptors() {
        List<BranchProjectFactoryDescriptor> result = new ArrayList<BranchProjectFactoryDescriptor>();
        for (BranchProjectFactoryDescriptor descriptor : ExtensionList.lookup(BranchProjectFactoryDescriptor.class)) {
            if (descriptor.isApplicable(getClazz())) {
                result.add(descriptor);
            }
        }
        return result;
    }

    /**
     * Returns the {@link BranchSource.DescriptorImpl}.
     *
     * @return the {@link BranchSource.DescriptorImpl}.
     */
    @SuppressWarnings({"unused", "unchecked"}) // used by stapler
    @NonNull
    public Descriptor<BranchSource> getBranchSourceDescriptor() {
        return Jenkins.getActiveInstance().getDescriptorOrDie(BranchSource.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<FolderIconDescriptor> getIconDescriptors() {
        return Collections.<FolderIconDescriptor>singletonList(
                Jenkins.getActiveInstance().getDescriptorByType(MetadataActionFolderIcon.DescriptorImpl.class)
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isIconConfigurable() {
        return false;
    }
}
