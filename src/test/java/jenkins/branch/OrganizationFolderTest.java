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

import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.model.View;
import jenkins.branch.harness.MultiBranchImpl;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.impl.SingleSCMNavigator;
import hudson.scm.NullSCM;
import hudson.util.RingBufferLogHandler;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import jenkins.scm.api.SCMSource;
import jenkins.scm.impl.SingleSCMSource;
import org.junit.Test;
import static org.junit.Assert.*;

import org.junit.Assert;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class OrganizationFolderTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    
    @Test
    public void configRoundTrip() throws Exception {
        OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
        List<MultiBranchProjectFactory> projectFactories = top.getProjectFactories();
        assertEquals(1, projectFactories.size());
        assertEquals(MockFactory.class, projectFactories.get(0).getClass());
        projectFactories.add(new MockFactory());
        top.getNavigators().add(new SingleSCMNavigator("stuff", Collections.<SCMSource>singletonList(new SingleSCMSource("id", "stuffy", new NullSCM()))));
        top = r.configRoundtrip(top);
        List<SCMNavigator> navigators = top.getNavigators();
        assertEquals(1, navigators.size());
        assertEquals(SingleSCMNavigator.class, navigators.get(0).getClass());
        assertEquals("stuff", ((SingleSCMNavigator) navigators.get(0)).getName());
        projectFactories = top.getProjectFactories();
        assertEquals(2, projectFactories.size());
        assertEquals(MockFactory.class, projectFactories.get(0).getClass());
        assertEquals(MockFactory.class, projectFactories.get(1).getClass());
    }

    @Test
    @Issue("JENKINS-31516")
    public void indexChildrenOnOrganizationFolderIndex() throws Exception {
        OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
        top.getNavigators().add(new SingleSCMNavigator("stuff", Collections.<SCMSource>singletonList(new SingleSCMSource("id", "stuffy", new NullSCM()))));
        top = r.configRoundtrip(top);

        RingBufferLogHandler logs = createJULTestHandler(); // switch to the mock log handler

        top.setDescription("Org folder test");
        top = r.configRoundtrip(top);
        waitForLogFileMessage("Indexing multibranch project: stuff", logs);
    }

    @Issue("JENKINS-32782")
    @Test
    public void emptyViewEquality() throws Exception {
        OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
        View emptyView = top.getPrimaryView();
        assertEquals("Welcome", emptyView.getViewName());
        assertEquals(emptyView, top.getPrimaryView());
        assertTrue(emptyView.isDefault());
    }

    @Issue("JENKINS-34246")
    @Test
    public void deletedMarker() throws Exception {
        OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
        List<MultiBranchProjectFactory> projectFactories = top.getProjectFactories();
        assertEquals(1, projectFactories.size());
        assertEquals(MockFactory.class, projectFactories.get(0).getClass());
        top.getNavigators().add(new SingleSCMNavigator("stuff", Collections.<SCMSource>singletonList(new SingleSCMSource("id", "stuffy", new NullSCM()))));
        top.scheduleBuild2(0).getFuture().get();
        top.getComputation().writeWholeLogTo(System.out);
        assertEquals(1, top.getItems().size());
        r.waitUntilNoActivity();
        MockFactory.live = false;
        try {
            top.scheduleBuild2(0).getFuture().get();
            top.getComputation().writeWholeLogTo(System.out);
            assertEquals(0, top.getItems().size());
        } finally {
            MockFactory.live = true;
        }
    }

    @TestExtension
    public static class ConfigRoundTripDescriptor extends MockFactoryDescriptor {}

    public static class MockFactory extends MultiBranchProjectFactory {
        @DataBoundConstructor
        public MockFactory() {}
        static boolean live = true;
        @Override
        public boolean recognizes(ItemGroup<?> parent, String name, List<? extends SCMSource> scmSources, Map<String, Object> attributes, TaskListener listener) throws IOException, InterruptedException {
            return live;
        }
        @Override
        public MultiBranchProject<?, ?> createNewProject(ItemGroup<?> parent, String name, List<? extends SCMSource> scmSources, Map<String,Object> attributes, TaskListener listener) throws IOException, InterruptedException {
            return new MultiBranchImpl(parent, name);
        }
    }
    static abstract class MockFactoryDescriptor extends MultiBranchProjectFactoryDescriptor {
        MockFactoryDescriptor() {
            super(MockFactory.class);
        }
        @Override
        public MultiBranchProjectFactory newInstance() {
            return new MockFactory();
        }
        @Override
        public String getDisplayName() {
            return "MockFactory";
        }
    }

    private RingBufferLogHandler createJULTestHandler() throws SecurityException, IOException {
        RingBufferLogHandler handler = new RingBufferLogHandler();
        SimpleFormatter formatter = new SimpleFormatter();
        handler.setFormatter(formatter);
        Logger logger = Logger.getLogger(MultiBranchImpl.class.getName());
        logger.addHandler(handler);
        return handler;
    }

    private void waitForLogFileMessage(String string, RingBufferLogHandler logs) throws IOException, InterruptedException {
        File rootDir = r.jenkins.getRootDir();
        synchronized (rootDir) {
            int limit = 0;
            while (limit < 5) {
                rootDir.wait(1000);
                for (LogRecord r : logs.getView()) {
                    if (r.getMessage().contains(string)) {
                        return;
                    }
                }
                limit++;
            }
        }
        Assert.assertTrue("Expected log not found: " + string, false);
    }

}
