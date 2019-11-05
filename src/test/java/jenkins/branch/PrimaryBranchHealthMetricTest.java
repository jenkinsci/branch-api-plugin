package jenkins.branch;

import hudson.model.FreeStyleProject;
import hudson.model.TopLevelItem;
import integration.harness.HealthReportingMultiBranchProject;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMSource;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
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
                    containsInAnyOrder(asMatchers(master.getBuildHealthReports(), Matchers::is))
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
                    "'master' branch's reports should not be present as it is not the primary branch",
                    prj.getBuildHealthReports(),
                    not(
                            containsInAnyOrder(asMatchers(master.getBuildHealthReports(), Matchers::is))
                    )
            );
            assertThat(
                    "'stable' branch's reports should be exactly the reports as it is the primary branch",
                    prj.getBuildHealthReports(),
                    containsInAnyOrder(asMatchers(stable.getBuildHealthReports(), Matchers::is))
            );
        }
    }

    /**
     * Helper for matching a list of instances with {@link Matchers#containsInAnyOrder(Collection)} or {@link
     * Matchers#contains(List)} as these expect a list of {@link Matcher} instance not a list of instances so you will
     * get a test failure due to a class cast exception because of type erasure.
     *
     * @param instances the instances you want to match.
     * @param matcherFunction the function to use for converting instances to matchers, normally {@link
     *         Matchers#is(Object)}.
     * @param <T> the type of instance.
     * @param <V> the type of object matched by the resulting matcher.
     * @return a list of {@link Matcher} instance, one for every instance in the input list.
     */
    private static <T,V> List<Matcher<V>> asMatchers(List<T> instances, Function<T, Matcher<V>> matcherFunction) {
        return instances.stream().map(matcherFunction).collect(Collectors.toList());
    }


}
