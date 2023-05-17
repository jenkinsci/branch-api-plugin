package jenkins.branch.naming.githubapimock;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static jenkins.branch.naming.githubapimock.MockGithubOrg.ORG_LOGIN;

public class MockGithubHead {
    private final String branchName;
    private final MockGithubRepository repo = new MockGithubRepository();

    public MockGithubHead(final String branchName) {
        this.branchName = branchName;
    }

    public String getRef() {
        return branchName;
    }

    public String getSha() {
        return hash(branchName);
    }

    public String getLabel() {
        return format("%s:%s", ORG_LOGIN, branchName);
    }

    public MockGithubRepository getRepo() {
        return repo;
    }

    public MockGithubUser getUser() {
        return new MockGithubUser();
    }

    private String hash(final String input) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] encodedHash = digest.digest(input.getBytes(UTF_8));
            return bytesToHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String bytesToHex(byte[] hash) {
        final StringBuilder hexBuilder = new StringBuilder(2 * hash.length);
        for (final byte b : hash) {
            final String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexBuilder.append('0');
            }
            hexBuilder.append(hex);
        }
        return hexBuilder.toString();
    }
}
