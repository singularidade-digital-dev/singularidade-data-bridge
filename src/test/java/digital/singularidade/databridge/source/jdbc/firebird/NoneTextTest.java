package digital.singularidade.databridge.source.jdbc.firebird;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoneTextTest {

    private static final String TEXT = "ação ‘x’ é";

    @Test
    void should_decode_valid_utf8_as_utf8() {
        assertThat(NoneText.of("WIN1252").decode(TEXT.getBytes(StandardCharsets.UTF_8))).isEqualTo(TEXT);
    }

    @Test
    void should_fall_back_to_win1252_when_the_bytes_are_not_utf8() {
        assertThat(NoneText.of("WIN1252").decode(TEXT.getBytes(Charset.forName("windows-1252")))).isEqualTo(TEXT);
    }

    @Test
    void should_keep_bytes_without_a_character_in_the_fallback() {
        byte[] undefinedInWin1252 = {'a', (byte) 0x81, (byte) 0x8D, 'b'};
        assertThat(NoneText.of("WIN1252").decode(undefinedInWin1252)).isEqualTo("a\u0081\u008Db");
    }

    @Test
    void should_use_the_chosen_fallback_with_firebird_or_java_names() {
        byte[] win1252 = TEXT.getBytes(Charset.forName("windows-1252"));
        assertThat(NoneText.of("ISO8859_1").decode(win1252)).isEqualTo("ação \u0091x\u0092 é");
        assertThat(NoneText.of("ISO-8859-1").decode(win1252)).isEqualTo("ação \u0091x\u0092 é");
        assertThat(NoneText.of("DOS850").decode(new byte[] {(byte) 0x87})).isEqualTo("ç");
        assertThat(NoneText.of("win1252").charsetName()).isEqualTo("windows-1252");
    }

    @Test
    void should_reject_unknown_charsets() {
        assertThatThrownBy(() -> NoneText.of("NOPE")).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("NOPE");
    }

    @Test
    void should_decode_ascii_and_empty_values_unchanged() {
        assertThat(NoneText.of("WIN1252").decode("plain".getBytes(StandardCharsets.US_ASCII))).isEqualTo("plain");
        assertThat(NoneText.of("WIN1252").decode(new byte[0])).isEmpty();
    }
}
