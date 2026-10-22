package digital.singularidade.databridge.source.jdbc.firebird;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirebirdTriggerTypeTest {

    @ParameterizedTest
    @CsvSource({
        "1,BEFORE INSERT", "2,AFTER INSERT", "3,BEFORE UPDATE", "4,AFTER UPDATE",
        "5,BEFORE DELETE", "6,AFTER DELETE", "17,BEFORE INSERT OR UPDATE",
        "18,AFTER INSERT OR UPDATE", "25,BEFORE INSERT OR DELETE", "26,AFTER INSERT OR DELETE",
        "27,BEFORE UPDATE OR DELETE", "28,AFTER UPDATE OR DELETE",
        "113,BEFORE INSERT OR UPDATE OR DELETE", "114,AFTER INSERT OR UPDATE OR DELETE"
    })
    void should_decode_table_trigger_types(int type, String clause) {
        assertThat(FirebirdTriggerType.decode(type).clause()).isEqualTo(clause);
    }

    @Test
    void should_split_timing_and_events() {
        FirebirdTriggerType t = FirebirdTriggerType.decode(17);
        assertThat(t.timing()).isEqualTo("BEFORE");
        assertThat(t.events()).containsExactly("INSERT", "UPDATE");
    }

    @Test
    void should_reject_database_and_ddl_triggers() {
        assertThatThrownBy(() -> FirebirdTriggerType.decode(8192)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FirebirdTriggerType.decode(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
