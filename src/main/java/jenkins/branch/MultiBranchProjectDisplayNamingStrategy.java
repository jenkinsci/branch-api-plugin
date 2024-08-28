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
import java.util.Objects;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import org.jvnet.localizer.Localizable;

import static java.lang.String.format;
import static org.apache.commons.lang.StringUtils.isBlank;

/**
 * Possible Display naming strategies.
 *
 * @since 2.7.1
 */
public enum MultiBranchProjectDisplayNamingStrategy {
    /**
     * Use the display name (if available) sourced from the {@link ObjectMetadataAction}.
     */
    OBJECT_DISPLAY_NAME(true, Messages._MultiBranchProjectDisplayNamingTrait_DisplayName()) {
        @Override
        public String generateName(@NonNull final String rawName, final String displayName) {
            return isBlank(displayName) ? rawName : displayName;
        }
    },
    /**
     * Use both the raw name and the display name from the {@link ObjectMetadataAction} (if available).
     */
    RAW_AND_OBJECT_DISPLAY_NAME(true, Messages._MultiBranchProjectDisplayNamingTrait_RawAndDisplayName()) {
        @Override
        public String generateName(@NonNull final String rawName, final String displayName) {
            if (isBlank(displayName)) {
                return rawName;
            }

            if (Objects.equals(rawName, displayName)) {
                return rawName;
            }

            // The raw name provided here in the context of pull requests is the pull request ID
            // We tidy up the ID so that they display consistently between SCMs
            String cleanedUpBranchName = rawName;
            if (cleanedUpBranchName.startsWith("MR-") || cleanedUpBranchName.startsWith("PR-")) {
                cleanedUpBranchName = "#" + cleanedUpBranchName.substring(3);
            }

            return format("%s (%s)", displayName, cleanedUpBranchName);
        }
    },
    ;

    private final boolean needsObjectDisplayName;
    private final Localizable displayName;

    MultiBranchProjectDisplayNamingStrategy(final boolean needsObjectDisplayName, final Localizable displayName) {
        this.needsObjectDisplayName = needsObjectDisplayName;
        this.displayName = displayName;
    }

    public boolean needsObjectDisplayName() {
        return needsObjectDisplayName;
    }

    public String getDisplayName() {
        return displayName.toString();
    }

    public abstract String generateName(@NonNull final String rawName, final String displayName);
}
