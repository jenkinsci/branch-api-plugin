package jenkins.branch;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.ListView;
import hudson.model.View;
import hudson.model.ViewGroup;
import hudson.security.ACL;
import hudson.security.Permission;
import java.io.IOException;

import jenkins.management.Badge;
import jenkins.scm.api.SCMCategory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.core.Authentication;

@Restricted(NoExternalUse.class)
public abstract class BaseView<T extends SCMCategory<?>> extends ListView {

    private final T category;

    public BaseView(ViewGroup owner, @NonNull T category) {
        super(category.getName(), owner);
        this.category = category;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return category.getDisplayName().toString();
    }

    @Override
    public Badge getBadge() {
        int count = getItems().size();
        return new Badge(String.valueOf(count), count + " items", Badge.Severity.INFO);
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
