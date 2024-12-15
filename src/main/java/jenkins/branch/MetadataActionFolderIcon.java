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

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.FolderIcon;
import com.cloudbees.hudson.plugins.folder.FolderIconDescriptor;
import hudson.Extension;
import hudson.Util;
import hudson.model.Hudson;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.metadata.AvatarMetadataAction;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;

/**
 * A {@link FolderIcon} specifically for {@link OrganizationFolder} and {@link MultiBranchProject} instances that will
 * delegate to the {@link AvatarMetadataAction} attached to the folder.
 *
 * @since 2.0
 */
public class MetadataActionFolderIcon extends FolderIcon {

    /**
     * Our owner.
     */
    private AbstractFolder<?> owner;

    /**
     * Our constructor.
     */
    @DataBoundConstructor
    public MetadataActionFolderIcon() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setOwner(AbstractFolder<?> folder) {
        this.owner = folder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIconClassName() {
        if (owner != null) {
            if (owner.isDisabled()) {
                return "icon-folder-disabled";
            }
            AvatarMetadataAction action = owner.getAction(AvatarMetadataAction.class);
            if (action != null) {
                String result = action.getAvatarIconClassName();
                if (result != null) {
                    // if the className is non-null, return that
                    return result;
                }
                try {
                    if (Util.isOverridden(AvatarMetadataAction.class, action.getClass(), "getAvatarImageOf", String.class)
                            && action.getAvatarImageOf("32x32") != null) {
                        // if the metadata action has a custom getAvatarImageOf then it may be using that to return
                        // a custom image URL rather than an Icon, in which case we need to check to see if the
                        // getAvatarImageOf is returning a non-null value which would necessitate returning null
                        // here in order to ensure that the getImageOf path is called.
                        // of course this can only happen if the getAvatarImageOf is overridden as the default
                        // will just produce an image url based on the Icon - which we already know is null.
                        return null;
                    }
                } catch (IllegalArgumentException ignore) {
                    // This would be thrown by Util.isOverridden if the method does not exist, which
                    // should never happen, as AvatarMetadataAction has the method (which we call)
                    // if it was overridden (because if not overridden invoking the method will return
                    // a non-null value based on the icon class name. We'd prefer to use the icon class
                    // name rather than generating an image url, which is why we do the is overridden check
                }
                // otherwise the metadata doesn't want to control the icon, so fall back to the descriptor's default
            }
            return owner.getDescriptor().getIconClassName();
        }
        return "icon-folder";
    }

    /**
     * {@inheritDoc}
     */
    public String getImageOf(String size) {
        if (owner != null) {
            AvatarMetadataAction action = owner.getAction(AvatarMetadataAction.class);
            if (action != null) {
                String result = action.getAvatarImageOf(size);
                if (result != null) {
                    return result;
                }
            }
        }
        String image = iconClassNameImageOf(size);
        return image != null
                ? image
                : (Stapler.getCurrentRequest().getContextPath() + Hudson.RESOURCE_PATH
                        + "/plugin/cloudbees-folder/images/" + size + "/folder.png");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        if (owner != null) {
            AvatarMetadataAction action = owner.getAction(AvatarMetadataAction.class);
            if (action != null) {
                String result = action.getAvatarDescription();
                if (result != null) {
                    return result;
                }
            }
            return owner.getPronoun();
        } else {
            return "Folder";
        }
    }

    /**
     * Our descriptor.
     */
    @Extension
    public static class DescriptorImpl extends FolderIconDescriptor {
        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return "Metadata Folder Icon";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractFolder> folderType) {
            return MultiBranchProject.class.isAssignableFrom(folderType)
                    || OrganizationFolder.class.isAssignableFrom(folderType);
        }
    }
}
