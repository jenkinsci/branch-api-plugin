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
import hudson.model.TaskListener;
import hudson.scm.SCM;
import java.io.IOException;
import javax.annotation.Nonnull;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMProbeStat;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceDescriptor;

public class MockSCMSource extends SCMSource {
    private final String controllerId;
    private final String repository;
    private transient MockSCMController controller;

    public MockSCMSource(String id, MockSCMController controller, String repository) {
        super(id);
        this.controllerId = controller.getId();
        this.controller = controller;
        this.repository = repository;
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

    public String getRepository() {
        return repository;
    }

    @Override
    protected void retrieve(@CheckForNull SCMSourceCriteria criteria, @NonNull SCMHeadObserver observer,
                            @NonNull TaskListener listener) throws IOException, InterruptedException {
        for (final String branch : controller().listBranches(repository)) {
            checkInterrupt();
            final String revision = controller().getRevision(repository, branch);
            if (criteria != null && !criteria.isHead(new SCMProbe() {
                @NonNull
                @Override
                public SCMProbeStat stat(@NonNull String path) throws IOException {
                    return SCMProbeStat.fromType(controller().stat(repository, revision, path));
                }

                @Override
                public void close() throws IOException {

                }

                @Override
                public String name() {
                    return branch;
                }

                @Override
                public long lastModified() {
                    return controller().lastModified(repository, revision);
                }
            }, listener)) {
                continue;
            }
            MockSCMHead head = new MockSCMHead(branch);
            observer.observe(head, new MockSCMRevision(head, revision));
        }
    }

    @NonNull
    @Override
    public SCM build(@NonNull SCMHead head, @CheckForNull SCMRevision revision) {
        return new MockSCM(controller(), repository, (MockSCMHead) head,
                revision instanceof MockSCMRevision ? (MockSCMRevision) revision : null);
    }

    @Extension
    public static class DescriptorImpl extends SCMSourceDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Mock SCM";
        }
    }
}
