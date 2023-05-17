package jenkins.branch.naming.githubapimock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
        return String.format("%s:%s", MockGithubOrg.ORG_LOGIN, branchName);
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
            final byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
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
