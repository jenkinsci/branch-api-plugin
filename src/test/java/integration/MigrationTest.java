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
        for (MultiBranchProject<?, ?> p : prj.getItems()) {
            byName.put(p.getName(), p);
            byDisplayName.put(p.getDisplayName(), p);
            for (Job<?, ?> j : p.getItems()) {
                jobByName.put(j.getFullName(), j);
                jobByDisplayName.put(j.getFullDisplayName(), j);
            }
        }
        System.out.println(byName.keySet());
        System.out.println(jobByName.keySet());
        assertThat("Display Names are repo names", byDisplayName.keySet(), containsInAnyOrder(
                "test.example.com",
                "Éireann",
                "Россия",
                "中国",
                "España",
                "대한민국"
        ));
        assertThat("Folder names have been mangled", byName.keySet(), containsInAnyOrder(
                "test-example-com.34nhgh",
                "0_c9ireann.giuvlt",  // Éireann
                "0_20_04_3e_0.pei3d7._38_04_4f_04", // Россия
                "0_2d_4e_fd_56.m4k0dn", // 中国
                "Espa_f1a.9jabqu", // España
                "0_00_b3_5c_d5_fc_bb_6d_ad.ufdgbs" // 대한민국
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
                "foo/test-example-com.34nhgh/master",
                "foo/test-example-com.34nhgh/feature_3a-b.jk8rfi.wsing-part-1",
                "foo/test-example-com.34nhgh/feature_3a-b.m8lpav.wsing-part-2",
                "foo/test-example-com.34nhgh/feature_3a-welcome.9dhrtb",
                "foo/0_c9ireann.giuvlt/master",
                "foo/0_c9ireann.giuvlt/gn_e9-nua.updi5h",
                "foo/0_20_04_3e_0.pei3d7._38_04_4f_04/master",
                "foo/0_20_04_3e_0.pei3d7._38_04_4f_04/0_3e_04_4.n168ksdsksof._04_39_04",
                "foo/0_2d_4e_fd_56.m4k0dn/master",
                "foo/0_2d_4e_fd_56.m4k0dn/0_79_72_81_5f-_b0_65.nt1m48",
                "foo/Espa_f1a.9jabqu/master",
                "foo/Espa_f1a.9jabqu/caracter_edstica-nuevo.h5da9f",
                "foo/0_00_b3_5c_d5_fc_bb_6d_ad.ufdgbs/master",
                "foo/0_00_b3_5c_d5_fc_bb_6d_ad.ufdgbs/0_b9_d2_c9_c.ps50ht._5c_b8_b4_c6"
        ));

        assertThat(prj.getItemByProjectName("Éireann"), notNullValue());
        assertThat(prj.getItemByProjectName("Éireann").getItemByBranchName("gné/nua"), notNullValue());
    }
}
