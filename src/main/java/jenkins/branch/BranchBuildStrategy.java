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

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;

/**
 * An extension point that allows controlling whether a specific {@link SCMHead} should be automatically built when
 * discovered.
 *
 * @since 2.0.0
 */
public abstract class BranchBuildStrategy extends AbstractDescribableImpl<BranchBuildStrategy>
        implements ExtensionPoint {
    /**
     * Should the specified {@link SCMHead} for the specified {@link SCMSource} be built whenever it is detected as
     * created / modified?
     *
     * @param source the {@link SCMSource}
     * @param head   the {@link SCMHead}
     * @return {@code true} if and only if the {@link SCMHead} should be automatically built when detected as created
     * / modified.
     */
    public abstract boolean isAutomaticBuild(SCMSource source, SCMHead head);

    /**
     * Should the specified {@link SCMRevision} of the {@link SCMHead} for the specified {@link SCMSource} be triggered
     * when the {@link SCMHead} has been detected as created / modified?
     *
     * @param source the {@link SCMSource}
     * @param head   the {@link SCMHead}
     * @param revision the {@link SCMRevision}
     * @return {@code true} if and only if the {@link SCMRevision} should be automatically built when the
     * {@link SCMHead} has been detected as created / modified.
     * @since 2.0.12
     */
    public boolean isAutomaticBuild(SCMSource source, SCMHead head, SCMRevision revision) {
        return isAutomaticBuild(source, head);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BranchBuildStrategyDescriptor getDescriptor() {
        return (BranchBuildStrategyDescriptor) super.getDescriptor();
    }
}
