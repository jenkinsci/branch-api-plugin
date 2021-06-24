package integration;

import hudson.model.TopLevelItem;
import integration.harness.BasicMultiBranchProject;
import jenkins.scm.impl.mock.MockSCMController;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.FlagRule;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;

import static junit.framework.TestCase.assertTrue;

public class UpdatingFromXmlWithDisabledFlagTest {

    /**
     * All tests in this class only create items and do not affect other global configuration, thus we trade test
     * execution time for the restriction on only touching items.
     */
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Rule
    public FlagRule flagRule = FlagRule.systemProperty("jenkins.branch.MultiBranchProject.fireSCMSourceBuildsAfterSave", "false");

    @Before
    public void cleanOutAllItems() throws Exception {
        for (TopLevelItem i : r.getInstance().getItems()) {
            i.delete();
        }
    }

    @Test
    public void given_multibranch_when_createFromXml_is_disabled_then_hasNoItems() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.cloneBranch("foo", "master", "feature");
            c.addFile("foo", "feature", "add new feature", "FEATURE", "new".getBytes());
            String configXml = IOUtils.toString(getClass().getResourceAsStream("UpdatingFromXmlTest/config.xml")).replace("fixme", c.getId());
            BasicMultiBranchProject prj = (BasicMultiBranchProject) r.jenkins.createProjectFromXML("foo", new ReaderInputStream(new StringReader(configXml)));
            r.waitUntilNoActivity();
            assertTrue(prj.getItems().isEmpty());
        }
    }

    @Test
    public void given_multibranch_when_updateFromXml_is_disabled_then_hasNoItems() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.cloneBranch("foo", "master", "feature");
            c.addFile("foo", "feature", "add new feature", "FEATURE", "new".getBytes());
            String configXml = IOUtils.toString(getClass().getResourceAsStream("UpdatingFromXmlTest/config.xml")).replace("fixme", c.getId());
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.updateByXml((Source) new StreamSource(new StringReader(configXml)));
            r.waitUntilNoActivity();
            assertTrue(prj.getItems().isEmpty());
        }
    }

}
