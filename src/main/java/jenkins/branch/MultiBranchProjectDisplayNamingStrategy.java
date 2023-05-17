/*
 * The MIT License
 *
 * Copyright (c) 2021, Christoph Obexer.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.metadata.ObjectMetadataAction;

import static java.lang.String.format;
import static org.apache.commons.lang.StringUtils.isBlank;

/**
 * Possible Display naming strategies.
 *
 * @since 2.7.1
 */
public enum MultiBranchProjectDisplayNamingStrategy {
    /**
     * Use the name as specified by the scm connector. (traditional behaviour)
     */
    RAW(false) {
        @Override
        public String generateName(@NonNull final String rawName, final String displayName) {
            return rawName;
        }
    },
    /**
     * Use the display name (if available) sourced from the {@link ObjectMetadataAction}.
     */
    OBJECT_DISPLAY_NAME(true) {
        @Override
        public String generateName(@NonNull final String rawName, final String displayName) {
            return displayName;
        }
    },
    /**
     * Use both the raw name and the display name from the {@link ObjectMetadataAction} (if available).
     */
    RAW_AND_OBJECT_DISPLAY_NAME(true) {
        @Override
        public String generateName(@NonNull final String rawName, final String displayName) {
            return isBlank(displayName) ? rawName : format("%s: %s", rawName, displayName);
        }
    },
    ;

    private final boolean needsDisplayName;

    MultiBranchProjectDisplayNamingStrategy(final boolean needsDisplayName) {
        this.needsDisplayName = needsDisplayName;
    }

    public boolean needsDisplayName() {
        return needsDisplayName;
    }

    public abstract String generateName(@NonNull final String rawName, final String displayName);
}
