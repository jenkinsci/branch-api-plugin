package jenkins.branch.naming.githubapimock;

import com.fasterxml.jackson.annotation.JsonProperty;
import jenkins.branch.naming.ProjectNamingStrategyTest;

public class MockGithubInfo {
    public MockGithubInfo() {}

    @JsonProperty("rate_limit_url")
    public String getRateLimitUrl() {
        return String.format("%s/rate_limit", ProjectNamingStrategyTest.MOCK_BASE_URL);
    }
}
