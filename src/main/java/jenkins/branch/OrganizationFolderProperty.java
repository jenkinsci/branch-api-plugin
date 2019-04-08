/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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

import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.Functions;
import hudson.model.TaskListener;
import java.io.IOException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * A {@link AbstractFolderProperty} that is specific to {@link OrganizationFolder}s.
 */
public abstract class OrganizationFolderProperty<C extends OrganizationFolder> extends AbstractFolderProperty<C> {

    /**
     * Performs an idempotent application of this property's decoration to the supplied child. If the child is already
     * correctly decorated then the child will be unchanged. Must be called in the context of a {@link BulkChange} that
     * covers the supplied child.
     *
     * @param child the child to decorate.
     * @param listener a listener to log any commentary to.
     */
    public final void applyDecoration(@NonNull MultiBranchProject<?, ?> child, @NonNull TaskListener listener) {
        if (!BulkChange.contains(child)) {
            throw new IllegalStateException(
                    "This method must only be called when a BulkChange is open for the supplied child"
            );
        }
        try {
            decorate(child, listener);
        } catch (IOException e) {
            Functions.printStackTrace(e,
                    listener.error("Could not apply %s decoration to %s", getDescriptor().getDisplayName(),
                            child.getDisplayName()));
        }
    }

    /**
     * SPI for performing an idempotent application of this property's decoration to the supplied child. If the child is
     * already correctly decorated then the child must be unchanged.
     *
     * @param child the child to decorate.
     * @param listener a listener to log any commentary to.
     * @throws IOException as a convenience to implementations as some of the expected changes may call methods
     *         that could throw this but shouldn't because of the API's requirement that a {@link BulkChange} contains
     *         the child.
     */
    protected abstract void decorate(@NonNull MultiBranchProject<?, ?> child, @NonNull TaskListener listener) throws IOException;

    /**
     * {@inheritDoc}
     */
    @Override
    public OrganizationFolderPropertyDescriptor getDescriptor() {
        return (OrganizationFolderPropertyDescriptor) super.getDescriptor();
    }
}
