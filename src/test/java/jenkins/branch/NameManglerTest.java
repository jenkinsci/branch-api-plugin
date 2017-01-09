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

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class NameManglerTest {

    @Test
    public void safeNames() {
        assertThat(NameMangler.apply("foo"), is("foo"));
        assertThat(NameMangler.apply("foo-bar"), is("foo-bar"));
        assertThat(NameMangler.apply("foo bar"), is("foo_bar-074vf0"));
    }

    @Test
    public void reservedNames() {
        assertThat(NameMangler.apply("."), is(".-tkvgu3"));
        assertThat(NameMangler.apply(".."), is("..-mpdh40"));
        assertThat(NameMangler.apply("con"), is("con-lkb1gc"));
        assertThat(NameMangler.apply("prn"), is("prn-n7ievs"));
        assertThat(NameMangler.apply("aux"), is("aux-75carl"));
        assertThat(NameMangler.apply("nul"), is("nul-3r8i6h"));
        assertThat(NameMangler.apply("com1"), is("com1-k0564q"));
        assertThat(NameMangler.apply("com2"), is("com2-ni698t"));
        assertThat(NameMangler.apply("com3"), is("com3-8ad2lm"));
        assertThat(NameMangler.apply("com4"), is("com4-j2s67g"));
        assertThat(NameMangler.apply("com5"), is("com5-8fdiog"));
        assertThat(NameMangler.apply("com6"), is("com6-v0rf0v"));
        assertThat(NameMangler.apply("com7"), is("com7-v5tsfp"));
        assertThat(NameMangler.apply("com8"), is("com8-o02opt"));
        assertThat(NameMangler.apply("com9"), is("com9-3bmuo4"));
        assertThat(NameMangler.apply("lpt1"), is("lpt1-cstki2"));
        assertThat(NameMangler.apply("lpt2"), is("lpt2-136d1i"));
        assertThat(NameMangler.apply("lpt3"), is("lpt3-cvdm8e"));
        assertThat(NameMangler.apply("lpt4"), is("lpt4-upc9bu"));
        assertThat(NameMangler.apply("lpt5"), is("lpt5-u2mmru"));
        assertThat(NameMangler.apply("lpt6"), is("lpt6-n50rnj"));
        assertThat(NameMangler.apply("lpt7"), is("lpt7-9eh7vi"));
        assertThat(NameMangler.apply("lpt8"), is("lpt8-gm9r02"));
        assertThat(NameMangler.apply("lpt9"), is("lpt9-55srnr"));
    }

    @Test
    public void slashNames() {
        assertThat(NameMangler.apply("foo/bar"), is("foo_bar-nj9av9"));
        assertThat(NameMangler.apply("foo/bar/fu manchu"), is("foo_bar_fu_manchu-k630nd"));
        assertThat(NameMangler.apply("foo/bar/fu manchu/1"), is("foo_bar_fu_manchu_1-vgnr4j"));
        assertThat(NameMangler.apply("foo/bar/fu manchu/12"), is("foo_bar_fu_manchu_12-6urklv"));
        assertThat(NameMangler.apply("foo/bar/fu manchu/123"), is("foo_bar_fu_manchu_123-nap1h9"));
        assertThat(NameMangler.apply("foo/bar/fu manchu/1234"), is("foo_bar_fu_manchu_1234-kstl5e"));
        assertThat(NameMangler.apply("foo/bar/fu manchu/12345"), is("foo_bar_fu_manchu_12345-i2apnp"));
        assertThat(NameMangler.apply("foo/bar/fu manchu/123456"), is( "foo_bar_fu_manchu_123456-8vabkm"));
        assertThat(NameMangler.apply("foo/bar/fu manchu/1234567"), is("foo_bar_fu_manchu_1234567-5h1u4c"));
        assertThat(NameMangler.apply("foo/bar/fu manchu/12345678"), is("foo_bar_fu_m-vrohpg-chu_12345678"));
        assertThat(NameMangler.apply("foo/bar/fu manchu/123456789"), is("foo_bar_fu_m-403j04-hu_123456789"));
        assertThat(NameMangler.apply("foo/bar/fu manchu/1234567890"), is("foo_bar_fu_m-jrvb2f-u_1234567890"));
        assertThat(NameMangler.apply("foo/bar/fu manchu/1234567890a"), is("foo_bar_fu_m-1dcfvj-_1234567890a"));
        assertThat(NameMangler.apply("foo/bar/fu manchu/1234567890ab"), is("foo_bar_fu_m-mdl920-1234567890ab"));
        assertThat(NameMangler.apply("foo/bar/fu manchu/1234567890abc"), is("foo_bar_fu_m-aql4gn-234567890abc"));
        assertThat(NameMangler.apply("foo/bar/fu manchu/1234567890abce"), is("foo_bar_fu_m-bt3j2r-34567890abce"));
        assertThat(NameMangler.apply("foo/bar/fu manchu/1234567890abcef"), is("foo_bar_fu_m-jjum74-4567890abcef"));
        assertThat(NameMangler.apply("foo/bar/fu manchu/1234567890abcefg"), is("foo_bar_fu_m-vddees-567890abcefg"));
    }

    @Test
    public void longNames() {
        assertThat(NameMangler.apply("cafebabedeadbeefcafebabedeadbeef"), is("cafebabedeadbeefcafebabedeadbeef"));
        assertThat(NameMangler.apply("cafebabedeadbeefcafebabedeadbeefcafebabedeadbeef"), is("cafebabed-98h82o58mhfo-edeadbeef"));
        assertThat(NameMangler.apply("cafebabedeadbeefcafebabeDeadbeefcafebabedeadbeef"), is("cafebabed-a67pve49oi0n-edeadbeef"));
        assertThat(NameMangler.apply("cafebabedeadbeefcafebabedeadbeef1"), is("cafebabedead-dfcoms-abedeadbeef1"));
        assertThat(NameMangler.apply("cafebabedeadbeefcafebabedeadbeef2"), is("cafebabedead-m0u50r-abedeadbeef2"));
    }

    @Test
    public void nonSafeNames() {
        assertThat(NameMangler.apply("Is maith liom criospaí"), is("Is_maith_liom_criospa%ed-0g5uh9"));
        assertThat(NameMangler.apply("Ich liebe Fußball"), is("Ich_liebe_Fu%dfball-fp53tq"));
        assertThat(NameMangler.apply("我喜欢披萨"), is("%11%62%9c%55-f9c1g4-%ab%62%28%84"));
        assertThat(NameMangler.apply("特征/新"), is("%79%72%81%5f_%b0%65-nt1m48"));
        assertThat(NameMangler.apply("특색/새로운"), is("%b9%d2%c9%c0-ps50ht-%5c%b8%b4%c6"));
        assertThat(NameMangler.apply("gné/nua"), is("gn%e9_nua-updi5h"));
        assertThat(NameMangler.apply("característica/nuevo"), is("caracter%edstica_nuevo-h5da9f"));
        assertThat(NameMangler.apply("особенность/новый"), is("%3e%04%41-n168ksdsksof-%04%39%04"));
    }
}
