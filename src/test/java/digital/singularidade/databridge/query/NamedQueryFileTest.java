package digital.singularidade.databridge.query;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamedQueryFileTest {

    private static void assertInvalid(String text, String message) {
        assertThatThrownBy(() -> NamedQueryFile.parse(text))
            .isInstanceOfSatisfying(DataBridgeException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_ARGS))
            .hasMessageContaining(message);
    }

    @Test
    void should_parse_named_blocks_in_order() {
        Map<String, NamedQuery> m = NamedQueryFile.parse("""
            -- Dicionario do SGH
            -- name: tabela
            SELECT NOME, MODULO FROM TBSGHTABELAS WHERE NOME = :tabela;

            -- name: colunas
            -- descricoes oficiais das colunas
            SELECT COLUNA, DESCRICAO
              FROM TBSGHCOLUNAS
             WHERE TABELA = :tabela
             ORDER BY COLUNA
            """);
        assertThat(m.keySet()).containsExactly("tabela", "colunas");
        assertThat(m.get("tabela").sql()).isEqualTo("SELECT NOME, MODULO FROM TBSGHTABELAS WHERE NOME = :tabela");
        assertThat(m.get("colunas").sql())
            .isEqualTo("SELECT COLUNA, DESCRICAO\n  FROM TBSGHCOLUNAS\n WHERE TABELA = :tabela\n ORDER BY COLUNA");
    }

    @Test
    void should_reject_duplicates_empty_blocks_and_sql_before_the_first_name() {
        assertInvalid("-- name: a\nSELECT 1\n-- name: a\nSELECT 2\n", "duplicate");
        assertInvalid("-- name: a\n-- only a comment\n", "empty");
        assertInvalid("SELECT 1\n-- name: a\nSELECT 2\n", "before the first");
        assertInvalid("", "no query");
    }
}
