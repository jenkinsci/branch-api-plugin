/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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

import com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger;
import hudson.model.TopLevelItem;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import integration.harness.BasicMultiBranchProject;
import integration.harness.BasicMultiBranchProjectFactory;
import integration.harness.BasicSCMSourceCriteria;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jenkins.scm.api.SCMSource;
import jenkins.scm.impl.SingleSCMNavigator;
import jenkins.scm.impl.SingleSCMSource;
import jenkins.scm.impl.mock.MockSCM;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMHead;
import jenkins.scm.impl.mock.MockSCMNavigator;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;

public class OrganizationChildTriggersPropertyTest {
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Before
    public void cleanOutAllItems() throws Exception {
        for (TopLevelItem i : r.getInstance().getItems()) {
            i.delete();
        }
    }

    @Test
    public void configRoundTrip() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("stuff");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "top");
            List<MultiBranchProjectFactory> projectFactories = prj.getProjectFactories();
            assertEquals(1, projectFactories.size());
            assertEquals(OrganizationFolderTest.MockFactory.class, projectFactories.get(0).getClass());
            projectFactories.add(new OrganizationFolderTest.MockFactory());
            prj.getNavigators().add(new SingleSCMNavigator("stuff",
                    Collections.<SCMSource>singletonList(new SingleSCMSource("id", "stuffy",
                            new MockSCM(c, "stuff", new MockSCMHead("master"), null))))
            );
            prj.getProperties().remove(OrganizationChildTriggersProperty.class);
            prj.addProperty(new OrganizationChildTriggersProperty(new PeriodicFolderTrigger("2d")));
            prj = r.configRoundtrip(prj);
            OrganizationChildTriggersProperty property =
                    prj.getProperties().get(OrganizationChildTriggersProperty.class);
            assertThat(property.getTemplates(), contains(instanceOf(PeriodicFolderTrigger.class)));
            PeriodicFolderTrigger trigger = (PeriodicFolderTrigger) property.getTemplates().get(0);
            assertThat(trigger.getInterval(), is("2d"));
        }
    }

    @Test
    public void given__orgFolder__when__created__then__child_triggers_default_to_1d() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            OrganizationChildTriggersProperty property = prj.getProperties().get(OrganizationChildTriggersProperty.class);
            assertThat("The property is created by default", property, notNullValue());
            assertThat("The child has a 1 day periodic scan by default",
                    property.getTemplates(),
                    contains(
                            Matchers.allOf(
                                    instanceOf(PeriodicFolderTrigger.class),
                                    hasProperty("interval", is("1d"))
                            )
                    )
            );
        }
    }

    @Test
    public void given__orgFolder__when__scan__then__child_triggers_applied() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.addFile("foo", "master", "adding marker", "marker.txt", "A marker".getBytes());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            BasicMultiBranchProject foo = (BasicMultiBranchProject) prj.getItem("foo");
            assertThat("We now have the child", foo, notNullValue());
            Map<TriggerDescriptor, Trigger<?>> triggers = foo.getTriggers();
            assertThat("The trigger is created", triggers.values(), contains(Matchers.allOf(
                    instanceOf(PeriodicFolderTrigger.class),
                    hasProperty("interval", is("1d"))
                    )
            ));
        }
    }

    @Test
    public void given__orgFolder_property_changed__when__then__scan__child_triggers_updated() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.addFile("foo", "master", "adding marker", "marker.txt", "A marker".getBytes());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            BasicMultiBranchProject foo = (BasicMultiBranchProject) prj.getItem("foo");
            assertThat("We now have the child", foo, notNullValue());
            Map<TriggerDescriptor, Trigger<?>> triggers = foo.getTriggers();
            assertThat("The trigger is created", triggers.values(), contains(Matchers.allOf(
                    instanceOf(PeriodicFolderTrigger.class),
                    hasProperty("interval", is("1d"))
                    )
            ));
            prj.getProperties().remove(OrganizationChildTriggersProperty.class);
            prj.addProperty(new OrganizationChildTriggersProperty(new PeriodicFolderTrigger("3d")));
            triggers = foo.getTriggers();
            assertThat("The trigger is not updated before a scan", triggers.values(), contains(Matchers.allOf(
                    instanceOf(PeriodicFolderTrigger.class),
                    hasProperty("interval", is("1d"))
                    )
            ));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            triggers = foo.getTriggers();
            assertThat("The trigger is updated after a scan", triggers.values(), contains(Matchers.allOf(
                    instanceOf(PeriodicFolderTrigger.class),
                    hasProperty("interval", is("3d"))
                    )
            ));
        }
    }

    @TestExtension
    public static class ConfigRoundTripDescriptor extends OrganizationFolderTest.MockFactoryDescriptor {
    }
}
