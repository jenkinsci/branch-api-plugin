package jenkins.branch;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.FreeStyleProject;
import hudson.model.TopLevelItem;
import integration.harness.BasicBranchProperty;
import integration.harness.BasicMultiBranchProject;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * @author Frédéric Laugier
 */
public class CustomNameBranchPropertyTest {
    /**
     * All tests in this class only create items and do not affect other global configuration, thus we trade test
     * execution time for the restriction on only touching items.
     */
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Before
    public void cleanOutAllItems() throws Exception {
        for (TopLevelItem i : r.getInstance().getItems()) {
            i.delete();
        }
    }

    @Test
    public void patternValues() throws Exception {
        assertThat(new CustomNameBranchProperty(null).getPattern(), nullValue());
        assertThat(new CustomNameBranchProperty("").getPattern(), nullValue());
        assertThat(new CustomNameBranchProperty("   ").getPattern(), nullValue());
        assertThat(new CustomNameBranchProperty("app-{}").getPattern(), is("app-{}"));
        assertThrows(IllegalArgumentException.class, () -> new CustomNameBranchProperty("foobar") );
    }

    @Test
    public void defaultName() throws Exception {
        try (final MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            BranchSource source = new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches()));
            source.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[]{
                    new CustomNameBranchProperty(null)
            }));
            prj.getSourcesList().add(source);
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();

            FreeStyleProject master = prj.getItem("master");
            assertNotNull(master);
            assertNotNull(master.getProperty(BasicBranchProperty.class));

            Branch branch = master.getProperty(BasicBranchProperty.class).getBranch();
            assertNotNull(branch);
            assertNotNull(branch.getProperty(CustomNameBranchProperty.class));
        }
    }

    @Test
    public void customName() throws Exception {
        try (final MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            BranchSource source = new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches()));
            source.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[]{
                    new CustomNameBranchProperty("app-{}")
            }));
            prj.getSourcesList().add(source);
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();

            assertNotNull(prj.getItem("app-master"));
            assertNull(prj.getItem("master"));
        }
    }

    @Test
    public void multipleReplacement() throws Exception {
        try (final MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            BranchSource source = new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches()));
            source.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[]{
                    new CustomNameBranchProperty("app-{}/{}")
            }));
            prj.getSourcesList().add(source);
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();

            assertNotNull(prj.getItem("app-master/master"));
            assertNull(prj.getItem("master"));
            assertNull(prj.getItem("app-master"));
            assertNull(prj.getItem("master/master"));
        }
    }
    @Test
    public void customNameWithMultipleSources() throws Exception {
        try (final MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createRepository("bar");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foobar");
            prj.setCriteria(null);
            BranchSource sourceFoo = new BranchSource(new MockSCMSource(c, "foo", new MockSCMDiscoverBranches()));
            sourceFoo.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[]{
                    new CustomNameBranchProperty("foo-{}")
            }));
            BranchSource sourceBar = new BranchSource(new MockSCMSource(c, "bar", new MockSCMDiscoverBranches()));
            sourceBar.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[]{
                    new CustomNameBranchProperty("bar-{}")
            }));
            prj.getSourcesList().add(sourceFoo);
            prj.getSourcesList().add(sourceBar);
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();

            assertNotNull(prj.getItem("foo-master"));
            assertNotNull(prj.getItem("bar-master"));
            assertNull(prj.getItem("master"));
        }
    }
}
