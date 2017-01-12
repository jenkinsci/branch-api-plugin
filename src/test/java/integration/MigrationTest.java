package integration;

import hudson.model.Job;
import hudson.model.TopLevelItem;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.OrganizationFolder;
import jenkins.scm.impl.mock.MockSCMController;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

public class MigrationTest {

    private static MockSCMController c;

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @BeforeClass
    public static void setupSCM() throws IOException {
        c = MockSCMController.recreate("ee708b58-864e-415c-a8fe-f5e458cda8d9");
        c.createRepository("test.example.com");
        c.createRepository("Éireann");
        c.createRepository("Россия");
        c.createRepository("中国");
        c.createRepository("España");
        c.createRepository("대한민국");
        c.createBranch("test.example.com", "feature: welcome");
        c.createBranch("test.example.com", "feature: browsing/part 1");
        c.createBranch("test.example.com", "feature: browsing/part 2");
        c.createBranch("Éireann", "gné/nua");
        c.createBranch("Россия", "особенность/новый");
        c.createBranch("中国", "特征/新");
        c.createBranch("España", "característica/nuevo");
        c.createBranch("대한민국", "특색/새로운");
    }

    @AfterClass
    public static void closeSCM() {
        IOUtils.closeQuietly(c);
        c = null;
    }

    @Test
    @LocalData
    public void nameMangling() throws IOException {
        TopLevelItem foo = j.jenkins.getItem("foo");
        assertThat(foo, instanceOf(OrganizationFolder.class));
        OrganizationFolder prj = (OrganizationFolder) foo;
        Map<String, MultiBranchProject> byName = new HashMap<>();
        Map<String, MultiBranchProject> byDisplayName = new HashMap<>();
        Map<String, Job> jobByName = new HashMap<>();
        Map<String, Job> jobByDisplayName = new HashMap<>();
        for (MultiBranchProject<?, ?> p : prj.getItems()) {
            byName.put(p.getName(), p);
            byDisplayName.put(p.getDisplayName(), p);
            for (Job<?, ?> j : p.getItems()) {
                jobByName.put(j.getFullName(), j);
                jobByDisplayName.put(j.getFullDisplayName(), j);
            }
        }
        assertThat("Display Names are repo names", byDisplayName.keySet(), containsInAnyOrder(
                "test.example.com",
                "Éireann",
                "Россия",
                "中国",
                "España",
                "대한민국"
        ));
        assertThat("Folder names have been mangled", byName.keySet(), containsInAnyOrder(
                "test.example.com",
                "%c9ireann@giuvlt",  // Éireann
                "%20%04%3e%04@pei3d7@%38%04%4f%04", // Россия
                "%2d%4e%fd%56@m4k0dn", // 中国
                "Espa%f1a@9jabqu", // España
                "%00%b3%5c%d5%fc%bb%6d%ad@ufdgbs" // 대한민국
        ));

        assertThat("Display Names are branch names", jobByDisplayName.keySet(), containsInAnyOrder(
                "foo » test.example.com » master",
                "foo » test.example.com » feature: browsing/part 1",
                "foo » test.example.com » feature: browsing/part 2",
                "foo » test.example.com » feature: welcome",
                "foo » Éireann » master",
                "foo » Éireann » gné/nua",
                "foo » Россия » master",
                "foo » Россия » особенность/новый",
                "foo » 中国 » master",
                "foo » 中国 » 特征/新",
                "foo » España » master",
                "foo » España » característica/nuevo",
                "foo » 대한민국 » master",
                "foo » 대한민국 » 특색/새로운"
        ));
        assertThat("Job names have been mangled", jobByName.keySet(), containsInAnyOrder(
                "foo/test.example.com/master",
                "foo/test.example.com/feature%3a_b@jk8rfi@wsing_part_1",
                "foo/test.example.com/feature%3a_b@m8lpav@wsing_part_2",
                "foo/test.example.com/feature%3a_welcome@9dhrtb",
                "foo/%c9ireann@giuvlt/master",
                "foo/%c9ireann@giuvlt/gn%e9_nua@updi5h",
                "foo/%20%04%3e%04@pei3d7@%38%04%4f%04/master",
                "foo/%20%04%3e%04@pei3d7@%38%04%4f%04/%3e%04%41@n168ksdsksof@%04%39%04",
                "foo/%2d%4e%fd%56@m4k0dn/master",
                "foo/%2d%4e%fd%56@m4k0dn/%79%72%81%5f_%b0%65@nt1m48",
                "foo/Espa%f1a@9jabqu/master",
                "foo/Espa%f1a@9jabqu/caracter%edstica_nuevo@h5da9f",
                "foo/%00%b3%5c%d5%fc%bb%6d%ad@ufdgbs/master",
                "foo/%00%b3%5c%d5%fc%bb%6d%ad@ufdgbs/%b9%d2%c9%c0@ps50ht@%5c%b8%b4%c6"
        ));
    }
}
