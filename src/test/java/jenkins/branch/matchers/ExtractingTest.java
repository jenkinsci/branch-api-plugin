package jenkins.branch.matchers;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static jenkins.branch.matchers.Extracting.extracting;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

class ExtractingTest {

    @Test
    void shouldExtractNothingFromEmptyIterable() {
        final Iterable<Tuple> data = Collections.emptyList();

        assertThat(data, extracting(Tuple::getFoo, is(empty())));
    }

    @Test
    void shouldExtractProperty() {
        final Iterable<Tuple> data = Arrays.asList(tuple("a",1), tuple("b", 2));

        assertThat(data, extracting(Tuple::getFoo, hasItem("a")));
    }

    @Test
    void shouldExtractPropertyFromMultipleObjects() {
        final Iterable<Tuple> data = Arrays.asList(tuple("a",1), tuple("b", 2));

        assertThat(data, extracting(Tuple::getFoo, hasItems("a", "b")));
        assertThat(data, extracting(Tuple::getBar, hasItems(1, 2)));
    }

    @Test
    void shouldSupportPartialMatchers() {
        final Iterable<Tuple> data = Arrays.asList(tuple("a",1), tuple("b", 2), tuple("c", 3));

        assertThat(data, extracting(Tuple::getFoo, hasItems("a", "c")));
    }

    private static Tuple tuple(String foo, int bar) {
        return new Tuple(foo, bar);
    }

    private static class Tuple {
        private final String foo;
        private final int bar;

        Tuple(String foo, int bar) {
            this.foo = foo;
            this.bar = bar;
        }

        public String getFoo() {
            return foo;
        }

        public int getBar() {
            return bar;
        }
    }
}
