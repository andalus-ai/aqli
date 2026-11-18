package tech.kayys.aqli.cli.spi.run;

import java.util.Collection;
import java.util.Optional;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParseResult;

/**
 * Discovers and coordinates CLI contributions for {@code aqli run}.
 *
 * <p>The registry is a discovery mechanism only. It does not select inference
 * backends, execute requests, or define canonical SDK request semantics.</p>
 */
public interface RunCapabilityRegistry {

    /**
     * Loads providers visible to the supplied class loader.
     */
    static RunCapabilityRegistry load(ClassLoader classLoader) {
        return ServiceLoaderRunCapabilityRegistry.load(classLoader);
    }

    /**
     * Loads providers visible to the thread context class loader.
     */
    static RunCapabilityRegistry load() {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        return load(contextLoader != null ? contextLoader : RunCapabilityRegistry.class.getClassLoader());
    }

    Optional<RunCapability> find(String capabilityId);

    /**
     * Returns capabilities in deterministic identifier order.
     */
    Collection<RunCapability> capabilities();

    /**
     * Invokes each capability's option contribution in deterministic order.
     */
    void registerOptions(CommandSpec commandSpec);

    /**
     * Validates every discovered capability against the parsed invocation.
     */
    void validate(ParseResult parseResult);

    /**
     * Captures a capability's parsed options as its declared concrete type.
     *
     * <p>Returns empty when the capability is not registered or does not
     * contribute an options snapshot. A requested type that differs from the
     * contributor's declared type is rejected rather than coerced.</p>
     */
    <T> Optional<T> snapshot(String capabilityId, ParseResult parseResult, Class<T> optionsType);
}
