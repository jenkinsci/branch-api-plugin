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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.AbstractDescribableImpl;
import hudson.model.TaskListener;
import java.io.IOException;
import jenkins.scm.api.SCMSourceCriteria;
import org.kohsuke.stapler.DataBoundConstructor;

public class BasicSCMSourceCriteria extends AbstractDescribableImpl<BasicSCMSourceCriteria>
        implements SCMSourceCriteria {
    private final String fileName;

    @DataBoundConstructor
    public BasicSCMSourceCriteria(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

    @Override
    public boolean isHead(@NonNull Probe probe, @NonNull TaskListener listener) throws IOException {
        if (fileName == null) {
            return true;
        }
        listener.getLogger().format("Checking for %s%n", fileName);
        return probe.stat(fileName).exists();
    }
}
