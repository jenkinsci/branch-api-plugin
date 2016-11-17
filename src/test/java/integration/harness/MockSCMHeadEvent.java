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
import java.util.Collections;
import java.util.Map;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;

public class MockSCMHeadEvent extends SCMHeadEvent<String> {

    private final MockSCMController controller;

    private final String repository;

    private final String head;

    private final String revision;

    public MockSCMHeadEvent(@NonNull Type type, MockSCMController controller,
                            String repository, String head, String revision) {
        super(type, head);
        this.controller = controller;
        this.repository = repository;
        this.head = head;
        this.revision = revision;
    }

    @Override
    public boolean isMatch(@NonNull SCMNavigator navigator) {
        return navigator instanceof MockSCMNavigator
                && ((MockSCMNavigator) navigator).getControllerId().equals(controller.getId());
    }

    @NonNull
    @Override
    public String getSourceName() {
        return repository;
    }

    @NonNull
    @Override
    public Map<SCMHead, SCMRevision> heads(@NonNull SCMSource source) {
        if (!(source instanceof MockSCMSource)) {
            return Collections.emptyMap();
        }
        if (!(((MockSCMSource) source).getControllerId().equals(controller.getId()))) {
            return Collections.emptyMap();
        }
        if (!repository.equals(((MockSCMSource) source).getRepository())) {
            return Collections.emptyMap();
        }
        MockSCMHead key = new MockSCMHead(head);
        return Collections.<SCMHead, SCMRevision>singletonMap(
                key,
                revision != null ? new MockSCMRevision(key, revision) : null
        );
    }
}
