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

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderDescriptor;
import com.cloudbees.hudson.plugins.folder.ChildNameGenerator;
import com.cloudbees.hudson.plugins.folder.FolderIconDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceDescriptor;
import org.jvnet.tiger_types.Types;

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
     * The base class of the projects that are produced by this factory.
     *
     * @since 2.0
     */
    @NonNull
    private final Class<? extends Job> projectClass;

    /**
     * Explicit constructor to use when type inference fails.
     *
     * @param clazz        the {@link MultiBranchProject} that this descriptor is for.
     * @param projectClass the {@link Job} type that the {@link MultiBranchProject} creates.
     * @since 2.0
     */
    protected MultiBranchProjectDescriptor(Class<? extends MultiBranchProject<?, ?>> clazz,
                                           Class<? extends Job> projectClass) {
        super(clazz);
        this.projectClass = projectClass;
    }

    /**
     * Semi explicit constructor to use when the descriptor is not an inner class of the {@link MultiBranchProject}.
     *
     * @param clazz the {@link MultiBranchProject} that this descriptor is for.
     * @since 2.0
     */
    protected MultiBranchProjectDescriptor(Class<? extends MultiBranchProject<?, ?>> clazz) {
        super(clazz);
        projectClass = inferProjectClass(clazz);
    }

    /**
     * Fully inferring constructor to use when the descriptor is an inner class of the {@link MultiBranchProject}
     * and type parameter inference works because it just should work.
     *
     * @since 2.0
     */
    protected MultiBranchProjectDescriptor() {
        super();
        projectClass = inferProjectClass((Class) clazz);
    }

    /**
     * Infers the base class of projects that the specified {@link MultiBranchProject} creates.
     *
     * @param clazz the specified {@link MultiBranchProject}.
     * @return the base class of project that the specified {@link MultiBranchProject} creates.
     */
    private static Class<? extends Job> inferProjectClass(Class<? extends MultiBranchProject> clazz) {
        Type bt = Types.getBaseClass(clazz, MultiBranchProject.class);
        if (bt instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) bt;
            // this 'p' is the closest approximation of P of MultiBranchProject<P,R>.
            Class p = Types.erasure(pt.getActualTypeArguments()[0]);
            if (!Job.class.isAssignableFrom(p)) {
                throw new AssertionError(
                        "Cannot infer job type produced by " + clazz + " perhaps use the explicit constructor");
            }
            return p;
        } else {
            throw new AssertionError(
                    "Cannot infer job type produced by " + clazz + " perhaps use the explicit constructor");
        }
    }

    /**
     * Returns the base class of the projects that are produced by this factory.
     *
     * @return the base class of the projects that are produced by this factory.
     * @since 2.0
     */
    @NonNull
    public Class<? extends Job> getProjectClass() {
        return projectClass;
    }

    /**
     * We have to extend {@link TopLevelItemDescriptor} but we want to be able to access {@link #clazz} as a
     * {@link MultiBranchProject} based type.
     *
     * @return the {@link #clazz}
     */
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
        List<BranchProjectFactoryDescriptor> result = new ArrayList<>();
        for (BranchProjectFactoryDescriptor descriptor : ExtensionList.lookup(BranchProjectFactoryDescriptor.class)) {
            if (descriptor.isApplicable(getClazz()) && descriptor.getProjectClass().isAssignableFrom(getProjectClass())) {
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
        return Jenkins.get().getDescriptorOrDie(BranchSource.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<FolderIconDescriptor> getIconDescriptors() {
        return Collections.singletonList(
                Jenkins.get().getDescriptorByType(MetadataActionFolderIcon.DescriptorImpl.class)
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isIconConfigurable() {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    @NonNull
    public <I extends TopLevelItem> ChildNameGenerator<AbstractFolder<I>,I> childNameGenerator() {
        return (ChildNameGenerator<AbstractFolder<I>, I>)ChildNameGeneratorImpl.INSTANCE;
    }

    public static class ChildNameGeneratorImpl<P extends Job<P, R> & TopLevelItem,
            R extends Run<P, R>> extends ChildNameGenerator<MultiBranchProject<P,R>,P> {

        /*package*/ static final ChildNameGeneratorImpl INSTANCE = new ChildNameGeneratorImpl();

        @Override
        @CheckForNull
        public String itemNameFromItem(@NonNull MultiBranchProject<P,R> parent, @NonNull P item) {
            BranchProjectFactory<P, R> factory = parent.getProjectFactory();
            if (factory.isProject(item)) {
                return NameEncoder.encode(factory.getBranch(item).getName());
            }
            String idealName = idealNameFromItem(parent, item);
            if (idealName != null) {
                return NameEncoder.encode(idealName);
            }
            return null;
        }

        @Override
        @CheckForNull
        public String dirNameFromItem(@NonNull MultiBranchProject<P,R> parent, @NonNull P item) {
            BranchProjectFactory<P, R> factory = parent.getProjectFactory();
            if (factory.isProject(item)) {
                return NameMangler.apply(factory.getBranch(item).getName());
            }
            String idealName = idealNameFromItem(parent, item);
            if (idealName != null) {
                return NameMangler.apply(idealName);
            }
            return null;
        }

        @Override
        @NonNull
        public String itemNameFromLegacy(@NonNull MultiBranchProject<P, R> parent, @NonNull String legacyDirName) {
            return NameEncoder.decode(legacyDirName);
        }

        @Override
        @NonNull
        public String dirNameFromLegacy(@NonNull MultiBranchProject<P, R> parent, @NonNull String legacyDirName) {
            return NameMangler.apply(NameEncoder.decode(legacyDirName));
        }

        // TODO remove after it is removed in cloudbees-folder
        public void recordLegacyName(MultiBranchProject<P, R> parent, P item, String legacyDirName) throws IOException {
            // no-op because we already tracked the name in Branch.getName()
        }

    }

}
