/*
 * The MIT License
 *
 * Copyright (c) 2016, IBM, Inc., Alastair D'Silva.
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

import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;

import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;

import java.util.logging.Logger;

enum PathEncoding {
    DEFAULT, BASE64, STRIP
};

@Extension
public class BranchGlobalDescriptor extends GlobalConfiguration {

    // @Extension
    // public static final class DescriptorImpl extends Descriptor<Branch> {

    /**
     * Which path encoding to use
     */
    private PathEncoding pathEncoding = PathEncoding.DEFAULT;

    public BranchGlobalDescriptor() {
        super();
        load();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json.getJSONObject("branchAPI"));
        save();
        return true;
    }

    /**
     * Get the display name for this descriptor
     *
     * @return the display name
     */
    public String getDisplayName() {
        return Messages.DisplayName();
    }

    /**
     * Get the currently selected path encoding
     *
     * @return the currently selected path encoding
     */
    public String getPathEncoding() {
        return pathEncoding.name();
    }

    /**
     * Get the enum value of the path encoding
     *
     * @return the path encoding
     */
    public PathEncoding getPathEncodingType() {
        return pathEncoding;
    }

    /**
     * Select the encoding to use to convert branch names to paths
     */
    public void setPathEncoding(String encoding) {
        pathEncoding = PathEncoding.valueOf(encoding);
    }

}
