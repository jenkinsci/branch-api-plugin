/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

import hudson.model.Action;
import hudson.model.Items;
import hudson.scm.NullSCM;
import java.util.Collections;
import jenkins.scm.impl.NullSCMSource;
import jenkins.scm.impl.mock.MockSCMHead;
import org.junit.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class BranchTest {

    @Test
    public void given_livePre2Branch_when_deserialized_then_objectInvariantsCorrect() throws Exception {
        Branch b = (Branch) Items.XSTREAM2
                .fromXML("<branch class=\"jenkins.branch.Branch\" >\n"
                        + "        <sourceId>any-id</sourceId>\n"
                        + "        <head class=\"jenkins.scm.impl.mock.MockSCMHead\">\n"
                        + "          <name>quicker</name>\n"
                        + "        </head>\n"
                        + "        <scm class=\"hudson.scm.NullSCM\"/>\n"
                        + "        <properties class=\"java.util.concurrent.CopyOnWriteArrayList\">\n"
                        + "          <jenkins.branch.NoTriggerBranchProperty/>\n"
                        + "        </properties>\n"
                        + "      </branch>");
        assertThat(b.getName(), is("quicker"));
        assertThat(b.getHead(), instanceOf(MockSCMHead.class));
        assertThat(b.getActions(), is(Collections.<Action>emptyList()));
        assertThat(b.getScm(), instanceOf(NullSCM.class));
        assertThat(b.getSourceId(), is("any-id"));
        assertThat(b.getProperties(), contains(instanceOf(NoTriggerBranchProperty.class)));
    }

    @Test
    public void given_deadPre2Branch_when_deserialized_then_objectInvariantsCorrect() throws Exception {
        Branch b = (Branch) Items.XSTREAM2
                .fromXML("<branch class=\"jenkins.branch.Branch$Dead\" >\n"
                        + "        <sourceId>::NullSCMSource::</sourceId>\n"
                        + "        <head class=\"jenkins.scm.impl.mock.MockSCMHead\">\n"
                        + "          <name>quicker</name>\n"
                        + "        </head>\n"
                        + "        <scm class=\"hudson.scm.NullSCM\"/>\n"
                        + "        <properties class=\"java.util.concurrent.CopyOnWriteArrayList\">\n"
                        + "          <jenkins.branch.NoTriggerBranchProperty/>\n"
                        + "        </properties>\n"
                        + "      </branch>");
        assertThat(b.getName(), is("quicker"));
        assertThat(b.getHead(), instanceOf(MockSCMHead.class));
        assertThat(b.getActions(), is(Collections.<Action>emptyList()));
        assertThat(b.getScm(), instanceOf(NullSCM.class));
        assertThat(b.getSourceId(), is(NullSCMSource.ID));
        assertThat(b.getProperties(), contains(instanceOf(NoTriggerBranchProperty.class)));
    }
}
