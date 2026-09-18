/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, Inc.
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

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.FreeStyleProject;
import java.nio.file.Path;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

final class WorkspaceLocatorImplAltDirTest {

    @RegisterExtension
    private final RealJenkinsExtension rje = new RealJenkinsExtension();

    @TempDir
    private Path tmp;

    @Test
    void itemFullName() throws Throwable {
        var ws = tmp.toString();
        rje.javaOptions("-Djenkins.model.Jenkins.workspacesDir=" + ws + "/${ITEM_FULL_NAME}", "-D" + WorkspaceLocatorImpl.class.getName() + ".MODE=ENABLED");
        rje.startJenkins();
        assertThat(rje.call(r -> r.jenkins.getWorkspaceFor(r.createProject(Folder.class, "dir").createProject(FreeStyleProject.class, "prj")).getRemote()), is(tmp.resolve("dir_prj").toString()));
    }

}
