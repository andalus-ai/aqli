package tech.kayys.aqli.cli.spi.run;

import picocli.CommandLine.ParseResult;

/**
 * Optional typed hand-off from a CLI capability to request assembly.
 *
 * <p>Implement this alongside {@link RunCapability} when parsed CLI values need
 * to be captured as a capability-owned immutable options value. The value is
 * an adapter concern; canonical request semantics remain in the SDK.</p>
 *
 * @param <T> the capability-owned options snapshot type
 */
public interface RunCapabilityOptions<T> {

    /**
     * Returns the exact type produced by {@link #snapshot(ParseResult)}.
     */
    Class<T> optionsType();

    /**
     * Creates an immutable snapshot from the parsed invocation.
     */
    T snapshot(ParseResult parseResult);
}
