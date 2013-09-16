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
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Publisher;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.impl.NullSCMSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A source code branch.
 */
public class Branch {

    /**
     * The ID of our {@link jenkins.scm.api.SCMSource}
     */
    private final String sourceId;

    /**
     * The name of the branch.
     */
    private final SCMHead head;

    /**
     * The {@link SCM} for this specific branch.
     */
    private final SCM scm;

    /**
     * The properties of this branch.
     */
    private final List<BranchProperty> properties = new CopyOnWriteArrayList<BranchProperty>();

    /**
     * Constructs a branch instance.
     *
     * @param sourceId the {@link jenkins.scm.api.SCMSource#getId()}
     * @param head     the name of the branch.
     * @param scm      the {@link SCM} for the branch.
     */
    public Branch(String sourceId, SCMHead head, SCM scm, List<? extends BranchProperty> properties) {
        this.sourceId = sourceId;
        this.head = head;
        this.scm = scm;
        this.properties.addAll(properties);
    }

    /**
     * Returns the {@link jenkins.scm.api.SCMSource#getId()} that this branch originates from.
     *
     * @return the {@link jenkins.scm.api.SCMSource#getId()} that this branch originates from.
     */
    public String getSourceId() {
        return sourceId;
    }

    /**
     * Returns the name of the branch.
     *
     * @return the name of the branch.
     */
    public String getName() {
        return head.getName();
    }

    /**
     * Returns the {@link SCMHead of the branch}
     *
     * @return the {@link SCMHead of the branch}
     */
    public SCMHead getHead() {
        return head;
    }

    /**
     * Returns the {@link SCM} for the branch.
     *
     * @return the {@link SCM} for the branch.
     */
    public SCM getScm() {
        return scm;
    }

    /**
     * Tests if a property of a specific type is present.
     *
     * @param clazz the specific property type
     * @return {@code true} if and only if there is a property of the specified type.
     */
    public boolean hasProperty(Class<? extends BranchProperty> clazz) {
        return getProperty(clazz) != null;
    }

    /**
     * Gets the specific property, or null if no such property is found.
     */
    public <T extends BranchProperty> T getProperty(Class<T> clazz) {
        for (BranchProperty p : properties) {
            if (clazz.isInstance(p)) {
                return clazz.cast(p);
            }
        }
        return null;
    }

    /**
     * Gets all the properties.
     */
    public List<BranchProperty> getProperties() {
        return properties;
    }

    /**
     * Takes the supplied publishers and gives each {@link BranchProperty} the option to contribute to the result.
     *
     * @param publishers the suggested publishers.
     * @return the configured final publishers.
     */
    public Map<Descriptor<Publisher>, Publisher> configurePublishers(
            Map<Descriptor<Publisher>, Publisher> publishers) {
        for (BranchProperty property : branchPropertiesInReverse()) {
            publishers = property.configurePublishers(publishers);
        }
        return publishers;
    }

    /**
     * Takes the supplied build wrappers and gives each {@link BranchProperty} the option to contribute to the result.
     *
     * @param wrappers the suggested build wrappers.
     * @return the configured final build wrappers.
     */
    public Map<Descriptor<BuildWrapper>, BuildWrapper> configureBuildWrappers(
            Map<Descriptor<BuildWrapper>, BuildWrapper> wrappers) {
        for (BranchProperty property : branchPropertiesInReverse()) {
            wrappers = property.configureBuildWrappers(wrappers);
        }
        return wrappers;
    }

    /**
     * Takes the supplied job properties and gives each {@link BranchProperty} the option to contribute to the result.
     *
     * @param jobProperties the suggested job properties.
     * @return the configured final job properties.
     */
    public <JobT extends Job<?, ?>> List<JobProperty<? super JobT>> configureJobProperties(
            List<JobProperty<? super JobT>> jobProperties) {
        for (BranchProperty property : branchPropertiesInReverse()) {
            jobProperties = property.configureJobProperties(jobProperties);
        }
        return jobProperties;
    }

    /**
     * Returns {@code true} iff the branch is can be built.
     *
     * @return {@code true} iff the branch is can be built.
     */
    public boolean isBuildable() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Branch)) {
            return false;
        }

        Branch branch = (Branch) o;

        if (sourceId != null ? !sourceId.equals(branch.sourceId) : branch.sourceId != null) {
            return false;
        }
        if (!head.equals(branch.head)) {
            return false;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int result = sourceId != null ? sourceId.hashCode() : 0;
        result = 31 * result + head.hashCode();
        return result;
    }

    /**
     * Returns the list of branch properties in reverse order.
     *
     * @return the list of branch properties in reverse order.
     */
    private List<BranchProperty> branchPropertiesInReverse() {
        List<BranchProperty> properties = new ArrayList<BranchProperty>(this.properties);
        Collections.sort(properties, DescriptorOrder.reverse(BranchProperty.class));
        return properties;
    }

    /**
     * Represents a dead branch.
     */
    public static class Dead extends Branch {
        /**
         * Constructor.
         *
         * @param name branch name.
         */
        public Dead(SCMHead name, List<? extends BranchProperty> properties) {
            super(NullSCMSource.ID, name, new NullSCM(), properties);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isBuildable() {
            return false;
        }
    }

    /**
     * A {@link Comparator} that compares {@link Describable} instances of a specific type based on the order of their
     * {@link Descriptor}s in {@link Jenkins}'s list of {@link Descriptor}s for that type.
     *
     * @param <T> the type of {@link Describable}.
     */
    private static class DescriptorOrder<T extends Describable<T>> implements Comparator<T> {
        /**
         * The list of {@link Descriptor}s to sort with.
         */
        private final DescriptorExtensionList<T, Descriptor<T>> descriptors;

        /**
         * Returns a {@link Comparator} that matches the order of the corresponding
         * {@link Jenkins#getDescriptorList(Class)}.
         *
         * @param type the type of {@link Describable}.
         * @param <T>  the type of {@link Describable}.
         * @return a {@link Comparator}.
         */
        public static <T extends Describable<T>> Comparator<T> forward(Class<T> type) {
            return new DescriptorOrder<T>(type);
        }

        /**
         * Returns a {@link Comparator} that reverses the order of the corresponding
         * {@link Jenkins#getDescriptorList(Class)}.
         *
         * @param type the type of {@link Describable}.
         * @param <T>  the type of {@link Describable}.
         * @return a {@link Comparator}.
         */
        public static <T extends Describable<T>> Comparator<T> reverse(Class<T> type) {
            return Collections.reverseOrder(forward(type));
        }

        /**
         * Constructor.
         *
         * @param type the type.
         */
        private DescriptorOrder(Class<T> type) {
            descriptors = Jenkins.getInstance().getDescriptorList(type);
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
}
