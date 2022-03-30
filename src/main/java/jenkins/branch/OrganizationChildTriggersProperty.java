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

import antlr.ANTLRException;
import com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Items;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.DescribableList;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.jvnet.tiger_types.Types;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Configures the {@link Trigger}s for the {@link MultiBranchProject} children of an {@link OrganizationFolder}.
 *
 * @since 2.4.0
 */
public class OrganizationChildTriggersProperty extends OrganizationFolderProperty<OrganizationFolder> {
    /**
     * Our {@link Trigger}s.
     */
    @NonNull
    private final List<Trigger<?>> templates;
    /**
     * The lazily populated XML serialized form of the template triggers to assist in faster change detection.
     */
    @CheckForNull
    private transient Map<Trigger<?>, String> templateXML;

    /**
     * Our constructor.
     *
     * @param templates the templates.
     */
    @DataBoundConstructor
    public OrganizationChildTriggersProperty(List<Trigger<?>> templates) {
        this.templates = new ArrayList<>(Util.fixNull(templates));
    }

    /**
     * Our constructor.
     *
     * @param templates the templates.
     */
    public OrganizationChildTriggersProperty(Trigger<?>... templates) {
        this(Arrays.asList(templates));
    }

    /**
     * Creates a new default instance of this property.
     *
     * @return a new default instance of this property.
     */
    public static OrganizationChildTriggersProperty newDefaultInstance() {
        try {
            return new OrganizationChildTriggersProperty(new PeriodicFolderTrigger("1d"));
        } catch (ANTLRException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Gets the current template triggers.
     *
     * @return the current template triggers.
     */
    public List<Trigger<?>> getTemplates() {
        return new DescribableList<>(Saveable.NOOP, templates);
    }

    /**
     * Get the lazily cached XML representation of the supplied template trigger.
     *
     * @param template the template.
     * @return the XML representation of the template.
     */
    @NonNull
    private String templateXML(@NonNull Trigger<?> template) {
        if (templateXML == null) {
            templateXML = new ConcurrentHashMap<>();
        }
        return templateXML.computeIfAbsent(template, Items.XSTREAM2::toXML);
    }

    /**
     * Compares the supplied {@link Trigger} to our corresponding template.
     *
     * @param template the template.
     * @param trigger the trigger.
     * @return {@code true} if the two configurations are the same.
     */
    private boolean sameAsTemplate(Trigger<?> template, Trigger<?> trigger) {
        // The Trigger contract does not mandate overriding Object#equals() so we need to compare somehow
        // Because Trigger can have transient state, we just compare the XML serialization because
        // that is what would be persisted and it shouldn't be different if they are the same.
        return templateXML(template).equals(Items.XSTREAM2.toXML(trigger));
    }

    /**
     * Clones a {@link Trigger}.
     *
     * @param template the template.
     * @param <T> the type of template.
     * @return a clone of the template.
     */
    @SuppressWarnings("unchecked")
    private <T extends Trigger> T newInstance(T template) {
        // The Trigger contract does not implement Cloneable, so we clone through the XML serialized form.
        return (T) Items.XSTREAM2.fromXML(templateXML(template));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorate(@NonNull MultiBranchProject<?, ?> child, @NonNull TaskListener listener)
            throws IOException {
        // triggers typically contain non-serialized state that is required to be retained
        // in order to ensure correct intervals
        Map<TriggerDescriptor, Trigger<?>> childTriggers = child.getTriggers();
        Map<Trigger<?>, Boolean> toRemove = new IdentityHashMap<>(childTriggers.size());
        List<Trigger<?>> toAddOrUpdate = new ArrayList<>();
        childTriggers.forEach((d, t) -> toRemove.put(t, Boolean.TRUE));
        for (Trigger<?> template : templates) {
            Trigger<?> current = childTriggers.get(template.getDescriptor());
            if (current != null) {
                toRemove.remove(current);
                if (!sameAsTemplate(template, current)) {
                    toAddOrUpdate.add(newInstance(template));
                }
            } else {
                toAddOrUpdate.add(newInstance(template));
            }
        }
        for (Trigger<?> t : toAddOrUpdate) {
            child.addTrigger(t);
        }
        toRemove.forEach((trigger, ignore) -> child.removeTrigger(trigger));
    }

    /**
     * Our descriptor.
     */
    @Extension
    public static class DescriptorImpl extends OrganizationFolderPropertyDescriptor {
        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.OrganizationChildTriggersProperty_DisplayName();
        }

        /**
         * Gets the applicable trigger descriptors.
         * @return the applicable trigger descriptors.
         */
        @Restricted(DoNotUse.class) // stapler only
        public List<TriggerDescriptor> getTriggerDescriptors() {
            // TODO We really would like to only consider the MultiBranchProjectDescriptor types that could be
            //  created by the available MultiBranchProjectFactory instances.
            List<MultiBranchProjectDescriptor> mbpDescriptors = MultiBranchProjectDescriptor.all()
                    .stream()
                    .filter(MultiBranchProjectDescriptor.class::isInstance)
                    .map(MultiBranchProjectDescriptor.class::cast).collect(
                    Collectors.toList());
            List<TriggerDescriptor> result = new ArrayList<>();
            for (TriggerDescriptor triggerDescriptor : Trigger.all()) {
                if (triggerDescriptor instanceof TimerTrigger.DescriptorImpl) {
                    // Periodically unless otherwise run is our replacement
                    continue;
                }
                if (triggerDescriptor instanceof SCMTrigger.DescriptorImpl) {
                    // HACK we don't have an Item so need to hard-code this exclusion
                    continue;
                }
                boolean atLeastOne = false;
                for (MultiBranchProjectDescriptor d: mbpDescriptors) {
                    if (d.isApplicable(triggerDescriptor)) {
                        atLeastOne = true;
                        break;
                    }
                }
                if (!atLeastOne) {
                    // not applicable to any MultibranchProjectDescriptor, so skip
                    continue;
                }
                // TODO if only we had TriggerDescriptor.isApplicableTo(TopLevelItemDescriptor) we could avoid
                //  type parameter extraction
                Type bt = Types.getBaseClass(triggerDescriptor.clazz, Trigger.class);
                if (bt instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType) bt;
                    Class t = Types.erasure(pt.getActualTypeArguments()[0]);
                    if (t.isAssignableFrom(MultiBranchProject.class)) {
                        result.add(triggerDescriptor);
                    }
                }
            }
            return result;
        }
    }
}
