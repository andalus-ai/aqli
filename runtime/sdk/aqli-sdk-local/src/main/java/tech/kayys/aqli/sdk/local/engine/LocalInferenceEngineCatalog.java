package tech.kayys.aqli.sdk.local.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import tech.kayys.aqli.spi.inference.InferenceEngineDescriptor;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.LocalInferenceEngine;

/**
 * CDI-backed inventory of local inference engines.
 *
 * <p>The catalog only discovers and selects an engine for a concrete request.
 * It does not resolve model aliases, initialize providers, retry requests, or
 * own engine lifecycle; those concerns remain with the AQLI runtime.</p>
 */
@ApplicationScoped
public class LocalInferenceEngineCatalog {

    private final List<LocalInferenceEngine> engines;

    @Inject
    public LocalInferenceEngineCatalog(Instance<LocalInferenceEngine> engines) {
        this(snapshot(engines));
    }

    /**
     * Constructor for deterministic unit tests and non-CDI composition.
     */
    public LocalInferenceEngineCatalog(Collection<? extends LocalInferenceEngine> engines) {
        Objects.requireNonNull(engines, "engines");
        this.engines = List.copyOf(engines);
    }

    /**
     * Selects exactly one engine. An explicit provider preference is strict:
     * it never silently falls back to a different provider.
     *
     * @throws IllegalArgumentException when no engine supports the request or
     *         an explicitly requested provider is unavailable/unsupported
     * @throws IllegalStateException when more than one engine claims the request
     */
    public Selection select(InferenceRequest request) {
        Objects.requireNonNull(request, "request");

        String preferred = request.getPreferredProvider()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse(null);

        List<Selection> candidates = candidates();
        if (preferred != null) {
            List<Selection> named = candidates.stream()
                    .filter(candidate -> candidate.descriptor().providerId().equalsIgnoreCase(preferred))
                    .toList();
            if (named.isEmpty()) {
                throw new IllegalArgumentException("Requested inference provider is not available: " + preferred);
            }
            if (named.size() > 1) {
                throw ambiguous(preferred, named);
            }
            Selection selected = named.getFirst();
            if (!selected.descriptor().supports(request)) {
                throw new IllegalArgumentException("Provider '" + preferred
                        + "' does not support model request '" + request.getModel() + "'");
            }
            return selected;
        }

        List<Selection> supported = candidates.stream()
                .filter(candidate -> candidate.descriptor().supports(request))
                .toList();
        if (supported.isEmpty()) {
            throw new IllegalArgumentException(
                    "No local inference provider supports model request '" + request.getModel() + "'");
        }
        if (supported.size() > 1) {
            throw ambiguous(request.getModel(), supported);
        }
        return supported.getFirst();
    }

    /**
     * Returns the stable provider identifiers exposed by discoverable engines.
     */
    public List<String> availableProviders() {
        return candidates().stream()
                .map(candidate -> candidate.descriptor().providerId())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<Selection> candidates() {
        List<Selection> result = new ArrayList<>();
        for (LocalInferenceEngine engine : engines) {
            if (engine instanceof InferenceEngineDescriptor descriptor) {
                String providerId = descriptor.providerId();
                if (providerId == null || providerId.isBlank()) {
                    throw new IllegalStateException("Inference engine has a blank provider identifier: "
                            + engine.getClass().getName());
                }
                result.add(new Selection(providerId, engine, descriptor));
            }
        }
        result.sort(Comparator.comparing(Selection::providerId, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private static IllegalStateException ambiguous(String target, List<Selection> matches) {
        String providers = matches.stream()
                .map(Selection::providerId)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList()
                .toString();
        return new IllegalStateException("Ambiguous local inference engine selection for '"
                + target + "': " + providers);
    }

    private static List<LocalInferenceEngine> snapshot(Instance<LocalInferenceEngine> engines) {
        Objects.requireNonNull(engines, "engines");
        List<LocalInferenceEngine> result = new ArrayList<>();
        engines.forEach(result::add);
        return List.copyOf(result);
    }

    public record Selection(
            String providerId,
            LocalInferenceEngine engine,
            InferenceEngineDescriptor descriptor) {
        public Selection {
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(engine, "engine");
            Objects.requireNonNull(descriptor, "descriptor");
        }
    }
}
