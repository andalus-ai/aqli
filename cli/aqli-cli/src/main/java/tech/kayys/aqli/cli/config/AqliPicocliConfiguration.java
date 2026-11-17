package tech.kayys.aqli.cli.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import io.quarkus.picocli.runtime.PicocliCommandLineFactory;
import picocli.CommandLine;
import tech.kayys.aqli.cli.spi.run.RunCapabilityRegistry;

/**
 * Composes the Quarkus-managed Picocli command model with optional run capabilities.
 *
 * <p>Options must be contributed after Quarkus has built the annotated command
 * hierarchy but before Picocli parses process arguments. Producing the customized
 * CommandLine from Quarkus' factory preserves the CDI-aware factory and the
 * existing {@code @TopCommand} command graph.</p>
 */
@ApplicationScoped
public class AqliPicocliConfiguration {

    @Produces
    @ApplicationScoped
    RunCapabilityRegistry runCapabilityRegistry() {
        return RunCapabilityRegistry.load();
    }

    @Produces
    @ApplicationScoped
    CommandLine commandLine(
            PicocliCommandLineFactory factory,
            RunCapabilityRegistry registry) {
        CommandLine commandLine = factory.create();
        CommandLine runCommand = commandLine.getSubcommands().get("run");
        if (runCommand == null) {
            throw new IllegalStateException(
                    "The AQLI Picocli command tree does not contain the required 'run' command");
        }

        registry.registerOptions(runCommand.getCommandSpec());
        return commandLine;
    }
}
