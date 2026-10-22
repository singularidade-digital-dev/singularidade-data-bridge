package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.BuildInfo;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "version", description = "Print version and exit.")
public final class VersionCommand implements Runnable {

    @Spec CommandSpec spec;

    @Override
    public void run() {
        CommandLine parent = spec.commandLine().getParent();
        String edition = parent.getCommand() instanceof CatalogMain ? CatalogMain.EDITION_SUFFIX : "";
        parent.getOut().println(BuildInfo.NAME + " " + BuildInfo.VERSION + edition);
    }
}
