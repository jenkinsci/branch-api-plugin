package jenkins.branch.naming.githubapimock;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class MockGithubRateLimitRecord {
    public MockGithubRateLimitRecord() {}

    public int getLimit() {
        return 5000;
    }

    public int getUsed() {
        return 7;
    }

    public int getRemaining() {
        return getLimit() - getUsed();
    }

    public long getReset() {
        return new Date(
            System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)
        ).getTime();
    }
}
