/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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
import hudson.model.Descriptor;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.views.ViewJobFilter;
import java.util.List;
import jenkins.scm.api.SCMHeadCategory;

/**
 * A {@link ViewJobFilter} that filters the children of a {@link MultiBranchProject} based on a {@link SCMHeadCategory}.
 * Designed for programmatic construction only. Not designed for user instantiation, hence no {@link Descriptor}.
 *
 * @since 2.0
 */
public class BranchCategoryFilter extends ViewJobFilter {

    /**
     * The category to filter on.
     */
    @NonNull
    private final SCMHeadCategory category;

    /**
     * Our constructor.
     *
     * @param category the category.
     */
    public BranchCategoryFilter(@NonNull SCMHeadCategory category) {
        this.category = category;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TopLevelItem> filter(List<TopLevelItem> added, List<TopLevelItem> all, View filteringView) {
        ViewGroup owner = filteringView.getOwner();
        if (owner instanceof MultiBranchProject) {
            MultiBranchProject<?, ?> project = (MultiBranchProject<?, ?>) owner;
            List<SCMHeadCategory> categories = category.isUncategorized()
                    ? SCMHeadCategory.collect(project.getSCMSources())
                    : null;
            BranchProjectFactory factory = project.getProjectFactory();
            for (TopLevelItem item : all) {
                if (added.contains(item)) {
                    continue;
                }
                if (!factory.isProject(item)) {
                    continue;
                }
                if (category.isMatch(factory.getBranch(factory.asProject(item)).getHead(), categories)) {
                    added.add(item);
                }
            }
        }

        return added;
    }
}
