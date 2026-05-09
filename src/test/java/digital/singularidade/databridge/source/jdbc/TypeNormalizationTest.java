package digital.singularidade.databridge.source.jdbc;

import org.junit.jupiter.api.Test;

import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;

class TypeNormalizationTest {

    @Test
    void bigint_no_size() {
        assertThat(TypeNormalization.toSqlType(Types.BIGINT, "BIGINT", null, null, null))
            .isEqualTo("bigint");
    }

    @Test
    void varchar_with_length() {
        assertThat(TypeNormalization.toSqlType(Types.VARCHAR, "VARCHAR", 100, null, null))
            .isEqualTo("varchar(100)");
    }

    @Test
    void numeric_with_precision_and_scale() {
        assertThat(TypeNormalization.toSqlType(Types.NUMERIC, "NUMERIC", null, 10, 2))
            .isEqualTo("numeric(10,2)");
    }

    @Test
    void numeric_without_scale() {
        assertThat(TypeNormalization.toSqlType(Types.NUMERIC, "NUMERIC", null, 10, 0))
            .isEqualTo("numeric(10,0)");
    }

    @Test
    void timestamp_passes_through() {
        assertThat(TypeNormalization.toSqlType(Types.TIMESTAMP, "TIMESTAMP", null, null, null))
            .isEqualTo("timestamp");
    }

    @Test
    void unknown_type_falls_back_to_lowercase_typename() {
        assertThat(TypeNormalization.toSqlType(Types.OTHER, "JSONB", null, null, null))
            .isEqualTo("jsonb");
    }

    @Test
    void bytea_normalized() {
        assertThat(TypeNormalization.toSqlType(Types.BINARY, "BYTEA", null, null, null))
            .isEqualTo("bytea");
    }
}
