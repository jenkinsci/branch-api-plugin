/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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
 */

package jenkins.branch;

import com.google.common.base.Charsets;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.model.*;
import hudson.model.listeners.ItemListener;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.slaves.WorkspaceLocator;
import net.sf.json.JSONObject;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Chooses manageable workspace names for branch projects.
 * @see "JENKINS-34564"
 */
@Restricted(NoExternalUse.class)
@Extension(ordinal = -100)
public class WorkspaceLocatorImpl extends WorkspaceLocator {

    private static final Logger LOGGER = Logger.getLogger(WorkspaceLocatorImpl.class.getName());

    /** The most characters to allow in a workspace directory name, relative to the root. Zero to disable altogether. */
    // TODO 2.4+ use SystemProperties
    static /* not final */ int PATH_MAX = Integer.getInteger(WorkspaceLocatorImpl.class.getName() + ".PATH_MAX", 80);

    @Override
    public FilePath locate(TopLevelItem item, Node node) {
        if (PATH_MAX == 0) {
            return null;
        }
        if (!(item.getParent() instanceof MultiBranchProject)) {
            return null;
        }
        String minimized = minimize(item.getFullName());
        if (node instanceof Jenkins) {
            return JenkinsWorkspaceBaseDirConfiguration.get().child(minimized);
        } else if (node instanceof Slave) {
            FilePath root = ((Slave) node).getWorkspaceRoot();
            return root != null ? root.child(minimized) : null;
        } else { // ?
            return null;
        }
    }

    static String uniqueSuffix(String name) {
        // TODO still in beta: byte[] sha256 = Hashing.sha256().hashString(name).asBytes();
        byte[] sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256").digest(name.getBytes(Charsets.UTF_16LE));
        } catch (NoSuchAlgorithmException x) {
            throw new AssertionError("https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#MessageDigest", x);
        }
        return new Base32(0).encodeToString(sha256).replaceFirst("=+$", "");
    }

    static String minimize(String name) {
        String mnemonic = name.replaceAll("(%[0-9A-F]{2}|[^a-zA-Z0-9-_.])+", "_");
        int maxSuffix = 53; /* ceil(256 / lg(32)) + length("-") */
        int maxMnemonic = Math.max(PATH_MAX - maxSuffix, 1);
        if (maxSuffix + maxMnemonic > PATH_MAX) {
            // The whole suffix cannot be included in the path.  Trim the suffix
            // and the mnemonic to fit inside PATH_MAX.  The mnemonic always gets
            // at least one character.  The suffix always gets 10 characters plus
            // the "-".  The rest of PATH_MAX is split evenly between the two.
            LOGGER.log(Level.WARNING, "WorkspaceLocatorImpl.PATH_MAX is small enough that workspace path collisions are more likely to occur");
            final int minSuffix = 10 + /* length("-") */ 1;
            maxMnemonic = Math.max((int)((PATH_MAX - minSuffix) / 2), 1);
            maxSuffix = Math.max(PATH_MAX - maxMnemonic, minSuffix);
        }
        maxSuffix = maxSuffix - 1; // Remove the "-"
        String result = StringUtils.right(mnemonic, maxMnemonic) + "-" + uniqueSuffix(name).substring(0, maxSuffix);
        return result;
    }

    /**
     * Cleans up workspace when an orphaned branch project is deleted.
     * @see "JENKINS-2111"
     */
    @Extension
    public static class Deleter extends ItemListener {

        @Override
        public void onDeleted(Item item) {
            if (item.getParent() instanceof MultiBranchProject) {
                String suffix = uniqueSuffix(item.getFullName());
                Jenkins jenkins = Jenkins.getActiveInstance();
                cleanUp(suffix, JenkinsWorkspaceBaseDirConfiguration.get().getFilePath(), jenkins);
                for (Node node : jenkins.getNodes()) {
                    if (node instanceof Slave) {
                        cleanUp(suffix, ((Slave) node).getWorkspaceRoot(), node);
                    }
                }
            }
        }

        private void cleanUp(String suffix, FilePath root, Node node) {
            try {
                if (root == null || !root.isDirectory()) {
                    return;
                }
                for (FilePath child : root.listDirectories()) {
                    if (child.getName().contains(suffix)) {
                        LOGGER.log(Level.INFO, "deleting obsolete workspace {0} on {1}", new Object[] {child, node.getNodeName()});
                        child.deleteRecursive();
                    }
                }
            } catch (IOException | InterruptedException x) {
                LOGGER.log(Level.WARNING, "could not clean up workspace directories under " + root + " on " + node.getNodeName(), x);
            }
        }

    }

    /**
     * Configurable location of where the workspaces should be stored.
     *
     * @see "JENKINS-38837"
     */
    @Restricted(NoExternalUse.class)
    @Extension
    public static class JenkinsWorkspaceBaseDirConfiguration extends GlobalConfiguration {
        private String path;

        public JenkinsWorkspaceBaseDirConfiguration() {
            load();
        }

        public static JenkinsWorkspaceBaseDirConfiguration get() {
            return ExtensionList.lookup(GlobalConfiguration.class).get(JenkinsWorkspaceBaseDirConfiguration.class);
        }

        /**
         * The base path, relative to $JENKINS_HOME or absolute.
         * @return the path
         */
        public String getPath() {
            if (StringUtils.isEmpty(path)) {
                path = "workspace/";
            }
            return path;
        }

        /**
         * The base path, relative to $JENKINS_HOME or absolute.
         *
         * @param path the path.
         */
        public void setPath(String path) {
            this.path = path;
        }

        public FilePath getFilePath() {
            return Jenkins.getActiveInstance().getRootPath().child(this.getPath());
        }

        public FilePath child(String name) {
            return getFilePath().child(name);
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {

            FilePath base = Jenkins.getActiveInstance().getRootPath().child(json.getString("path"));
            try {
                if (!base.exists()) {
                    base.mkdirs(); //Throws IOException if it fails
                } else {
                    base.child(".test").touch(System.currentTimeMillis()); //Throws IOException if it fails
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.SEVERE, "MultiBranch workspace write test failed.", e);
                throw new FormException("MultiBranch workspace write test failed: " + e.getMessage(), e, "path");
            }

            req.bindJSON(this, json);
            save();
            return true;
        }

        public FormValidation doCheckPath(@QueryParameter String value) {
            FilePath base = Jenkins.getActiveInstance().getRootPath().child(value);
            try {
                if (!base.exists()) {
                    return FormValidation.warning(Messages.JenkinsWorkspaceBaseDirConfiguration_PathDoesNotExist(base.getRemote()));
                } else {
                    if (base.absolutize().equals(Jenkins.getActiveInstance().getRootPath())) {
                        //Quick simple check just in case
                        return FormValidation.error(Messages.JenkinsWorkspaceBaseDirConfiguration_PathIsJenkinsHome());
                    }
                    try {
                        base.child(".test").touch(System.currentTimeMillis());
                    } catch (IOException e) {
                        return FormValidation.error(Messages.JenkinsWorkspaceBaseDirConfiguration_NotWriteable(base.getRemote()));
                    }
                }
            } catch (IOException | InterruptedException e) {
                return FormValidation.warning(Messages.JenkinsWorkspaceBaseDirConfiguration_PathCheckFailed(e.getMessage()));
            }
            return FormValidation.ok();
        }
    }

}
