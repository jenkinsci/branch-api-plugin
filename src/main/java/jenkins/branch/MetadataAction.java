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

import com.cloudbees.hudson.plugins.folder.FolderIcon;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.InvisibleAction;
import jenkins.model.Jenkins;
import org.apache.commons.jelly.JellyContext;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.kohsuke.stapler.Stapler;

/**
 * Holds metadata about a {@link OrganizationFolder} or {@link MultiBranchProject}.
 *
 * @since FIXME
 */
public abstract class MetadataAction extends InvisibleAction {
    /**
     * Returns the display name of the object or {@code null} to fall back to the default display name of the
     * {@link OrganizationFolder} or {@link MultiBranchProject} to which this {@link MetadataAction} is attached.
     *
     * @return the display name of the object or {@code null}
     */
    @CheckForNull
    public String getObjectDisplayName() {
        return null;
    }

    /**
     * Returns the description of the object or {@code null} to fall back to the default description of the
     * {@link OrganizationFolder} or {@link MultiBranchProject} to which this {@link MetadataAction} is attached.
     *
     * @return the description of the object or {@code null}
     */
    @CheckForNull
    public String getObjectDescription() {
        return null;
    }

    /**
     * Returns the external url of the object or {@code null} if the object does not have an exernal url.
     *
     * @return the display name of the object or {@code null}
     */
    @CheckForNull
    public String getObjectUrl() {
        return null;
    }

    /**
     * Returns the {@link Icon} class specification for the {@link FolderIcon}
     *
     * @return the {@link Icon} class specification
     * @see MetadataActionFolderIcon#getIconClassName()
     */
    @CheckForNull
    public String getFolderIconClassName() {
        return null;
    }

    /**
     * Returns the {@link FolderIcon#getDescription()} or {@code null} to fall back to the default description of the
     * {@link OrganizationFolder} or {@link MultiBranchProject} to which this {@link MetadataAction} is attached.
     *
     * @return the {@link FolderIcon#getDescription()} or {@code null}
     * @see MetadataActionFolderIcon#getDescription()
     */
    @CheckForNull
    public String getFolderIconDescription() {
        return null;
    }

    /**
     * Returns the {@link FolderIcon#getImageOf(String)} or {@code null} to fall back to the default of the
     * {@link OrganizationFolder} or {@link MultiBranchProject} to which this {@link MetadataAction} is attached.
     *
     * @param size the size.
     * @return the url or {@code null}
     * @see MetadataActionFolderIcon#getImageOf(String)
     */
    @CheckForNull
    public String getFolderIconImageOf(@NonNull String size) {
        return folderIconClassNameImageOf(getFolderIconClassName(), size);
    }

    /**
     * Helper method to resolve the icon image url.
     *
     * @param iconClassName the icon class name.
     * @param size          the size string, e.g. {@code 16x16}, {@code 24x24}, etc.
     * @return the icon image url or {@code null}
     */
    @CheckForNull
    protected final String folderIconClassNameImageOf(@CheckForNull String iconClassName, @NonNull String size) {
        if (StringUtils.isNotBlank(iconClassName)) {
            String spec = null;
            if ("16x16".equals(size)) {
                spec = "icon-sm";
            } else if ("24x24".equals(size)) {
                spec = "icon-md";
            } else if ("32x32".equals(size)) {
                spec = "icon-lg";
            } else if ("48x48".equals(size)) {
                spec = "icon-xlg";
            }
            if (spec != null) {
                Icon icon = IconSet.icons.getIconByClassSpec(iconClassName + " " + spec);
                if (icon != null) {
                    JellyContext ctx = new JellyContext();
                    ctx.setVariable("resURL", Stapler.getCurrentRequest().getContextPath() + Jenkins.RESOURCE_PATH);
                    return icon.getQualifiedUrl(ctx);
                }
            }
        }
        return null;
    }

}
