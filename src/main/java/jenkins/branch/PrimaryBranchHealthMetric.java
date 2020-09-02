/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetric;
import com.cloudbees.hudson.plugins.folder.health.FolderHealthMetricDescriptor;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Actionable;
import hudson.model.HealthReport;
import hudson.model.Item;
import hudson.model.Job;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A {@link FolderHealthMetric} for {@link MultiBranchProject} instances that only reports the health of the primary
 * branch.
 *
 * @since 2.4.0
 */
public class PrimaryBranchHealthMetric extends FolderHealthMetric {

    /**
     * Constructor.
     */
    @DataBoundConstructor
    public PrimaryBranchHealthMetric() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Reporter reporter() {
        return new ReporterImpl();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Type getType() {
        return Type.IMMEDIATE_TOP_LEVEL_ITEMS;
    }

    /**
     * Our descriptor.
     */
    @Extension
    public static class DescriptorImpl extends FolderHealthMetricDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Health of the primary branch of a repository";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractFolder> containerType) {
            return MultiBranchProject.class.isAssignableFrom(containerType);
        }
    }

    /**
     * Our {@link Reporter}.
     */
    private static class ReporterImpl implements Reporter {
        /**
         * The primary instance reports.
         */
        @CheckForNull
        private List<HealthReport> reports;

        /**
         * {@inheritDoc}
         */
        @Override
        public void observe(Item item) {
            if ((item instanceof Actionable)
                    && ((Actionable) item).getAction(PrimaryInstanceMetadataAction.class) != null) {
                if (reports == null) {
                    reports = new ArrayList<>();
                    reports.addAll(Util.fixNull(getHealthReports(item)));
                }
            }
        }

        public static List<HealthReport> getHealthReports(Item item) {
            if (item instanceof Job) {
                return ((Job<?,?>) item).getBuildHealthReports();
            } else if (item instanceof Folder) {
                return ((Folder) item).getBuildHealthReports();
            } else {
                try {
                    Method getBuildHealth = item.getClass().getMethod("getBuildHealthReports");
                    return (List<HealthReport>) getBuildHealth.invoke(item);
                } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException var2) {
                    HealthReport report = getHealthReport(item);
                    return report == null ? null : Collections.singletonList(report);
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<HealthReport> report() {
            return Util.fixNull(reports);
        }
    }
}
