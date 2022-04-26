/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import hudson.model.Descriptor;
import edu.umd.cs.findbugs.annotations.CheckForNull;

/**
 * A kind of {@link MultiBranchProjectFactory}.
 * @since 0.2-beta-5
 */
public abstract class MultiBranchProjectFactoryDescriptor extends Descriptor<MultiBranchProjectFactory> {

    protected MultiBranchProjectFactoryDescriptor() {}

    protected MultiBranchProjectFactoryDescriptor(Class<? extends MultiBranchProjectFactory> clazz) {
        super(clazz);
    }

    /**
     * Creates a factory instance with a default configuration.
     * @return a default factory, or null to not registered this factory by default
     */
    @CheckForNull
    public abstract MultiBranchProjectFactory newInstance();

}
