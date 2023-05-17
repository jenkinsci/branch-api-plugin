package jenkins.branch.naming.githubapimock;

import com.fasterxml.jackson.annotation.JsonProperty;
import jenkins.branch.naming.ProjectNamingStrategyTest;

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
        return String.format("%s/%s", MockGithubOrg.ORG_LOGIN, REPO_NAME);
    }

    public String getDefaultBranch() {
        return "master";
    }

    @JsonProperty("html_url")
    public String getHtmlUrl() {
        return String.format("%s/%s/%s", ProjectNamingStrategyTest.MOCK_BASE_URL, REPO_NAME, MockGithubOrg.ORG_LOGIN);
    }
}
