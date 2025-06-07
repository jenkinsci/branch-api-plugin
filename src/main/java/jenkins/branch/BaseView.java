package jenkins.branch;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.ListView;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.security.ACL;
import hudson.security.Permission;
import java.io.IOException;
import jenkins.scm.api.SCMCategory;
import org.springframework.security.core.Authentication;

public abstract class BaseView<T extends SCMCategory<?>> extends ListView {

    protected final T category;

    public BaseView(ViewGroup owner, @NonNull T category) {
        super(category.getName(), owner);
        this.category = category;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return category.getDisplayName() + " (" + getItems().size() + ")";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRecurse() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ACL getACL() {
        final ACL acl = super.getACL();
        return new ACL() {
            @Override
            public boolean hasPermission2(@NonNull Authentication a, @NonNull Permission permission) {
                if (View.CREATE.equals(permission)
                        || View.CONFIGURE.equals(permission)
                        || View.DELETE.equals(permission)) {
                    return false;
                }
                return acl.hasPermission2(a, permission);
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void save() throws IOException {
        // no-op
    }
}
