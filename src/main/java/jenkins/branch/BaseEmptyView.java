package jenkins.branch;

import hudson.model.Item;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.model.Descriptor.FormException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import javax.servlet.ServletException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Holds the basic 'empty view' parent
 */
public class BaseEmptyView extends View {
    /**
     * Constructor
     */
    public BaseEmptyView(ViewGroup owner) {
        super("Welcome", owner);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDefault() {
        // TODO might be better for the base implementation in View to be written this way rather than using ==
        return equals(getOwnerPrimaryView());
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
    protected void submit(StaplerRequest req) throws IOException, ServletException, FormException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        throw new UnsupportedOperationException();
    }
}
