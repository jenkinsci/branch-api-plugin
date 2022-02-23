package integration.harness;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.HealthReport;
import hudson.model.HealthReportingAction;
import hudson.tasks.Builder;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import jenkins.util.NonLocalizable;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

public class MockHealthReportBuildStep extends hudson.tasks.Builder {

    private static final AtomicInteger score = new AtomicInteger();

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        HealthReport report = new HealthReport((score.incrementAndGet() & 0xffff) % 101, new NonLocalizable("Score " + score.get()));
        build.addAction(new MyHealthReportingAction(report));
        return super.prebuild(build, listener);
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<Builder> {
        public DescriptorImpl() {
        }

        public Builder newInstance(StaplerRequest req, JSONObject data) {
            throw new UnsupportedOperationException();
        }
    }

    private static class MyHealthReportingAction implements HealthReportingAction {
        private final HealthReport report;

        public MyHealthReportingAction(HealthReport report) {
            this.report = report;
        }

        @Override
        public HealthReport getBuildHealth() {
            return report;
        }

        @CheckForNull
        @Override
        public String getIconFileName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getDisplayName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getUrlName() {
            return null;
        }
    }
}
