package jenkins.branch.naming.githubapimock;

import com.fasterxml.jackson.annotation.JsonProperty;
import jenkins.branch.naming.ProjectNamingStrategyTest;

public class MockPullRequest {
    public static final int PR_NUMBER = 123;
    public static final String PR_NAME = String.format("PR-%s", PR_NUMBER);
    public static final String PR_TITLE = "A PR that implements dat feature";

    public MockPullRequest() {}

    public int getNumber() {
        return PR_NUMBER;
    }

    public String getState() {
        return "open";
    }

    public String getName() {
        return PR_NAME;
    }

    public String getTitle() {
        return PR_TITLE;
    }

    public MockGithubUser getUser() {
        return new MockGithubUser();
    }

    @JsonProperty("html_url")
    public String getHtmlUrl() {
        return String.format(
            "%s/%s/%s/pulls/%s",
            ProjectNamingStrategyTest.stubBaseUrl,
            MockGithubOrg.ORG_LOGIN,
            MockGithubRepository.REPO_NAME,
            PR_NUMBER
        );
    }

    public MockGithubHead getBase() {
        return new MockGithubHead("master");
    }

    public MockGithubHead getHead() {
        return new MockGithubHead("feature");
    }
}
