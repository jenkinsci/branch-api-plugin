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

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.PollingResult;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.xml.sax.SAXException;

public class MockSCM extends SCM {
    private final String controllerId;
    private final String repository;
    private final MockSCMHead branch;
    private final MockSCMRevision revision;
    private transient MockSCMController controller;

    public MockSCM(MockSCMController controller, String repository, MockSCMHead branch, MockSCMRevision revision) {
        this.controllerId = controller.getId();
        this.controller = controller;
        this.repository = repository;
        this.revision = revision;
        this.branch = branch;
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
    public boolean supportsPolling() {
        return true;
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        return false;
    }

    @Override
    public PollingResult compareRemoteRevisionWith(@Nonnull Job<?, ?> project, @Nullable Launcher launcher,
                                                   @Nullable FilePath workspace, @Nonnull TaskListener listener,
                                                   @Nonnull SCMRevisionState baseline)
            throws IOException, InterruptedException {
        if (baseline instanceof MockSCMRevisionState) {
            String revision = this.revision == null
                    ? controller().getRevision(repository, branch.getName())
                    : this.revision.getHash();
            if (((MockSCMRevisionState) baseline).getRevision().getHash().equals(revision)) {
                return PollingResult.NO_CHANGES;
            }
            return PollingResult.SIGNIFICANT;
        }
        return PollingResult.BUILD_NOW;
    }

    @Override
    public void checkout(@Nonnull Run<?, ?> build, @Nonnull Launcher launcher, @Nonnull FilePath workspace,
                         @Nonnull TaskListener listener, @CheckForNull File changelogFile,
                         @CheckForNull SCMRevisionState baseline) throws IOException, InterruptedException {
        String hash =
                controller().checkout(workspace, repository, revision == null ? branch.getName() : revision.getHash());
        try (FileWriter writer = new FileWriter(changelogFile)) {
            Items.XSTREAM2.toXML(controller().log(repository, hash), writer);
        }
        build.addAction(new MockSCMRevisionState(new MockSCMRevision(branch, hash)));
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new ChangeLogParser() {
            @Override
            public ChangeLogSet<? extends ChangeLogSet.Entry> parse(Run build, RepositoryBrowser<?> browser,
                                                                    File changelogFile)
                    throws IOException, SAXException {
                List<MockSCMController.LogEntry> entries =
                        (List<MockSCMController.LogEntry>) Items.XSTREAM2.fromXML(changelogFile);
                return new MockSCMChangeLogSet(build, browser, entries);
            }
        };
    }

    public static class MockSCMRevisionState extends SCMRevisionState {
        private final MockSCMRevision revision;

        public MockSCMRevisionState(MockSCMRevision revision) {
            this.revision = revision;
        }

        public MockSCMRevision getRevision() {
            return revision;
        }
    }

    @Extension
    public static class DescriptorImpl extends SCMDescriptor<MockSCM> {

        public DescriptorImpl() {
            super(MockSCMRepositoryBrowser.class);
        }
    }
}
