package digital.singularidade.databridge.source.jdbc.firebird;

import digital.singularidade.databridge.source.jdbc.firebird.FirebirdTypes.FieldType;
import org.junit.jupiter.api.Test;

import static digital.singularidade.databridge.source.jdbc.firebird.FirebirdTypes.blobSqlType;
import static digital.singularidade.databridge.source.jdbc.firebird.FirebirdTypes.isText;
import static digital.singularidade.databridge.source.jdbc.firebird.FirebirdTypes.nativeType;
import static org.assertj.core.api.Assertions.assertThat;

class FirebirdTypesTest {

    private static FieldType t(int code, Integer sub, Integer clen, Integer prec, Integer scale, Integer seg) {
        return new FieldType(code, sub, clen, clen, 1, prec, scale, seg);
    }

    @Test
    void should_render_numeric_and_decimal_on_every_storage_size() {
        assertThat(nativeType(t(16, 1, null, 15, -2, null))).isEqualTo("NUMERIC(15,2)");
        assertThat(nativeType(t(8, 1, null, 9, -3, null))).isEqualTo("NUMERIC(9,3)");
        assertThat(nativeType(t(8, 2, null, 4, -2, null))).isEqualTo("DECIMAL(4,2)");
        assertThat(nativeType(t(7, 1, null, 4, -1, null))).isEqualTo("NUMERIC(4,1)");
        assertThat(nativeType(t(26, 2, null, 38, -4, null))).isEqualTo("DECIMAL(38,4)");
        assertThat(nativeType(t(27, 0, null, 15, -2, null))).isEqualTo("NUMERIC(15,2)");
    }

    @Test
    void should_render_plain_integers_and_floats() {
        assertThat(nativeType(t(7, 0, null, null, 0, null))).isEqualTo("SMALLINT");
        assertThat(nativeType(t(8, null, null, null, 0, null))).isEqualTo("INTEGER");
        assertThat(nativeType(t(16, 0, null, null, 0, null))).isEqualTo("BIGINT");
        assertThat(nativeType(t(27, null, null, null, 0, null))).isEqualTo("DOUBLE PRECISION");
        assertThat(nativeType(t(10, null, null, null, 0, null))).isEqualTo("FLOAT");
    }

    @Test
    void should_use_precision_default_when_catalog_has_none() {
        assertThat(nativeType(t(16, 1, null, null, -2, null))).isEqualTo("NUMERIC(18,2)");
        assertThat(nativeType(t(7, 1, null, 0, -2, null))).isEqualTo("NUMERIC(4,2)");
    }

    @Test
    void should_render_text_lengths_in_characters() {
        assertThat(nativeType(new FieldType(37, 0, 10, 40, 4, null, 0, null))).isEqualTo("VARCHAR(10)");
        assertThat(nativeType(new FieldType(37, 0, null, 40, 4, null, 0, null))).isEqualTo("VARCHAR(10)");
        assertThat(nativeType(t(14, 0, 1, null, 0, null))).isEqualTo("CHAR(1)");
        assertThat(isText(t(14, 0, 1, null, 0, null))).isTrue();
        assertThat(isText(t(8, null, null, null, 0, null))).isFalse();
    }

    @Test
    void should_render_blobs_and_mark_text_blobs() {
        assertThat(nativeType(t(261, 1, null, null, 0, 80))).isEqualTo("BLOB SUB_TYPE TEXT");
        assertThat(nativeType(t(261, 0, null, null, 0, 4096))).isEqualTo("BLOB SUB_TYPE BINARY SEGMENT SIZE 4096");
        assertThat(nativeType(t(261, 7, null, null, 0, 80))).isEqualTo("BLOB SUB_TYPE 7");
        assertThat(blobSqlType(t(261, 1, null, null, 0, 80))).isEqualTo("text");
        assertThat(blobSqlType(t(261, 0, null, null, 0, 80))).isEqualTo("blob");
        assertThat(blobSqlType(t(37, 0, 10, null, 0, null))).isNull();
        assertThat(isText(t(261, 1, null, null, 0, 80))).isTrue();
        assertThat(isText(t(261, 0, null, null, 0, 80))).isFalse();
    }

    @Test
    void should_render_date_time_and_newer_types() {
        assertThat(nativeType(t(12, null, null, null, 0, null))).isEqualTo("DATE");
        assertThat(nativeType(t(13, null, null, null, 0, null))).isEqualTo("TIME");
        assertThat(nativeType(t(35, null, null, null, 0, null))).isEqualTo("TIMESTAMP");
        assertThat(nativeType(t(28, null, null, null, 0, null))).isEqualTo("TIME WITH TIME ZONE");
        assertThat(nativeType(t(29, null, null, null, 0, null))).isEqualTo("TIMESTAMP WITH TIME ZONE");
        assertThat(nativeType(t(23, null, null, null, 0, null))).isEqualTo("BOOLEAN");
        assertThat(nativeType(t(24, null, null, null, 0, null))).isEqualTo("DECFLOAT(16)");
        assertThat(nativeType(t(25, null, null, null, 0, null))).isEqualTo("DECFLOAT(34)");
        assertThat(nativeType(t(26, 0, null, null, 0, null))).isEqualTo("INT128");
        assertThat(nativeType(t(999, null, null, null, 0, null))).isEqualTo("UNKNOWN_TYPE_999");
    }
}
