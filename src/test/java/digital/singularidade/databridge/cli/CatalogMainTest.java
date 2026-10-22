package digital.singularidade.databridge.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogMainTest {

    @Test
    void should_not_expose_free_form_query() {
        CommandLine cl = new CommandLine(new CatalogMain());
        assertThat(cl.getSubcommands().keySet())
            .containsExactlyInAnyOrder("version", "extract", "extract-all", "list-tables", "run-query");
    }

    @Test
    void should_print_the_catalog_edition() {
        StringWriter out = new StringWriter();
        int exit = new CommandLine(new CatalogMain()).setOut(new PrintWriter(out)).execute("version");
        assertThat(exit).isZero();
        assertThat(out.toString().strip()).startsWith("singularidade-data-bridge ").endsWith(" (catalog)");
    }

    @Test
    void should_keep_the_full_edition_version_line_unchanged() {
        StringWriter out = new StringWriter();
        new CommandLine(new Main()).setOut(new PrintWriter(out)).execute("version");
        assertThat(out.toString().strip()).doesNotContain("(catalog)");
    }
}
