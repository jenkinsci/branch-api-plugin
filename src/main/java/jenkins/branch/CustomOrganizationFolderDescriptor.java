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
import hudson.ExtensionListListener;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.View;
import hudson.model.ViewGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.api.SCMNavigatorDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Ensures that when we have a non-null {@link SCMNavigatorDescriptor#newInstance(String)}, a special <b>New Item</b> entry is displayed instead of the generic {@link OrganizationFolder.DescriptorImpl}.
 */
@SuppressWarnings("rawtypes") // not our fault
@Restricted(NoExternalUse.class)
public class CustomOrganizationFolderDescriptor extends TopLevelItemDescriptor {

    private static final Logger LOGGER = Logger.getLogger(CustomOrganizationFolderDescriptor.class.getName());

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

    /**
     * Needed if it wants SCMNavigator implementations are categorized in Jenkins 2.x.
     *
     * TODO: Override when the baseline is upgraded to 2.x
     *
     * @return A string with the Item description.
     */
    public String getDescription() {
        return delegate.getDescription();
    }

    /**
     * Needed if it wants SCMNavigator implementations are categorized in Jenkins 2.x.
     *
     * TODO: Override when the baseline is upgraded to 2.x
     *
     * @return A string it represents a ItemCategory identifier.
     */
    public String getCategoryId() {
        return delegate.getCategoryId();
    }

    /**
     * Needed if it wants SCMNavigator implementations are categorized in Jenkins 2.x.
     *
     * TODO: Override when the baseline is upgraded to 2.x
     *
     * @return A string it represents a URL pattern to get the Item icon in different sizes.
     */
    public String getIconFilePathPattern() {
        return delegate.getIconFilePathPattern();
    }

    @Override
    public TopLevelItem newInstance(ItemGroup parent, String name) {
        OrganizationFolder p = new OrganizationFolder(parent, name);
        p.getNavigators().add(delegate.newInstance(name));
        return p;
    }

    @Override
    public String toString() {
        return "CustomOrganizationFolderDescriptor[" + delegate.getId() + "]";
    }

    // TODO HACK ALERT! better for DescriptorVisibilityFilter to allow items to be added as well as removed; or consider using ExtensionFinder

    @Initializer(after=InitMilestone.PLUGINS_STARTED, before=InitMilestone.EXTENSIONS_AUGMENTED)
    public static void addSpecificDescriptors() {
        LOGGER.fine("ran addSpecificDescriptors");
        doAddSpecificDescriptors();
        ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class).addListener(new ListenerImpl());
    }

    private static class ListenerImpl extends ExtensionListListener {

        @Override
        public void onChange() {
            doAddSpecificDescriptors();
        }

    }

    @SuppressWarnings("deprecation") // dynamic registration intentional here
    private static void doAddSpecificDescriptors() {
        LOGGER.fine("ran doAddSpecificDescriptors");
        List<CustomOrganizationFolderDescriptor> old = new ArrayList<>();
        for (TopLevelItemDescriptor d : all()) {
            if (d instanceof CustomOrganizationFolderDescriptor) {
                old.add((CustomOrganizationFolderDescriptor) d);
            }
        }
        LOGGER.log(Level.FINE, "clearing {0}", old);
        all().removeAll(old);
        if (ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class).isEmpty()) {
            LOGGER.fine("no MultiBranchProjectFactoryDescriptor");
            return; // nothing like workflow-multibranch installed, so do not even offer this option
        }
        TopLevelItemDescriptor.all().size(); // TODO must force ExtensionList.ensureLoaded to be called, else .add adds to both .legacyInstances and .extensions, then later .ensureLoaded adds two copies!
        for (SCMNavigatorDescriptor d : ExtensionList.lookup(SCMNavigatorDescriptor.class)) {
            if (d.newInstance((String) null) != null) {
                LOGGER.log(Level.FINE, "adding {0}", d.getId());
                TopLevelItemDescriptor.all().add(new CustomOrganizationFolderDescriptor(d));
            }
        }
        LOGGER.fine("done");
    }

    /**
     * Hides {@link OrganizationFolder.DescriptorImpl}.
     */
    @Extension
    public static class HideGeneric extends DescriptorVisibilityFilter {

        @Override
        public boolean filter(Object context, Descriptor descriptor) {
            LOGGER.log(Level.FINER, "filtering {0}", descriptor.getId());
            if (descriptor instanceof OrganizationFolder.DescriptorImpl && (context instanceof View || context instanceof ViewGroup)) {
                return false;
            }
            return true;
        }

    }

}
