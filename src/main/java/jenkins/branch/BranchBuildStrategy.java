/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.ProtectedExternally;

/**
 * An extension point that allows controlling whether a specific {@link SCMHead} should be automatically built when
 * discovered.
 * <p>
 * Methods marked as {@code SPI:} are intended to be implemented by implementers of {@link BranchBuildStrategy}.
 * Methods marked as {@code API:} are intended to be invoked consumers of {@link BranchBuildStrategy}.
 * A consumer invoking a {@code SPI:} method may get a {@link UnsupportedOperationException}.
 *
 * @since 2.0.0
 */
public abstract class BranchBuildStrategy extends AbstractDescribableImpl<BranchBuildStrategy>
        implements ExtensionPoint {
    /**
     * SPI: Should the specified {@link SCMHead} for the specified {@link SCMSource} be built whenever it is detected as
     * created / modified?
     *
     * @param source the {@link SCMSource}
     * @param head   the {@link SCMHead}
     * @return {@code true} if and only if the {@link SCMHead} should be automatically built when detected as created
     * / modified.
     * @deprecated use {@link #automaticBuild(SCMSource, SCMHead, SCMRevision, SCMRevision)}
     */
    @Deprecated
    @Restricted(ProtectedExternally.class)
    public boolean isAutomaticBuild(@NonNull SCMSource source, @NonNull SCMHead head) {
        throw new UnsupportedOperationException("Modern implementation accessed using legacy API method");
    }

    /**
     * SPI: Should the specified {@link SCMRevision} of the {@link SCMHead} for the specified {@link SCMSource} be
     * triggered when the {@link SCMHead} has been detected as created / modified?
     *
     * @param source   the {@link SCMSource}
     * @param head     the {@link SCMHead}
     * @param revision the {@link SCMRevision}
     * @return {@code true} if and only if the {@link SCMRevision} should be automatically built when the
     * {@link SCMHead} has been detected as created / modified.
     * @since 2.0.12
     * @deprecated use {@link #automaticBuild(SCMSource, SCMHead, SCMRevision, SCMRevision)}
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    @Restricted(ProtectedExternally.class)
    public boolean isAutomaticBuild(@NonNull SCMSource source, @NonNull SCMHead head, @NonNull SCMRevision revision) {
        throw new UnsupportedOperationException("Modern implementation accessed using legacy API method");
    }

    /**
     * SPI: Should the specified {@link SCMRevision} of the {@link SCMHead} for the specified {@link SCMSource} be
     * triggered when the {@link SCMHead} has been detected as created / modified?
     *
     * @param source       the {@link SCMSource}
     * @param head         the {@link SCMHead}
     * @param currRevision the {@link SCMRevision} that the head is now at
     * @param prevRevision the {@link SCMRevision} that the head was last seen at or {@code null} if this is a newly
     *                     discovered head. Care should be taken to consider the case of non
     *                     {@link SCMRevision#isDeterministic()} previous revisions as polling for changes will have
     *                     confirmed that there is a change between this and {@code currRevision} even if the two
     *                     are equal.
     * @return {@code true} if and only if the {@link SCMRevision} should be automatically built when the
     * {@link SCMHead} has been detected as created / modified.
     * @since 2.0.17
     */
    @Restricted(ProtectedExternally.class)
    public abstract boolean isAutomaticBuild(@NonNull SCMSource source,
                                             @NonNull SCMHead head,
                                             @NonNull SCMRevision currRevision,
                                             @CheckForNull SCMRevision prevRevision);

    /**
     * API: Should the specified {@link SCMRevision} of the {@link SCMHead} for the specified {@link SCMSource} be
     * triggered when the {@link SCMHead} has been detected as created / modified?
     *
     * @param source       the {@link SCMSource}
     * @param head         the {@link SCMHead}
     * @param currRevision the {@link SCMRevision} that the head is now at
     * @param prevRevision the {@link SCMRevision} that the head was last seen at or {@code null} if this is a newly
     *                     discovered head. Care should be taken to consider the case of non
     *                     {@link SCMRevision#isDeterministic()} previous revisions as polling for changes will have
     *                     confirmed that there is a change between this and {@code currRevision} even if the two
     *                     are equal.
     * @return {@code true} if and only if the {@link SCMRevision} should be automatically built when the
     * {@link SCMHead} has been detected as created / modified.
     * @since 2.0.17
     */
    @SuppressWarnings("deprecation")
    public final boolean automaticBuild(@NonNull SCMSource source,
                                        @NonNull SCMHead head,
                                        @NonNull SCMRevision currRevision,
                                        @CheckForNull SCMRevision prevRevision) {
        if (Util.isOverridden(BranchBuildStrategy.class, getClass(), "isAutomaticBuild", SCMSource.class,
                SCMHead.class, SCMRevision.class, SCMRevision.class)) {
            // modern implementation written to the 2.0.17+ spec
            return isAutomaticBuild(source, head, currRevision, prevRevision);
        }
        if (Util.isOverridden(BranchBuildStrategy.class, getClass(), "isAutomaticBuild", SCMSource.class,
                SCMHead.class, SCMRevision.class)) {
            // legacy implementation written to the 2.0.12-2.0.16 spec
            return isAutomaticBuild(source, head, currRevision);
        }
        if (Util.isOverridden(BranchBuildStrategy.class, getClass(), "isAutomaticBuild", SCMSource.class,
                SCMHead.class)) {
            // legacy implementation written to the 2.0.0-2.0.11 spec
            return isAutomaticBuild(source, head);
        }
        // this is going to throw an abstract method exception, but we should never get here as all implementations
        // have to at least have overridden one of the methods above.
        return isAutomaticBuild(source, head, currRevision, prevRevision);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BranchBuildStrategyDescriptor getDescriptor() {
        return (BranchBuildStrategyDescriptor) super.getDescriptor();
    }
}
