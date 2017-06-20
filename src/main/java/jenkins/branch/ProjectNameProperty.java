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

package jenkins.branch;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMSourceObserver;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Holds the unmangled project name provided to {@link SCMSourceObserver#observe(String)} and used to create a
 * {@link MultiBranchProject} within a {@link OrganizationFolder}. Normally one would hope that the name provided by
 * {@link SCMSourceObserver#observe(String)} can be directly mapped to {@link MultiBranchProject#getName()}
 * however as we have no control over either the length or the characters coming from the {@link SCMNavigator}
 * we need to mangle the name with {@link NameMangler#apply(String)}. As unmangled names are not reconstructable
 * from the mangled name, we need to store the original unmangled name, hence this property.
 * <p>
 * Note that this is not an issue for the children of {@link MultiBranchProject} as {@link Branch#getName()} is
 * the unmangled name of the branch.
 *
 * @since 2.0.0
 */
public class ProjectNameProperty extends AbstractFolderProperty<MultiBranchProject<?, ?>> {

    private final String name;

    public ProjectNameProperty(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public AbstractFolderProperty<?> reconfigure(StaplerRequest req, JSONObject form) throws Descriptor.FormException {
        return this;
    }

    @Extension
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Project name";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractFolder> containerType) {
            return MultiBranchProject.class.isAssignableFrom(containerType);
        }
    }
}
