package jenkins.branch.naming.githubapimock;

public class MockGithubRateLimit {
    public MockGithubRateLimit() {}

    public MockGithubResourcesRateLimit getResources() {
        return new MockGithubResourcesRateLimit();
    }
}
