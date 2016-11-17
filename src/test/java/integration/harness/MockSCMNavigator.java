/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
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
 *
 */

package integration.harness;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.io.IOException;
import javax.annotation.Nonnull;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMSourceObserver;
import org.kohsuke.stapler.DataBoundConstructor;

public class MockSCMNavigator extends SCMNavigator {

    private final String controllerId;
    private transient MockSCMController controller;

    @DataBoundConstructor
    public MockSCMNavigator(String id) {
        this.controllerId = id;
    }

    public MockSCMNavigator(MockSCMController controller) {
        this.controllerId = controller.getId();
        this.controller = controller;
    }

    public String getControllerId() {
        return controllerId;
    }

    private MockSCMController controller() {
        if (controller == null) {
            controller = MockSCMController.lookup(controllerId);
        }
        return controller;
    }

    @Override
    public void visitSources(@NonNull SCMSourceObserver observer) throws IOException, InterruptedException {
        for (String name : controller().listRepositories()) {
            checkInterrupt();
            SCMSourceObserver.ProjectObserver po = observer.observe(name);
            po.addSource(new MockSCMSource(null, controller, name));
            po.complete();
        }
    }

    @Extension
    public static class DescriptorImpl extends SCMNavigatorDescriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Mock SCM";
        }

        @Override
        public SCMNavigator newInstance(@CheckForNull String name) {
            return null;
        }
    }
}
