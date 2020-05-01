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

import com.cloudbees.hudson.plugins.folder.computed.ChildObserver;
import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import hudson.ExtensionList;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ParameterDefinition;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.TaskListener;
import hudson.model.View;
import hudson.scm.NullSCM;
import hudson.security.Permission;
import integration.harness.BasicMultiBranchProject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jenkins.branch.harness.MultiBranchImpl;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMSource;
import jenkins.scm.impl.SingleSCMNavigator;
import jenkins.scm.impl.SingleSCMSource;
import jenkins.scm.impl.mock.MockSCM;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMDiscoverChangeRequests;
import jenkins.scm.impl.mock.MockSCMDiscoverTags;
import jenkins.scm.impl.mock.MockSCMHead;
import jenkins.scm.impl.mock.MockSCMNavigator;
import org.acegisecurity.Authentication;
import org.acegisecurity.providers.TestingAuthenticationToken;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import static jenkins.branch.matchers.Extracting.extracting;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OrganizationFolderTest {

    public static class OrganizationFolderBranchProperty extends ParameterDefinitionBranchProperty {

        @DataBoundConstructor
        public OrganizationFolderBranchProperty() {
            super();
        }

        @TestExtension
        public static class DescriptorImpl extends BranchPropertyDescriptor {
            @Override
            protected boolean isApplicable(MultiBranchProjectDescriptor projectDescriptor) {
                return projectDescriptor instanceof BasicMultiBranchProject.DescriptorImpl;
            }
        }
    }

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void configRoundTrip() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("stuff");
            OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
            List<MultiBranchProjectFactory> projectFactories = top.getProjectFactories();
            assertThat(projectFactories, extracting(f -> f.getDescriptor(), hasItem(ExtensionList.lookupSingleton(ConfigRoundTripDescriptor.class))));
            projectFactories.add(new MockFactory());
            top.getNavigators().add(new SingleSCMNavigator("stuff",
                    Collections.<SCMSource>singletonList(new SingleSCMSource("id", "stuffy",
                            new MockSCM(c, "stuff", new MockSCMHead("master"), null))))
            );
            top = r.configRoundtrip(top);
            List<SCMNavigator> navigators = top.getNavigators();
            assertEquals(1, navigators.size());
            assertEquals(SingleSCMNavigator.class, navigators.get(0).getClass());
            assertEquals("stuff", ((SingleSCMNavigator) navigators.get(0)).getName());
            projectFactories = top.getProjectFactories();
            assertThat(projectFactories, extracting(f -> f.getDescriptor(), hasItems(ExtensionList.lookupSingleton(ConfigRoundTripDescriptor.class), ExtensionList.lookupSingleton(ConfigRoundTripDescriptor.class))));
        }
    }

    @Issue("JENKINS-48837")
    @Test
    public void verifyBranchPropertiesAppliedOnNewProjects() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("stuff");
            OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
            List<MultiBranchProjectFactory> projectFactories = top.getProjectFactories();
            assertEquals(1, projectFactories.size());
            assertEquals(MockFactory.class, projectFactories.get(0).getClass());
            top.getNavigators().add(new SingleSCMNavigator("stuff",
                    Collections.<SCMSource>singletonList(new SingleSCMSource("stuffy",
                            new MockSCM(c, "stuff", new MockSCMHead("master"), null))))
                    );
            OrganizationFolderBranchProperty instance = new OrganizationFolderBranchProperty();
            instance.setParameterDefinitions(Collections.<ParameterDefinition>singletonList(
                    new StringParameterDefinition("PARAM_STR", "PARAM_DEFAULT_0812673", "The param")
                    ));
            top.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[] { instance }));
            top = r.configRoundtrip(top);

            top.scheduleBuild(0);
            r.waitUntilNoActivity();

            // get the child project produced by the factory after scan
            MultiBranchImpl prj = (MultiBranchImpl) top.getItem("stuff");
            // verify new multibranch project have branch properties inherited from folder
            assertThat(prj.getSources().get(0).getStrategy(), instanceOf(DefaultBranchPropertyStrategy.class));
            DefaultBranchPropertyStrategy strategy = (DefaultBranchPropertyStrategy) prj.getSources().get(0).getStrategy();
            assertThat(strategy.getProps().get(0), instanceOf(OrganizationFolderBranchProperty.class));
            OrganizationFolderBranchProperty property = (OrganizationFolderBranchProperty) strategy.getProps().get(0);
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
    @Issue("JENKINS-31516")
    public void indexChildrenOnOrganizationFolderIndex() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("stuff");
            OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
            top.getNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches(), new MockSCMDiscoverTags(), new MockSCMDiscoverChangeRequests()));
            assertThat("Top level has not been scanned", top.getItem("stuff"), nullValue());
            top.scheduleBuild(0);
            r.waitUntilNoActivity();
            assertThat("Top level has been scanned", top.getItem("stuff"), notNullValue());
            assertThat("We have run an index on the child item",
                    top.getItem("stuff").getComputation().getResult(), is(Result.SUCCESS)
            );
        }
    }

    @Issue("JENKINS-32782")
    @Test
    public void emptyViewEquality() throws Exception {
        OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
        View emptyView = top.getPrimaryView();
        assertEquals(BaseEmptyView.VIEW_NAME, emptyView.getViewName());
        assertEquals(Messages.BaseEmptyView_displayName(), emptyView.getDisplayName());
        assertEquals(emptyView, top.getPrimaryView());
        assertTrue(emptyView.isDefault());
    }

    @Issue("JENKINS-34246")
    @Test
    public void deletedMarker() throws Exception {
        OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
        List<MultiBranchProjectFactory> projectFactories = top.getProjectFactories();
        assertThat(projectFactories, extracting(f -> f.getDescriptor(), hasItem(ExtensionList.lookupSingleton(ConfigRoundTripDescriptor.class))));
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

    /**
     * When an OrganizationFolder is created and provided with no {@link MultiBranchProjectFactory} implementations,
     * it should automatically add the enabled-by-default factories for the current Jenkins instance
     * when {@link OrganizationFolder#onCreatedFromScratch()} is called.
     * @throws IOException
     */
    @Test
    public void defaultFactoriesWhenNeeded() throws IOException {

        // programmatically create an OrganizationFolder
        OrganizationFolder newbie = new OrganizationFolder( r.jenkins, "newbie" );

        // it should have no factories
        assertEquals(
            "A newly-created OrganizationFolder has no MultiBranchProjectFactories.",
            0,
            newbie.getProjectFactories().size() );

        // these are all of the MultiBranchProjectFactory definitions.
        ExtensionList<MultiBranchProjectFactoryDescriptor> factory_descriptors = r.jenkins.getExtensionList( MultiBranchProjectFactoryDescriptor.class );

        // at some point after creating it, onCreatedFromScratch() would be called by Jenkins
        newbie.onCreatedFromScratch();

        // now, the folder should have all the default factories.
        for( MultiBranchProjectFactoryDescriptor factory_descriptor : factory_descriptors ) {
            if( factory_descriptor.newInstance() != null) {
                // this factory is enabled by default, and should be present in the org folder

                boolean default_factory_was_present = false;

                for( MultiBranchProjectFactory org_factory : newbie.getProjectFactories() ) {
                    if( org_factory.getDescriptor().getClass().equals( factory_descriptor.getClass() ) ) {

                        // the factory that the org contained was one of the enabled-by-default factories.
                        default_factory_was_present = true;
                        break;
                    }
                }

                assertTrue(
                    "After #onCreatedFromScratch(), the enabled-by-default MultiBranchProjectFactory [" + factory_descriptor.getDisplayName()+ "] is present in an OrganizationFolder created with no initial factories.",
                    default_factory_was_present );

            }
        }

        // additionally, all of the Org Folder's factories should be enabled-by-default factories.
        for( MultiBranchProjectFactory org_factory : newbie.getProjectFactories() ) {

            boolean org_factory_is_default_factory = false;

            for( MultiBranchProjectFactoryDescriptor factory_descriptor : factory_descriptors ) {

                if( ( factory_descriptor.newInstance() != null  ) &&
                    ( org_factory.getDescriptor().getClass().equals( factory_descriptor.getClass() ) ) )
                {
                    // this descriptor was enabled by default AND it matches the org's factory descriptor
                    org_factory_is_default_factory = true;
                    break;
                }
            }

            assertTrue(
                "After #onCreatedFromScratch() with no initial project factories, the OrganizationFolder MultiBranchProjectFactory [" + org_factory.getDescriptor().getDisplayName()+ "] is one of the enabled-by-default factories.",
                org_factory_is_default_factory );
        }
    }

    /**
     * When an OrganizationFolder is created and provided with at least one {@link MultiBranchProjectFactory},
     * it should not add any other factories when {@link OrganizationFolder#onCreatedFromScratch()} is called.
     */
    @Test
    public void noDefaultFactoriesWhenNotNeeded() {

        // programmatically create an OrganizationFolder
        OrganizationFolder newbie = new OrganizationFolder( r.jenkins, "newbie" );

        // it should have no factories
        assertEquals(
            "A newly-created OrganizationFolder has no MultiBranchProjectFactories.",
            0,
            newbie.getProjectFactories().size() );

        // set exactly one non-default factory
        newbie.getProjectFactories().clear();
        MockFactory mock_factory = new MockFactory();
        newbie.getProjectFactories().add( mock_factory );

        // at some point after creating it, onCreatedFromScratch() would be called by Jenkins
        newbie.onCreatedFromScratch();

        assertEquals(
            "An OrganizationFolder created with a non-default project factory should only have one factory after onCreatedFromScratch()",
            1,
            newbie.getProjectFactories().size() );

        assertEquals(
            "An OrganizationFolder created with a non-default project factory should only have that particular factory after onCreatedFromScratch()",
            mock_factory.getDescriptor(),
            newbie.getProjectFactories().get( 0 ).getDescriptor() ) ;

    }

    @Test
    public void modifyAclsWhenInComputedFolder() throws IOException, InterruptedException {

        Set<Permission> suppressed_permissions =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                Item.CONFIGURE, Item.DELETE, View.CONFIGURE, View.CREATE, View.DELETE)));

        // we need a ComputedFolder to be the parent of our OrganizationFolder
        ComputedFolder<OrganizationFolder> computed_folder = new ComputedFolder<OrganizationFolder>(r.jenkins, "top") {

            @Override
            protected void computeChildren(
                ChildObserver<OrganizationFolder> observer,
                TaskListener listener)
                    throws IOException, InterruptedException
            {
                /*
                 * We don't actually need to compute children during a unit test.
                 * We just need an OrganizationFolder to have this ComputedFolder
                 * as its parent.
                 *
                 * Since item parents are set on the item when constructing it,
                 * we can do that when we create the OrganizationFolder.
                 *
                 * Also, #scheduleBuild(...) to enqueue a computeChildren
                 * doesn't work out-of-the-box in the current unit-test setup.
                 */
            }
        };

        OrganizationFolder org_folder = new OrganizationFolder( computed_folder, "org" );

        // SYSTEM (the default authentication scope) can do everything, so we need to look like someone else.
        Authentication some_user = new TestingAuthenticationToken( this, "testing", null );

        // verify that all of of the suppressed permissions are actually suppressed!
        for( Permission perm : suppressed_permissions ) {

            assertFalse(
                "OrganizationFolders in ComputedFolders should suppress the [" + perm.getId() + "] permission.",
                org_folder.getACL().hasPermission( some_user, perm ) );
        }

    }

    @Test
    public void modifyAclsWhenNotInComputedFolder() throws IOException {

        Set<Permission> suppressed_permissions =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                Item.CONFIGURE, Item.DELETE, View.CONFIGURE, View.CREATE, View.DELETE)));

        OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");

        // SYSTEM (the default authentication scope) can do everything, so we need to look like someone else.
        Authentication some_user = new TestingAuthenticationToken( this, "testing", null );

        // verify that none of the suppressed permissions are suppressed
        for( Permission perm : suppressed_permissions ) {

            assertTrue(
                "Organization Folders in non-computed parents do not suppress the [" + perm.getId() + "] permission.",
                top.getACL().hasPermission( some_user, perm ) );
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

}
