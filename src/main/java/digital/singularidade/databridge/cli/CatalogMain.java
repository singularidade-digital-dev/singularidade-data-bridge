package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.BuildInfo;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Entry point of {@code data-bridge-catalog.jar}: the catalog commands without free-form SQL. The JAR is
 * shaded without {@code QueryCommand} and {@link Main}, so {@code query} does not exist there at all;
 * fixed queries run through {@code run-query} with a reviewed queries file.
 */
@Command(
    name = "data-bridge",
    mixinStandardHelpOptions = true,
    versionProvider = CatalogMain.VersionProvider.class,
    description = "Catalog edition: extract, extract-all, list-tables, list-generators and run-query "
                + "(no free-form query).",
    subcommands = { VersionCommand.class, ExtractCommand.class, ExtractAllCommand.class,
                    ListTablesCommand.class, ListGeneratorsCommand.class, RunQueryCommand.class }
)
public final class CatalogMain implements Runnable {

    static final String EDITION_SUFFIX = " (catalog)";

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exit = new CommandLine(new CatalogMain()).execute(args);
        System.exit(exit);
    }

    static final class VersionProvider implements CommandLine.IVersionProvider {
        @Override public String[] getVersion() {
            return new String[] { BuildInfo.NAME + " " + BuildInfo.VERSION + EDITION_SUFFIX };
        }
    }
}
