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
import hudson.model.Job;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import org.jvnet.tiger_types.Types;

/**
 * Base class for all {@link BranchProjectFactory} instances.
 */
public abstract class BranchProjectFactoryDescriptor extends Descriptor<BranchProjectFactory<?, ?>> {

    /**
     * The base class of the projects that are produced by this factory.
     *
     * @since 2.0
     */
    @NonNull
    private final Class<? extends Job> branchProjectClass;

    /**
     * Explicit constructor to use when type inference fails.
     *
     * @param clazz              the {@link BranchProjectFactory} that this descriptor is for.
     * @param branchProjectClass the {@link Job} type that the {@link BranchProjectFactory} creates.
     * @since 2.0
     */
    protected BranchProjectFactoryDescriptor(Class<? extends BranchProjectFactory<?, ?>> clazz,
                                             Class<? extends Job> branchProjectClass) {
        super(clazz);
        this.branchProjectClass = branchProjectClass;
    }

    /**
     * Semi explicit constructor to use when the descriptor is not an inner class of the {@link BranchProjectFactory}.
     *
     * @param clazz the {@link BranchProjectFactory} that this descriptor is for.
     * @since 2.0
     */
    protected BranchProjectFactoryDescriptor(Class<? extends BranchProjectFactory<?, ?>> clazz) {
        super(clazz);
        Type bt = Types.getBaseClass(clazz, BranchProjectFactory.class);
        if (bt instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) bt;
            // this 'p' is the closest approximation of P of BranchProjectFactory<P,R>.
            Class p = Types.erasure(pt.getActualTypeArguments()[0]);
            if (!Job.class.isAssignableFrom(p)) {
                throw new AssertionError(
                        "Cannot infer job type produced by " + clazz + " perhaps use the explicit constructor");
            }
            branchProjectClass = p;
        } else {
            throw new AssertionError(
                    "Cannot infer job type produced by " + clazz + " perhaps use the explicit constructor");
        }
    }

    /**
     * Fully inferring constructor to use when the descriptor is an inner class of the {@link BranchProjectFactory}
     * and type parameter inference works because it just should work.
     *
     * @since 2.0
     */
    protected BranchProjectFactoryDescriptor() {
        super();
        Type bt = Types.getBaseClass(clazz, BranchProjectFactory.class);
        if (bt instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) bt;
            // this 'p' is the closest approximation of P of BranchProjectFactory<P,R>.
            Class p = Types.erasure(pt.getActualTypeArguments()[0]);
            if (!Job.class.isAssignableFrom(p)) {
                throw new AssertionError(
                        "Cannot infer job type produced by " + clazz + " perhaps use the explicit constructor");
            }
            branchProjectClass = p;
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
        return branchProjectClass;
    }

    /**
     * Returns {@code true} if and only if this {@link BranchPropertyDescriptor} is applicable in the specified type
     * of {@link MultiBranchProject}.
     *
     * @param clazz the type of {@link MultiBranchProject}.
     * @return {@code true} if this factory can be used in the {@link MultiBranchProject}.
     */
    public abstract boolean isApplicable(Class<? extends MultiBranchProject> clazz);
}
