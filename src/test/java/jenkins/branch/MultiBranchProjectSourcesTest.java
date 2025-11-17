/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc.
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

import integration.harness.BasicMultiBranchProject;
import java.util.List;
import java.util.logging.Level;
import jenkins.scm.impl.mock.MockSCMSource;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.recipes.LocalData;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public final class MultiBranchProjectSourcesTest {

    @Rule public final JenkinsRule r = new JenkinsRule();
    @Rule public final LoggerRule logging = new LoggerRule().record(MultiBranchProject.class, Level.FINE);

    @LocalData
    @Test public void oldMBPWithIds() throws Exception {
        assertThat(idsOf(r.jenkins.getItemByFullName("mbp", BasicMultiBranchProject.class)), contains("30d15538-4f60-437f-81cd-9c46ee6e7ec6", "01d26901-3b39-45ac-915e-e65d806fbcba"));
    }

    @LocalData
    @Test public void oldMBPWithoutIds() throws Exception {
        assertThat(idsOf(r.jenkins.getItemByFullName("mbp", BasicMultiBranchProject.class)), contains("1", "2"));
    }

    @Test public void setSourcesList() throws Exception {
        var mbp = r.createProject(BasicMultiBranchProject.class, "mbp");
        var bs1 = new BranchSource(new MockSCMSource("c", "r1"));
        var bs2 = new BranchSource(new MockSCMSource("c", "r2"));
        mbp.setSourcesList(List.of(bs1, bs2));
        assertThat(idsOf(mbp), contains("1", "2"));
        mbp.setSourcesList(List.of(bs2));
        assertThat(idsOf(mbp), contains("2"));
        var bs3 = new BranchSource(new MockSCMSource("c", "r3"));
        mbp.setSourcesList(List.of(bs1, bs2, bs3));
        assertThat(idsOf(mbp), contains("1", "2", "3"));
    }

    @Test public void getSourcesListAdd() throws Exception {
        var mbp = r.createProject(BasicMultiBranchProject.class, "mbp");
        var bs1 = new BranchSource(new MockSCMSource("c", "r1"));
        var bs2 = new BranchSource(new MockSCMSource("c", "r2"));
        mbp.getSourcesList().add(bs1);
        assertThat(idsOf(mbp), contains("1"));
        mbp.getSourcesList().add(bs2);
        assertThat(idsOf(mbp), contains("1", "2"));
    }

    private static List<String> idsOf(MultiBranchProject<?, ?> mbp) {
        return mbp.getSources().stream().map(bs -> bs.getSource().getId()).toList();
    }

}
