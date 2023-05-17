package jenkins.branch.naming;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Logger;
import jenkins.branch.BranchSource;
import jenkins.branch.MultiBranchProjectDisplayNamingStrategy;
import jenkins.branch.MultiBranchProjectDisplayNamingTrait;
import jenkins.branch.naming.githubapimock.MockGithubInfo;
import jenkins.branch.naming.githubapimock.MockGithubJenkinsfileContent;
import jenkins.branch.naming.githubapimock.MockGithubOrg;
import jenkins.branch.naming.githubapimock.MockGithubRateLimit;
import jenkins.branch.naming.githubapimock.MockGithubRepository;
import jenkins.branch.naming.githubapimock.MockGithubUser;
import jenkins.branch.naming.githubapimock.MockPullRequest;
import jenkins.branch.naming.githubapimock.ResourceHandler;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.github_branch_source.OriginPullRequestDiscoveryTrait;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.*;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This class tests that project naming strategies (see {@link MultiBranchProjectDisplayNamingStrategy}) applied to
 * multi-branch jobs via {@link MultiBranchProjectDisplayNamingTrait} are used to set the display name of change
 * request jobs inside the multi-branch project.
 *
 * It does this by using the github-branch-source plugin and stubbing the GitHub REST API to create a fake change
 * request. Then it checks the job's display name against what
 * {@link MultiBranchProjectDisplayNamingStrategy#generateName(String, String)} generates for that change request.
 *
 * Another option for testing this (that wouldn't have us rely on another plugin) would be to implement our own
 * fake {@link SCMSource} but that's probably even more complex. Since the github-branch-source plugin is one of the
 * most used (and fingers-crossed, stable) in the Jenkins ecosystem, and mocking a REST API is trivial, the tradeoff is
 * acceptable.
 */
public class ProjectNamingStrategyTest {
    private static final Logger LOGGER = Logger.getLogger(ProjectNamingStrategyTest.class.getName());

    @ClassRule
    public static final JenkinsRule r = new JenkinsRule();

    public static String stubBaseUrl ;
    public static HttpServer githubApiStub;

    @BeforeClass
    public static void setupGithubApiStub() throws Exception {
        final int stubPort = findAvailablePort();
        stubBaseUrl = String.format("http://localhost:%s", stubPort);
        githubApiStub = HttpServer.create(new InetSocketAddress(stubPort), 0);

        final MockGithubInfo githubInfo = new MockGithubInfo();
        stubGithubApiCall("/", githubInfo);

        final MockGithubRateLimit githubRateLimit = new MockGithubRateLimit();
        stubGithubApiCall("/rate_limit", githubRateLimit);

        final MockGithubOrg org = new MockGithubOrg();
        stubGithubApiCall(String.format("/orgs/%s", MockGithubOrg.ORG_LOGIN), org);

        final MockGithubUser user = new MockGithubUser();
        stubGithubApiCall(String.format("/users/%s", MockGithubUser.USER_LOGIN), user);

        final MockGithubRepository repository = new MockGithubRepository();
        stubGithubApiCall(String.format(
            "/repos/%s/%s", MockGithubOrg.ORG_LOGIN, MockGithubRepository.REPO_NAME
        ), repository);
        stubGithubApiCall(String.format(
            "/repos/%s/%s", MockGithubUser.USER_LOGIN, MockGithubRepository.REPO_NAME
        ), null);
        stubGithubApiCall(String.format(
            "/orgs/%s/repos", MockGithubOrg.ORG_LOGIN
        ), Collections.singletonList(repository));

        final MockPullRequest pr = new MockPullRequest();
        stubGithubApiCall(String.format(
            "/repos/%s/%s/pulls/%s", MockGithubOrg.ORG_LOGIN, MockGithubRepository.REPO_NAME, MockPullRequest.PR_NUMBER
        ), pr);
        stubGithubApiCall(String.format(
            "/repos/%s/%s/pulls", MockGithubOrg.ORG_LOGIN, MockGithubRepository.REPO_NAME
        ), Collections.singletonList(pr));

        final MockGithubJenkinsfileContent jenkinsfileContent = new MockGithubJenkinsfileContent();
        stubGithubApiCall(String.format(
            "/repos/%s/%s/contents/", MockGithubOrg.ORG_LOGIN, MockGithubRepository.REPO_NAME
        ), Collections.singletonList(jenkinsfileContent));

        githubApiStub.start();
    }

    @AfterClass
    public static void shutdownGithubApiStub() {
        githubApiStub.stop(0);
    }

    @Test
    @Issue("JENKINS-55348")
    public void testRawStrategy() throws Exception {
        testNamingStrategy(MultiBranchProjectDisplayNamingStrategy.RAW);
    }

    @Test
    @Issue("JENKINS-55348")
    public void testCompositeStrategy() throws Exception {
        testNamingStrategy(MultiBranchProjectDisplayNamingStrategy.RAW_AND_OBJECT_DISPLAY_NAME);
    }

    @Test
    @Issue("JENKINS-55348")
    public void testObjectNameStrategy() throws Exception {
        testNamingStrategy(MultiBranchProjectDisplayNamingStrategy.OBJECT_DISPLAY_NAME);
    }

    public void testNamingStrategy(final MultiBranchProjectDisplayNamingStrategy namingStrategy) throws Exception {
        final Jenkins jenkinsInstance = r.jenkins;
        final String projectName = String.format("Project_%s", namingStrategy.name());
        final WorkflowMultiBranchProject multiBranchProject =
            jenkinsInstance.createProject(WorkflowMultiBranchProject.class, projectName);

        final GitHubSCMSource source = new GitHubSCMSource(MockGithubOrg.ORG_LOGIN, MockGithubRepository.REPO_NAME);
        source.setApiUri(stubBaseUrl);
        source.setTraits(Arrays.asList(
            new OriginPullRequestDiscoveryTrait(2),
            new MultiBranchProjectDisplayNamingTrait(namingStrategy)
        ));
        final BranchSource branchSource = new BranchSource(source);
        multiBranchProject.getSourcesList().add(branchSource);
        r.configRoundtrip(multiBranchProject);

        final WorkflowJob prProject = jenkinsInstance.getItemByFullName(
            String.format("%s/PR-%s", projectName, MockPullRequest.PR_NUMBER),
            WorkflowJob.class
        );

        final String expectedName = namingStrategy.generateName(MockPullRequest.PR_NAME, MockPullRequest.PR_TITLE);
        assertNotNull(prProject, "No job was created for the pull request");
        assertEquals(expectedName, prProject.getDisplayName(), "The job name doesn't match the naming strategy");
    }

    private static int findAvailablePort() {
        try (final ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (final IOException e) {
            throw new RuntimeException("Could not find an available port for GitHub API stub");
        }
    }

    private static <RESPONSE> void stubGithubApiCall(final String url, final RESPONSE response) {
        LOGGER.fine(String.format("Setting up stub for %s", url));
        githubApiStub.createContext(url, new ResourceHandler<>(url, response));
    }
}
