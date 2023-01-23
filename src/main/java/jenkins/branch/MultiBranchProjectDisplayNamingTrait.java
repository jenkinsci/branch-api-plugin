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

import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.ListBoxModel;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Discovery;

/**
 * A {@link SCMSourceTrait} that controls how to set display names for {@link SCMHead}s
 * with additional information like merge/change/pull requests.
 *
 * @since 2.7.1
 */
public class MultiBranchProjectDisplayNamingTrait extends SCMSourceTrait {
    /**
     * The display naming strategy.
     */
    @NonNull
    private final MultiBranchProjectDisplayNamingStrategy displayNamingStrategy;

    /**
     * Constructor for both stapler and programmatic instantiation.
     *
     * @param displayNamingStrategy the {@link MultiBranchProjectDisplayNamingStrategy}.
     */
    @DataBoundConstructor
    public MultiBranchProjectDisplayNamingTrait(MultiBranchProjectDisplayNamingStrategy displayNamingStrategy) {
        this.displayNamingStrategy = displayNamingStrategy;
    }

    /**
     * Gets the {@link MultiBranchProjectDisplayNamingStrategy}.
     *
     * @return the {@link MultiBranchProjectDisplayNamingStrategy}.
     */
    @NonNull
    public MultiBranchProjectDisplayNamingStrategy getDisplayNamingStrategy() {
        return displayNamingStrategy;
    }

    /**
     * Our descriptor.
     */
    @Symbol("multiBranchProjectDisplayNaming")
    @Extension
    @Discovery
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.MultiBranchProjectDisplayNamingTrait_TraitDisplayName();
        }

        /**
         * Populates the display naming strategy options.
         *
         * @return the display naming strategy options.
         */
        @NonNull
        @Restricted(NoExternalUse.class)
        @SuppressWarnings("unused") // stapler
        public ListBoxModel doFillDisplayNamingStrategyItems() {
            ListBoxModel result = new ListBoxModel();
            result.add(Messages.MultiBranchProjectDisplayNamingTrait_Raw(), MultiBranchProjectDisplayNamingStrategy.RAW.name());
            result.add(Messages.MultiBranchProjectDisplayNamingTrait_DisplayName(), MultiBranchProjectDisplayNamingStrategy.OBJECT_DISPLAY_NAME.name());
            result.add(Messages.MultiBranchProjectDisplayNamingTrait_RawAndDisplayName(), MultiBranchProjectDisplayNamingStrategy.RAW_AND_OBJECT_DISPLAY_NAME.name());
            return result;
        }
    }
}
