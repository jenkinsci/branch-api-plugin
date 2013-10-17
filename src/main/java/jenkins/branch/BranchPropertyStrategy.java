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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import jenkins.scm.api.SCMHead;

import java.util.List;

/**
 * A strategy for determining the properties that apply to a specific {@link SCMHead}.
 *
 * @author Stephen Connolly
 */
public abstract class BranchPropertyStrategy extends AbstractDescribableImpl<BranchPropertyStrategy> implements ExtensionPoint {

    /**
     * Returns the list of properties to be injected into the {@link Branch} for the specified {@link SCMHead}.
     *
     * @param head the {@link SCMHead}
     * @return the list of properties.
     */
    @NonNull
    public abstract List<BranchProperty> getPropertiesFor(SCMHead head);

    /**
     * {@inheritDoc}
     */
    @Override
    public BranchPropertyStrategyDescriptor getDescriptor() {
        return (BranchPropertyStrategyDescriptor) super.getDescriptor();
    }
}
