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
import java.net.MalformedURLException;
import java.net.URL;
import jenkins.scm.api.ChangeRequestSCMHead;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.actions.ChangeRequestAction;

public class MockChangeRequestSCMHead extends ChangeRequestSCMHead {
    private final ChangeRequestActionImpl action;

    public MockChangeRequestSCMHead(Integer number, String target) {
        super("CR-" + number);
        action = new ChangeRequestActionImpl(number, target);
    }

    @NonNull
    @Override
    public ChangeRequestAction getChangeRequestAction() {
        return action;
    }

    public Integer getNumber() {
        return action.number;
    }

    private static class ChangeRequestActionImpl extends ChangeRequestAction {
        private final Integer number;
        private final String target;

        public ChangeRequestActionImpl(Integer number, String target) {
            this.number = number;
            this.target = target;
        }

        @Override
        public String getId() {
            return Integer.toString(number);
        }

        @Override
        public URL getURL() {
            try {
                return new URL("http://changes.example.com/" + number);
            } catch (MalformedURLException e) {
                return null;
            }
        }

        @Override
        public String getTitle() {
            return String.format("Change request #%d", number);
        }

        @Override
        public String getAuthor() {
            return "bob";
        }

        @Override
        public String getAuthorDisplayName() {
            return "Bob Smith";
        }

        @Override
        public String getAuthorEmail() {
            return "bob@example.com";
        }

        @Override
        public SCMHead getTarget() {
            return target == null ? null : new MockSCMHead(target, false);
        }
    }
}
