package digital.singularidade.databridge.output;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemNamesTest {

    @Test
    void should_detect_firebird_generated_names() {
        assertThat(SystemNames.isSystemGenerated("INTEG_12")).isTrue();
        assertThat(SystemNames.isSystemGenerated("RDB$PRIMARY3")).isTrue();
        assertThat(SystemNames.isSystemGenerated("RDB$FOREIGN2")).isTrue();
        assertThat(SystemNames.isSystemGenerated("RDB$41")).isTrue();
    }

    @Test
    void should_keep_user_given_names() {
        assertThat(SystemNames.isSystemGenerated("PK_TBPAI")).isFalse();
        assertThat(SystemNames.isSystemGenerated("INTEG_X")).isFalse();
        assertThat(SystemNames.isSystemGenerated("RDB$SECURITY_CLASS")).isFalse();
        assertThat(SystemNames.isSystemGenerated(null)).isFalse();
    }
}
