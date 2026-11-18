package tech.kayys.aqli.cli.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Resolves a model reference against the CLI's local model index.
 *
 * <p>This keeps local-index lookup and the resulting disabled/missing-path
 * behavior separate from repository preparation and runtime route selection.</p>
 */
final class LocalModelIndexInputResolver {

    record Options(
            String modelId,
            String format,
            String providerId,
            boolean providerExplicit,
            boolean quietOutput,
            boolean jsonMode,
            boolean ocrJsonOutput) {
    }

    record Result(
            String modelId,
            String localPath,
            boolean customModelPathUsed,
            String providerId,
            String format,
            boolean disabled,
            List<String> messages) {

        Result {
            messages = List.copyOf(messages);
        }

        boolean resolved() {
            return customModelPathUsed;
        }
    }

    private LocalModelIndexInputResolver() {
    }

    static Result resolve(
            Options options,
            Function<LocalModelIndex.Entry, String> providerInference,
            Function<LocalModelIndex.Entry, String> formatInference) {
        if (options == null) {
            throw new IllegalArgumentException("local model index options are required");
        }

        List<String> messages = new ArrayList<>();
        String modelId = options.modelId();
        String providerId = options.providerId();
        String format = options.format();

        if (modelId == null || modelId.isBlank()) {
            return unresolved(modelId, providerId, format);
        }

        try {
            String preferredIndexFormat =
                    ModelFormatResolver.preferredLocalIndexFormat(modelId, format, providerId);
            var indexed = LocalModelIndexResolver.findExactReference(
                    modelId,
                    preferredIndexFormat,
                    (reference, lookupFormat) -> lookupFormat == null
                            ? LocalModelIndex.find(reference)
                            : LocalModelIndex.find(reference, lookupFormat));
            if (indexed.isEmpty()) {
                return unresolved(modelId, providerId, format);
            }

            var entry = indexed.get();
            if (!entry.enabled) {
                return new Result(
                        modelId,
                        null,
                        false,
                        providerId,
                        format,
                        true,
                        List.of(
                                "\\n" + (char) 27 + "[31mError: Model '" + entry.id + "' is disabled."
                                        + (char) 27 + "[0m",
                                (char) 27 + "[33mUse 'aqli model enable " + entry.shortId
                                        + "' to enable it for inference." + (char) 27 + "[0m\\n"));
            }

            if (entry.path == null || entry.path.isBlank() || !Files.exists(Path.of(entry.path))) {
                return unresolved(modelId, providerId, format);
            }

            String localPath = Path.of(entry.path).toAbsolutePath().toString();
            modelId = localPath;
            if (!options.providerExplicit()) {
                String inferred = providerInference.apply(entry);
                if (inferred != null && !inferred.isBlank()) {
                    providerId = inferred;
                }
            }
            if (format == null || format.isBlank()) {
                format = formatInference.apply(entry);
            }
            if (!options.quietOutput() && !options.jsonMode() && !options.ocrJsonOutput()) {
                messages.add("Resolved local model index entry: " + localPath);
            }
            return new Result(modelId, localPath, true, providerId, format, false, messages);
        } catch (Exception indexLookupFailure) {
            // Preserve the existing fallback to the normal SDK resolution flow.
            return unresolved(modelId, providerId, format);
        }
    }

    private static Result unresolved(String modelId, String providerId, String format) {
        return new Result(modelId, null, false, providerId, format, false, List.of());
    }
}
