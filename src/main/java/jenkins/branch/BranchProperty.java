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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Job;
import hudson.model.Run;

import java.util.ArrayList;
import java.util.List;

/**
 * Additional information associated with {@link Branch}.
 * <p/>
 * {@link jenkins.scm.api.SCMSource}s can use properties to convey additional implementation/SCM specific
 * information that's not captured in the base {@link Branch} class.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class BranchProperty extends AbstractDescribableImpl<BranchProperty> implements ExtensionPoint {

    /**
     * Returns a {@link ProjectDecorator} for the supplied project instance.
     * @param project the project instance.
     * @param <P> the type of project.
     * @param <B> the type of build of the project.
     * @return a {@link ProjectDecorator} or {@code null} if none appropriate to this type of project.
     */
    @CheckForNull
    @SuppressWarnings("unchecked")
    public final <P extends Job<P,B>,B extends Run<P,B>> ProjectDecorator<P,B> decorator(P project) {
        return (ProjectDecorator<P, B>) decorator(project.getClass());
    }

    /**
     * Returns a {@link ProjectDecorator} for the specific project type.
     * @param clazz the project class.
     * @param <P> the type of project.
     * @param <B> the type of build of the project.
     * @return a {@link ProjectDecorator} or {@code null} if none appropriate to this type of project.
     */
    @CheckForNull
    public <P extends Job<P,B>,B extends Run<P,B>> ProjectDecorator<P,B> decorator(Class<P> clazz) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public BranchPropertyDescriptor getDescriptor() {
        return (BranchPropertyDescriptor) super.getDescriptor();
    }

    /**
     * Utility helper method that ensures you have an {@link ArrayList} but avoids copying unless required.
     * @param list the list that may or may not be an {@link ArrayList}.
     * @param <T> the type of elements in the list.
     * @return either the supplied {@link List} if it was an {@link ArrayList} or a copy of the supplied list in a
     * newly created {@link ArrayList}.
     */
    protected static <T> ArrayList<T> asArrayList(List<T> list) {
        return list instanceof ArrayList ? (ArrayList<T>)list : new ArrayList<T>(list);
    }
}
