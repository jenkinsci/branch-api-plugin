package jenkins.branch.naming.githubapimock;

import com.fasterxml.jackson.annotation.JsonProperty;

import static java.lang.String.format;
import static jenkins.branch.naming.githubapimock.MockGithubOrg.ORG_LOGIN;
import static jenkins.branch.naming.githubapimock.MockGithubRepository.REPO_NAME;
import static jenkins.branch.naming.NamingStrategyTest.MOCK_BASE_URL;

public class MockPullRequest {
    public static final int PR_NUMBER = 123;
    public static final String PR_NAME = format("PR-%s", PR_NUMBER);
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
        return format("%s/%s/%s/pulls/%s", MOCK_BASE_URL, ORG_LOGIN, REPO_NAME, getNumber());
    }

    public MockGithubHead getBase() {
        return new MockGithubHead("master");
    }

    public MockGithubHead getHead() {
        return new MockGithubHead("feature");
    }
}
