/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
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
 *
 */

package jenkins.branch;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.StringParameterDefinition;
import hudson.model.TopLevelItem;
import integration.harness.BasicDummyStepBranchProperty;
import integration.harness.BasicMultiBranchProject;
import java.util.Collections;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMSource;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class ParameterDefinitionBranchPropertyTest {
    /**
     * All tests in this class only create items and do not affect other global configuration, thus we trade test
     * execution time for the restriction on only touching items.
     */
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Before
    public void cleanOutAllItems() throws Exception {
        for (TopLevelItem i : r.getInstance().getItems()) {
            i.delete();
        }
    }

    @Test
    public void getSetParameterDefinitions() throws Exception {
        ParameterDefinitionBranchPropertyImpl instance = new ParameterDefinitionBranchPropertyImpl();
        instance.setParameterDefinitions(Collections.<ParameterDefinition>singletonList(
                new StringParameterDefinition("PARAM_STR", "PARAM_DEFAULT_0812673", "The param")
        ));
        assertThat(instance.getParameterDefinitions(), contains(
                allOf(
                        instanceOf(StringParameterDefinition.class),
                        hasProperty("name", is("PARAM_STR")),
                        hasProperty("defaultValue", is("PARAM_DEFAULT_0812673")),
                        hasProperty("description", is("The param"))
                )
        ));

    }

    @Test
    public void isApplicable_Job() throws Exception {
        assertThat("Jobs are not parameterized",
                new ParameterDefinitionBranchPropertyImpl().isApplicable(Job.class), is(false));

    }

    @Test
    public void isApplicable_Job_implementing_ParameterizedJob() throws Exception {
        assertThat("Parameterized Jobs are are Applicable",
                new ParameterDefinitionBranchPropertyImpl().isApplicable(ParamJob.class), is(true));

    }

    public static abstract class ParamJob extends Job implements ParameterizedJobMixIn.ParameterizedJob {
        protected ParamJob(ItemGroup parent, String name) {
            super(parent, name);
        }
    }

    @Test
    public void jobDecorator_Job() throws Exception {
        assertThat(new ParameterDefinitionBranchPropertyImpl().jobDecorator(Job.class), nullValue());
    }

    @Test
    public void jobDecorator_Job_implementing_ParameterizedJob() throws Exception {
        assertThat(new ParameterDefinitionBranchPropertyImpl().jobDecorator(ParamJob.class), notNullValue());
    }

    @Test
    public void configRoundtrip() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            BranchSource source = new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches()));
            ParameterDefinitionBranchPropertyImpl instance = new ParameterDefinitionBranchPropertyImpl();
            instance.setParameterDefinitions(Collections.<ParameterDefinition>singletonList(
                    new StringParameterDefinition("PARAM_STR", "PARAM_DEFAULT_0812673", "The param")
            ));
            source.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[]{
                    instance, new BasicDummyStepBranchProperty()
            }));
            prj.getSourcesList().add(source);
            r.configRoundtrip(prj);
            assertThat(prj.getSources().get(0).getStrategy(), instanceOf(DefaultBranchPropertyStrategy.class));
            DefaultBranchPropertyStrategy strategy =
                    (DefaultBranchPropertyStrategy) prj.getSources().get(0).getStrategy();
            assertThat(strategy.getProps().get(0), instanceOf(ParameterDefinitionBranchPropertyImpl.class));
            ParameterDefinitionBranchPropertyImpl property = (ParameterDefinitionBranchPropertyImpl) strategy.getProps().get(0);
            assertThat(property.getParameterDefinitions(), contains(
                    allOf(
                            instanceOf(StringParameterDefinition.class),
                            hasProperty("name", is("PARAM_STR")),
                            hasProperty("defaultValue", is("PARAM_DEFAULT_0812673")),
                            hasProperty("description", is("The param"))
                    )
            ));
        }
    }

    @Test
    public void parametersAreConfiguredOnBranchJobs() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            BranchSource source = new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches()));
            ParameterDefinitionBranchPropertyImpl instance = new ParameterDefinitionBranchPropertyImpl();
            instance.setParameterDefinitions(Collections.<ParameterDefinition>singletonList(
                    new StringParameterDefinition("PARAM_STR", "PARAM_DEFAULT_0812673", "The param")
            ));
            source.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[]{
                    instance, new BasicDummyStepBranchProperty()
            }));
            prj.getSourcesList().add(source);
            prj.scheduleBuild(0);
            r.waitUntilNoActivity();
            r.assertLogContains("PARAM_STR=PARAM_DEFAULT_0812673", prj.getItem("master").getLastBuild());
        }
    }

    public static class ParameterDefinitionBranchPropertyImpl extends ParameterDefinitionBranchProperty {

        @DataBoundConstructor
        public ParameterDefinitionBranchPropertyImpl() {
            super();
        }

        @TestExtension
        public static class DescriptorImpl extends BranchPropertyDescriptor {
            @Override
            protected boolean isApplicable(@NonNull MultiBranchProjectDescriptor projectDescriptor) {
                return projectDescriptor instanceof BasicMultiBranchProject.DescriptorImpl;
            }
        }
    }
}
