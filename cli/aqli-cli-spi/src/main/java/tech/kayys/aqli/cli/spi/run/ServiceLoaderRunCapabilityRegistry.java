package tech.kayys.aqli.cli.spi.run;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParseResult;

/**
 * ServiceLoader-backed immutable registry with eager consistency checks.
 */
final class ServiceLoaderRunCapabilityRegistry implements RunCapabilityRegistry {

    private final Map<String, RunCapability> byId;
    private final List<RunCapability> orderedCapabilities;

    private ServiceLoaderRunCapabilityRegistry(Collection<? extends RunCapabilityProvider> providers) {
        Objects.requireNonNull(providers, "providers");

        List<RunCapability> discovered = new ArrayList<>();
        for (RunCapabilityProvider provider : providers) {
            Objects.requireNonNull(provider, "ServiceLoader returned a null provider");
            String providerId = requireId(provider.capabilityId(), "provider");
            RunCapability capability = Objects.requireNonNull(
                    provider.create(), "RunCapabilityProvider '" + providerId + "' returned null");
            String capabilityId = requireId(capability.id(), "capability");

            if (!providerId.equals(capabilityId)) {
                throw new IllegalStateException("Run capability provider id '" + providerId
                        + "' does not match capability id '" + capabilityId + "'");
            }
            discovered.add(capability);
        }

        discovered.sort(Comparator.comparing(RunCapability::id));
        Map<String, RunCapability> indexed = new LinkedHashMap<>();
        for (RunCapability capability : discovered) {
            RunCapability previous = indexed.putIfAbsent(capability.id(), capability);
            if (previous != null) {
                throw new IllegalStateException("Duplicate run capability id: " + capability.id());
            }
        }

        this.byId = Map.copyOf(indexed);
        this.orderedCapabilities = List.copyOf(discovered);
    }

    static RunCapabilityRegistry load(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        List<RunCapabilityProvider> providers = new ArrayList<>();
        try {
            ServiceLoader.load(RunCapabilityProvider.class, classLoader)
                    .forEach(providers::add);
        } catch (ServiceConfigurationError error) {
            throw new IllegalStateException("Unable to load AQLI run capability providers", error);
        }
        return new ServiceLoaderRunCapabilityRegistry(providers);
    }

    static RunCapabilityRegistry fromProviders(Collection<? extends RunCapabilityProvider> providers) {
        return new ServiceLoaderRunCapabilityRegistry(providers);
    }

    @Override
    public Optional<RunCapability> find(String capabilityId) {
        return Optional.ofNullable(byId.get(capabilityId));
    }

    @Override
    public Collection<RunCapability> capabilities() {
        return orderedCapabilities;
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        orderedCapabilities.forEach(capability -> capability.registerOptions(commandSpec));
    }

    @Override
    public void validate(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        orderedCapabilities.forEach(capability -> capability.validate(parseResult));
    }

    private static String requireId(String id, String owner) {
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Run capability " + owner + " id must not be blank");
        }
        if (!id.equals(id.trim())) {
            throw new IllegalStateException("Run capability " + owner + " id must not contain surrounding whitespace");
        }
        return id;
    }
}
