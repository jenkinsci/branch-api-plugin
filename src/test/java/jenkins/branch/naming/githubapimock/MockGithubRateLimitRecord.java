package jenkins.branch.naming.githubapimock;

import java.util.Date;

import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MINUTES;

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
        return new Date(currentTimeMillis() + MINUTES.toMillis(5))
                .getTime();
    }
}
