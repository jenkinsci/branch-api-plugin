package jenkins.branch.matchers;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Extracting<Element, Property> extends TypeSafeDiagnosingMatcher<Iterable<Element>> {

    private final Function<? super Element, Property> extractor;
    private final Matcher<?> nextMatcher;

    private Extracting(Function<? super Element, Property> extractor, Matcher<?> nextMatcher) {
        this.extractor = extractor;
        this.nextMatcher = nextMatcher;
    }

    @Override
    protected boolean matchesSafely(Iterable<Element> inColl, Description mismatchDescription) {
        final Iterable<Property> collection = StreamSupport.stream(inColl.spliterator(), false)
                .map(extractor)
                .collect(Collectors.toList());

        final boolean matches = nextMatcher.matches(collection);

        if (!matches) {
            nextMatcher.describeMismatch(collection, mismatchDescription);
        }
        return matches;
    }


    @Override
    public void describeTo(Description description) {
        description
                .appendDescriptionOf(nextMatcher);
    }

    /**
     * Extracts the specified property from a collection of things, and then lets you assert on the extracted properties. (The analog of AssertJ's <code>assertThat(things).extracting("foo")</code>.)
     * <p>
     * Use this in a matcher chain. For example, if you have list of POJOs with a property 'foo', you could write <code>assertThat(list, extracting(t -> t.getFoo(), hasItems("a", "b")))</code>.
     *
     * @param <Element> type of the elements in the original collection
     * @param <Property> type of extracted property
     * @param extractor name of the property to extract from the collection
     * @param nextMatcher matcher to test the extracted property's value(s)
     * @return the extracting matcher
     */
    public static <Element, Property> Matcher<Iterable<Element>> extracting(Function<? super Element, Property> extractor, Matcher<?> nextMatcher) {
        return new Extracting<>(extractor, nextMatcher);
    }
}