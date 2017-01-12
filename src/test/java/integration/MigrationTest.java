/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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
 *
 */

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
import static org.hamcrest.Matchers.notNullValue;
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
        System.out.println("Jobs");
        System.out.println("====");
        System.out.println();
        // Assume NFC
        String espana = "España";
        String espanaEncoded = "Espa_f1a.9jabqu";
        String ireland = "Éireann";
        String irelandEncoded = "0_c9ireann.giuvlt";
        String korea = "대한민국";
        String koreaEncoded = "0_b3_00_d5_5c_bb_fc_ad_6d.ufdgbs";
        String korea2 = "특색/새로운";
        String korea2Encoded = "0_d2_b9_c0_c.ps50ht._b8_5c_c6_b4";
        for (MultiBranchProject<?, ?> p : prj.getItems()) {
            System.out.printf("%s ==> %s%n", p.getName(), p.getDisplayName());
            if (p.getName().equals("Espan_03_03a.eqqe01")) {
                // NFD
                espana = "Espa\u006e\u0303a";
                espanaEncoded = "Espan_03_03a.eqqe01";
            }
            if (p.getName().equals("E_03_01ireann.0qtq11")) {
                // NFD
                ireland = "E\u0301ireann";
                irelandEncoded = "E_03_01ireann.0qtq11";
            }
            if (p.getName().equals("0_11_03_1.3gi5g2rs7pg4._6e_11_a8")) {
                // NFD
                korea = "\u1103\u1162\u1112\u1161\u11ab\u1106\u1175\u11ab\u1100\u116e\u11a8";
                koreaEncoded = "0_11_03_1.3gi5g2rs7pg4._6e_11_a8";
                korea2 = "\u1110\u1173\u11a8\u1109\u1162\u11a8/\u1109\u1162\u1105\u1169\u110b\u116e\u11ab";
                korea2Encoded = "0_11_10_1.m479ph0h00p7._6e_11_ab";
            }
            byName.put(p.getName(), p);
            byDisplayName.put(p.getDisplayName(), p);
            for (Job<?, ?> j : p.getItems()) {
                System.out.printf("  %s ==> %s%n", j.getName(), j.getDisplayName());
                jobByName.put(j.getFullName(), j);
                jobByDisplayName.put(j.getFullDisplayName(), j);
            }
        }
        assertThat("Display Names are repo names", byDisplayName.keySet(), containsInAnyOrder(
                "test.example.com",
                ireland,
                "Россия",
                "中国",
                espana,
                korea
        ));
        assertThat("Folder names have been mangled", byName.keySet(), containsInAnyOrder(
                "test-example-com.34nhgh",
                irelandEncoded,  // Éireann
                "0_04_20_04_3.pei3d7._04_38_04_4f", // Россия
                "0_4e_2d_56_fd.m4k0dn", // 中国
                espanaEncoded, // España
                koreaEncoded // 대한민국
        ));

        assertThat("Display Names are branch names", jobByDisplayName.keySet(), containsInAnyOrder(
                "foo » test.example.com » master",
                "foo » test.example.com » feature: browsing/part 1",
                "foo » test.example.com » feature: browsing/part 2",
                "foo » test.example.com » feature: welcome",
                "foo » " + ireland + " » master",
                "foo » " + ireland + " » gné/nua",
                "foo » Россия » master",
                "foo » Россия » особенность/новый",
                "foo » 中国 » master",
                "foo » 中国 » 特征/新",
                "foo » " + espana + " » master",
                "foo » " + espana + " » característica/nuevo",
                "foo » " + korea + " » master",
                "foo » " + korea + " » " + korea2
        ));
        assertThat("Job names have been mangled", jobByName.keySet(), containsInAnyOrder(
                "foo/test-example-com.34nhgh/master",
                "foo/test-example-com.34nhgh/feature_3a-b.jk8rfi.wsing-part-1",
                "foo/test-example-com.34nhgh/feature_3a-b.m8lpav.wsing-part-2",
                "foo/test-example-com.34nhgh/feature_3a-welcome.9dhrtb",
                "foo/" + irelandEncoded + "/master",
                "foo/" + irelandEncoded + "/gn_e9-nua.updi5h",
                "foo/0_04_20_04_3.pei3d7._04_38_04_4f/master",
                "foo/0_04_20_04_3.pei3d7._04_38_04_4f/0_04_3e_0.n168ksdsksof._4b_04_39",
                "foo/0_4e_2d_56_fd.m4k0dn/master",
                "foo/0_4e_2d_56_fd.m4k0dn/0_72_79_5f_81-_65_b0.nt1m48",
                "foo/" + espanaEncoded + "/master",
                "foo/" + espanaEncoded + "/caracter_edstica-nuevo.h5da9f",
                "foo/" + koreaEncoded + "/master",
                "foo/" + koreaEncoded + "/" + korea2Encoded
        ));

        assertThat(prj.getItemByProjectName(ireland), notNullValue());
        assertThat(prj.getItemByProjectName(ireland).getItemByBranchName("gné/nua"), notNullValue());
    }
}
