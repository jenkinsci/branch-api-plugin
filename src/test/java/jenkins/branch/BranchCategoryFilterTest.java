/*
 * The MIT License
 *
 * Copyright (c) 2016-2017 CloudBees, Inc.
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

package jenkins.branch;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.View;
import hudson.scm.SCM;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;

import javax.annotation.Nonnull;

import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.TagSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import jenkins.scm.impl.mock.MockSCMHead;
import jenkins.scm.impl.mock.MockTagSCMHead;
import org.hamcrest.Matchers;
import org.junit.Test;

import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BranchCategoryFilterTest {
    @Test
    public void filter_uncategorizedCategory() throws Exception {
        MultiBranchProject owner = mock(MultiBranchProject.class);
        View filteringView = mock(View.class);
        when(filteringView.getOwner()).thenReturn(owner);
        BranchProjectFactory factory = mock(BranchProjectFactory.class);
        when(owner.getProjectFactory()).thenReturn(factory);
        MockSCMSource source = new MockSCMSource();
        when(owner.getSCMSources()).thenReturn(Collections.singletonList(source));
        TopLevelJob child1 = new TopLevelJob("child1");
        when(factory.isProject(child1)).thenReturn(true);
        when(factory.asProject(child1)).thenReturn(child1);
        SCM scm = mock(SCM.class);
        when(factory.getBranch(child1)).thenReturn(
                new Branch("1", new MockSCMHead("master"), scm, Collections.<BranchProperty>emptyList()));
        TopLevelJob child2 = new TopLevelJob("child2");
        when(factory.isProject(child2)).thenReturn(true);
        when(factory.asProject(child2)).thenReturn(child2);
        when(factory.getBranch(child2)).thenReturn(new Branch("1", new MockSCMHead("fork"), scm,
                Collections.<BranchProperty>emptyList()));
        TopLevelJob child3 = new TopLevelJob("child3");
        when(factory.isProject(child3)).thenReturn(true);
        when(factory.asProject(child3)).thenReturn(child3);
        when(factory.getBranch(child3)).thenReturn(new Branch("1", new MockTagSCMHead("master-1.0", 0L), scm,
                Collections.<BranchProperty>emptyList()));
        TopLevelJob child4 = new TopLevelJob("child4");
        when(factory.isProject(child4)).thenReturn(false);
        List<TopLevelItem> added = new ArrayList<>();
        added.add(child1);
        List<TopLevelItem> all = new ArrayList<>();
        all.add(child1);
        all.add(child2);
        all.add(child3);
        all.add(child4);
        new BranchCategoryFilter(UncategorizedSCMHeadCategory.DEFAULT).filter(added, all, filteringView);
        assertThat(added, Matchers.<TopLevelItem>containsInAnyOrder(child1, child2));

        added.clear();
        added.add(child1);
        all.clear();
        all.add(child1);
        all.add(child2);
        all.add(child3);
        all.add(child4);
        new BranchCategoryFilter(TagSCMHeadCategory.DEFAULT).filter(added, all, filteringView);
        assertThat(added, Matchers.<TopLevelItem>containsInAnyOrder(child1, child3));
    }

    @Test
    public void filter_specificCategory() throws Exception {
        MultiBranchProject owner = mock(MultiBranchProject.class);
        View filteringView = mock(View.class);
        when(filteringView.getOwner()).thenReturn(owner);
        BranchProjectFactory factory = mock(BranchProjectFactory.class);
        when(owner.getProjectFactory()).thenReturn(factory);
        MockSCMSource source = new MockSCMSource();
        when(owner.getSCMSources()).thenReturn(Collections.singletonList(source));
        TopLevelJob child1 = new TopLevelJob("child1");
        when(factory.isProject(child1)).thenReturn(true);
        when(factory.asProject(child1)).thenReturn(child1);
        SCM scm = mock(SCM.class);
        when(factory.getBranch(child1)).thenReturn(
                new Branch("1", new MockSCMHead("master"), scm, Collections.<BranchProperty>emptyList()));
        TopLevelJob child2 = new TopLevelJob("child2");
        when(factory.isProject(child2)).thenReturn(true);
        when(factory.asProject(child2)).thenReturn(child2);
        when(factory.getBranch(child2)).thenReturn(new Branch("1", new MockSCMHead("fork"), scm,
                Collections.<BranchProperty>emptyList()));
        TopLevelJob child3 = new TopLevelJob("child3");
        when(factory.isProject(child3)).thenReturn(true);
        when(factory.asProject(child3)).thenReturn(child3);
        when(factory.getBranch(child3)).thenReturn(new Branch("1", new MockTagSCMHead("master-1.0", 0L), scm,
                Collections.<BranchProperty>emptyList()));
        TopLevelJob child4 = new TopLevelJob("child4");
        when(factory.isProject(child4)).thenReturn(false);
        List<TopLevelItem> added = new ArrayList<>();
        added.add(child1);
        List<TopLevelItem> all = new ArrayList<>();
        all.add(child1);
        all.add(child2);
        all.add(child3);
        all.add(child4);
        new BranchCategoryFilter(TagSCMHeadCategory.DEFAULT).filter(added, all, filteringView);
        assertThat(added, Matchers.<TopLevelItem>containsInAnyOrder(child1, child3));

    }

    private static class TopLevelJob extends Job<TopLevelJob, TopLevelRun> implements TopLevelItem {

        protected TopLevelJob(String name) {
            super(null, name);
        }

        @Override
        public TopLevelItemDescriptor getDescriptor() {
            return null;
        }

        @Override
        public boolean isBuildable() {
            return false;
        }

        @Override
        protected SortedMap<Integer, ? extends TopLevelRun> _getRuns() {
            return null;
        }

        @Override
        protected void removeRun(TopLevelRun run) {
        }
    }

    private static abstract class TopLevelRun extends Run<TopLevelJob, TopLevelRun> {

        protected TopLevelRun(@Nonnull TopLevelJob job) throws IOException {
            super(job);
        }
    }

    private static class MockSCMSource extends SCMSource {

        private MockSCMSourceDescriptor descriptor;

        protected MockSCMSource() {
            super("1");
            this.descriptor = new MockSCMSourceDescriptor();
        }

        @Override
        public boolean isCategoryEnabled(@NonNull SCMHeadCategory category) {
            return true;
        }

        @Override
        public SCMSourceDescriptor getDescriptor() {
            return descriptor;
        }

        private static class MockSCMSourceDescriptor extends SCMSourceDescriptor {

            @NonNull
            @Override
            protected SCMHeadCategory[] createCategories() {
                return new SCMHeadCategory[]{
                        UncategorizedSCMHeadCategory.DEFAULT,
                        TagSCMHeadCategory.DEFAULT,
                        ChangeRequestSCMHeadCategory.DEFAULT
                };
            }
        }

        @Override
        protected void retrieve(SCMSourceCriteria criteria,
                                SCMHeadObserver observer,
                                SCMHeadEvent<?> event,
                                TaskListener listener) throws IOException, InterruptedException {
        }

        @Override
        public SCM build(SCMHead head, SCMRevision revision) {
            return null;
        }

    }

}
