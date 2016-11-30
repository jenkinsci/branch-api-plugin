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

import hudson.Extension;
import hudson.model.BallColor;
import hudson.model.Item;
import hudson.model.Job;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;
import hudson.views.StatusColumn;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link ListViewColumn} that shows the status icon for item ensuring that {@link Branch.Dead} jobs are reported as
 * disabled.
 *
 * @since 2.0
 */
public class BranchStatusColumn extends StatusColumn {
    /**
     * Constructor.
     */
    @DataBoundConstructor
    public BranchStatusColumn() {
    }

    /**
     * Gets the {@link BallColor} for an item.
     *
     * @param item the item.
     * @return the {@link BallColor}.
     */
    @Restricted(NoExternalUse.class)
    public BallColor iconColor(Item item) {
        if (item.getParent() instanceof MultiBranchProject) {
            MultiBranchProject p = (MultiBranchProject) item.getParent();
            BranchProjectFactory factory = p.getProjectFactory();
            if (factory.isProject(item)) {
                Branch b = factory.getBranch(factory.asProject(item));
                if (b instanceof Branch.Dead) {
                    return BallColor.DISABLED;
                }
            }
        }
        if (item instanceof Job) {
            return ((Job) item).getIconColor();
        }
        try {
            Method method = item.getClass().getMethod("getIconColor");
            if (BallColor.class.isAssignableFrom(method.getReturnType())) {
                return (BallColor) method.invoke(item);
            }
            return null;
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            return null;
        }
    }

    /**
     * Our descriptor.
     */
    @Extension
    public static class DescriptorImpl extends ListViewColumnDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.DescriptionColumn_displayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean shownByDefault() {
            return false;
        }
    }
}
