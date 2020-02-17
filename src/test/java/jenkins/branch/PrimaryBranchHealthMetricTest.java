package jenkins.branch;

import hudson.model.FreeStyleProject;
import hudson.model.TopLevelItem;
import integration.harness.BasicMultiBranchProject;
import java.util.Collections;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMSource;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.*;

public class PrimaryBranchHealthMetricTest {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Before
    public void cleanOutAllItems() throws Exception {
        //Use item map to avoid the permissions check
        for (TopLevelItem i : r.getInstance().getItemMap().values()) {
            i.delete();
        }
    }

    @Test
    public void given__multibranch_without_primary__when__reporting_health__then__empty() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", "stable");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
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
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches())));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            prj.getHealthMetrics().replaceBy(Collections.singletonList(new PrimaryBranchHealthMetric()));
            prj.invalidateBuildHealthReports();
            FreeStyleProject master = prj.getItem("master");
            assertThat("We now have the master branch", master, notNullValue());
            assertThat(prj.getBuildHealthReports(), containsInAnyOrder(master.getBuildHealth()));
        }
    }

    @Test
    public void given__multibranch_with_primary__when__reporting_health__then__non_empty() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo", "stable");
            c.setPrimaryBranch("foo", "stable");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
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
            assertThat(prj.getBuildHealthReports(), not(containsInAnyOrder(master.getBuildHealth())));
            assertThat(prj.getBuildHealthReports(), containsInAnyOrder(stable.getBuildHealth()));
        }
    }


}
