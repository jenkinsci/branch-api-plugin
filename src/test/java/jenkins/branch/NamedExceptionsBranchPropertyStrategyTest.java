package jenkins.branch;

import org.junit.Test;

import static jenkins.branch.NamedExceptionsBranchPropertyStrategy.Named.isMatch;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author Stephen Connolly
 */
public class NamedExceptionsBranchPropertyStrategyTest {
    @Test
    public void examplesFromHelpText() throws Exception {
        // "production"  matches one and only one branch
        assertThat(isMatch("production", "production"), is(true));
        assertThat(isMatch("Production", "production"), is(true));
        assertThat(isMatch("PRODUCTION", "production"), is(true));
        assertThat(isMatch("proDuctIon", "production"), is(true));
        assertThat(isMatch("staging", "production"), is(false));
        // "sandbox/*" matches sandbox/acme but not sandbox/coyote/wiley
        assertThat(isMatch("trunk", "sandbox/*"), is(false));
        assertThat(isMatch("sandbox/acme", "sandbox/*"), is(true));
        assertThat(isMatch("sandbox/coyote/wiley", "sandbox/*"), is(false));
        // "sandbox/**" matches sandbox/acme and sandbox/coyote/wiley
        assertThat(isMatch("trunk", "sandbox/**"), is(false));
        assertThat(isMatch("sandbox/acme", "sandbox/**"), is(true));
        assertThat(isMatch("sandbox/coyote/wiley", "sandbox/**"), is(true));
        // "production,staging" matches two specific branches
        assertThat(isMatch("production", "production,staging"), is(true));
        assertThat(isMatch("staging", "production,staging"), is(true));
        assertThat(isMatch("test", "production,staging"), is(false));
        // "production,staging*" matches the production branch and any branch starting with staging
        assertThat(isMatch("production", "production,staging*"), is(true));
        assertThat(isMatch("staging", "production,staging*"), is(true));
        assertThat(isMatch("staging2", "production,staging*"), is(true));
        assertThat(isMatch("test", "production,staging*"), is(false));
        // "!staging/**,staging/test/**" matches any branch that is not the a staging branch, but will match the staging/test branches
        assertThat(isMatch("production", "!staging/**,staging/test/**"), is(true));
        assertThat("lack of trailing / matches /**", isMatch("staging", "!staging/**,staging/test/**"), is(false));
        assertThat(isMatch("staging/", "!staging/**,staging/test/**"), is(false));
        assertThat(isMatch("staging/acme", "!staging/**,staging/test/**"), is(false));
        assertThat(isMatch("staging/acme/foo", "!staging/**,staging/test/**"), is(false));
        assertThat("lack of trailing / matches /**", isMatch("staging/test", "!staging/**,staging/test/**"), is(true));
        assertThat(isMatch("staging/test/foo", "!staging/**,staging/test/**"), is(true));
        // simple escape
        assertThat(isMatch("\\!starts-with-invert", "\\!starts-with-invert"), is(false));
        assertThat(isMatch("!starts-with-invert", "\\!starts-with-invert"), is(true));
        // escape escape
        assertThat(isMatch("\\!starts-with-escape", "\\\\!starts-with-escape"), is(true));
        assertThat(isMatch("\\\\!starts-with-escape", "\\\\!starts-with-escape"), is(false));
        // no internal escapes needed
        assertThat(isMatch("no-internal-!-escape", "no-internal-!-escape"), is(true));
        assertThat(isMatch("no-internal-!-escape", "no-internal-\\!-escape"), is(false));
        assertThat(isMatch("no-internal-\\!-escape", "no-internal-\\!-escape"), is(true));
        assertThat(isMatch("no-internal-\\-escape", "no-internal-\\-escape"), is(true));
        assertThat(isMatch("no-internal-\\-escape", "no-internal-\\\\-escape"), is(false));
        assertThat(isMatch("no-internal-\\\\-escape", "no-internal-\\\\-escape"), is(true));
    }
}
