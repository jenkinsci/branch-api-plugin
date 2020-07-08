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

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.ExtensionList;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Items;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.View;
import integration.harness.BasicMultiBranchProjectFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.impl.SingleSCMNavigator;
import jenkins.scm.impl.mock.MockSCMNavigator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

public class CustomOrganizationFolderDescriptorTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public LoggerRule logger = new LoggerRule().record(CustomOrganizationFolderDescriptor.class, Level.ALL);

    @Before
    public void removeIntegrationTestMocks() throws Exception {
        assertTrue(ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class).remove(ExtensionList.lookup(
                MultiBranchProjectFactoryDescriptor.class).get(
                BasicMultiBranchProjectFactory.DescriptorImpl.class)));
        assertTrue(ExtensionList.lookup(SCMNavigatorDescriptor.class)
                .remove(ExtensionList.lookup(SCMNavigatorDescriptor.class).get(
                        MockSCMNavigator.DescriptorImpl.class)));
    }

    @Test
    public void noNavigatorNoFactoryInstalled() throws Exception {
        assumeThat(ExtensionList.lookup(SCMNavigatorDescriptor.class), hasSize(1));
        assumeThat(ExtensionList.lookup(SCMNavigatorDescriptor.class).get(0).getClass(), is(SingleSCMNavigator.DescriptorImpl.class));
        assumeThat(ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class), is(empty()));
        assertThat(newItemTypes(), containsInAnyOrder());
    }

    @Test
    public void someNavigatorNoFactoryInstalled() throws Exception {
        assumeThat(ExtensionList.lookup(SCMNavigatorDescriptor.class), hasSize(2));
        assumeThat(ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class), is(empty()));
        assertThat(newItemTypes(), containsInAnyOrder());
    }
    @TestExtension("someNavigatorNoFactoryInstalled")
    public static class SomeNavigatorNoFactoryInstalledDescriptor extends MockNavigatorDescriptor {}

    @Test
    public void noNavigatorSomeFactoryInstalled() throws Exception {
        assumeThat(ExtensionList.lookup(SCMNavigatorDescriptor.class), hasSize(1));
        assumeThat(ExtensionList.lookup(SCMNavigatorDescriptor.class).get(0).getClass(), is(SingleSCMNavigator.DescriptorImpl.class));
        assumeThat(ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class), hasSize(1));
        assertThat(newItemTypes(), containsInAnyOrder());
    }
    @TestExtension("noNavigatorSomeFactoryInstalled")
    public static class NoNavigatorSomeFactoryInstalledDescriptor extends OrganizationFolderTest.MockFactoryDescriptor {}

    @Test
    public void someNavigatorSomeFactoryInstalled() throws Exception {
        assumeThat(ExtensionList.lookup(SCMNavigatorDescriptor.class), hasSize(2));
        assumeThat(ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class), hasSize(1));
        assertThat(newItemTypes(), containsInAnyOrder("MockNavigator"));
    }
    @TestExtension("someNavigatorSomeFactoryInstalled")
    public static class SomeNavigatorSomeFactoryInstalledDescriptor1 extends MockNavigatorDescriptor {}
    @TestExtension("someNavigatorSomeFactoryInstalled")
    public static class SomeNavigatorSomeFactoryInstalledDescriptor2 extends OrganizationFolderTest.MockFactoryDescriptor {}

    @Issue("JENKINS-33106")
    @SuppressWarnings("deprecation") // ExtensionList.add simulating dynamic installation
    @Test
    public void dynamicLoad() throws Exception {
        assertEquals(Collections.emptyList(), newItemTypes());
        ExtensionList.lookup(SCMNavigatorDescriptor.class).add(new SomeNavigatorSomeFactoryInstalledDescriptor1());
        ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class).add(new SomeNavigatorSomeFactoryInstalledDescriptor2());
        assertThat(newItemTypes(), containsInAnyOrder("MockNavigator"));
    }

    @Issue("JENKINS-34239")
    @SuppressWarnings("deprecation") // ExtensionList.add simulating dynamic installation
    @Test
    public void dynamicLoadReversed() throws Exception {
        assertEquals(Collections.emptyList(), newItemTypes());
        ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class).add(new SomeNavigatorSomeFactoryInstalledDescriptor2());
        ExtensionList.lookup(SCMNavigatorDescriptor.class).add(new SomeNavigatorSomeFactoryInstalledDescriptor1());
        assertThat(newItemTypes(), containsInAnyOrder("MockNavigator"));
    }

    @Issue("JENKINS-39520")
    @SuppressWarnings("deprecation") // ExtensionList.add simulating dynamic installation
    @Test
    public void dynamicLoad2() throws Exception {
        assumeThat(CustomOrganizationFolderDescriptor.SHOW_GENERIC, is(true));
        assertEquals(Collections.emptyList(), newItemTypes());
        ExtensionList.lookup(SCMNavigatorDescriptor.class).add(new SomeNavigatorNoFactoryInstalledDescriptor());
        ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class).add(new SomeNavigatorSomeFactoryInstalledDescriptor2());
        ExtensionList.lookup(SCMNavigatorDescriptor.class).add(new SomeNavigatorSomeFactoryInstalledDescriptor1());
        assertThat("When there is more than one navigator then the generic organization folder makes sense to show",
                newItemTypes(), containsInAnyOrder("MockNavigator", "MockNavigator", "Organization Folder"));
    }

    @Issue("JENKINS-41171")
    @SuppressWarnings("deprecation") // ExtensionList.add simulating dynamic installation
    @Test
    public void dynamicLoad3() throws Exception {
        assumeThat(CustomOrganizationFolderDescriptor.SHOW_GENERIC, is(false));
        assertEquals(Collections.emptyList(), newItemTypes());
        ExtensionList.lookup(SCMNavigatorDescriptor.class).add(new SomeNavigatorNoFactoryInstalledDescriptor());
        ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class).add(new SomeNavigatorSomeFactoryInstalledDescriptor2());
        ExtensionList.lookup(SCMNavigatorDescriptor.class).add(new SomeNavigatorSomeFactoryInstalledDescriptor1());
        assertThat("When there is more than one navigator then the generic organization folder makes sense to show",
                newItemTypes(), containsInAnyOrder("MockNavigator", "MockNavigator"));
    }

    @Issue("JENKINS-31949")
    @Test
    public void insideFolder() throws Exception {
        Folder folder = r.jenkins.createProject(Folder.class, "d");
        List<String> names = new ArrayList<>();
        for (TopLevelItemDescriptor d : DescriptorVisibilityFilter.apply(folder, Items.all())) {
            if (d.clazz == OrganizationFolder.class || d instanceof CustomOrganizationFolderDescriptor) {
                names.add(d.getDisplayName());
            }
        }
        assertThat(names, containsInAnyOrder());
    }

    private static class MockNavigator extends SCMNavigator {
        @Override
        protected String id() {
            return "mock";
        }

        @Override
        public void visitSources(SCMSourceObserver observer) throws IOException, InterruptedException {}
    }
    private static abstract class MockNavigatorDescriptor extends SCMNavigatorDescriptor {

        MockNavigatorDescriptor() {
            super(MockNavigator.class);
        }

        @Override
        public SCMNavigator newInstance(String name) {
            return new MockNavigator();
        }

        @Override
        public String getDisplayName() {
            return "MockNavigator";
        }

    }

    private List<String> newItemTypes() {
        assertEquals(OrganizationFolder.DescriptorImpl.class, r.jenkins.getDescriptor(OrganizationFolder.class).getClass());
        View allView = r.jenkins.getView("All");
        assertNotNull(allView);
        // Cf. View/newJob.jelly:
        List<String> names = new ArrayList<>();
        for (TopLevelItemDescriptor d : DescriptorVisibilityFilter.apply(allView, Items.all())) {
            if (d.clazz == OrganizationFolder.class || d instanceof CustomOrganizationFolderDescriptor) {
                names.add(d.getDisplayName());
            }
        }
        return names;
    }

}