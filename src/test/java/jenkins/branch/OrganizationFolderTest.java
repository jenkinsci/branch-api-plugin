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
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.model.View;
import hudson.scm.NullSCM;
import hudson.security.Permission;
import integration.harness.BasicMultiBranchProject;
import integration.harness.BasicMultiBranchProjectFactory;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import static jenkins.branch.matchers.Extracting.extracting;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@WithJenkins
class OrganizationFolderTest {

    public static class OrganizationFolderBranchProperty extends ParameterDefinitionBranchProperty {

        @DataBoundConstructor
        public OrganizationFolderBranchProperty() {
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

    private JenkinsRule r;

    @SuppressWarnings("unused")
    private final LogRecorder logging = new LogRecorder().record(ComputedFolder.class, Level.FINE).record(OrganizationFolder.class, Level.FINE);

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void configRoundTrip() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("stuff");
            OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
            List<MultiBranchProjectFactory> projectFactories = top.getProjectFactories();
            assertThat(projectFactories, extracting(MultiBranchProjectFactory::getDescriptor, hasItem(ExtensionList.lookupSingleton(ConfigRoundTripDescriptor.class))));
            projectFactories.add(new MockFactory());
            top.getNavigators().add(new SingleSCMNavigator("stuff",
                    Collections.singletonList(new SingleSCMSource("stuffy",
                            new MockSCM(c, "stuff", new MockSCMHead("master"), null))))
            );
            top = r.configRoundtrip(top);
            List<SCMNavigator> navigators = top.getNavigators();
            assertEquals(1, navigators.size());
            assertEquals(SingleSCMNavigator.class, navigators.get(0).getClass());
            assertEquals("stuff", ((SingleSCMNavigator) navigators.get(0)).getName());
            projectFactories = top.getProjectFactories();
            assertThat(projectFactories, extracting(MultiBranchProjectFactory::getDescriptor, hasItems(ExtensionList.lookupSingleton(ConfigRoundTripDescriptor.class), ExtensionList.lookupSingleton(ConfigRoundTripDescriptor.class))));
        }
    }

    @Issue("JENKINS-48837")
    @Test
    void verifyBranchPropertiesAppliedOnNewProjects() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("stuff");
            OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
            List<MultiBranchProjectFactory> projectFactories = top.getProjectFactories();
            assertThat(projectFactories, extracting(MultiBranchProjectFactory::getDescriptor, hasItem(ExtensionList.lookupSingleton(ConfigRoundTripDescriptor.class))));
            top.getNavigators().add(new SingleSCMNavigator("stuff",
                    Collections.singletonList(new SingleSCMSource("stuffy",
                            new MockSCM(c, "stuff", new MockSCMHead("master"), null))))
                    );
            OrganizationFolderBranchProperty instance = new OrganizationFolderBranchProperty();
            instance.setParameterDefinitions(Collections.singletonList(
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
    void indexChildrenOnOrganizationFolderIndex() throws Exception {
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
    void emptyViewEquality() throws Exception {
        OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
        View emptyView = top.getPrimaryView();
        assertEquals(BaseEmptyView.VIEW_NAME, emptyView.getViewName());
        assertEquals(Messages.BaseEmptyView_displayName(), emptyView.getDisplayName());
        assertEquals(emptyView, top.getPrimaryView());
        assertTrue(emptyView.isDefault());
    }

    @Issue("JENKINS-34246")
    @Test
    void deletedMarker() throws Exception {
        assumeTrue(
            ExtensionList.lookup(MultiBranchProjectFactoryDescriptor.class).stream().map(d -> d.clazz).toList().containsAll(List.of(MockFactory.class, BasicMultiBranchProjectFactory.class)),
            "TODO fails if jth.jenkins-war.path includes WorkflowMultiBranchProjectFactory since SingleSCMSource ignores SCMSourceCriteria");
        OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
        List<MultiBranchProjectFactory> projectFactories = top.getProjectFactories();
        assertThat(projectFactories, extracting(MultiBranchProjectFactory::getDescriptor, hasItem(ExtensionList.lookupSingleton(ConfigRoundTripDescriptor.class))));
        top.getNavigators().add(new SingleSCMNavigator("stuff", Collections.singletonList(new SingleSCMSource("stuffy", new NullSCM()))));
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
     */
    @Test
    void defaultFactoriesWhenNeeded() {

        // programmatically create an OrganizationFolder
        OrganizationFolder newbie = new OrganizationFolder( r.jenkins, "newbie" );

        // it should have no factories
        assertEquals(
            0,
            newbie.getProjectFactories().size(),
            "A newly-created OrganizationFolder has no MultiBranchProjectFactories." );

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
                    default_factory_was_present,
                    "After #onCreatedFromScratch(), the enabled-by-default MultiBranchProjectFactory [" + factory_descriptor.getDisplayName()+ "] is present in an OrganizationFolder created with no initial factories." );

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
                org_factory_is_default_factory,
                "After #onCreatedFromScratch() with no initial project factories, the OrganizationFolder MultiBranchProjectFactory [" + org_factory.getDescriptor().getDisplayName()+ "] is one of the enabled-by-default factories." );
        }
    }

    /**
     * When an OrganizationFolder is created and provided with at least one {@link MultiBranchProjectFactory},
     * it should not add any other factories when {@link OrganizationFolder#onCreatedFromScratch()} is called.
     */
    @Test
    void noDefaultFactoriesWhenNotNeeded() {

        // programmatically create an OrganizationFolder
        OrganizationFolder newbie = new OrganizationFolder( r.jenkins, "newbie" );

        // it should have no factories
        assertEquals(
            0,
            newbie.getProjectFactories().size(),
            "A newly-created OrganizationFolder has no MultiBranchProjectFactories." );

        // set exactly one non-default factory
        newbie.getProjectFactories().clear();
        MockFactory mock_factory = new MockFactory();
        newbie.getProjectFactories().add( mock_factory );

        // at some point after creating it, onCreatedFromScratch() would be called by Jenkins
        newbie.onCreatedFromScratch();

        assertEquals(
            1,
            newbie.getProjectFactories().size(),
            "An OrganizationFolder created with a non-default project factory should only have one factory after onCreatedFromScratch()" );

        assertEquals(
            mock_factory.getDescriptor(),
            newbie.getProjectFactories().get( 0 ).getDescriptor(),
            "An OrganizationFolder created with a non-default project factory should only have that particular factory after onCreatedFromScratch()" ) ;

    }

    @Test
    void modifyAclsWhenInComputedFolder() {

        Set<Permission> suppressed_permissions =
                Set.of(Item.CONFIGURE, Item.DELETE, View.CONFIGURE, View.CREATE, View.DELETE);

        // we need a ComputedFolder to be the parent of our OrganizationFolder
        ComputedFolder<OrganizationFolder> computed_folder = new ComputedFolder<>(r.jenkins, "top") {

            @Override
            protected void computeChildren(
                    ChildObserver<OrganizationFolder> observer,
                    TaskListener listener) {
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
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        Authentication some_user = User.getById("testing", true).impersonate2();

        // verify that all of the suppressed permissions are actually suppressed!
        for( Permission perm : suppressed_permissions ) {

            assertFalse(
                org_folder.getACL().hasPermission2( some_user, perm ),
                "OrganizationFolders in ComputedFolders should suppress the [" + perm.getId() + "] permission." );
        }

    }

    @Test
    void modifyAclsWhenNotInComputedFolder() throws IOException {

        Set<Permission> suppressed_permissions =
                Set.of(Item.CONFIGURE, Item.DELETE, View.CONFIGURE, View.CREATE, View.DELETE);

        OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");

        // SYSTEM (the default authentication scope) can do everything, so we need to look like someone else.
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        Authentication some_user = User.getById("testing", true).impersonate2();

        // verify that none of the suppressed permissions are suppressed
        for( Permission perm : suppressed_permissions ) {

            assertTrue(
                top.getACL().hasPermission2( some_user, perm ),
                "Organization Folders in non-computed parents do not suppress the [" + perm.getId() + "] permission." );
        }

    }

    @TestExtension
    public static class ConfigRoundTripDescriptor extends MockFactoryDescriptor {}

    public static class MockFactory extends MultiBranchProjectFactory {
        @DataBoundConstructor
        public MockFactory() {}
        static boolean live = true;
        @Override
        public boolean recognizes(@NonNull ItemGroup<?> parent, @NonNull String name, @NonNull List<? extends SCMSource> scmSources,
                                  @NonNull Map<String, Object> attributes, @NonNull TaskListener listener) {
            return live;
        }
        @NonNull
        @Override
        public MultiBranchProject<?, ?> createNewProject(@NonNull ItemGroup<?> parent, @NonNull String name,
                                                         @NonNull List<? extends SCMSource> scmSources, @NonNull Map<String,Object> attributes,
                                                         @NonNull TaskListener listener) {
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
        @NonNull
        @Override
        public String getDisplayName() {
            return "MockFactory";
        }
    }

}
