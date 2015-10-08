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
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.impl.SingleSCMNavigator;
import hudson.scm.NullSCM;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jenkins.scm.api.SCMSource;
import jenkins.scm.impl.SingleSCMSource;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class OrganizationFolderTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    
    @Test public void configRoundTrip() throws Exception {
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
    @TestExtension("configRoundTrip") public static class ConfigRoundTripDescriptor extends MockFactoryDescriptor {}

    public static class MockFactory extends MultiBranchProjectFactory {
        @DataBoundConstructor public MockFactory() {}
        @Override public MultiBranchProject<?, ?> createProject(ItemGroup<?> parent, String name, List<? extends SCMSource> scmSources, Map<String,Object> attributes, TaskListener listener) throws IOException, InterruptedException {
            return null;
        }
    }
    static abstract class MockFactoryDescriptor extends MultiBranchProjectFactoryDescriptor {
        MockFactoryDescriptor() {
            super(MockFactory.class);
        }
        @Override public MultiBranchProjectFactory newInstance() {
            return new MockFactory();
        }
        @Override public String getDisplayName() {
            return "MockFactory";
        }
    }

}
