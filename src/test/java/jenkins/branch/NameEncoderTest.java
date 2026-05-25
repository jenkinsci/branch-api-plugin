/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class NameEncoderTest {

    @Test
    void smokes() {
        assertRoundTrip("test");
        assertRoundTrip(".");
        assertRoundTrip("..");
        assertRoundTrip("test/foo");
        assertRoundTrip("test%bar");
        assertRoundTrip("test #1");
    }

    @Test
    void safeNames() {
        assertRoundTrip("foo");
        assertRoundTrip("foo-bar");
        assertRoundTrip("foo bar");
        assertRoundTrip("foo/bar");
        assertRoundTrip("foo\\bar");
    }

    @Test
    void reservedNames() {
        assertRoundTrip(".");
        assertRoundTrip("..");
        assertRoundTrip("con");
        assertRoundTrip("prn");
        assertRoundTrip("aux");
        assertRoundTrip("nul");
        assertRoundTrip("com1");
        assertRoundTrip("com2");
        assertRoundTrip("com3");
        assertRoundTrip("com4");
        assertRoundTrip("com5");
        assertRoundTrip("com6");
        assertRoundTrip("com7");
        assertRoundTrip("com8");
        assertRoundTrip("com9");
        assertRoundTrip("lpt1");
        assertRoundTrip("lpt2");
        assertRoundTrip("lpt3");
        assertRoundTrip("lpt4");
        assertRoundTrip("lpt5");
        assertRoundTrip("lpt6");
        assertRoundTrip("lpt7");
        assertRoundTrip("lpt8");
        assertRoundTrip("lpt9");
    }

    @Test
    void slashNames() {
        assertRoundTrip("foo/bar");
        assertRoundTrip("foo/bar/fu manchu");
        assertRoundTrip("foo/bar/fu manchu/1");
        assertRoundTrip("foo/bar/fu manchu/12");
        assertRoundTrip("foo/bar/fu manchu/123");
        assertRoundTrip("foo/bar/fu manchu/1234");
        assertRoundTrip("foo/bar/fu manchu/12345");
        assertRoundTrip("foo/bar/fu manchu/123456");
        assertRoundTrip("foo/bar/fu manchu/1234567");
        assertRoundTrip("foo/bar/fu manchu/12345678");
        assertRoundTrip("foo/bar/fu manchu/123456789");
        assertRoundTrip("foo/bar/fu manchu/1234567890");
        assertRoundTrip("foo/bar/fu manchu/1234567890a");
        assertRoundTrip("foo/bar/fu manchu/1234567890ab");
        assertRoundTrip("foo/bar/fu manchu/1234567890abc");
        assertRoundTrip("foo/bar/fu manchu/1234567890abce");
        assertRoundTrip("foo/bar/fu manchu/1234567890abcef");
        assertRoundTrip("foo/bar/fu manchu/1234567890abcefg");
    }

    @Test
    void longNames() {
        assertRoundTrip("cafebabedeadbeefcafebabedeadbeef");
        assertRoundTrip("cafebabedeadbeefcafebabedeadbeefcafebabedeadbeef");
        assertRoundTrip("cafebabedeadbeefcafebabeDeadbeefcafebabedeadbeef");
        assertRoundTrip("cafebabedeadbeefcafebabedeadbeef1");
        assertRoundTrip("cafebabedeadbeefcafebabedeadbeef2");
    }

    @Test
    void nonSafeNames() {
        assertRoundTrip("Is maith liom criospaí");
        assertRoundTrip("Ich liebe Fußball");
        assertRoundTrip("我喜欢披萨");
        assertRoundTrip("特征/新");
        assertRoundTrip("특색/새로운");
        assertRoundTrip("gné/nua");
        assertRoundTrip("característica/nuevo");
        assertRoundTrip("особенность/новый");
    }

    @Test
    void spain() {
        assertRoundTrip("Espana");
        assertRoundTrip("España");
        assertRoundTrip("Espa\u006e\u0303a");
    }

    @Test
    void ireland() {
        assertRoundTrip("Eireann");
        assertRoundTrip("Éireann");
        assertRoundTrip("E\u0301ireann");
    }

    private static void assertRoundTrip(String name) {
        assertThat(NameEncoder.decode(NameEncoder.encode(name)), equalTo(name));
    }
}
