/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
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

import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleProject;
import hudson.model.ListView;
import hudson.model.Result;
import hudson.model.TopLevelItem;
import hudson.views.JobColumn;
import hudson.views.StatusColumn;
import hudson.views.WeatherColumn;
import integration.harness.BasicMultiBranchProject;
import integration.harness.BasicMultiBranchProjectFactory;
import java.util.Arrays;
import java.util.Collections;
import jenkins.branch.Branch;
import jenkins.branch.BranchSource;
import jenkins.branch.DescriptionColumn;
import jenkins.branch.NameMangler;
import jenkins.branch.OrganizationFolder;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMEvents;
import jenkins.scm.api.SCMSourceEvent;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMLink;
import jenkins.scm.impl.mock.MockSCMNavigator;
import jenkins.scm.impl.mock.MockSCMSource;
import jenkins.scm.impl.mock.MockSCMSourceEvent;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class BrandingTest {

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
    public void given_multibranch_when_noSourcesDefined_then_noSourceBrandingPresent() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
        }
    }

    @Test
    public void given_multibranch_when_sourceDefined_then_sourceBrandingPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getAction(MockSCMLink.class), hasProperty("id", is("source")));
        }
    }

    @Test
    public void given_multibranch_when_sourceDefined_then_sourceBrandingPresentAfterSourceEvent()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            fire(new MockSCMSourceEvent(SCMEvent.Type.UPDATED, c, "foo"));
            r.waitUntilNoActivity();
            assertThat(prj.getAction(MockSCMLink.class), hasProperty("id", is("source")));
        }
    }

    @Test
    public void given_multibranch_when_branches_then_branchBrandingPresent()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getItem("master").getAction(MockSCMLink.class), hasProperty("id", is("branch")));
        }
    }

    @Test
    public void given_multibranch_when_branches_then_runBrandingPresent()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getItem("master").getBuildByNumber(1).getAction(MockSCMLink.class),
                    hasProperty("id", is("revision")));
        }
    }

    @Test
    public void given_orgFolder_when_noNavigatorsDefined_then_noNavigatorBrandingPresent() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
        }
    }

    @Test
    public void given_multibranch_when_sourceHasNonSafeNames_then_branchDisplayNameNotMangled() throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.createBranch("foo","特征/新");
            c.createBranch("foo","특색/새로운");
            c.createBranch("foo","gné/nua");
            c.createBranch("foo","característica/nuevo");
            c.createBranch("foo","особенность/новый");

            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();

            // now for the fun
            FreeStyleProject master = null;
            FreeStyleProject irish = null;
            FreeStyleProject chinese = null;
            FreeStyleProject korean = null;
            FreeStyleProject spanish = null;
            FreeStyleProject russian = null;

            for (FreeStyleProject p: prj.getItems()) {
                Branch branch = prj.getProjectFactory().getBranch(p);
                String name = branch.getName();
                if ("master".equals(name)) {
                    master = p;
                } else if ("gné/nua".equals(name)) {
                    irish = p;
                } else if ("特征/新".equals(name)) {
                    chinese = p;
                } else if ("특색/새로운".equals(name)) {
                    korean = p;
                } else if ("característica/nuevo".equals(name)) {
                    spanish = p;
                } else if ("особенность/новый".equals(name)) {
                    russian = p;
                }
            }
            assertThat("We have the master branch", master, notNullValue());
            assertThat("The master branch was built", master.getLastBuild(), notNullValue());
            assertThat("The master branch build was success", master.getLastBuild().getResult(), is(Result.SUCCESS));
            assertThat(master.getDisplayName(), is("master"));
            assertThat(master.getName(), is("master"));

            assertThat("We have the Irish branch", irish, notNullValue());
            assertThat("The Irish branch was built", irish.getLastBuild(), notNullValue());
            assertThat("The Irish branch build was success", irish.getLastBuild().getResult(), is(Result.SUCCESS));
            assertThat(irish.getDisplayName(), is("gné/nua"));
            assertThat(irish.getName(), not(is("gné/nua")));

            assertThat("We have the Chinese branch", chinese, notNullValue());
            assertThat("The Chinese branch was built", chinese.getLastBuild(), notNullValue());
            assertThat("The Chinese branch build was success", chinese.getLastBuild().getResult(), is(Result.SUCCESS));
            assertThat(chinese.getDisplayName(), is("特征/新"));
            assertThat(chinese.getName(), not(is("特征/新")));

            assertThat("We have the Korean branch", korean, notNullValue());
            assertThat("The Korean branch was built", korean.getLastBuild(), notNullValue());
            assertThat("The Korean branch build was success", korean.getLastBuild().getResult(), is(Result.SUCCESS));
            assertThat(korean.getDisplayName(), is("특색/새로운"));
            assertThat(korean.getName(), not(is("특색/새로운")));

            assertThat("We have the Spanish branch", spanish, notNullValue());
            assertThat("The Spanish branch was built", spanish.getLastBuild(), notNullValue());
            assertThat("The Spanish branch build was success", spanish.getLastBuild().getResult(), is(Result.SUCCESS));
            assertThat(spanish.getDisplayName(), is("característica/nuevo"));
            assertThat(spanish.getName(), not(is("característica/nuevo")));

            assertThat("We have the Russian branch", russian, notNullValue());
            assertThat("The Russian branch was built", russian.getLastBuild(), notNullValue());
            assertThat("The Russian branch build was success", russian.getLastBuild().getResult(), is(Result.SUCCESS));
            assertThat(russian.getDisplayName(), is("особенность/новый"));
            assertThat(russian.getName(), not(is("особенность/новый")));

            assertThat(prj.getItems(), containsInAnyOrder(master, irish, chinese, korean, spanish, russian));
        }
    }

    @Test
    public void given_orgFolder_when_navigatorDefined_then_navigatorBrandingPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getAction(MockSCMLink.class), hasProperty("id", is("organization")));
        }
    }

    @Test
    public void given_orgFolder_when_navigatorDefined_then_sourceBrandingPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getItem("foo").getAction(MockSCMLink.class), hasProperty("id", is("source")));
        }
    }

    @Test
    public void given_orgFolder_when_navigatorDefined_then_branchBrandingPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getItem("foo").getItem("master").getAction(MockSCMLink.class),
                    hasProperty("id", is("branch")));
        }
    }

    @Test
    public void given_orgFolder_when_navigatorDefined_then_revisionBrandingPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            assertThat(prj.getAction(MockSCMLink.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getItem("foo").getItem("master").getBuildByNumber(1).getAction(MockSCMLink.class),
                    hasProperty("id", is("revision")));
        }
    }

    @Test
    public void given_multibranch_when_decoratedSourceDefined_then_descriptionPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.setDescription("foo", "The Foo Project of Manchu");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            assertThat(prj.getDescription(), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getDescription(), is("The Foo Project of Manchu"));
        }
    }

    @Test
    public void given_multibranch_when_decoratedSourceDefined_then_displayNamePresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.setDisplayName("foo", "Foo Project");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            assertThat(prj.getDisplayName(), is("foo"));
            assertThat(prj.getDisplayNameOrNull(), nullValue());
            assertThat(prj.getAction(ObjectMetadataAction.class), nullValue());
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getAction(ObjectMetadataAction.class), notNullValue());
            assertThat(prj.getDisplayName(), is("Foo Project"));
        }
    }

    @Test
    public void given_orgFolder_when_decoratedSourceDefined_then_descriptionLinkPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.setDescription("foo", "The Foo Project of Manchu");
            c.setUrl("foo", "http://foo.manchu.example.com/");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getItem("foo").getAction(ObjectMetadataAction.class), allOf(
                    hasProperty("objectDescription", is("The Foo Project of Manchu")),
                    hasProperty("objectUrl", is("http://foo.manchu.example.com/")),
                    hasProperty("objectDisplayName", nullValue())
            ));
            JenkinsRule.WebClient webClient = r.createWebClient();
            HtmlPage page = webClient.getPage(prj);
            HtmlAnchor href = page.getAnchorByHref("http://foo.manchu.example.com/");
            assertThat(href.getTextContent(), containsString("The Foo Project of Manchu"));
        }
    }

    @Test
    public void given_multibranch_when_decoratedSourceDefined_then_folderIconPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.setRepoIconClassName("icon-star");
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "foo");
            prj.setCriteria(null);
            prj.getSourcesList().add(new BranchSource(new MockSCMSource(null, c, "foo", true, false, false)));
            assertThat(prj.getIcon().getIconClassName(), not(is("icon-star")));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getIcon().getIconClassName(), is("icon-star"));
        }
    }

    @Test
    public void given_orgFolder_when_decoratedOrganizationDefined_then_folderIconPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.setOrgIconClassName("icon-star");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            assertThat(prj.getIcon().getIconClassName(), not(is("icon-star")));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getIcon().getIconClassName(), is("icon-star"));
        }
    }

    @Test
    public void given_orgFolder_when_decoratedOrganizationDefined_then_displayNamePresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.setDisplayName("Foo Organization");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            assertThat(prj.getDisplayName(), is("foo"));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getDisplayName(), is("Foo Organization"));
        }
    }

    @Test
    public void given_orgFolder_when_decoratedOrganizationDefined_then_descriptionLinkPresentAfterIndexing()
            throws Exception {
        try (MockSCMController c = MockSCMController.create()) {
            c.createRepository("foo");
            c.setDescription("The Foo of Manchu");
            c.setUrl("http://foo.manchu.example.com/");
            OrganizationFolder prj = r.jenkins.createProject(OrganizationFolder.class, "foo");
            prj.getSCMNavigators().add(new MockSCMNavigator(c, true, false, false));
            prj.getProjectFactories().replaceBy(Collections.singletonList(new BasicMultiBranchProjectFactory(null)));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();
            assertThat(prj.getAction(ObjectMetadataAction.class), allOf(
                    hasProperty("objectDescription", is("The Foo of Manchu")),
                    hasProperty("objectUrl", is("http://foo.manchu.example.com/")),
                    hasProperty("objectDisplayName", nullValue())
            ));
            ListView view = new ListView("descript");
            view.setIncludeRegex(".*");
            view.getColumns().replaceBy(Arrays.asList(
                    new StatusColumn(),
                    new WeatherColumn(),
                    new JobColumn(),
                    new DescriptionColumn()
            ));
            r.jenkins.addView(view);
            JenkinsRule.WebClient webClient = r.createWebClient();
            HtmlPage page = webClient.getPage(view);
            HtmlAnchor href = page.getAnchorByHref("http://foo.manchu.example.com/");
            assertThat(href.getTextContent(), containsString("The Foo of Manchu"));
        }
    }

    private void fire(MockSCMSourceEvent event) throws Exception {
        long watermark = SCMEvents.getWatermark();
        SCMSourceEvent.fireNow(event);
        SCMEvents.awaitAll(watermark);
        r.waitUntilNoActivity();
    }

}
