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

package jenkins.branch;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.scm.SCM;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.TagSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import jenkins.scm.impl.mock.MockSCMHead;
import jenkins.scm.impl.mock.MockTagSCMHead;
import org.hamcrest.Matchers;
import org.junit.Test;

import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.any;
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
        MockSCMSource source = mock(MockSCMSource.class);
        SCMSourceDescriptor sourceDescriptor = new MockSCMSource.MockSCMSourceDescriptor();
        when(owner.getSCMSources()).thenReturn(Collections.singletonList(source));
        when(source.getDescriptor()).thenReturn(sourceDescriptor);
        when(source.isCategoryEnabled(any(SCMHeadCategory.class))).thenReturn(true);
        TopLevelJob child1 = mock(TopLevelJob.class);
        when(factory.isProject(child1)).thenReturn(true);
        when(factory.asProject(child1)).thenReturn(child1);
        SCM scm = mock(SCM.class);
        when(factory.getBranch(child1)).thenReturn(
                new Branch("1", new MockSCMHead("master"), scm, Collections.<BranchProperty>emptyList()));
        TopLevelJob child2 = mock(TopLevelJob.class);
        when(factory.isProject(child2)).thenReturn(true);
        when(factory.asProject(child2)).thenReturn(child2);
        when(factory.getBranch(child2)).thenReturn(new Branch("1", new MockSCMHead("fork"), scm,
                Collections.<BranchProperty>emptyList()));
        TopLevelJob child3 = mock(TopLevelJob.class);
        when(factory.isProject(child3)).thenReturn(true);
        when(factory.asProject(child3)).thenReturn(child3);
        when(factory.getBranch(child3)).thenReturn(new Branch("1", new MockTagSCMHead("master-1.0"), scm,
                Collections.<BranchProperty>emptyList()));
        TopLevelJob child4 = mock(TopLevelJob.class);
        when(factory.isProject(child4)).thenReturn(false);
        List<TopLevelItem> added = new ArrayList<>();
        added.add(child1);
        List<TopLevelItem> all = new ArrayList<>();
        all.add(child1);
        all.add(child2);
        all.add(child3);
        all.add(child4);
        new BranchCategoryFilter(new UncategorizedSCMHeadCategory()).filter(added, all, filteringView);
        assertThat(added, Matchers.<TopLevelItem>containsInAnyOrder(child1, child2));

        added.clear();
        added.add(child1);
        all.clear();
        all.add(child1);
        all.add(child2);
        all.add(child3);
        all.add(child4);
        new BranchCategoryFilter(new TagSCMHeadCategory()).filter(added, all, filteringView);
        assertThat(added, Matchers.<TopLevelItem>containsInAnyOrder(child1, child3));
    }

    @Test
    public void filter_specificCategory() throws Exception {
        MultiBranchProject owner = mock(MultiBranchProject.class);
        View filteringView = mock(View.class);
        when(filteringView.getOwner()).thenReturn(owner);
        BranchProjectFactory factory = mock(BranchProjectFactory.class);
        when(owner.getProjectFactory()).thenReturn(factory);
        MockSCMSource source = mock(MockSCMSource.class);
        SCMSourceDescriptor sourceDescriptor = new MockSCMSource.MockSCMSourceDescriptor();
        when(owner.getSCMSources()).thenReturn(Collections.singletonList(source));
        when(source.getDescriptor()).thenReturn(sourceDescriptor);
        when(source.isCategoryEnabled(any(SCMHeadCategory.class))).thenReturn(true);
        TopLevelJob child1 = mock(TopLevelJob.class);
        when(factory.isProject(child1)).thenReturn(true);
        when(factory.asProject(child1)).thenReturn(child1);
        SCM scm = mock(SCM.class);
        when(factory.getBranch(child1)).thenReturn(
                new Branch("1", new MockSCMHead("master"), scm, Collections.<BranchProperty>emptyList()));
        TopLevelJob child2 = mock(TopLevelJob.class);
        when(factory.isProject(child2)).thenReturn(true);
        when(factory.asProject(child2)).thenReturn(child2);
        when(factory.getBranch(child2)).thenReturn(new Branch("1", new MockSCMHead("fork"), scm,
                Collections.<BranchProperty>emptyList()));
        TopLevelJob child3 = mock(TopLevelJob.class);
        when(factory.isProject(child3)).thenReturn(true);
        when(factory.asProject(child3)).thenReturn(child3);
        when(factory.getBranch(child3)).thenReturn(new Branch("1", new MockTagSCMHead("master-1.0"), scm,
                Collections.<BranchProperty>emptyList()));
        TopLevelJob child4 = mock(TopLevelJob.class);
        when(factory.isProject(child4)).thenReturn(false);
        List<TopLevelItem> added = new ArrayList<>();
        added.add(child1);
        List<TopLevelItem> all = new ArrayList<>();
        all.add(child1);
        all.add(child2);
        all.add(child3);
        all.add(child4);
        new BranchCategoryFilter(new TagSCMHeadCategory()).filter(added, all, filteringView);
        assertThat(added, Matchers.<TopLevelItem>containsInAnyOrder(child1, child3));

    }

    private static abstract class TopLevelJob extends Job<TopLevelJob, TopLevelRun> implements TopLevelItem {

        protected TopLevelJob(ItemGroup parent, String name) {
            super(parent, name);
        }
    }

    private static abstract class TopLevelRun extends Run<TopLevelJob, TopLevelRun> {

        protected TopLevelRun(@Nonnull TopLevelJob job) throws IOException {
            super(job);
        }
    }

    private static abstract class MockSCMSource extends SCMSource {

        protected MockSCMSource() {
            super("1");
        }

        @Override
        public boolean isCategoryEnabled(@NonNull SCMHeadCategory category) {
            return true;
        }

        private static class MockSCMSourceDescriptor extends SCMSourceDescriptor {

            @NonNull
            @Override
            protected SCMHeadCategory[] createCategories() {
                return new SCMHeadCategory[]{
                        new UncategorizedSCMHeadCategory(),
                        new TagSCMHeadCategory(),
                        new ChangeRequestSCMHeadCategory()
                };
            }
        }

    }

}
