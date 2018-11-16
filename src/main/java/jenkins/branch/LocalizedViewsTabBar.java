/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

import hudson.model.View;
import hudson.views.DefaultViewsTabBar;
import hudson.views.ViewsTabBar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nonnull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * JENKINS-39300 work-around. Replace with {@link DefaultViewsTabBar} once JENKINS-39300 is in baseline core.
 */
// TODO replace with DefaultViewsTabBar after JENKINS-39300 is available in core.
@Restricted(NoExternalUse.class)
public class LocalizedViewsTabBar extends ViewsTabBar {
    /**
     * Sorts the views by {@link View#getDisplayName()}.
     *
     * @param views the views.
     * @return the sorted views
     * @since 2.0
     */
    @Nonnull
    @Restricted(NoExternalUse.class)
    @SuppressWarnings("unused") // invoked from stapler view
    public List<View> sort(@Nonnull List<? extends View> views) {
        List<View> result = new ArrayList<>(views);
        Collections.sort(result, new Comparator<View>() {
            @Override
            public int compare(View o1, View o2) {
                return o1.getDisplayName().compareTo(o2.getDisplayName());
            }
        });
        return result;
    }

}
