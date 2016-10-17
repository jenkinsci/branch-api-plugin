package jenkins.branch;

import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.views.ViewJobFilter;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

/**
 * Base class for a {@link ViewJobFilter} that will filter based on the {@link Branch} that a job in a
 * {@link MultiBranchProject} belongs to.
 *
 * @since FIXME
 */
public abstract class SimpleViewBranchFilter extends ViewJobFilter {

    /**
     * Optimization to avoid processing the less common case of removing already added items.
     */
    private final boolean processExclusions = isExcludedOverridden();

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public final List<TopLevelItem> filter(List<TopLevelItem> added, List<TopLevelItem> all, View filteringView) {
        ItemGroup<? extends TopLevelItem> itemGroup = filteringView.getOwnerItemGroup();
        if (itemGroup instanceof MultiBranchProject) {
            // fast path
            MultiBranchProject project = (MultiBranchProject) itemGroup;
            BranchProjectFactory factory = project.getProjectFactory();
            if (processExclusions) {
                for (Iterator<TopLevelItem> iterator = added.iterator(); iterator.hasNext(); ) {
                    TopLevelItem i = iterator.next();
                    if (factory.isProject(i) && isExcluded(factory.getBranch(factory.asProject(i)))) {
                        iterator.remove();
                    }
                }
            }
            for (TopLevelItem i : all) {
                if (factory.isProject(i) && isIncluded(factory.getBranch(factory.asProject(i)))) {
                    added.add(i);
                }
            }
        } else {
            // ok this may be a view pulling in all child items, so we need to check each item as we go
            if (processExclusions) {
                for (Iterator<TopLevelItem> iterator = added.iterator(); iterator.hasNext(); ) {
                    TopLevelItem i = iterator.next();
                    ItemGroup<? extends Item> parent = i.getParent();
                    if (parent instanceof MultiBranchProject) {
                        BranchProjectFactory factory = ((MultiBranchProject) parent).getProjectFactory();
                        if (factory.isProject(i) && isExcluded(factory.getBranch(factory.asProject(i)))) {
                            iterator.remove();
                        }
                    }
                }
            }
            for (TopLevelItem i : all) {
                ItemGroup<? extends Item> parent = i.getParent();
                if (parent instanceof MultiBranchProject) {
                    BranchProjectFactory factory = ((MultiBranchProject) parent).getProjectFactory();
                    if (factory.isProject(i) && isIncluded(factory.getBranch(factory.asProject(i)))) {
                        added.add(i);
                    }
                }
            }
        }
        return added;
    }

    /**
     * Tests if the supplied branch should be excluded.
     *
     * @param branch the {@link Branch}.
     * @return {@code true} to exclude the branch from the view.
     */
    public boolean isExcluded(Branch branch) {
        return false;
    }

    /**
     * Tests if the supplied branch should be included. Inclusion wins over exclusion.
     *
     * @param branch the {@link Branch}.
     * @return {@code true} to include the branch from the view.
     */
    public abstract boolean isIncluded(Branch branch);

    /**
     * Checks if {@link #isExcluded(Branch)} has been overridden.
     *
     * @return {@code true} if we need to process the added list, {@code false} if we can safely assume
     * {@link #isExcluded(Branch)} will always return {@code false}
     */
    private boolean isExcludedOverridden() {
        try {
            Method isExcluded = getClass().getMethod("isExcluded", Branch.class);
            return isExcluded.getDeclaringClass() != SimpleViewBranchFilter.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
