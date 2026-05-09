package digital.singularidade.databridge.source.jdbc;

import digital.singularidade.databridge.error.DataBridgeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriverHintsTest {

    @Test
    void detects_postgres() {
        assertThat(DriverHints.fromUrl("jdbc:postgresql://h/db")).isEqualTo(DriverHints.PG);
    }

    @Test
    void detects_firebird() {
        assertThat(DriverHints.fromUrl("jdbc:firebirdsql://h/db")).isEqualTo(DriverHints.FIREBIRD);
    }

    @Test
    void detects_oracle() {
        assertThat(DriverHints.fromUrl("jdbc:oracle:thin:@h:1521:db")).isEqualTo(DriverHints.ORACLE);
    }

    @Test
    void detects_mssql() {
        assertThat(DriverHints.fromUrl("jdbc:sqlserver://h:1433;databaseName=db")).isEqualTo(DriverHints.MSSQL);
    }

    @Test
    void detects_mysql() {
        assertThat(DriverHints.fromUrl("jdbc:mysql://h/db")).isEqualTo(DriverHints.MYSQL);
    }

    @Test
    void unknown_prefix_throws_unsupported() {
        assertThatThrownBy(() -> DriverHints.fromUrl("jdbc:h2:mem:test"))
            .isInstanceOf(DataBridgeException.class)
            .hasMessageContaining("Unsupported JDBC URL prefix");
    }

    @Test
    void wire_name_for_source_field() {
        assertThat(DriverHints.PG.wireName()).isEqualTo("postgresql");
        assertThat(DriverHints.FIREBIRD.wireName()).isEqualTo("firebird");
        assertThat(DriverHints.ORACLE.wireName()).isEqualTo("oracle");
        assertThat(DriverHints.MSSQL.wireName()).isEqualTo("mssql");
        assertThat(DriverHints.MYSQL.wireName()).isEqualTo("mysql");
    }

    @Test
    void schema_required_flag() {
        assertThat(DriverHints.PG.requiresSchema()).isTrue();
        assertThat(DriverHints.ORACLE.requiresSchema()).isTrue();
        assertThat(DriverHints.MSSQL.requiresSchema()).isTrue();
        assertThat(DriverHints.FIREBIRD.requiresSchema()).isFalse();
        assertThat(DriverHints.MYSQL.requiresSchema()).isFalse();
    }
}
