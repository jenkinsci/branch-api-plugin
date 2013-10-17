/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc.
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

import hudson.model.Descriptor.FormException;
import hudson.model.Item;
import hudson.model.TopLevelItem;
import hudson.model.View;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * Special view used when {@link MultiBranchProject} has no branches.
 *
 * This view shows the UI to guide users to configure and index branches.
 *
 * @author Kohsuke Kawaguchi
 */
public class MultiBranchProjectWelcomeView extends View {
    /**
     * Constructor
     */
    public MultiBranchProjectWelcomeView(MultiBranchProject owner) {
        super("Welcome", owner);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<TopLevelItem> getItems() {
        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(TopLevelItem item) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onJobRenamed(Item item, String oldName, String newName) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void submit(StaplerRequest req) throws IOException, ServletException, FormException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        throw new UnsupportedOperationException();
    }

    // no descriptor because this is an internally used view and not instantiable
}
