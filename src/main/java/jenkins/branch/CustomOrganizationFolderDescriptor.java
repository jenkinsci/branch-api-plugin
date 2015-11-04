/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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
import hudson.ExtensionList;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.View;
import jenkins.scm.api.SCMNavigatorDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Ensures that when we have a non-null {@link SCMNavigatorDescriptor#newInstance(String)}, a special <b>New Item</b> entry is displayed instead of the generic {@link OrganizationFolder.DescriptorImpl}.
 */
@SuppressWarnings("rawtypes") // not our fault
@Restricted(NoExternalUse.class)
public class CustomOrganizationFolderDescriptor extends TopLevelItemDescriptor {

    public final SCMNavigatorDescriptor delegate;

    CustomOrganizationFolderDescriptor(SCMNavigatorDescriptor delegate) {
        super(TopLevelItem.class); // do not register as OrganizationFolder
        this.delegate = delegate;
    }
    
    @Override
    public String getId() {
        return OrganizationFolder.class.getName() + "." + delegate.getId(); // must be distinct from OrganizationFolder.DescriptorImpl
    }
    
    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public TopLevelItem newInstance(ItemGroup parent, String name) {
        OrganizationFolder p = new OrganizationFolder(parent, name);
        p.getNavigators().add(delegate.newInstance(name));
        return p;
    }

    // TODO better for DescriptorVisibilityFilter to allow items to be added as well as removed
    @SuppressWarnings("deprecation") // dynamic registration intentional here
    @Initializer(after=InitMilestone.PLUGINS_STARTED, before=InitMilestone.EXTENSIONS_AUGMENTED)
    public static void addSpecificDescriptors() {
        if (ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class).isEmpty()) {
            return; // nothing like workflow-multibranch installed, so do not even offer this option
        }
        TopLevelItemDescriptor.all().size(); // TODO must force ExtensionList.ensureLoaded to be called, else .add adds to both .legacyInstances and .extensions, then later .ensureLoaded adds two copies!
        for (SCMNavigatorDescriptor d : ExtensionList.lookup(SCMNavigatorDescriptor.class)) {
            if (d.newInstance((String) null) != null) {
                TopLevelItemDescriptor.all().add(new CustomOrganizationFolderDescriptor(d));
            }
        }
    }

    /**
     * Hides {@link OrganizationFolder.DescriptorImpl}.
     */
    @Extension
    public static class HideGeneric extends DescriptorVisibilityFilter {

        @Override
        public boolean filter(Object context, Descriptor descriptor) {
            if (descriptor instanceof OrganizationFolder.DescriptorImpl && context instanceof View) {
                return false;
            }
            return true;
        }

    }

}
