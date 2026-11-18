package tech.kayys.aqli.cli.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapabilityRegistry;

/**
 * Runs capability-specific CLI validation after Picocli has parsed the command.
 */
@ApplicationScoped
public class RunCapabilityParseResultObserver {

    private final RunCapabilityRegistry registry;

    @Inject
    public RunCapabilityParseResultObserver(RunCapabilityRegistry registry) {
        this.registry = registry;
    }

    void validateRunCapabilities(@Observes ParseResult rootParseResult) {
        ParseResult current = rootParseResult;
        while (current != null) {
            if ("run".equals(current.commandSpec().name())) {
                registry.validate(current);
                return;
            }
            current = current.subcommand();
        }
    }
}
