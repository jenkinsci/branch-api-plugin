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

import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.util.AlternativeUiTextProvider;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Provides the alternative text for {@code AbstractItem.TASK_NOUN} when running on newer versions of Jenkins.
 *
 * @since 2.0.8
 */
@Restricted(NoExternalUse.class)
@Extension
public class TaskNounUiTextProvider extends AlternativeUiTextProvider {
    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(TaskNounUiTextProvider.class.getName());
    /**
     * Either {@code AbstractItem.TASK_NOUN} or {@code null}. This field will be removed once the baseline version of
     * Jenkins has the {@code AbstractItem.TASK_NOUN} field.
     */
    private final Message<AbstractItem> taskNoun;

    /**
     * Default constructor.
     */
    public TaskNounUiTextProvider() {
        this.taskNoun = AbstractItem.TASK_NOUN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> String getText(Message<T> text, T context) {
        if (text == taskNoun && (context instanceof MultiBranchProject || context instanceof OrganizationFolder)) {
            return Messages.TaskNounUiTextProvider_TaskNoun();
        }
        return null;
    }
}
