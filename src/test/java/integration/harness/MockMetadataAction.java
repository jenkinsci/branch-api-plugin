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

import jenkins.branch.MetadataAction;

public class MockMetadataAction extends MetadataAction {
    private final String description;
    private final String displayName;
    private final String url;
    private final String iconClassName;

    public MockMetadataAction(String description, String displayName, String url, String iconClassName) {
        this.description = description;
        this.displayName = displayName;
        this.url = url;
        this.iconClassName = iconClassName;
    }

    @Override
    public String getObjectDescription() {
        return description;
    }

    @Override
    public String getObjectDisplayName() {
        return displayName;
    }

    @Override
    public String getObjectUrl() {
        return url;
    }

    @Override
    public String getFolderIconClassName() {
        return iconClassName;
    }

    @Override
    public String getFolderIconDescription() {
        return iconClassName == null ? null : "Mock SCM";
    }

}
