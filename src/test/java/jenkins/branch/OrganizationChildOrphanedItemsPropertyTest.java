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

import com.cloudbees.hudson.plugins.folder.computed.DefaultOrphanedItemStrategy;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.TopLevelItem;
import integration.harness.BasicMultiBranchProject;
import integration.harness.BasicMultiBranchProjectFactory;
import integration.harness.BasicSCMSourceCriteria;
import java.util.Collections;
import java.util.List;
import jenkins.scm.api.SCMSource;
import jenkins.scm.impl.SingleSCMNavigator;
import jenkins.scm.impl.SingleSCMSource;
import jenkins.scm.impl.mock.MockSCM;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMHead;
import jenkins.scm.impl.mock.MockSCMNavigator;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class OrganizationChildOrphanedItemsPropertyTest {
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Before
    public void cleanOutAllItems() throws Exception {
        for (TopLevelItem i : r.getInstance().getItems()) {
            i.delete();
        }
        for (Computer comp : r.jenkins.getComputers()) {
            for (Executor e : comp.getExecutors()) {
                if (e.getCauseOfDeath() != null) {
                    e.doYank();
                }
            }
            for (Executor e : comp.getOneOffExecutors()) {
                if (e.getCauseOfDeath() != null) {
                    e.doYank();
                }
            }
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
            prj.getProperties().remove(OrganizationChildOrphanedItemsProperty.class);
            prj.addProperty(new OrganizationChildOrphanedItemsProperty(new DefaultOrphanedItemStrategy(true,5,7)));
            prj = r.configRoundtrip(prj);
            OrganizationChildOrphanedItemsProperty property =
                    prj.getProperties().get(OrganizationChildOrphanedItemsProperty.class);
            assertThat(property.getStrategy(), instanceOf(DefaultOrphanedItemStrategy.class));
            DefaultOrphanedItemStrategy strategy = (DefaultOrphanedItemStrategy) property.getStrategy();
            assertThat(strategy.isPruneDeadBranches(), is(true));
            assertThat(strategy.getDaysToKeep(), is(5));
            assertThat(strategy.getNumToKeep(), is(7));
        }
    }

    @Test
    public void given__orgFolder__when__created__then__proprerty_is_same_as_folder() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            OrganizationChildOrphanedItemsProperty property =
                    prj.getProperties().get(OrganizationChildOrphanedItemsProperty.class);
            assertThat(property, notNullValue());
            assertThat(property.getStrategy(), instanceOf(OrganizationChildOrphanedItemsProperty.Inherit.class));
        }
    }

    @Test
    public void given__orgFolder__when__scan__then__child_strategy_applied() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.addFile("foo", "master", "adding marker", "marker.txt", "A marker".getBytes());
            prj.getProperties().remove(OrganizationChildOrphanedItemsProperty.class);
            prj.addProperty(new OrganizationChildOrphanedItemsProperty(new DefaultOrphanedItemStrategy(true, 5, 7)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            BasicMultiBranchProject foo = (BasicMultiBranchProject) prj.getItem("foo");
            assertThat("We now have the child", foo, notNullValue());
            assertThat(foo.getOrphanedItemStrategy(), instanceOf(DefaultOrphanedItemStrategy.class));
            DefaultOrphanedItemStrategy strategy = (DefaultOrphanedItemStrategy) foo.getOrphanedItemStrategy();
            assertThat(strategy.isPruneDeadBranches(), is(true));
            assertThat(strategy.getDaysToKeep(), is(5));
            assertThat(strategy.getNumToKeep(), is(7));
        }
    }

    @Test
    public void given__same_as_parent__when__scan__then__parent_strategy_applied() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
            prj.getProjectFactories().replaceBy(Collections
                    .singletonList(new BasicMultiBranchProjectFactory(new BasicSCMSourceCriteria("marker.txt"))));
            c.createRepository("foo");
            c.addFile("foo", "master", "adding marker", "marker.txt", "A marker".getBytes());
            prj.setOrphanedItemStrategy(new DefaultOrphanedItemStrategy(true, 5, 7));
            prj.getProperties().remove(OrganizationChildOrphanedItemsProperty.class);
            prj.addProperty(OrganizationChildOrphanedItemsProperty.newDefaultInstance());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            BasicMultiBranchProject foo = (BasicMultiBranchProject) prj.getItem("foo");
            assertThat("We now have the child", foo, notNullValue());
            assertThat(foo.getOrphanedItemStrategy(), instanceOf(DefaultOrphanedItemStrategy.class));
            DefaultOrphanedItemStrategy strategy = (DefaultOrphanedItemStrategy) foo.getOrphanedItemStrategy();
            assertThat(strategy.isPruneDeadBranches(), is(true));
            assertThat(strategy.getDaysToKeep(), is(5));
            assertThat(strategy.getNumToKeep(), is(7));
        }
    }

    @Test
    public void given__orgFolder_property_changed__when__scan__then_child_strategy_updated() throws Exception {
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
            assertThat(foo.getOrphanedItemStrategy(), instanceOf(DefaultOrphanedItemStrategy.class));
            DefaultOrphanedItemStrategy strategy = (DefaultOrphanedItemStrategy) foo.getOrphanedItemStrategy();
            assertThat("Initial default strategy", strategy.isPruneDeadBranches(), is(true));
            assertThat("Initial default strategy", strategy.getDaysToKeep(), is(-1));
            assertThat("Initial default strategy", strategy.getNumToKeep(), is(-1));
            prj.getProperties().remove(OrganizationChildOrphanedItemsProperty.class);
            prj.addProperty(new OrganizationChildOrphanedItemsProperty(new DefaultOrphanedItemStrategy(true, 5, 7)));
            assertThat(foo.getOrphanedItemStrategy(), instanceOf(DefaultOrphanedItemStrategy.class));
            strategy = (DefaultOrphanedItemStrategy) foo.getOrphanedItemStrategy();
            assertThat("Not updated before scan", strategy.isPruneDeadBranches(), is(true));
            assertThat("Not updated before scan", strategy.getDaysToKeep(), is(-1));
            assertThat("Not updated before scan", strategy.getNumToKeep(), is(-1));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            strategy = (DefaultOrphanedItemStrategy) foo.getOrphanedItemStrategy();
            assertThat("Updated after scan", strategy.isPruneDeadBranches(), is(true));
            assertThat("Updated after scan", strategy.getDaysToKeep(), is(5));
            assertThat("Updated after scan", strategy.getNumToKeep(), is(7));
        }
    }

    @TestExtension
    public static class ConfigRoundTripDescriptor extends OrganizationFolderTest.MockFactoryDescriptor {
    }
}
