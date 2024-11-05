package jenkins.branch;

import hudson.model.FreeStyleProject;
import integration.harness.BasicBranchProjectFactory;
import integration.harness.BasicDummyStepBranchProperty;
import integration.harness.BasicMultiBranchProject;
import jenkins.model.Jenkins;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMDiscoverChangeRequests;
import jenkins.scm.impl.mock.MockSCMSource;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * This class tests that project naming strategies (see {@link MultiBranchProjectDisplayNamingStrategy}) applied to
 * multi-branch jobs via {@link MultiBranchProjectDisplayNamingTrait} are used to set the display name of change
 * request jobs inside the multi-branch project.
 */
public class ProjectNamingStrategyTest {
    @ClassRule
    public static final JenkinsRule r = new JenkinsRule();

    public static final String REPO_NAME = "MyRepo";

    @Test
    @Issue("JENKINS-55348")
    public void testCompositeStrategy() throws Exception {
        testNamingStrategy(MultiBranchProjectDisplayNamingStrategy.RAW_AND_OBJECT_DISPLAY_NAME);
    }

    @Test
    @Issue("JENKINS-74811")
    public void testRawStrategy() throws Exception {
        testNamingStrategy(MultiBranchProjectDisplayNamingStrategy.RAW);
    }

    @Test
    @Issue("JENKINS-55348")
    public void testObjectNameStrategy() throws Exception {
        testNamingStrategy(MultiBranchProjectDisplayNamingStrategy.OBJECT_DISPLAY_NAME);
    }

    public void testNamingStrategy(final MultiBranchProjectDisplayNamingStrategy namingStrategy) throws Exception {
        final Jenkins jenkinsInstance = r.jenkins;
        final String mainBranch = "master";
        final String projectName = String.format("Project_%s", namingStrategy.name());

        try (final MockSCMController mockScm = MockSCMController.create()) {
            mockScm.createRepository(REPO_NAME);

            final Integer crNumber = mockScm.openChangeRequest(REPO_NAME, mainBranch);
            final String crName = String.format("CR-%s", crNumber);
            final String crTitle = String.format("Change request #%s", crNumber);

            final BasicMultiBranchProject project = jenkinsInstance.createProject(BasicMultiBranchProject.class, projectName);
            project.setCriteria(null);
            project.setProjectFactory(new BasicBranchProjectFactory());

            final MockSCMSource scmSource = new MockSCMSource(
                mockScm,
                REPO_NAME,
                new MockSCMDiscoverBranches(),
                new MockSCMDiscoverChangeRequests(),
                new MultiBranchProjectDisplayNamingTrait(namingStrategy)
            );

            final BranchSource source = new BranchSource(scmSource);
            source.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[]{new BasicDummyStepBranchProperty()}));
            project.getSourcesList().add(source);
            r.configRoundtrip(project);

            final FreeStyleProject branchProject = jenkinsInstance.getItemByFullName(
                String.format("%s/%s", projectName, mainBranch),
                FreeStyleProject.class
            );

            final String expectedBranchProjectName = namingStrategy.generateName(mainBranch, "");
            assertNotNull(branchProject, "No job was created for the main branch");
            assertEquals(expectedBranchProjectName, branchProject.getDisplayName(), "The job name doesn't match the naming strategy");

            final FreeStyleProject crProject = jenkinsInstance.getItemByFullName(
                String.format("%s/%s", projectName, crName),
                FreeStyleProject.class
            );

            final String expectedCrProjectName = namingStrategy.generateName(crName, crTitle);
            assertNotNull(crProject, "No job was created for the pull request");
            assertEquals(expectedCrProjectName, crProject.getDisplayName(), "The job name doesn't match the naming strategy");
        }
    }
}
