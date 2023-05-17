package jenkins.branch.naming.githubapimock;

import com.fasterxml.jackson.annotation.JsonProperty;

import static jenkins.branch.naming.NamingStrategyTest.MOCK_BASE_URL;

public class MockGithubInfo {
    public MockGithubInfo() {}

    @JsonProperty("rate_limit_url")
    public String getRateLimitUrl() {
        return String.format("%s/rate_limit", MOCK_BASE_URL);
    }
}
