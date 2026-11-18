package tech.kayys.aqli.cli.spi.run;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParseResult;

/**
 * A CLI contribution to the {@code aqli run} command.
 *
 * <p>Implementations own only command-line concerns: declaring their options and
 * validating parsed CLI input. They must map values to canonical SDK capability
 * requests through a capability-owned adapter; this SPI is not an execution API.</p>
 *
 * <p>The Picocli dependency is intentional because this is a CLI-specific SPI.
 * SDK, REST, and gRPC contracts must not depend on this interface.</p>
 */
public interface RunCapability {

    /**
     * Stable, lowercase capability identifier (for example {@code text} or {@code audio}).
     */
    String id();

    /**
     * Contributes this capability's options to the {@code run} command model.
     *
     * <p>Option names should be namespaced where appropriate to avoid collisions.</p>
     */
    void registerOptions(CommandSpec commandSpec);

    /**
     * Validates this capability's parsed options before request mapping.
     */
    default void validate(ParseResult parseResult) {
        // Most capabilities have no additional cross-option validation.
    }
}
