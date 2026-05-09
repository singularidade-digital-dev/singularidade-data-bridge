package digital.singularidade.databridge.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "version", description = "Print version and exit.")
public final class VersionCommand implements Runnable {

    @Spec CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().getParent().getOut().println("singularidade-data-bridge 0.1.0");
    }
}
