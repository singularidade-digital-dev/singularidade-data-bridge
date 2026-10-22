package digital.singularidade.databridge.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlScannerTest {

    @Test
    void should_blank_literals_identifiers_and_comments_keeping_length_and_delimiters() {
        String sql = "SELECT 'a;b''c', \"X;Y\" /* ; */ FROM t -- ;\nWHERE $q$;$q$ = 1";
        String blanked = SqlScanner.blank(sql);
        assertThat(blanked).hasSameSizeAs(sql);
        assertThat(blanked).doesNotContain(";");
        assertThat(blanked).startsWith("SELECT '      ', \"   \"").contains("FROM t").contains("\nWHERE $q$ $q$ = 1");
    }

    @Test
    void should_treat_unterminated_literals_as_running_to_the_end() {
        assertThat(SqlScanner.blank("SELECT 'abc")).isEqualTo("SELECT '   ");
    }
}
