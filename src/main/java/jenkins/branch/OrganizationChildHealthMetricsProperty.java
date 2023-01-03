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

import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetric;
import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetricDescriptor;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Items;
import hudson.model.TaskListener;
import hudson.util.DescribableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A property that sets the health metrics for immediate children of an {@link OrganizationFolder}.
 *
 * @since 2.4.0
 */
public class OrganizationChildHealthMetricsProperty extends OrganizationFolderProperty<OrganizationFolder> {
    /**
     * Our templates.
     */
    private final List<FolderHealthMetric> templates;
    /**
     * The lazily populated XML serialized form of the templates to assist in faster change detection.
     */
    @CheckForNull
    private transient Map<FolderHealthMetric, String> templateXML;

    /**
     * Constructor.
     *
     * @param templates the folder health metrics.
     */
    @DataBoundConstructor
    public OrganizationChildHealthMetricsProperty(List<FolderHealthMetric> templates) {
        this.templates = new ArrayList<>(Util.fixNull(templates));
    }

    /**
     * Gets the current template metrics.
     *
     * @return the current template metrics.
     */
    public List<FolderHealthMetric> getTemplates() {
        return Collections.unmodifiableList(templates);
    }

    /**
     * Get the lazily cached XML representation of the supplied template trigger.
     *
     * @param template the template.
     * @return the XML representation of the template.
     */
    @NonNull
    private String templateXML(@NonNull FolderHealthMetric template) {
        if (templateXML == null) {
            templateXML = new ConcurrentHashMap<>();
        }
        return templateXML.computeIfAbsent(template, Items.XSTREAM2::toXML);
    }

    /**
     * Compares the supplied {@link FolderHealthMetric} to our corresponding template.
     *
     * @param template the template.
     * @param trigger the metric.
     * @return {@code true} if the two configurations are the same.
     */
    private boolean sameAsTemplate(FolderHealthMetric template, FolderHealthMetric trigger) {
        return templateXML(template).equals(Items.XSTREAM2.toXML(trigger));
    }

    /**
     * Clones a {@link FolderHealthMetric}.
     *
     * @param template the template.
     * @param <T> the type of template.
     * @return a clone of the template.
     */
    @SuppressWarnings("unchecked")
    private <T extends FolderHealthMetric> T newInstance(T template) {
        // The FolderHealthMetric contract does not implement Cloneable, so we clone through the XML serialized form.
        return (T) Items.XSTREAM2.fromXML(templateXML(template));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void decorate(@NonNull MultiBranchProject<?, ?> child, @NonNull TaskListener listener)
            throws IOException {
        DescribableList<FolderHealthMetric, FolderHealthMetricDescriptor> metrics = child.getHealthMetrics();
        List<FolderHealthMetric> toAdd = new ArrayList<>(templates);
        boolean updated = false;
        List<FolderHealthMetric> update = new ArrayList<>(metrics);
        OUTER:
        for (ListIterator<FolderHealthMetric> currentIter = update.listIterator(); currentIter.hasNext(); ) {
            FolderHealthMetric current = currentIter.next();
            for (Iterator<FolderHealthMetric> templateIter = toAdd.iterator(); templateIter.hasNext(); ) {
                FolderHealthMetric healthMetric = templateIter.next();
                if (current.getClass().equals(healthMetric.getClass())) {
                    templateIter.remove();
                    if (!sameAsTemplate(healthMetric, current)) {
                        updated = true;
                        currentIter.set(newInstance(healthMetric));
                    }
                    continue OUTER;
                }
            }
            currentIter.remove();
        }
        for (FolderHealthMetric healthMetric : toAdd) {
            updated = true;
            update.add(newInstance(healthMetric));
        }
        if (updated) {
            metrics.replaceBy(update);
        }
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
            return Messages.OrganizationChildHealthMetricsProperty_DisplayName();
        }

        /**
         * Health metrics that can be configured for a {@link MultiBranchProject}.
         *
         * @return the health metric descriptors.
         */
        @Restricted(DoNotUse.class) // stapler only
        @NonNull
        public List<FolderHealthMetricDescriptor> getHealthMetricDescriptors() {
            List<FolderHealthMetricDescriptor> r = new ArrayList<>();
            for (FolderHealthMetricDescriptor d : FolderHealthMetricDescriptor.all()) {
                if (d.isApplicable(MultiBranchProject.class)) {
                    r.add(d);
                }
            }
            return r;
        }

        /**
         * Creates the default templates to use if none have been configured.
         *
         * @return the default templates to use if none have been configured
         */
        @Restricted(DoNotUse.class) // stapler only
        @NonNull
        public List<FolderHealthMetric> getDefaultTemplates() {
            List<FolderHealthMetric> metrics = new ArrayList<>();
            for (FolderHealthMetricDescriptor d : FolderHealthMetricDescriptor.all()) {
                if (d.isApplicable(MultiBranchProject.class)) {
                    FolderHealthMetric metric = d.createDefault();
                    if (metric != null) {
                        metrics.add(metric);
                    }
                }
            }
            return metrics;
        }
    }
}
