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

import hudson.model.FreeStyleProject;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.tasks.LogRotator;
import java.io.IOException;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BuildRetentionBranchPropertyTest {

    @Test
    public void decoratesStandardJobByFieldReflectionAccess() throws Exception {
        BuildRetentionBranchProperty instance = new BuildRetentionBranchProperty(new LogRotator(5, 5, 5, 5));
        Job job = new FreeStyleProject(mock(ItemGroup.class), "foo");
        assertThat(instance.jobDecorator((Class) Job.class).project(job).getBuildDiscarder(), is(instance.getBuildDiscarder()));
    }

    @Test
    public void decoratesNonStandardJobBySetter() throws Exception {
        BuildRetentionBranchProperty instance = new BuildRetentionBranchProperty(new LogRotator(5, 5, 5, 5));
        Job job = mock(Job.class);
        when(job.getBuildDiscarder()).thenReturn(new LogRotator(0, 0, 0, 0));
        instance.jobDecorator((Class) Job.class).project(job);
        verify(job).setBuildDiscarder(instance.getBuildDiscarder());
    }

    @Test
    public void decoratesIsBestEffort() throws Exception {
        BuildRetentionBranchProperty instance = new BuildRetentionBranchProperty(new LogRotator(5, 5, 5, 5));
        Job job = mock(Job.class);
        when(job.getBuildDiscarder()).thenReturn(new LogRotator(0, 0, 0, 0));
        doThrow(new IOException("boom")).when(job).setBuildDiscarder(new LogRotator(5, 5, 5, 5));
        instance.jobDecorator((Class) Job.class).project(job);
        assertTrue("The IOException was caught and ignored", true);
        verify(job).setBuildDiscarder(instance.getBuildDiscarder());
    }
}
