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
import hudson.model.Action;
import java.util.ArrayList;
import java.util.List;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.actions.TagAction;

public class MockSCMHead extends SCMHead {
    private final TagAction tag;

    public MockSCMHead(@NonNull String name, boolean tag) {
        super(name);
        this.tag = tag ? new TagAction() : null;
    }

    @NonNull
    @Override
    public List<? extends Action> getAllActions() {
        if (tag != null) {
            List<Action> actions = new ArrayList<Action>(super.getAllActions());
            actions.add(tag);
            return actions;
        } else {
            return super.getAllActions();
        }
    }
}
