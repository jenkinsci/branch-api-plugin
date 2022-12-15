package jenkins.branch;

import hudson.model.FreeStyleProject;
import hudson.model.TopLevelItem;
import integration.harness.HealthReportingMultiBranchProject;

import java.util.Collections;

import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMSource;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

public class PrimaryBranchHealthMetricTest {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Before
    public void cleanOutAllItems() throws Exception {
        for (TopLevelItem i : r.getInstance().getItems()) {
            i.delete();
        }
    }

    @Test
    public void given__multibranch_without_primary__when__reporting_health__then__empty() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", "stable");
            HealthReportingMultiBranchProject prj = r.jenkins.createProject(HealthReportingMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            prj.getHealthMetrics().replaceBy(Collections.singletonList(new PrimaryBranchHealthMetric()));
            prj.invalidateBuildHealthReports();
            assertThat(prj.getBuildHealthReports(), hasSize(0));
        }
    }

    @Test
    public void given__multibranch_with_primary__when__reporting_health__then__primary_health() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", "stable");
            c.setPrimaryBranch("foo", "master");
            HealthReportingMultiBranchProject prj = r.jenkins.createProject(HealthReportingMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            prj.getHealthMetrics().replaceBy(Collections.singletonList(new PrimaryBranchHealthMetric()));
            prj.invalidateBuildHealthReports();
            FreeStyleProject master = prj.getItem("master");
            assertThat("We now have the master branch", master, notNullValue());
            assertThat("'master' branch's reports should be exactly the reports as it is the primary branch",
                    prj.getBuildHealthReports(),
                    containsInAnyOrder(master.getBuildHealthReports().toArray())
            );
        }
    }

    @Test
    public void given__multibranch_with_primary__when__reporting_health__then__non_empty() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", "stable");
            // we want to ensure that the implementation is based on the SCM's reported primary branch and not just
            // hard-coded to the string 'master'
            c.setPrimaryBranch("foo", "stable");
            HealthReportingMultiBranchProject prj = r.jenkins.createProject(HealthReportingMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            prj.getHealthMetrics().replaceBy(Collections.singletonList(new PrimaryBranchHealthMetric()));
            prj.invalidateBuildHealthReports();
            FreeStyleProject master = prj.getItem("master");
            assertThat("We now have the master branch", master, notNullValue());
            FreeStyleProject stable = prj.getItem("stable");
            assertThat("We now have the stable branch", stable, notNullValue());
            assertThat(
                    "none of 'master' branch's reports should be present as it is not the primary branch",
                    prj.getBuildHealthReports(),
                    everyItem(not(is(in(master.getBuildHealthReports().toArray()))))
            );
            assertThat(
                    "'stable' branch's reports should be exactly the reports as it is the primary branch",
                    prj.getBuildHealthReports(),
                    containsInAnyOrder(stable.getBuildHealthReports().toArray())
            );
        }
    }
}
