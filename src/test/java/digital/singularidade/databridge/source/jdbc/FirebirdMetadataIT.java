package digital.singularidade.databridge.source.jdbc;

import digital.singularidade.databridge.output.CheckConstraint;
import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.output.ColumnStatsMode;
import digital.singularidade.databridge.output.Index;
import digital.singularidade.databridge.output.Metadata;
import digital.singularidade.databridge.output.SourceUrlRedaction;
import digital.singularidade.databridge.output.Trigger;
import digital.singularidade.databridge.output.UniqueConstraint;
import digital.singularidade.databridge.pipeline.MetadataPipeline;
import digital.singularidade.databridge.source.CardinalityMode;
import digital.singularidade.databridge.support.FbFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FirebirdMetadataIT {

    private static FbFixture fx;
    private static JdbcSource src;
    private static Metadata m;

    @BeforeAll
    static void start() {
        fx = new FbFixture();
        src = JdbcSource.open(fx.jdbcUrl());
        m = new MetadataPipeline(new PrintStream(OutputStream.nullOutputStream()), CardinalityMode.SKIP,
            ColumnStatsMode.HISTOGRAM_ONLY, SourceUrlRedaction.HOST_PORT)
            .run(src, fx.jdbcUrl(), null, "PRITENSESQ", 0);
    }

    @AfterAll
    static void stop() {
        src.close();
        fx.close();
    }

    private static Column col(String name) {
        return m.columns().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    private static Index idx(String name) {
        return m.indexes().stream().filter(i -> i.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void should_fill_unique_and_check_constraints() {
        assertThat(m.uniqueConstraints())
            .containsExactly(new UniqueConstraint("UK_PRITENSESQ_EXT", List.of("CODEXT", "EMPRESA")));
        assertThat(m.checkConstraints()).extracting(CheckConstraint::definition)
            .containsExactlyInAnyOrder("(PERC BETWEEN 0 AND 100)", "(QTDE >= 0)");
    }

    @Test
    void should_describe_domain_based_columns() {
        assertThat(col("ATIVO").domain())
            .isEqualTo(new Column.Domain("DM_FLAG", true, "'F'", "(VALUE IN ('T','F'))"));
        assertThat(col("ATIVO").nullable()).isFalse();
        assertThat(col("ATIVO").defaultValue()).isEqualTo("'F'");
        assertThat(col("VALOR").domain().name()).isEqualTo("DM_VALOR");
        assertThat(col("EMPRESA").domain()).isNull();
    }

    @Test
    void should_report_native_types_charsets_and_blob_kinds() {
        assertThat(col("OBS").sqlType()).isEqualTo("text");
        assertThat(col("OBS").nativeType()).isEqualTo("BLOB SUB_TYPE TEXT");
        assertThat(col("OBS").charset()).isEqualTo("ISO8859_1");
        assertThat(col("FOTO").sqlType()).isEqualTo("blob");
        assertThat(col("VALOR").nativeType()).isEqualTo("NUMERIC(15,2)");
        assertThat(col("VALOR").sqlType()).isEqualTo("numeric(15,2)");
        assertThat(col("PERC").nativeType()).isEqualTo("DECIMAL(4,2)");
        assertThat(col("NOME").charset()).isEqualTo("WIN1252");
        assertThat(col("NOME").collation()).isEqualTo("WIN_PTBR");
        assertThat(col("ID").nativeType()).isEqualTo("INTEGER");
        assertThat(col("ID").charset()).isNull();
    }

    @Test
    void should_link_generators_and_computed_expressions() {
        assertThat(col("ID").sequence())
            .isEqualTo(new Column.Sequence("GEN_IDITENSESQ", null, "trigger", "TR_PRITENSESQ_BI"));
        assertThat(col("SEQ").sequence()).isEqualTo(new Column.Sequence("RDB$1", null, "identity", null));
        assertThat(col("SEQ").generated().isIdentity()).isTrue();
        assertThat(col("TOTAL").generated().isComputed()).isTrue();
        assertThat(col("TOTAL").generated().generationExpression()).isEqualTo("(VALOR * QTDE)");
        assertThat(col("EMPRESA").sequence()).isNull();
    }

    @Test
    void should_describe_expression_descending_inactive_and_backing_indexes() {
        Index expr = idx("IX_PRITENSESQ_UPNOME");
        assertThat(expr.columns()).isEmpty();
        assertThat(expr.expression()).isEqualTo("(UPPER(NOME))");
        assertThat(expr.ordinalAsc()).containsExactly(true);
        assertThat(idx("IX_PRITENSESQ_DTCAD").ordinalAsc()).containsExactly(false);
        assertThat(idx("IX_PRITENSESQ_OFF").active()).isFalse();
        assertThat(idx("IX_PRITENSESQ_MULTI").columns()).containsExactly("NOME", "EMPRESA", "CODPAI");
        assertThat(idx("FK_PRITENSESQ_PAI").constraint()).isEqualTo("FK_PRITENSESQ_PAI");
        assertThat(idx("PK_PRITENSESQ").primary()).isTrue();
        assertThat(idx("PK_PRITENSESQ").unique()).isTrue();
        assertThat(m.indexes()).extracting(Index::name).isSorted();
    }

    @Test
    void should_list_triggers_with_state_position_and_generators() {
        assertThat(m.triggers()).extracting(Trigger::name)
            .containsExactly("TR_PRITENSESQ_BI", "TR_PRITENSESQ_BIU", "TR_PRITENSESQ_AD");
        Trigger biu = m.triggers().get(1);
        assertThat(biu.events()).containsExactly("INSERT", "UPDATE");
        assertThat(biu.active()).isFalse();
        assertThat(biu.position()).isEqualTo(5);
        assertThat(m.triggers().get(0).generators()).containsExactly("GEN_IDITENSESQ");
        assertThat(m.version()).isEqualTo("1.1");
    }

    @Test
    void should_sample_rows_on_firebird() {
        assertThat(src.sample(null, "PRITENSESQ", 1).rowCount()).isEqualTo(1);
    }
}
