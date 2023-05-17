package jenkins.branch.naming.githubapimock;

import com.fasterxml.jackson.annotation.JsonProperty;

import static java.lang.String.format;
import static jenkins.branch.naming.githubapimock.MockGithubOrg.ORG_LOGIN;
import static jenkins.branch.naming.NamingStrategyTest.MOCK_BASE_URL;

public class MockGithubRepository {

    public static final String REPO_NAME = "MyRepo";

    public MockGithubRepository() {}

    public String getName() {
        return REPO_NAME;
    }

    public MockGithubUser getOwner() {
        return new MockGithubUser();
    }

    @JsonProperty("full_name")
    public String getFullName() {
        return format("%s/%s", ORG_LOGIN, REPO_NAME);
    }

    public String getDefaultBranch() {
        return "master";
    }

    @JsonProperty("html_url")
    public String getHtmlUrl() {
        return format("%s/%s/%s", MOCK_BASE_URL, REPO_NAME, ORG_LOGIN);
    }
}
