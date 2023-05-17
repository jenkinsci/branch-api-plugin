package jenkins.branch.naming;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
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
import jenkins.model.Jenkins;

import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.github_branch_source.OriginPullRequestDiscoveryTrait;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static jenkins.branch.MultiBranchProjectDisplayNamingStrategy.OBJECT_DISPLAY_NAME;
import static jenkins.branch.MultiBranchProjectDisplayNamingStrategy.RAW;
import static jenkins.branch.MultiBranchProjectDisplayNamingStrategy.RAW_AND_OBJECT_DISPLAY_NAME;
import static jenkins.branch.naming.githubapimock.MockGithubOrg.ORG_LOGIN;
import static jenkins.branch.naming.githubapimock.MockGithubRepository.REPO_NAME;
import static jenkins.branch.naming.githubapimock.MockGithubUser.USER_LOGIN;
import static jenkins.branch.naming.githubapimock.MockPullRequest.PR_NAME;
import static jenkins.branch.naming.githubapimock.MockPullRequest.PR_NUMBER;
import static jenkins.branch.naming.githubapimock.MockPullRequest.PR_TITLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class NamingStrategyTest {
    static final int MOCK_PORT = 27960;
    public static final String MOCK_BASE_URL = format("http://localhost:%s", MOCK_PORT);
    private static final ObjectMapper MAPPER = new ObjectMapper().disable(FAIL_ON_EMPTY_BEANS);

    @ClassRule
    public static final JenkinsRule r = new JenkinsRule();
    @ClassRule
    public static final WireMockRule wireMockRule = new WireMockRule(options().port(MOCK_PORT));

    @BeforeClass
    public static void setupGithubApiStub() throws JsonProcessingException {
        final MockGithubInfo githubInfo = new MockGithubInfo();
        stubGithubApiCall("", githubInfo);
        stubGithubApiCall("/", githubInfo);

        final MockGithubRateLimit githubRateLimit = new MockGithubRateLimit();
        stubGithubApiCall("/rate_limit", githubRateLimit);

        final MockGithubOrg org = new MockGithubOrg();
        stubGithubApiCall(format("/orgs/%s", ORG_LOGIN), org);

        final MockGithubUser user = new MockGithubUser();
        stubGithubApiCall(format("/users/%s", USER_LOGIN), user);

        final MockGithubRepository repository = new MockGithubRepository();
        stubGithubApiCall(format("/repos/%s/%s", ORG_LOGIN, REPO_NAME), repository);
        stubGithubApiCall(format("/repos/%s/%s", USER_LOGIN, REPO_NAME), null);
        stubGithubApiCall(format("/orgs/%s/repos?per_page=100", ORG_LOGIN), singletonList(repository));

        final MockPullRequest pr = new MockPullRequest();
        stubGithubApiCall(format("/repos/%s/%s/pulls/%s", ORG_LOGIN, REPO_NAME, PR_NUMBER), pr);
        stubGithubApiCall(format("/repos/%s/%s/pulls?state=open", ORG_LOGIN, REPO_NAME), singletonList(pr));

        final MockGithubJenkinsfileContent jenkinsfileContent = new MockGithubJenkinsfileContent();
        final String encodedRef = join("%2F", "refs", "pull", String.valueOf(PR_NUMBER), "head");
        stubGithubApiCall(
                format("/repos/%s/%s/contents/?ref=%s", ORG_LOGIN, REPO_NAME, encodedRef),
                singletonList(jenkinsfileContent)
        );
    }

    @Test
    @Issue("JENKINS-55348")
    public void testRawStrategy() throws Exception {
        testNamingStrategy(RAW);
    }

    @Test
    @Issue("JENKINS-55348")
    public void testCompositeStrategy() throws Exception {
        testNamingStrategy(RAW_AND_OBJECT_DISPLAY_NAME);
    }

    @Test
    @Issue("JENKINS-55348")
    public void testObjectNameStrategy() throws Exception {
        testNamingStrategy(OBJECT_DISPLAY_NAME);
    }

    public void testNamingStrategy(final MultiBranchProjectDisplayNamingStrategy namingStrategy) throws Exception {
        final Jenkins jenkinsInstance = r.jenkins;
        final String projectName = format("Project_%s", namingStrategy.name());
        final WorkflowMultiBranchProject multiBranchProject =
                jenkinsInstance.createProject(WorkflowMultiBranchProject.class, projectName);

        final GitHubSCMSource source = new GitHubSCMSource(ORG_LOGIN, REPO_NAME);
        source.setApiUri(MOCK_BASE_URL);
        source.setTraits(asList(
                new OriginPullRequestDiscoveryTrait(2),
                new MultiBranchProjectDisplayNamingTrait(namingStrategy)
        ));
        final BranchSource branchSource = new BranchSource(source);
        multiBranchProject.getSourcesList().add(branchSource);
        r.configRoundtrip(multiBranchProject);

        final WorkflowJob prProject = jenkinsInstance.getItemByFullName(
                format("%s/PR-%s", projectName, PR_NUMBER),
                WorkflowJob.class
        );

        final String expectedName = namingStrategy.generateName(PR_NAME, PR_TITLE);
        assertNotNull(prProject, "No job was created for the pull request");
        assertEquals(expectedName, prProject.getDisplayName(), "The job name doesn't match the naming strategy");
    }

    private static <RESPONSE> void stubGithubApiCall(final String url, final RESPONSE response) throws JsonProcessingException {
        System.out.printf("Setting up stub for %s%n", url);
        if (response == null) {
            stubFor(get(url).willReturn(jsonResponse("\"Oopsie, nothing there\"", 404)));
        } else {
            stubFor(get(url).willReturn(jsonResponse(MAPPER.writeValueAsString(response), 200)));
        }
    }
}
