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

import hudson.ExtensionList;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Items;
import hudson.model.TaskListener;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.View;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.impl.SingleSCMNavigator;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class SpecificDescriptorTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    
    @Test public void noNavigatorNoFactoryInstalled() throws Exception {
        assertEquals(1, ExtensionList.lookup(SCMNavigatorDescriptor.class).size());
        assertEquals(SingleSCMNavigator.DescriptorImpl.class, ExtensionList.lookup(SCMNavigatorDescriptor.class).get(0).getClass());
        assertEquals(0, ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class).size());
        assertEquals(Collections.emptyList(), newItemTypes());
    }

    @Test public void someNavigatorNoFactoryInstalled() throws Exception {
        assertEquals(2, ExtensionList.lookup(SCMNavigatorDescriptor.class).size());
        assertEquals(0, ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class).size());
        assertEquals(Collections.emptyList(), newItemTypes());
    }
    @TestExtension("someNavigatorNoFactoryInstalled") public static class SomeNavigatorNoFactoryInstalledDescriptor extends MockNavigatorDescriptor {}

    @Test public void noNavigatorSomeFactoryInstalled() throws Exception {
        assertEquals(1, ExtensionList.lookup(SCMNavigatorDescriptor.class).size());
        assertEquals(SingleSCMNavigator.DescriptorImpl.class, ExtensionList.lookup(SCMNavigatorDescriptor.class).get(0).getClass());
        assertEquals(1, ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class).size());
        assertEquals(Collections.emptyList(), newItemTypes());
    }
    @TestExtension("noNavigatorSomeFactoryInstalled") public static class NoNavigatorSomeFactoryInstalledDescriptor extends OrganizationFolderTest.MockFactoryDescriptor {}

    @Test public void someNavigatorSomeFactoryInstalled() throws Exception {
        assertEquals(2, ExtensionList.lookup(SCMNavigatorDescriptor.class).size());
        assertEquals(1, ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class).size());
        assertEquals(Collections.singletonList("MockNavigator"), newItemTypes());
    }
    @TestExtension("someNavigatorSomeFactoryInstalled") public static class SomeNavigatorSomeFactoryInstalledDescriptor1 extends MockNavigatorDescriptor {}
    @TestExtension("someNavigatorSomeFactoryInstalled") public static class SomeNavigatorSomeFactoryInstalledDescriptor2 extends OrganizationFolderTest.MockFactoryDescriptor {}

    private static class MockNavigator extends SCMNavigator {
        @Override public Map<String, ? extends List<? extends SCMSource>> discoverSources(SCMSourceOwner context, TaskListener listener) throws IOException, InterruptedException {
            return Collections.emptyMap();
        }
    }
    private static abstract class MockNavigatorDescriptor extends SCMNavigatorDescriptor {
        MockNavigatorDescriptor() {
            super(MockNavigator.class);
        }
        @Override public SCMNavigator newInstance(String name) {
            return new MockNavigator();
        }
        @Override public String getDisplayName() {
            return "MockNavigator";
        }
    }

    private List<String> newItemTypes() {
        assertEquals(OrganizationFolder.DescriptorImpl.class, r.jenkins.getDescriptor(OrganizationFolder.class).getClass());
        View allView = r.jenkins.getView("All");
        assertNotNull(allView);
        // Cf. View/newJob.jelly:
        List<String> names = new ArrayList<String>();
        for (TopLevelItemDescriptor d : DescriptorVisibilityFilter.apply(allView, Items.all())) {
            if (d.clazz == OrganizationFolder.class || d instanceof SpecificDescriptor) {
                names.add(d.getDisplayName());
            }
        }
        return names;
    }

}
