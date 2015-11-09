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

import hudson.Util;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import jenkins.scm.api.SCMHead;
import jenkins.scm.impl.NullSCMSource;

/**
 * A source code branch.
 * Not to be subclassed outside this plugin.
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
        // TODO this could include a uniquifying prefix defined in BranchSource
        return head.getName();
    }

    /**
     * Gets a branch name suitable for use in paths.
     * @return {@link #getName} with URL-unsafe characters escaped
     * @since 0.2-beta-7
     */
    public String getEncodedName() {
        return Util.rawEncode(getName());
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

}
