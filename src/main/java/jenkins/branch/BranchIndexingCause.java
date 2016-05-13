/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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
import hudson.model.Cause;
import hudson.model.ItemGroup;
import hudson.model.Run;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * Declares that a build was due to branch indexing.
 */
public final class BranchIndexingCause extends Cause {

    private transient MultiBranchProject<?,?> multiBranchProject;

    BranchIndexingCause() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAddedTo(Run build) {
        ItemGroup<?> g = build.getParent().getParent();
        if (g instanceof MultiBranchProject) {
            multiBranchProject = (MultiBranchProject) g;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoad(Run<?,?> build) {
        onAddedTo(build);
    }

    /**
     * Gets the associated multibranch project.
     * @return the multibranch project (nonnull unless deleted before restart)
     */
    @CheckForNull
    public MultiBranchProject<?,?> getMultiBranchProject() {
        return multiBranchProject;
    }

    @Restricted(DoNotUse.class) // Jelly
    @CheckForNull
    public String getIndexingUrl() {
        return multiBranchProject != null ? multiBranchProject.getIndexing().getUrl() : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getShortDescription() {
        return "Branch indexing";
    }

}
