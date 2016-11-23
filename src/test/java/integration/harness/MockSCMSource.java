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
import hudson.model.Action;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMProbeStat;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.TagSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class MockSCMSource extends SCMSource {
    private final String controllerId;
    private final String repository;
    private final boolean includeBranches;
    private final boolean includeTags;
    private final boolean includeChangeRequests;
    private transient MockSCMController controller;

    @DataBoundConstructor
    public MockSCMSource(@CheckForNull String id, String controllerId, String repository, boolean includeBranches,
                         boolean includeTags, boolean includeChangeRequests) {
        super(id);
        this.controllerId = controllerId;
        this.repository = repository;
        this.includeBranches = includeBranches;
        this.includeTags = includeTags;
        this.includeChangeRequests = includeChangeRequests;
    }

    public MockSCMSource(String id, MockSCMController controller, String repository, boolean includeBranches,
                         boolean includeTags, boolean includeChangeRequests) {
        super(id);
        this.controllerId = controller.getId();
        this.controller = controller;
        this.repository = repository;
        this.includeBranches = includeBranches;
        this.includeTags = includeTags;
        this.includeChangeRequests = includeChangeRequests;
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

    public boolean isIncludeBranches() {
        return includeBranches;
    }

    public boolean isIncludeTags() {
        return includeTags;
    }

    public boolean isIncludeChangeRequests() {
        return includeChangeRequests;
    }

    @Override
    protected void retrieve(@CheckForNull SCMSourceCriteria criteria, @NonNull SCMHeadObserver observer,
                            @NonNull TaskListener listener) throws IOException, InterruptedException {
        if (includeBranches) {
            for (final String branch : controller().listBranches(repository)) {
                checkInterrupt();
                String revision = controller().getRevision(repository, branch);
                MockSCMHead head = new MockSCMHead(branch, false);
                if (criteria == null || criteria.isHead(new MockSCMProbe(head, revision), listener)) {
                    observer.observe(head, new MockSCMRevision(head, revision));
                }
            }
        }
        if (includeTags) {
            for (final String tag : controller().listTags(repository)) {
                checkInterrupt();
                String revision = controller().getRevision(repository, tag);
                MockSCMHead head = new MockSCMHead(tag, true);
                if (criteria == null || criteria.isHead(new MockSCMProbe(head, revision), listener)) {
                    observer.observe(head, new MockSCMRevision(head, revision));
                }
            }
        }
        if (includeChangeRequests) {
            for (final Integer number : controller().listChangeRequests(repository)) {
                checkInterrupt();
                String revision = controller().getRevision(repository, "change-request/" + number);
                String target = controller().getTarget(repository, number);
                MockChangeRequestSCMHead head = new MockChangeRequestSCMHead(number, target);
                if (criteria == null || criteria.isHead(new MockSCMProbe(head, revision), listener)) {
                    observer.observe(head, new MockSCMRevision(head, revision));
                }
            }
        }
    }

    @NonNull
    @Override
    public SCM build(@NonNull SCMHead head, @CheckForNull SCMRevision revision) {
        return new MockSCM(this, head, revision instanceof MockSCMRevision ? (MockSCMRevision) revision : null);
    }


    @NonNull
    @Override
    protected Map<Class<? extends Action>, Action> retrieveActions(@NonNull TaskListener listener)
            throws IOException, InterruptedException {
        Map<Class<? extends Action>, Action> result = new HashMap<>();
        result.put(MockSCMLink.class, new MockSCMLink("source"));
        String description = controller().getDescription(repository);
        String displayName = controller().getDisplayName(repository);
        String url = controller().getUrl(repository);
        String iconClassName = controller().getRepoIconClassName();
        if (description != null || displayName != null || url != null || iconClassName != null) {
            result.put(MockMetadataAction.class, new MockMetadataAction(description, displayName, url, iconClassName));
        }
        return result;
    }

    @NonNull
    @Override
    protected Map<Class<? extends Action>, Action> retrieveActions(@NonNull SCMRevision revision,
                                                                   @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        return Collections.<Class<? extends Action>, Action>singletonMap(MockSCMLink.class,
                new MockSCMLink("revision"));
    }

    @NonNull
    @Override
    protected Map<Class<? extends Action>, Action> retrieveActions(@NonNull SCMHead head,
                                                                   @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        return Collections.<Class<? extends Action>, Action>singletonMap(MockSCMLink.class,
                new MockSCMLink("branch"));
    }

    @Override
    protected boolean isCategoryEnabled(@NonNull SCMHeadCategory category) {
        if (category instanceof ChangeRequestSCMHeadCategory) {
            return includeChangeRequests;
        }
        if (category instanceof TagSCMHeadCategory) {
            return includeTags;
        }
        return true;
    }

    @Extension
    public static class DescriptorImpl extends SCMSourceDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Mock SCM";
        }

        public ListBoxModel doFillControllerIdItems() {
            ListBoxModel result = new ListBoxModel();
            for (MockSCMController c : MockSCMController.all()) {
                result.add(c.getId());
            }
            return result;
        }

        public ListBoxModel doFillRepositoryItems(@QueryParameter String controllerId) throws IOException {
            ListBoxModel result = new ListBoxModel();
            MockSCMController c = MockSCMController.lookup(controllerId);
            if (c != null) {
                for (String r : c.listRepositories()) {
                    result.add(r);
                }
            }
            return result;
        }

        @NonNull
        @Override
        protected SCMHeadCategory[] createCategories() {
            return new SCMHeadCategory[]{
                    new UncategorizedSCMHeadCategory(),
                    new ChangeRequestSCMHeadCategory(),
                    new TagSCMHeadCategory()
            };
        }
    }

    private class MockSCMProbe extends SCMProbe {
        private final String revision;
        private final SCMHead head;

        public MockSCMProbe(SCMHead head, String revision) {
            this.revision = revision;
            this.head = head;
        }

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
            return head.getName();
        }

        @Override
        public long lastModified() {
            return controller().lastModified(repository, revision);
        }
    }
}
