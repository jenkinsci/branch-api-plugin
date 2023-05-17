package jenkins.branch.naming.githubapimock;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MockGithubResourcesRateLimit {
    public MockGithubResourcesRateLimit() {}

    public MockGithubRateLimitRecord getCore() {
        return new MockGithubRateLimitRecord();
    }

    public MockGithubRateLimitRecord getSearch() {
        return new MockGithubRateLimitRecord();
    }

    public MockGithubRateLimitRecord getGraphql() {
        return new MockGithubRateLimitRecord();
    }

    @JsonProperty("integration_manifest")
    public MockGithubRateLimitRecord getIntegrationManifest() {
        return new MockGithubRateLimitRecord();
    }
}
