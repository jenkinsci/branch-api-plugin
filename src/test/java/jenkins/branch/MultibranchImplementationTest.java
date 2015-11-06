/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import java.io.IOException;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import jenkins.branch.harness.MultiBranchImpl;
import jenkins.scm.api.SCMSourceOwner;

public class MultibranchImplementationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    public static boolean check = true;

    @Test
    @Ignore("JENKINS-31432 neds to be fixed")
    public void createMultiBranchProjectWithListenerTest() throws IOException {
        j.jenkins.createProject(MultiBranchImpl.class, "test");
        Assert.assertTrue("SCMSourceOwner.getSCMSources() should never throw NullPointerException", check);
    }

    @TestExtension
    public static class SCMSourceOwnerListener extends ItemListener {

        @Override
        public void onUpdated(Item item) {
            if (item instanceof SCMSourceOwner) {
                try {
                    ((SCMSourceOwner) item).getSCMSources();
                } catch (NullPointerException e) {
                    check = false;
                    throw e;
                }
            }
        }

    }

}
