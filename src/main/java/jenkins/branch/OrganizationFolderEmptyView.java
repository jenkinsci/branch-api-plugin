package jenkins.branch;

import hudson.model.ViewGroup;

/**
 * Special view used when {@link OrganizationFolder} has no repositories.
 *
 * This view shows the UI to guide users to set up the repositories.
 */
public class OrganizationFolderEmptyView extends BaseEmptyView {
    /**
     * Constructor
     */
    public OrganizationFolderEmptyView(ViewGroup owner) {
        super(owner);
    }
}
