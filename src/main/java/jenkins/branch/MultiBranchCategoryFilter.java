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
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCategory;

/**
 * A {@link ViewJobFilter} that filters the children of a {@link OrganizationFolder} based on a
 * {@link SCMSourceCategory}.
 * Designed for programmatic construction only. Not designed for user instantiation, hence no {@link Descriptor}.
 *
 * @since FIXME
 */
public class MultiBranchCategoryFilter extends ViewJobFilter {

    /**
     * The category to filter on.
     */
    @NonNull
    private final SCMSourceCategory category;

    /**
     * Our constructor.
     *
     * @param category the category.
     */
    public MultiBranchCategoryFilter(SCMSourceCategory category) {
        this.category = category;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TopLevelItem> filter(List<TopLevelItem> added, List<TopLevelItem> all, View filteringView) {
        ViewGroup owner = filteringView.getOwner();
        if (owner instanceof OrganizationFolder) {
            List<SCMSourceCategory> categories = category.isUncategorized()
                    ? SCMSourceCategory.collect(((OrganizationFolder) owner).getNavigators())
                    : null;
            OUTER:
            for (TopLevelItem item : all) {
                if (added.contains(item)) {
                    continue;
                }
                if (!(item instanceof MultiBranchProject)) {
                    continue;
                }
                for (SCMSource s : ((MultiBranchProject<?, ?>) item).getSCMSources()) {
                    if (category.isMatch(s, categories)) {
                        added.add(item);
                        continue OUTER;
                    }
                }
            }
        }

        return added;
    }
}
