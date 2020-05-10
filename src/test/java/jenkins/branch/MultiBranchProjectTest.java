package jenkins.branch;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Stephen Connolly
 */
public class MultiBranchProjectTest {

    @Test
    public void smokes() {
        assertThat(MultiBranchProject.rawDecode("Hello world"), is("Hello world"));
        assertThat(MultiBranchProject.rawDecode("Hello+world"), is("Hello+world"));
        assertThat(MultiBranchProject.rawDecode("origin/production"), is("origin/production"));
        assertThat(MultiBranchProject.rawDecode("origin%2fproduction"), is("origin/production"));
        assertThat(MultiBranchProject.rawDecode("origin%2Fproduction"), is("origin/production"));
        assertThat(MultiBranchProject.rawDecode("origin%production"), is("origin%production"));
        assertThat(MultiBranchProject.rawDecode("origin%foo"), is("origin%foo"));
        assertThat(MultiBranchProject.rawDecode("origin%2f50%"), is("origin/50%"));
        assertThat(MultiBranchProject.rawDecode("origin%2f50%2"), is("origin/50%2"));
        assertThat(MultiBranchProject.rawDecode("origin%2f50%26"), is("origin/50&"));
    }
}
