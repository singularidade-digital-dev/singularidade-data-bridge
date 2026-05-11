package digital.singularidade.databridge.source.jdbc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for {@link JdbcSource#parseTextArray} / {@link JdbcSource#parseFloatArray}.
 *
 * <p>These parsers consume {@code pg_stats.most_common_vals::text} and
 * {@code pg_stats.most_common_freqs::text}. In production we hit a
 * {@code StringIndexOutOfBoundsException} when the value was a single character (e.g. {@code "{"}),
 * because the old code unconditionally did {@code substring(1, length-1)} which becomes
 * {@code substring(1, 0)} for length-1 inputs. These tests pin the defense.
 */
class JdbcSourceParseArrayTest {

    @Test
    void parseTextArray_handles_null_empty_and_braces_only() {
        assertThat(JdbcSource.parseTextArray(null)).isEmpty();
        assertThat(JdbcSource.parseTextArray("")).isEmpty();
        assertThat(JdbcSource.parseTextArray("{}")).isEmpty();
    }

    @Test
    void parseTextArray_handles_length_one_garbage_without_crashing() {
        // Production crash repro: a single brace or any 1-char string used to throw
        // StringIndexOutOfBoundsException with message "Range [1, 0) out of bounds for length 1".
        assertThat(JdbcSource.parseTextArray("{")).isEmpty();
        assertThat(JdbcSource.parseTextArray("}")).isEmpty();
        assertThat(JdbcSource.parseTextArray("x")).isEmpty();
        assertThat(JdbcSource.parseTextArray(" ")).isEmpty();
    }

    @Test
    void parseTextArray_rejects_strings_without_braces() {
        assertThat(JdbcSource.parseTextArray("foo,bar")).isEmpty();
        assertThat(JdbcSource.parseTextArray("\"foo\"")).isEmpty();
    }

    @Test
    void parseTextArray_extracts_comma_separated_quoted_values() {
        assertThat(JdbcSource.parseTextArray("{\"foo\",\"bar\",\"baz\"}"))
            .containsExactly("foo", "bar", "baz");
    }

    @Test
    void parseTextArray_extracts_unquoted_values() {
        assertThat(JdbcSource.parseTextArray("{1,2,3}"))
            .containsExactly("1", "2", "3");
    }

    @Test
    void parseFloatArray_handles_length_one_garbage_without_crashing() {
        assertThat(JdbcSource.parseFloatArray("{")).isEmpty();
        assertThat(JdbcSource.parseFloatArray("}")).isEmpty();
        assertThat(JdbcSource.parseFloatArray("x")).isEmpty();
    }

    @Test
    void parseFloatArray_handles_null_empty_and_braces_only() {
        assertThat(JdbcSource.parseFloatArray(null)).isEmpty();
        assertThat(JdbcSource.parseFloatArray("")).isEmpty();
        assertThat(JdbcSource.parseFloatArray("{}")).isEmpty();
    }

    @Test
    void parseFloatArray_extracts_doubles() {
        List<Double> result = JdbcSource.parseFloatArray("{0.5,0.25,0.125}");
        assertThat(result).containsExactly(0.5, 0.25, 0.125);
    }

    @Test
    void parseFloatArray_skips_non_numeric_tokens() {
        List<Double> result = JdbcSource.parseFloatArray("{0.5,abc,0.25}");
        assertThat(result).containsExactly(0.5, 0.25);
    }
}
