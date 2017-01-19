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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nonnull;
import jenkins.model.TransientActionFactory;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMSource;
import jenkins.scm.impl.NullSCMSource;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * A source code branch.
 * Not to be subclassed outside this plugin.
 */
@ExportedBean
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
    private final CopyOnWriteArrayList<BranchProperty> properties = new CopyOnWriteArrayList<BranchProperty>();

    /**
     * The {@link SCMSource#fetchActions(SCMHead, SCMHeadEvent, TaskListener)} for this branch.
     * The collection is always replaced as a whole. We store the whole collection in an ArrayList so that the XStream
     * representation is simpler.
     * @since 2.0
     */
    private List<Action> actions;

    /**
     * Constructs a branch instance.
     *
     * @param sourceId the {@link jenkins.scm.api.SCMSource#getId()}
     * @param head     the name of the branch.
     * @param scm      the {@link SCM} for the branch.
     * @param properties the properties to initiate the branch with.
     */
    public Branch(String sourceId, SCMHead head, SCM scm, List<? extends BranchProperty> properties) {
        this.sourceId = sourceId;
        this.head = head;
        this.scm = scm;
        this.properties.addAll(properties);
        this.actions = new ArrayList<>();
    }

    /**
     * Internal copy constructor for creating dead branches.
     * @param id the id of the new {@link SCMSource}.
     * @param scm the new {@link SCM}.
     * @param b the branch to copy.
     * @since 2.0
     */
    private Branch(String id, SCM scm, Branch b) {
        this.sourceId = id;
        this.head = b.head;
        this.scm = scm;
        this.properties.addAll(b.properties);
        this.actions = new ArrayList<>(b.actions);
    }

    /**
     * Ensure actions is never null.
     *
     * @return the deserialized object.
     * @throws ObjectStreamException if things go wrong.
     */
    private Object readResolve() throws ObjectStreamException {
        if (actions == null) {
            actions = new ArrayList<>();
        }
        return this;
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
     *
     * @return {@link #getName} with URL-unsafe characters escaped
     * @since 0.2-beta-7
     */
    public String getEncodedName() {
        return NameMangler.apply(getName());
    }

    /**
     * Returns the {@link SCMHead of the branch}
     *
     * @return the {@link SCMHead of the branch}
     */
    @Exported
    public SCMHead getHead() {
        return head;
    }

    /**
     * Returns the {@link SCM} for the branch.
     *
     * @return the {@link SCM} for the branch.
     */
    @Exported
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
     * Gets the specific property, or {@code null} if no such property is found.
     *
     * @param <T> the type of property.
     * @param clazz the type of property.
     * @return the the specific property, or {@code null} if no such property is found.
     */
    @CheckForNull
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
     *
     * @return the properties.
     */
    @NonNull
    public List<BranchProperty> getProperties() {
        return properties;
    }

    /**
     * Gets all the actions.
     *
     * @return all the actions
     */
    @NonNull
    public List<Action> getActions() {
        return actions == null ? Collections.<Action>emptyList() : Collections.unmodifiableList(actions);
    }

    /**
     * Sets the actions atomically.
     * @param actions the new actions
     */
    /*package*/ void setActions(@NonNull List<Action> actions) {
        this.actions = new ArrayList<>(actions);
    }

    /**
     * Gets the specific action, or null if no such property is found.
     *
     * @param <T> the type of action
     * @param clazz the type of action.
     * @return the first action of the requested type or {@code null} if no such action is present.
     */
    @CheckForNull
    public <T extends Action> T getAction(Class<T> clazz) {
        if (actions == null) {
            return null;
        }
        for (Action p : actions) {
            if (clazz.isInstance(p)) {
                return clazz.cast(p);
            }
        }
        return null;
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
         * @param name       branch name.
         * @param properties the initial branch properties
         */
        public Dead(SCMHead name, List<? extends BranchProperty> properties) {
            super(NullSCMSource.ID, name, new NullSCM(), properties);
        }

        /**
         * Constructor.
         *
         * @param b the branch that is now dead.
         * @since 2.0
         */
        public Dead(Branch b) {
            super(NullSCMSource.ID, new NullSCM(), b);
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
     * Ensures that the {@link Branch#getActions()} are always present in the {@link Job#getAllActions()}.
     * NOTE: We need to use a transient action factory as {@link AbstractProject#getActions()} is unmodifiable
     * and consequently {@link AbstractProject#addAction(Action)} will always fail.
     *
     * @since 2.0
     */
    @Extension
    public static class TransientJobActionsFactoryImpl extends TransientActionFactory<Job> {

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<Job> type() {
            return Job.class;
        }

        /**
         * {@inheritDoc}
         */
        @Nonnull
        @Override
        public Collection<? extends Action> createFor(@Nonnull Job target) {
            if (target.getParent() instanceof MultiBranchProject) {
                MultiBranchProject p = (MultiBranchProject) target.getParent();
                BranchProjectFactory factory = p.getProjectFactory();
                if (factory.isProject(target)) {
                    Branch b = factory.getBranch(factory.asProject(target));
                    return b.getActions();
                }
            }
            return Collections.emptyList();
        }
    }

}
