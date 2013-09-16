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

import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;

import java.io.IOException;
import java.util.Map;

/**
 * A strategy for removing {@link Branch} projects after they no longer have a source in their
 * {@link MultiBranchProject}.
 */
public abstract class DeadBranchStrategy extends AbstractDescribableImpl<DeadBranchStrategy> {

    /**
     * The owner of this branch source. Branch sources may need to cache information from remote servers and
     * therfore need to keep it with the project that they are associated with.
     */
    private transient MultiBranchProject<?, ?> owner;

    public abstract <P extends AbstractProject<P, R> & TopLevelItem, R extends AbstractBuild<P, R>>
    void runDeadBranchCleanup(TaskListener listener, Map<String, P> deadBranches) throws IOException,
            InterruptedException;

    /**
     * Returns the owner of this branch source. Branch sources may need to cache information from remote servers and
     * therefore need to keep it with the project that they are associated with.
     *
     * @return the owner of this branch source.
     */
    public MultiBranchProject<?, ?> getOwner() {
        return owner;
    }

    /**
     * Called by {@link MultiBranchProject} to set the owner once the source is attached to the project.
     *
     * @param owner the owner.
     */
    public void setOwner(MultiBranchProject<?, ?> owner) {
        this.owner = owner;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DeadBranchStrategyDescriptor getDescriptor() {
        return (DeadBranchStrategyDescriptor) super.getDescriptor();
    }

}
