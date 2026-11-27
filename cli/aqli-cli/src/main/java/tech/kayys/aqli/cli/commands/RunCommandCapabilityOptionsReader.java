package tech.kayys.aqli.cli.commands;

import java.util.Objects;

import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapabilityRegistry;

/**
 * Reads required, typed option snapshots for one parsed {@code aqli run} invocation.
 *
 * <p>Capability discovery and option ownership remain in the registry/providers;
 * this reader only applies the command's required-capability policy.</p>
 */
final class RunCommandCapabilityOptionsReader {

    private final RunCapabilityRegistry registry;
    private final ParseResult parseResult;

    RunCommandCapabilityOptionsReader(RunCapabilityRegistry registry, ParseResult parseResult) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.parseResult = Objects.requireNonNull(parseResult, "parseResult");
    }

    <T> T require(String capabilityId, Class<T> optionsType, String missingMessage) {
        Objects.requireNonNull(capabilityId, "capabilityId");
        Objects.requireNonNull(optionsType, "optionsType");
        Objects.requireNonNull(missingMessage, "missingMessage");
        return registry.snapshot(capabilityId, parseResult, optionsType)
                .orElseThrow(() -> new IllegalStateException(missingMessage));
    }
}
