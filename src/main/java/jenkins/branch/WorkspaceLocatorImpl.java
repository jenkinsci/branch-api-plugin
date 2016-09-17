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

import com.google.common.hash.Hashing;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.model.TopLevelItem;
import hudson.remoting.Base64;
import jenkins.model.Jenkins;
import jenkins.slaves.WorkspaceLocator;

/**
 * Chooses manageable workspace names for branch projects.
 */
@Extension
public class WorkspaceLocatorImpl extends WorkspaceLocator {

    @Override
    public FilePath locate(TopLevelItem item, Node node) {
        if (!(item.getParent() instanceof MultiBranchProject)) {
            return null;
        }
        String minimized = minimize(item.getFullName());
        if (node instanceof Jenkins) {
            return ((Jenkins) node).getRootPath().child("workspace/" + minimized);
        } else if (node instanceof Slave) {
            FilePath root = ((Slave) node).getWorkspaceRoot();
            return root != null ? root.child(minimized) : null;
        } else { // ?
            return null;
        }
    }

    static String minimize(String name) {
        return name.replaceAll("(%[0-9A-F]{2}|[^a-zA-Z0-9-_.])+", "_").replaceFirst(".*?(.{0,36}$)", "$1") + "-" +
            Base64.encode(Hashing.sha256().hashString(name).asBytes()).replace('/', '_').replace('+', '.').replaceFirst("=+$", "");
    }

}
