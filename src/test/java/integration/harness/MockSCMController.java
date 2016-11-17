/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package integration.harness;

import hudson.FilePath;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.WeakHashMap;
import jenkins.scm.api.SCMFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

public class MockSCMController implements Closeable {

    private static Map<String, MockSCMController> instances = new WeakHashMap<>();

    private String id = UUID.randomUUID().toString();

    private Map<String, Repository> repositories = new TreeMap<>();

    private MockSCMController() {
    }

    public static MockSCMController create() {
        MockSCMController c = new MockSCMController();
        synchronized (instances) {
            instances.put(c.id, c);
        }
        return c;
    }

    public static MockSCMController lookup(String id) {
        synchronized (instances) {
            return instances.get(id);
        }
    }

    public String getId() {
        return id;
    }

    @Override
    public void close() {
        synchronized (instances) {
            instances.remove(id);
        }
        repositories.clear();
    }

    public synchronized void createRepository(String name) {
        repositories.put(name, new Repository());
        createBranch(name, "master");
    }

    public synchronized void deleteRepository(String name) {
        repositories.remove(name);
    }

    public synchronized List<String> listRepositories() {
        return new ArrayList<>(repositories.keySet());
    }

    public synchronized void createBranch(String repository, String branch) {
        State state = new State();
        repositories.get(repository).revisions.put(state.getHash(), state);
        repositories.get(repository).heads.put(branch, state.getHash());
    }

    public synchronized void cloneBranch(String repository, String srcBranch, String dstBranch) {
        repositories.get(repository).heads.put(dstBranch, repositories.get(repository).heads.get(srcBranch));
    }

    public synchronized void deleteBranch(String repository, String branch) {
        repositories.get(repository).heads.remove(branch);
    }

    public synchronized List<String> listBranches(String repository) {
        return new ArrayList<>(repositories.get(repository).heads.keySet());
    }

    public synchronized String getRevision(String repository, String branch) {
        return repositories.get(repository).heads.get(branch);
    }

    public synchronized void addFile(String repository, String branch, String message, String path, byte[] content) {
        Repository repo = repositories.get(repository);
        State base = repo.revisions.get(repo.heads.get(branch));
        State state = new State(base, message, Collections.singletonMap(path, content), Collections.<String>emptySet());
        repo.revisions.put(state.getHash(), state);
        repo.heads.put(branch, state.getHash());
    }

    public synchronized void rmFile(String repository, String branch, String message, String path) {
        Repository repo = repositories.get(repository);
        State base = repo.revisions.get(repo.heads.get(branch));
        State state =
                new State(base, message, Collections.<String, byte[]>emptyMap(), Collections.<String>singleton(path));
        repo.revisions.put(state.getHash(), state);
        repo.heads.put(branch, state.getHash());
    }


    public synchronized String checkout(File workspace, String repository, String branchOrHead) throws IOException {
        State state = resolve(repository, branchOrHead);

        for (Map.Entry<String, byte[]> entry : state.files.entrySet()) {
            FileUtils.writeByteArrayToFile(new File(workspace, entry.getKey()), entry.getValue());
        }
        return state.getHash();
    }

    public synchronized String checkout(FilePath workspace, String repository, String branchOrHead)
            throws IOException, InterruptedException {
        State state = resolve(repository, branchOrHead);

        for (Map.Entry<String, byte[]> entry : state.files.entrySet()) {
            workspace.child(entry.getKey()).copyFrom(new ByteArrayInputStream(entry.getValue()));
        }
        return state.getHash();
    }

    private State resolve(String repository, String branchOrHead) {
        return repositories.get(repository).revisions.get(
                repositories.get(repository).heads.containsKey(branchOrHead) ? repositories.get(repository).heads.get(
                        branchOrHead) : branchOrHead);
    }

    public synchronized List<LogEntry> log(String repository, String branchOrHead) {
        State state = resolve(repository, branchOrHead);
        List<LogEntry> result = new ArrayList<>();
        while (state != null) {
            result.add(new LogEntry(state.getHash(), state.timestamp, state.message));
            state = state.parent;
        }
        return result;
    }

    public synchronized SCMFile.Type stat(String repository, String branchOrHead, String path) {
        State state = resolve(repository, branchOrHead);
        if (state == null) {
            return SCMFile.Type.NONEXISTENT;
        }
        if (state.files.containsKey(path)) {
            return SCMFile.Type.REGULAR_FILE;
        }
        for (String p : state.files.keySet()) {
            if (p.startsWith(path + "/")) {
                return SCMFile.Type.DIRECTORY;
            }
        }
        return SCMFile.Type.NONEXISTENT;
    }

    public synchronized long lastModified(String repository, String branchOrHead) {
        State state = resolve(repository, branchOrHead);
        if (state == null) {
            return 0L;
        }
        return state.timestamp;
    }

    private static class Repository {
        private Map<String, State> revisions = new TreeMap<>();
        private Map<String, String> heads = new TreeMap<>();

    }

    private static class State {
        private final State parent;
        private final String message;
        private final long timestamp;
        private final Map<String, byte[]> files;
        private transient String hash;

        public State() {
            this.parent = null;
            this.message = null;
            this.timestamp = System.currentTimeMillis();
            this.files = new TreeMap<>();
        }

        public State(State parent, String message, Map<String, byte[]> added, Set<String> removed) {
            this.parent = parent;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
            Map<String, byte[]> files = parent != null
                    ? new TreeMap<String, byte[]>(parent.files)
                    : new TreeMap<String, byte[]>();
            files.keySet().removeAll(removed);
            files.putAll(added);
            this.files = files;
        }

        public String getHash() {
            if (hash == null) {
                try {
                    Charset utf8 = Charset.forName("UTF-8");
                    MessageDigest sha = MessageDigest.getInstance("SHA-1");
                    if (parent != null) {
                        sha.update(new BigInteger(parent.getHash(), 16).toByteArray());
                    }
                    sha.update(StringUtils.defaultString(message).getBytes(utf8));
                    sha.update((byte) (timestamp & 0xff));
                    sha.update((byte) ((timestamp >> 8) & 0xff));
                    sha.update((byte) ((timestamp >> 16) & 0xff));
                    sha.update((byte) ((timestamp >> 24) & 0xff));
                    sha.update((byte) ((timestamp >> 32) & 0xff));
                    sha.update((byte) ((timestamp >> 40) & 0xff));
                    sha.update((byte) ((timestamp >> 48) & 0xff));
                    sha.update((byte) ((timestamp >> 56) & 0xff));
                    for (Map.Entry<String, byte[]> e : files.entrySet()) {
                        sha.update(e.getKey().getBytes(utf8));
                        sha.update(e.getValue());
                    }
                    hash = javax.xml.bind.DatatypeConverter.printHexBinary(sha.digest()).toLowerCase();
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException("SHA-1 message digest mandated by JLS");
                }
            }
            return hash;
        }
    }

    public static final class LogEntry {
        private final String hash;
        private final long timestamp;
        private final String message;

        private LogEntry(String hash, long timestamp, String message) {
            this.hash = hash;
            this.timestamp = timestamp;
            this.message = message;
        }

        public String getHash() {
            return hash;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return String.format("Commit %s%nDate: %tc%n%s%n", hash, timestamp, message);
        }
    }
}
