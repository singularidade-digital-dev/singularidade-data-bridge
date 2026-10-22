package digital.singularidade.databridge.source.jdbc.firebird;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbCheck;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbColumn;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbForeignKey;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbIndex;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbKey;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbSequenceLink;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbTrigger;
import digital.singularidade.databridge.support.FbFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirebirdCatalogIT {

    private static FbFixture fx;
    private static Connection c;

    @BeforeAll
    static void start() throws SQLException {
        fx = new FbFixture();
        c = DriverManager.getConnection(fx.jdbcUrl());
    }

    @AfterAll
    static void stop() throws SQLException {
        c.close();
        fx.close();
    }

    private static FbColumn col(FbTable t, String name) {
        return t.columns().stream().filter(x -> x.name().equals(name)).findFirst().orElseThrow();
    }

    private static FbIndex idx(FbTable t, String name) {
        return t.indexes().stream().filter(x -> x.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void should_read_domains_with_not_null_default_and_check() {
        FbTable t = FirebirdCatalog.read(c, "PRITENSESQ");
        assertThat(col(t, "ATIVO").domainName()).isEqualTo("DM_FLAG");
        FbTable.FbDomain flag = t.domain("DM_FLAG").orElseThrow();
        assertThat(flag.notNull()).isTrue();
        assertThat(flag.defaultValue()).isEqualTo("'F'");
        assertThat(flag.checkSource()).isEqualTo("CHECK (VALUE IN ('T','F'))");
        assertThat(col(t, "ATIVO").effectiveNotNull(flag)).isTrue();
        assertThat(col(t, "EMPRESA").domainName()).isNull();
        assertThat(t.domains()).extracting(FbTable.FbDomain::name).containsExactly("DM_FLAG", "DM_NOME", "DM_VALOR");
    }

    @Test
    void should_read_native_types_charsets_and_collations() {
        FbTable t = FirebirdCatalog.read(c, "PRITENSESQ");
        assertThat(col(t, "VALOR").nativeType()).isEqualTo("NUMERIC(15,2)");
        assertThat(col(t, "QTDE").nativeType()).isEqualTo("NUMERIC(9,3)");
        assertThat(col(t, "PERC").nativeType()).isEqualTo("DECIMAL(4,2)");
        assertThat(col(t, "APELIDO").nativeType()).isEqualTo("VARCHAR(10)");
        assertThat(col(t, "APELIDO").charset()).isEqualTo("UTF8");
        assertThat(col(t, "NOME").charset()).isEqualTo("WIN1252");
        assertThat(col(t, "NOME").collation()).isEqualTo("WIN_PTBR");
        assertThat(col(t, "OBS").nativeType()).isEqualTo("BLOB SUB_TYPE TEXT");
        assertThat(col(t, "OBS").charset()).isEqualTo("ISO8859_1");
        assertThat(col(t, "FOTO").nativeType()).isEqualTo("BLOB SUB_TYPE BINARY");
        assertThat(col(t, "FOTO").charset()).isNull();
        assertThat(t.databaseCharset()).isEqualTo("ISO8859_1");
    }

    @Test
    void should_read_defaults_computed_and_identity_columns() {
        FbTable t = FirebirdCatalog.read(c, "PRITENSESQ");
        assertThat(col(t, "DTCAD").columnDefault()).isEqualTo("CURRENT_TIMESTAMP");
        assertThat(col(t, "TOTAL").computedSource()).isEqualTo("(VALOR * QTDE)");
        assertThat(col(t, "SEQ").identityType()).isEqualTo(1);
        assertThat(col(t, "SEQ").columnNotNull()).isTrue();
        assertThat(t.columns()).extracting(FbColumn::name).startsWith("ID", "EMPRESA", "CODPAI", "CODGRUPO");
    }

    @Test
    void should_link_generators_to_columns_by_identity_and_trigger_assignment() {
        FbTable t = FirebirdCatalog.read(c, "PRITENSESQ");
        assertThat(col(t, "SEQ").sequence()).isEqualTo(new FbSequenceLink("RDB$1", "identity", null));
        assertThat(col(t, "ID").sequence())
            .isEqualTo(new FbSequenceLink("GEN_IDITENSESQ", "trigger", "TR_PRITENSESQ_BI"));
        assertThat(col(t, "EMPRESA").sequence()).isNull();
    }

    @Test
    void should_read_keys_in_key_order() {
        FbTable t = FirebirdCatalog.read(c, "PRITENSESQ");
        assertThat(t.primaryKey().columns()).containsExactly("ID");
        assertThat(t.uniques()).extracting(FbKey::columns).containsExactly(List.of("CODEXT", "EMPRESA"));
        assertThat(FirebirdCatalog.read(c, "TBPAI").primaryKey().columns()).containsExactly("CODIGO", "EMPRESA");
    }

    @Test
    void should_read_foreign_keys_with_rules_and_referenced_columns() {
        FbTable t = FirebirdCatalog.read(c, "PRITENSESQ");
        FbForeignKey pai = t.foreignKeys().stream()
            .filter(f -> f.refTable().equals("TBPAI")).findFirst().orElseThrow();
        assertThat(pai.name()).isEqualTo("FK_PRITENSESQ_PAI");
        assertThat(pai.columns()).containsExactly("CODPAI", "EMPRESA");
        assertThat(pai.refColumns()).containsExactly("CODIGO", "EMPRESA");
        assertThat(pai.deleteRule()).isEqualTo("CASCADE");
        assertThat(pai.updateRule()).isEqualTo("RESTRICT");
        FbForeignKey grupo = t.foreignKeys().stream()
            .filter(f -> f.refTable().equals("TBGRUPO")).findFirst().orElseThrow();
        assertThat(grupo.name()).startsWith("INTEG_");
        assertThat(grupo.indexName()).startsWith("RDB$FOREIGN");
    }

    @Test
    void should_read_check_constraints_verbatim() {
        FbTable t = FirebirdCatalog.read(c, "PRITENSESQ");
        assertThat(t.checks()).extracting(FbCheck::source)
            .containsExactlyInAnyOrder("CHECK (PERC BETWEEN 0 AND 100)", "CHECK (QTDE >= 0)");
        assertThat(t.checks()).extracting(FbCheck::name).contains("CK_PRITENSESQ_PERC");
    }

    @Test
    void should_read_indexes_with_order_direction_expression_state_and_backing_constraint() {
        FbTable t = FirebirdCatalog.read(c, "PRITENSESQ");
        assertThat(idx(t, "IX_PRITENSESQ_MULTI").columns()).containsExactly("NOME", "EMPRESA", "CODPAI");
        assertThat(idx(t, "IX_PRITENSESQ_DTCAD").descending()).isTrue();
        assertThat(idx(t, "IX_PRITENSESQ_UPNOME").expression()).isEqualTo("(UPPER(NOME))");
        assertThat(idx(t, "IX_PRITENSESQ_UPNOME").columns()).isEmpty();
        assertThat(idx(t, "IX_PRITENSESQ_OFF").active()).isFalse();
        assertThat(idx(t, "IX_PRITENSESQ_MULTI").active()).isTrue();
        assertThat(idx(t, "FK_PRITENSESQ_PAI").constraintName()).isEqualTo("FK_PRITENSESQ_PAI");
        assertThat(idx(t, "PK_PRITENSESQ").unique()).isTrue();
        assertThat(idx(t, "IX_PRITENSESQ_MULTI").constraintName()).isNull();
    }

    @Test
    void should_read_triggers_with_decoded_type_position_state_and_generators() {
        FbTable t = FirebirdCatalog.read(c, "PRITENSESQ");
        assertThat(t.triggers()).extracting(FbTrigger::name)
            .containsExactly("TR_PRITENSESQ_BI", "TR_PRITENSESQ_BIU", "TR_PRITENSESQ_AD");
        FbTrigger biu = t.triggers().get(1);
        assertThat(biu.timing()).isEqualTo("BEFORE");
        assertThat(biu.events()).containsExactly("INSERT", "UPDATE");
        assertThat(biu.position()).isEqualTo(5);
        assertThat(biu.active()).isFalse();
        assertThat(biu.body()).startsWith("AS").endsWith("END");
        assertThat(t.triggers().get(0).generators()).containsExactly("GEN_IDITENSESQ");
        assertThat(t.triggers().get(2).timing()).isEqualTo("AFTER");
        assertThat(t.triggerGenerators()).containsExactly("GEN_IDITENSESQ");
    }

    @Test
    void should_read_comments_and_quoted_mixed_case_names() {
        FbTable t = FirebirdCatalog.read(c, "PRITENSESQ");
        assertThat(t.description()).isEqualTo("Itens de esquema de prescricao");
        assertThat(col(t, "VALOR").description()).isEqualTo("Valor unitario");
        FbTable mista = FirebirdCatalog.read(c, "TbMista");
        assertThat(mista.columns()).extracting(FbColumn::name).containsExactly("Cod", "Nome");
        assertThat(col(mista, "Nome").charset()).isEqualTo("NONE");
    }

    @Test
    void should_fail_with_table_not_found() {
        assertThatThrownBy(() -> FirebirdCatalog.read(c, "NAOEXISTE"))
            .isInstanceOfSatisfying(DataBridgeException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCodes.TABLE_NOT_FOUND));
    }
}
