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

/**
 * Encodes names that are not nice so that they are safe to use as url path segments.
 * We don't want to do a full url encoding, only replace problematic names with {@code %} escaped variants so
 * that when double encoded by Stapler etc we bypass issues.
 *
 * @since 2.0.0
 */
public final class NameEncoder {

    /**
     * Utility class.
     */
    private NameEncoder() {
        throw new IllegalAccessError("Utility class");
    }

    public static String encode(String name) {
        if ("".equals(name)) {
            return "%00";
        }
        if (".".equals(name)) {
            // just enough escaping to bypass the URL segment meaning of ".."
            return "%2E";
        }
        if ("..".equals(name)) {
            // just enough escaping to bypass the URL segment meaning of ".."
            return "%2E.";
        }
        StringBuilder buf = new StringBuilder(name.length() + 16);
        for (char c : name.toCharArray()) {
            /*
              RFC 3986 has this to say about paths and path segments:

                path    = path-abempty    ; begins with "/" or is empty
                        / path-absolute   ; begins with "/" but not "//"
                        / path-noscheme   ; begins with a non-colon segment
                        / path-rootless   ; begins with a segment
                        / path-empty      ; zero characters
                unreserved  = ALPHA / DIGIT / "-" / "." / "_" / "~"
                gen-delims  = ":" / "/" / "?" / "#" / "[" / "]" / "@"

                sub-delims  = "!" / "$" / "&" / "'" / "(" / ")"
                            / "*" / "+" / "," / ";" / "="

                path-abempty  = *( "/" segment )
                path-absolute = "/" [ segment-nz *( "/" segment ) ]
                path-noscheme = segment-nz-nc *( "/" segment )
                path-rootless = segment-nz *( "/" segment )
                path-empty    = 0<pchar>

                segment       = *pchar
                segment-nz    = 1*pchar
                segment-nz-nc = 1*( unreserved / pct-encoded / sub-delims / "@" )
                            ; non-zero-length segment without any colon ":"

                pchar         = unreserved / pct-encoded / sub-delims / ":" / "@"

            Now how does this apply to us?

            Well we know that this is a segment within an URL that has a scheme and we know we are not the first
            segment. That means that we have to comply with the rules of segment-nz.

            For our case, we only want to encode things that could cause problems in URLs. Our response is expected
            to get encoded again anyway, so we just need to encode the parts of gen-delims that are not valid in pchar!

            In other words "/" / "?" / "#" / "[" / "]" everything else is going to be encoded for us anyway.

            Note for safety, we will also encode "\" as some browsers convert that to a "/" so we want it double encoded
            on the browser url. And finally we need to escape our escape character "%"
          */
            switch (c) {
                case '#':
                    buf.append("%23");
                    break;
                case '%':
                    buf.append("%25");
                    break;
                case '/':
                    buf.append("%2F");
                    break;
                case '?':
                buf.append("%3F");
                break;
                case '[':
                    buf.append("%5B");
                    break;
                case ']':
                    buf.append("%5D");
                    break;
                case '\\':
                    buf.append("%5C");
                    break;
                default:
                    buf.append(c);
                    break;
            }
        }
        return buf.toString();
    }

    public static String decode(String name) {
        if ("%00".equals(name)) {
            return "";
        }
        if ("%2E".equals(name)) {
            return ".";
        }
        if ("%2E.".equals(name)) {
            return "..";
        }
        if (name.indexOf('%') == -1) {
            return name;
        }
        StringBuilder buf = new StringBuilder(name.length() + 16);
        int matchIndex = 0;
        char match = 0;
        for (char c : name.toCharArray()) {
            switch (matchIndex) {
                case 1:
                    if (c == '2' || c == '3' || c == '5') {
                        match = c;
                        matchIndex = 2;
                    } else {
                        buf.append('%');
                        buf.append(c);
                        matchIndex = 0;
                    }
                    break;
                case 2:
                    switch (match) {
                        case '2':
                            switch (c) {
                                case '3':
                                    buf.append('#');
                                    break;
                                case '5':
                                    buf.append('%');
                                    break;
                                case 'F':
                                    buf.append('/');
                                    break;
                                default:
                                    buf.append('%');
                                    buf.append(match);
                                    buf.append(c);
                                    break;
                            }
                            break;
                        case '3':
                            if (c == 'F') {
                                buf.append('?');
                            } else {
                                buf.append('%');
                                buf.append(match);
                                buf.append(c);
                            }
                            break;
                        case '5':
                            switch (c) {
                                case 'B':
                                    buf.append('[');
                                    break;
                                case 'C':
                                    buf.append('\\');
                                    break;
                                case 'D':
                                    buf.append(']');
                                    break;
                                default:
                                    buf.append('%');
                                    buf.append(match);
                                    buf.append(c);
                                    break;
                            }
                            break;
                        default:
                            buf.append('%');
                            buf.append(match);
                            buf.append(c);
                            break;
                    }
                    matchIndex = 0;
                    break;
                default:
                    if (c == '%') {
                        matchIndex = 1;
                    } else {
                        matchIndex = 0;
                        buf.append(c);
                    }
                    break;
            }
            switch (c) {
                case '/':
                    buf.append("%2F");
                    break;
                case '?':
                    buf.append("%3F");
                    break;
                case '#':
                    buf.append("%23");
                    break;
                case '[':
                    buf.append("%5B");
                    break;
                case ']':
                    buf.append("%5D");
                    break;
                case '\\':
                    buf.append("%5C");
                    break;
                default:
                    buf.append(c);
                    break;
            }
        }
        return buf.toString();
    }
}
