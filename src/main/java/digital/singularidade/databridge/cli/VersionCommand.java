package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.BuildInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "version", description = "Print version and exit.")
public final class VersionCommand implements Runnable {

    @Spec CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().getParent().getOut().println(BuildInfo.NAME + " " + BuildInfo.VERSION);
    }
}
