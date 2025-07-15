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
import integration.harness.BasicMultiBranchProjectFactory;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.OrganizationFolder;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMNavigator;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsSessionRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

public class MigrationTest {
    private static final Logger LOGGER = Logger.getLogger(MigrationTest.class.getName());

    private static MockSCMController c;

    @Rule
    public JenkinsSessionRule r = new JenkinsSessionRule();

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
    public void createdFromScratch() throws Throwable {
        r.then(j -> {
                OrganizationFolder foo = j.createProject(OrganizationFolder.class, "foo");
                foo.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
                foo.getProjectFactories()
                        .replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
                foo.scheduleBuild2(0).getFuture().get();
                j.waitUntilNoActivity();
                assertDataMigrated(foo);
        });
        r.then(j -> {
                TopLevelItem foo = j.jenkins.getItem("foo");
                assertDataMigrated(foo);
        });
    }

    @Test
    public void createdFromScratch_full_reload() throws Throwable {
        r.then(j -> {
                OrganizationFolder foo = j.createProject(OrganizationFolder.class, "foo");
                foo.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
                foo.getProjectFactories()
                        .replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
                foo.scheduleBuild2(0).getFuture().get();
                j.waitUntilNoActivity();
                j.jenkins.reload();
                assertDataMigrated(foo);
        });
        r.then(j -> {
                TopLevelItem foo = j.jenkins.getItem("foo");
                assertDataMigrated(foo);
        });
    }

    @Test
    public void createdFromScratch_folder_reload() throws Throwable {
        r.then(j -> {
                OrganizationFolder foo = j.createProject(OrganizationFolder.class, "foo");
                foo.getSCMNavigators().add(new MockSCMNavigator(c, new MockSCMDiscoverBranches()));
                foo.getProjectFactories()
                        .replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
                foo.scheduleBuild2(0).getFuture().get();
                j.waitUntilNoActivity();
                foo.doReload();
                assertDataMigrated(foo);
        });
        r.then(j -> {
                TopLevelItem foo = j.jenkins.getItem("foo");
                assertDataMigrated(foo);
        });
    }

    private void assertDataMigrated(TopLevelItem foo) throws Exception {
        assertThat(foo, instanceOf(OrganizationFolder.class));
        OrganizationFolder prj = (OrganizationFolder) foo;
        Map<String, MultiBranchProject> byName = new HashMap<>();
        Map<String, MultiBranchProject> byDirName = new HashMap<>();
        Map<String, MultiBranchProject> byDisplayName = new HashMap<>();
        Map<String, Job> jobByName = new HashMap<>();
        Map<String, Job> jobByDirName = new HashMap<>();
        Map<String, Job> jobByDisplayName = new HashMap<>();
        LOGGER.log(Level.INFO, "Jobs");
        LOGGER.log(Level.INFO, "====");
        // Assume NFC
        String espana = "España";
        String espanaMangled = "Espa_f1a.9jabqu";
        String ireland = "Éireann";
        String irelandMangled = "0_c9ireann.giuvlt";
        String korea = "대한민국";
        String koreaMangled = "0_b3_00_d5_5c_bb_fc_ad_6d.ufdgbs";
        String korea2 = "특색/새로운";
        String korea2Mangled = "0_d2_b9_c0_c.ps50ht._b8_5c_c6_b4";
        // Assume not windows-1252
        String russia = "Россия";
        String russiaMangled = "0_04_20_04_3.pei3d7._04_38_04_4f";
        String china = "中国";
        String chinaMangled = "0_4e_2d_56_fd.m4k0dn";
        for (MultiBranchProject<?, ?> p : prj.getItems()) {
            String dirName = p.getRootDir().getName();
            LOGGER.log(Level.INFO, String.format("%s ==> %s ==> %s == \"%s\"%n", dirName, p.getName(), p.getDisplayName(), asJavaString(p.getDisplayName())));
            byName.put(p.getName(), p);
            if (dirName.equals("Espan_03_03a.eqqe01")) {
                // NFD
                espana = "Espa\u006e\u0303a";
                espanaMangled = "Espan_03_03a.eqqe01";
            }
            if (dirName.equals("Espan_cc_01_92a.po41g5")) {
                // Windows-1252
                espana = "Espan\u00cc\u0192a";
                espanaMangled = "Espan_cc_01_92a.po41g5";
            }
            if (dirName.equals("E_03_01ireann.0qtq11")) {
                // NFD
                ireland = "E\u0301ireann";
                irelandMangled = "E_03_01ireann.0qtq11";
            }
            if (dirName.equals("E_cc_ff_fdireann.k4nq8h")) {
                // Windows-1252
                ireland = "E\u00cc\ufffdireann";
                irelandMangled = "E_cc_ff_fdireann.k4nq8h";
            }
            if (dirName.equals("0_d0_a0_d.co5tjl6h6dbk._d1_ff_fd")) {
                // Windows-1252
                russia = "\u00d0\u00a0\u00d0\u00be\u00d1\ufffd\u00d1\ufffd\u00d0\u00b8\u00d1\ufffd";
                russiaMangled = "0_d0_a0_d.co5tjl6h6dbk._d1_ff_fd";
            }
            if (dirName.equals("0_e4_b8_ad_e5_20_3a_bd.546c3v")) {
                // Windows-1252
                china = "\u00e4\u00b8\u00ad\u00e5\u203a\u00bd";
                chinaMangled = "0_e4_b8_ad_e5_20_3a_bd.546c3v";
            }
            if (dirName.equals("0_11_03_1.3gi5g2rs7pg4._6e_11_a8")) {
                // NFD
                korea = "\u1103\u1162\u1112\u1161\u11ab\u1106\u1175\u11ab\u1100\u116e\u11a8";
                koreaMangled = "0_11_03_1.3gi5g2rs7pg4._6e_11_a8";
            }
            if (dirName.equals("0_e1_20_1.78185nsomvje._20_20_a8")) {
                // Windows-1252
                korea = "\u00e1\u201e\u0192\u00e1\u2026\u00a2\u00e1\u201e\u2019\u00e1\u2026\u00a1\u00e1\u2020\u00ab"
                        + "\u00e1\u201e\u2020\u00e1\u2026\u00b5\u00e1\u2020\u00ab\u00e1\u201e\u20ac\u00e1\u2026\u00ae"
                        + "\u00e1\u2020\u00a8";
                koreaMangled = "0_e1_20_1.78185nsomvje._20_20_a8";
            }
            byDirName.put(dirName, p);
            byDisplayName.put(p.getDisplayName(), p);
            for (Job<?, ?> j : p.getItems()) {
                String jobDirName = prj.getRootDir().getName() + "/" + p.getRootDir().getName() + "/" + j.getRootDir().getName();
                LOGGER.log(Level.INFO, String.format("  %s ==> %s ==> %s == \"%s\" ", jobDirName, j.getName(), j.getDisplayName(),
                        asJavaString(j.getDisplayName())));
                if (j.getName().equals("0_11_10_1.m479ph0h00p7._6e_11_ab")) {
                    // NFD
                    korea2 = "\u1110\u1173\u11a8\u1109\u1162\u11a8/\u1109\u1162\u1105\u1169\u110b\u116e\u11ab";
                    korea2Mangled = "0_11_10_1.m479ph0h00p7._6e_11_ab";
                }
                jobByName.put(j.getFullName(), j);
                jobByDirName.put(jobDirName, j);
                jobByDisplayName.put(j.getFullDisplayName(), j);
            }
        }
        assertThat("Display Names are repo names", byDisplayName.keySet(), containsInAnyOrder(
                "test.example.com",
                ireland,
                russia,
                china,
                espana,
                korea
        ));
        assertThat("Directory names have been mangled", byDirName.keySet(), containsInAnyOrder(
                "test-example-com.34nhgh",
                irelandMangled,  // Éireann
                russiaMangled, // Россия
                chinaMangled, // 中国
                espanaMangled, // España
                koreaMangled // 대한민국
        ));
        assertThat("Folder names have been minimal url path segment pre-encoded", byName.keySet(), containsInAnyOrder(
                "test.example.com",
                ireland,  // Éireann
                russia, // Россия
                china, // 中国
                espana, // España
                korea // 대한민국
        ));

        assertThat("Display Names are branch names", jobByDisplayName.keySet(), containsInAnyOrder(
                "foo » test.example.com » master",
                "foo » test.example.com » feature: browsing/part 1",
                "foo » test.example.com » feature: browsing/part 2",
                "foo » test.example.com » feature: welcome",
                "foo » " + ireland + " » master",
                "foo » " + ireland + " » gné/nua",
                "foo » " + russia + " » master",
                "foo » " + russia + " » особенность/новый",
                "foo » " + china + " » master",
                "foo » " + china + " » 特征/新",
                "foo » " + espana + " » master",
                "foo » " + espana + " » característica/nuevo",
                "foo » " + korea + " » master",
                "foo » " + korea + " » " + korea2
        ));
        assertThat("Job directory names have been mangled", jobByDirName.keySet(), containsInAnyOrder(
                "foo/test-example-com.34nhgh/master",
                "foo/test-example-com.34nhgh/feature_3a-b.jk8rfi.wsing-part-1",
                "foo/test-example-com.34nhgh/feature_3a-b.m8lpav.wsing-part-2",
                "foo/test-example-com.34nhgh/feature_3a-welcome.9dhrtb",
                "foo/" + irelandMangled + "/master",
                "foo/" + irelandMangled + "/gn_e9-nua.updi5h",
                "foo/" + russiaMangled + "/master",
                "foo/" + russiaMangled + "/0_04_3e_0.n168ksdsksof._4b_04_39",
                "foo/" + chinaMangled + "/master",
                "foo/" + chinaMangled + "/0_72_79_5f_81-_65_b0.nt1m48",
                "foo/" + espanaMangled + "/master",
                "foo/" + espanaMangled + "/caracter_edstica-nuevo.h5da9f",
                "foo/" + koreaMangled + "/master",
                "foo/" + koreaMangled + "/" + korea2Mangled
        ));

        assertThat("Job names have been minimal url path segment pre-encoded", jobByName.keySet(), containsInAnyOrder(
                "foo/test.example.com/master",
                "foo/test.example.com/feature: browsing%2Fpart 1",
                "foo/test.example.com/feature: browsing%2Fpart 2",
                "foo/test.example.com/feature: welcome",
                "foo/" + ireland + "/master",
                "foo/" + ireland + "/gné%2Fnua",
                "foo/" + russia + "/master",
                "foo/" + russia + "/особенность%2Fновый",
                "foo/" + china + "/master",
                "foo/" + china + "/特征%2F新",
                "foo/" + espana + "/master",
                "foo/" + espana + "/característica%2Fnuevo",
                "foo/" + korea + "/master",
                "foo/" + korea + "/특색%2F새로운"
        ));

        assertThat(prj.getItemByProjectName(ireland), notNullValue());
        assertThat(prj.getItemByProjectName(ireland).getItemByBranchName("gné/nua"), notNullValue());
    }

    private CharSequence asJavaString(String rawString) {
        StringBuilder b = new StringBuilder();
        for (char c: rawString.toCharArray()) {
            if (c >=32 && c < 128) {
                b.append(c);
            } else {
                b.append("\\u").append(StringUtils.leftPad(Integer.toHexString(c&0xffff), 4, '0'));
            }
        }
        return b;
    }
}
