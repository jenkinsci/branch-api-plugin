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

import hudson.DescriptorExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import java.util.Collections;
import java.util.Comparator;

/**
 * A {@link java.util.Comparator} that compares {@link hudson.model.Describable} instances of a specific type based
 * on the order of their
 * {@link hudson.model.Descriptor}s in {@link jenkins.model.Jenkins}'s list of {@link hudson.model.Descriptor}s for
 * that type.
 *
 * @param <T> the type of {@link hudson.model.Describable}.
 */
public class DescriptorOrder<T extends Describable<T>> implements Comparator<T> {
    /**
     * The list of {@link hudson.model.Descriptor}s to sort with.
     */
    private final DescriptorExtensionList<T, Descriptor<T>> descriptors;

    /**
     * Returns a {@link java.util.Comparator} that matches the order of the corresponding
     * {@link jenkins.model.Jenkins#getDescriptorList(Class)}.
     *
     * @param type the type of {@link hudson.model.Describable}.
     * @param <T>  the type of {@link hudson.model.Describable}.
     * @return a {@link java.util.Comparator}.
     */
    public static <T extends Describable<T>> Comparator<T> forward(Class<T> type) {
        return new DescriptorOrder<>(type);
    }

    /**
     * Returns a {@link java.util.Comparator} that reverses the order of the corresponding
     * {@link jenkins.model.Jenkins#getDescriptorList(Class)}.
     *
     * @param type the type of {@link hudson.model.Describable}.
     * @param <T>  the type of {@link hudson.model.Describable}.
     * @return a {@link java.util.Comparator}.
     */
    public static <T extends Describable<T>> Comparator<T> reverse(Class<T> type) {
        return Collections.reverseOrder(forward(type));
    }

    /**
     * Constructor.
     *
     * @param type the type.
     */
    DescriptorOrder(Class<T> type) {
        descriptors = Jenkins.get().getDescriptorList(type);
    }

    /**
     * {@inheritDoc}
     */
    public int compare(T o1, T o2) {
        int i1 = o1 == null ? -1 : descriptors.indexOf(o1.getDescriptor());
        int i2 = o2 == null ? -1 : descriptors.indexOf(o2.getDescriptor());
        if (i1 == -1) {
            return i2 == -1 ? 0 : 1;
        }
        if (i2 == -1) {
            return -1;
        }
        if (i1 == i2) {
            return 0;
        }
        if (i1 < i2) {
            return -1;
        }
        return 1;
    }
}
