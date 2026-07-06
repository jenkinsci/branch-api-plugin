package jenkins.branch;

import hudson.model.FreeStyleProject;
import integration.harness.BasicMultiBranchProject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMSource;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

/**
 * When a branch source loses its {@code <id>} on disk (a
 * deserialization regression that leaves {@link jenkins.scm.api.SCMSource#getId()}
 * blank), {@link MultiBranchProject#buildSourceMap()} assigns it a fresh sequential
 * id ({@code "1"} onwards) in memory. The branch sub-jobs on disk still carry the legacy
 * alphanumeric {@code sourceId}, so every subsequent scan sees a mismatch, logs a
 * spurious "Takeover ... from source that no longer exists", reopens the branch and
 * schedules an unwanted build.
 */
public class SourceIdTakeoverTest {

    /** Fixed controller id so the mock repository survives {@link jenkins.model.Jenkins#reload()}. */
    private static final String CONTROLLER_ID = "controller";

    /**
     * The legacy alphanumeric source id that the branch sub-jobs carry on disk, as written by the
     * pre-upgrade branch source plugin (@code ABCDEFGH12}).
     */
    private static final String LEGACY_SOURCE_ID = "ABCDEFGH12";

    @Rule
    public final JenkinsRule r = new JenkinsRule();

    @Test
    public void blankSourceIdOnLoad_doesNotTakeoverOrRebuild() throws Throwable {
        try (MockSCMController c = MockSCMController.recreate(CONTROLLER_ID)) {
            c.createRepository("repo");

            // 1. Build the project normally with an explicit legacy source id and index it once so
            // the master branch job is created and built (build #1)
            BasicMultiBranchProject prj = r.jenkins.createProject(BasicMultiBranchProject.class, "mbp");
            prj.setCriteria(null);
            prj.setSourcesList(List.of(
                new BranchSource(new MockSCMSource(c, "repo", new MockSCMDiscoverBranches()).withId(LEGACY_SOURCE_ID))));
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();

            FreeStyleProject master = prj.getItem("master");
            assertThat("master branch created", master, notNullValue());
            assertThat("master built once", master.getLastBuild().getNumber(), is(1));
            assertThat("branch carries the legacy source id on disk",
                prj.getProjectFactory().getBranch(master).getSourceId(), is(LEGACY_SOURCE_ID));

            // 2. Simulate the deserialization regression: strip the <id> element from the source in
            // the on-disk config.xml. On reload getId() returns "" and buildSourceMap() will
            // renumber the live source to "1", while the branch sub-jobs keep LEGACY_SOURCE_ID.
            stripSourceId(prj.getConfigFile().getFile().toPath());

            // 3. Reload from disk (equivalent to a controller restart).
            r.jenkins.reload();
            prj = (BasicMultiBranchProject) r.jenkins.getItemByFullName("mbp");
            master = prj.getItem("master");

            // source id "1" in memory vs legacy branch sourceId.
            assertThat("source was renumbered to a sequential id in memory",
                prj.getSources().get(0).getSource().getId(), is("1"));
            assertThat("branch still carries the legacy source id",
                prj.getProjectFactory().getBranch(master).getSourceId(), is(LEGACY_SOURCE_ID));

            // 4. Trigger a scan with no SCM change. This must be a no-op: no takeover, no rebuild.
            prj.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();

            String log = computationLog(prj);
            assertThat("no spurious takeover on rescan", log,
                not(containsString("Takeover")));
            assertThat("no source-no-longer-exists takeover on rescan", log,
                not(containsString("from source that no longer exists")));
            assertThat("branch reports no changes", log,
                containsString("No changes detected"));
            assertThat("master was not rebuilt", master.getLastBuild().getNumber(), is(1));
        }
    }

    /** Removes the {@code <id>...</id>} element from the (single) source in a multibranch config.xml. */
    private static void stripSourceId(Path configXml) throws Exception {
        String xml = Files.readString(configXml, StandardCharsets.UTF_8);
        String stripped = xml.replaceAll("\\s*<id>[^<]*</id>", "");
        assertThat("an <id> was present to strip", stripped, is(not(xml)));
        Files.writeString(configXml, stripped, StandardCharsets.UTF_8);
    }

    private static String computationLog(MultiBranchProject<?, ?> prj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        prj.getComputation().writeWholeLogTo(baos);
        return baos.toString(StandardCharsets.UTF_8);
    }
}
