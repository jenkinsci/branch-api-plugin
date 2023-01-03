package integration;

import hudson.model.TopLevelItem;
import integration.harness.BasicMultiBranchProject;
import jenkins.scm.impl.mock.MockSCMController;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static junit.framework.TestCase.assertTrue;

public class UpdatingFromXmlTest {

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
    public void given_multibranch_when_createFromXml_then_hasItems() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.cloneBranch("foo", "master", "feature");
            c.addFile("foo", "feature", "add new feature", "FEATURE", "new".getBytes());
            String configXml = IOUtils.toString(getClass().getResourceAsStream("UpdatingFromXmlTest/config.xml"), StandardCharsets.UTF_8).replace("fixme", c.getId());
            BasicMultiBranchProject prj = (BasicMultiBranchProject) r.jenkins.createProjectFromXML("foo", new ReaderInputStream(new StringReader(configXml), StandardCharsets.UTF_8));
            r.waitUntilNoActivity();
            assertTrue(prj.getItems().size() > 0);
        }
    }

    @Test
    public void given_multibranch_when_updateFromXml_then_hasItems() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.cloneBranch("foo", "master", "feature");
            c.addFile("foo", "feature", "add new feature", "FEATURE", "new".getBytes());
            String configXml = IOUtils.toString(getClass().getResourceAsStream("UpdatingFromXmlTest/config.xml"), StandardCharsets.UTF_8).replace("fixme", c.getId());
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.updateByXml((Source) new StreamSource(new StringReader(configXml)));
            r.waitUntilNoActivity();
            assertTrue(prj.getItems().size() > 0);
        }
    }

}
