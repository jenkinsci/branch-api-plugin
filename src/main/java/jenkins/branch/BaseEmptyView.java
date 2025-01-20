package jenkins.branch;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Item;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.model.Descriptor.FormException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import jakarta.servlet.ServletException;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Holds the basic 'empty view' parent
 */
public class BaseEmptyView extends View {

    /**
     * The empty view name
     */
    public static final String VIEW_NAME = "welcome";

    /**
     * {@inheritDoc}
     */
    public BaseEmptyView(ViewGroup owner) {
        super(VIEW_NAME, owner);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return Messages.BaseEmptyView_displayName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDefault() {
        // TODO might be better for the base implementation in View to be written this way rather than using ==
        return equals(owner.getPrimaryView());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEditable() {
        return false;
    }

    /**
     * Equal to any view of the same class and owner.
     * {@inheritDoc}
     */
    @Override
    public final boolean equals(Object obj) {
        return obj != null && obj.getClass() == getClass() && ((BaseEmptyView) obj).owner == owner;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int hashCode() {
        return owner == null ? 0 : owner.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
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
    protected void submit(StaplerRequest2 req) throws IOException, ServletException, FormException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Item doCreateItem(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        throw new UnsupportedOperationException();
    }
}
