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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import org.apache.commons.lang.StringUtils;

/**
 * Mangles names that are not nice so that they are safe to use on filesystem.
 * We try to keep names that are alpha-numeric and optionally contain the {@code -} character unmangled as long as they
 * are shorter than {@link #MAX_SAFE_LENGTH} characters. For all other names we mangle. In mangling we try to keep
 * the semantic meaning of separator characters like {@code /\._} and space by mangling as {@code -}. All other
 * characters are encoded using {@code _} as a prefix for the hex code. Finally we inject the hash with {@code .} used
 * to identify the hash portion.
 *
 * @since 2.0.0
 */
public final class NameMangler {

    static final int MAX_SAFE_LENGTH = 32;
    private static final int MIN_HASH_LENGTH = 6;
    private static final int MAX_HASH_LENGTH = 12;

    /**
     * Utility class.
     */
    private NameMangler() {
        throw new IllegalAccessError("Utility class");
    }

    public static String apply(String name) {
        if (name.length() <= MAX_SAFE_LENGTH) {
            boolean unsafe = false;
            boolean first = true;
            for (char c : name.toCharArray()) {
                if (first) {
                    if (c == '-') {
                        // no leading dash
                        unsafe = true;
                        break;
                    }
                    first = false;
                }
                if (!isSafe(c)) {
                    unsafe = true;
                    break;
                }
            }
            // See https://msdn.microsoft.com/en-us/library/aa365247 we need to consistently reserve names across all OS
            if (!unsafe) {
                // we know it is only US-ASCII if we got to here
                switch (name.toLowerCase(Locale.ENGLISH)) {
                    case ".":
                    case "..":
                    case "con":
                    case "prn":
                    case "aux":
                    case "nul":
                    case "com1":
                    case "com2":
                    case "com3":
                    case "com4":
                    case "com5":
                    case "com6":
                    case "com7":
                    case "com8":
                    case "com9":
                    case "lpt1":
                    case "lpt2":
                    case "lpt3":
                    case "lpt4":
                    case "lpt5":
                    case "lpt6":
                    case "lpt7":
                    case "lpt8":
                    case "lpt9":
                        unsafe = true;
                        break;
                    default:
                        if (name.endsWith(".")) {
                            unsafe = true;
                        }
                        break;
                }
            }
            if (!unsafe) {
                return name;
            }
        }
        StringBuilder buf = new StringBuilder(name.length() + 16);
        for (char c : name.toCharArray()) {
            if (isSafe(c)) {
                buf.append(c);
            } else if (c == '/' || c == '\\' || c == ' ' || c == '.' || c == '_') {
                if (buf.length() == 0) {
                    buf.append("0-");
                } else {
                    buf.append('-');
                }
            } else if (c <= 0xff) {
                if (buf.length() == 0) {
                    buf.append("0_");
                } else {
                    buf.append('_');
                }
                buf.append(StringUtils.leftPad(Integer.toHexString(c & 0xff), 2, '0'));
            } else {
                if (buf.length() == 0) {
                    buf.append("0_");
                } else {
                    buf.append('_');
                }
                buf.append(StringUtils.leftPad(Integer.toHexString(((c & 0xffff) >> 8)&0xff), 2, '0'));
                buf.append('_');
                buf.append(StringUtils.leftPad(Integer.toHexString(c & 0xff), 2, '0'));
            }
        }
        // use the digest of the original name
        String digest;
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            byte[] bytes = sha.digest(name.getBytes(StandardCharsets.UTF_8));
            int bits = 0;
            int data = 0;
            StringBuilder dd = new StringBuilder(32);
            for (byte b : bytes) {
                while (bits >= 5) {
                    dd.append(toDigit(data & 0x1f));
                    bits -= 5;
                    data = data >> 5;
                }
                data = data | ((b & 0xff) << bits);
                bits += 8;
            }
            digest = dd.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not installed", e);    // impossible
        }
        if (buf.length() <= MAX_SAFE_LENGTH - MIN_HASH_LENGTH - 1) {
            // we have room to add the min hash
            buf.append('.');
            buf.append(StringUtils.right(digest, MIN_HASH_LENGTH));
            return buf.toString();
        }
        // buf now holds the mangled string, we will now try and rip the middle out to put in some of the digest
        int overage = buf.length() - MAX_SAFE_LENGTH;
        String hash;
        if (overage <= MIN_HASH_LENGTH) {
            hash = "." + StringUtils.right(digest, MIN_HASH_LENGTH) + ".";
        } else if (overage > MAX_HASH_LENGTH) {
            hash = "." + StringUtils.right(digest, MAX_HASH_LENGTH) + ".";
        } else {
            hash = "." + StringUtils.right(digest, overage) + ".";
        }
        int start = (MAX_SAFE_LENGTH - hash.length()) / 2;
        buf.delete(start, start + hash.length() + overage);
        buf.insert(start, hash);
        return buf.toString();
    }

    private static char toDigit(int n) {
        return (char) (n < 10 ? '0' + n : 'a' + n - 10);
    }

    private static boolean isSafe(char c) {
        // we use a smaller set than is strictly possible as we would prefer to mangle outside this set
        return ('a' <= c && c <= 'z')
                || ('A' <= c && c <= 'Z')
                || ('0' <= c && c <= '9')
                || '-' == c;
    }
}
