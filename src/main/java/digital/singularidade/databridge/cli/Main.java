package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.BuildInfo;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "data-bridge",
    mixinStandardHelpOptions = true,
    versionProvider = Main.VersionProvider.class,
    subcommands = { VersionCommand.class, ExtractCommand.class, ExtractAllCommand.class,
                    QueryCommand.class, ListTablesCommand.class, ServeCommand.class }
)
public final class Main implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exit = new CommandLine(new Main()).execute(args);
        System.exit(exit);
    }

    static final class VersionProvider implements CommandLine.IVersionProvider {
        @Override public String[] getVersion() {
            return new String[] { BuildInfo.NAME + " " + BuildInfo.VERSION };
        }
    }
}
