package tech.kayys.aqli.cli.commands;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import tech.kayys.aqli.sdk.model.ModelResolution;
import tech.kayys.aqli.sdk.model.PullProgress;
import tech.kayys.aqli.sdk.runtime.AqliRuntime;

/**
 * Coordinates SDK model preparation and the CLI's Hugging Face repository fallback.
 *
 * <p>Provider/runtime selection remains with the caller. This class only resolves
 * a requested model into a local artifact and reports preparation outcomes.</p>
 */
final class RunModelPreparationResolver {

    record Options(
            String modelId,
            String requestedModelRef,
            String format,
            String providerId,
            String pluginId,
            String ggufQuant,
            String ggufOutType,
            boolean forceGguf,
            boolean forceRepositoryRefresh,
            boolean providerExplicit,
            boolean quietOutput,
            List<String> fallbackModelIds) {

        Options {
            fallbackModelIds = fallbackModelIds == null ? List.of() : List.copyOf(fallbackModelIds);
        }
    }

    record Result(
            String modelId,
            String localPath,
            boolean customModelPathUsed,
            String providerId,
            String format,
            boolean stopExecution,
            String error,
            List<String> notices) {

        Result {
            notices = List.copyOf(notices);
        }

        boolean successful() {
            return error == null;
        }
    }

    private RunModelPreparationResolver() {
    }

    static Result resolve(
            Options options,
            AqliRuntime runtime,
            Function<ModelReference, LocalModelIndex.Entry> repositoryFallback,
            Function<LocalModelIndex.Entry, String> providerInference,
            Function<LocalModelIndex.Entry, String> formatInference,
            Function<String, String> compatibilityIssueDetector,
            BiPredicate<String, String> localRepositoryRequired,
            Consumer<PullProgress> progressCallback) {
        if (options == null || runtime == null) {
            throw new IllegalArgumentException("model preparation options and runtime are required");
        }

        String modelId = options.modelId();
        String providerId = options.providerId();
        String format = options.format();
        String localPath = null;
        boolean localModelResolved = false;
        java.util.ArrayList<String> notices = new java.util.ArrayList<>();
        String quant = options.ggufQuant() != null
                ? options.ggufQuant()
                : (options.ggufOutType() != null ? options.ggufOutType() : "Q4_K_M");

        try {
            boolean forceHuggingFaceRefresh = options.forceRepositoryRefresh()
                    && ModelSourceResolver.huggingFaceRepositoryId(options.requestedModelRef()) != null;
            ModelResolution resolution = forceHuggingFaceRefresh
                    ? new ModelResolution(modelId, null, null)
                    : runtime.prepareModel(
                            modelId,
                            format,
                            options.pluginId(),
                            options.forceGguf(),
                            quant,
                            options.fallbackModelIds(),
                            progressCallback);

            modelId = resolution.getModelId();
            if (resolution.getLocalPath() != null) {
                localPath = resolution.getLocalPath();
                localModelResolved = true;
                notices.add("Model ready at: " + displayPath(localPath));
            }

            if (!localModelResolved) {
                LocalModelIndex.Entry downloaded = findRepositoryFallback(options, repositoryFallback, modelId, format, providerId);
                if (isUsable(downloaded)) {
                    localPath = Path.of(downloaded.path).toAbsolutePath().toString();
                    modelId = localPath;
                    localModelResolved = true;
                    if (!options.providerExplicit()) {
                        String inferred = providerInference.apply(downloaded);
                        if (inferred != null && !inferred.isBlank()) {
                            providerId = inferred;
                        }
                    }
                    if (format == null || format.isBlank()) {
                        format = formatInference.apply(downloaded);
                    }
                    notices.add("Model ready at: " + displayPath(localPath));
                } else if (localRepositoryRequired.test(options.requestedModelRef(), providerId)) {
                    return result(modelId, localPath, false, providerId, format, true, null, notices);
                }
            }

            String notice = resolution.getNotice();
            if (notice != null && !notice.isBlank()) {
                String compatibilityIssue = compatibilityIssueDetector.apply(localPath);
                boolean staleGemma4Notice = notice.contains("Gemma4 multimodal text checkpoints")
                        && compatibilityIssue == null;
                if (!staleGemma4Notice) {
                    notices.add(notice);
                }
            }
            if (providerId == null || providerId.isBlank()) {
                providerId = resolution.getProviderId();
            }
            return result(modelId, localPath, localModelResolved, providerId, format, false, null, notices);
        } catch (Exception preparationFailure) {
            try {
                LocalModelIndex.Entry downloaded = findRepositoryFallback(
                        options, repositoryFallback, modelId, format, providerId);
                if (isUsable(downloaded)) {
                    localPath = Path.of(downloaded.path).toAbsolutePath().toString();
                    modelId = localPath;
                    localModelResolved = true;
                    if (!options.providerExplicit()) {
                        String inferred = providerInference.apply(downloaded);
                        if (inferred != null && !inferred.isBlank()) {
                            providerId = inferred;
                        }
                    }
                    if (format == null || format.isBlank()) {
                        format = formatInference.apply(downloaded);
                    }
                    notices.add("Model ready at: " + displayPath(localPath));
                }
            } catch (Exception fallbackFailure) {
                return result(modelId, localPath, false, providerId, format, true,
                        "Failed to prepare model: " + fallbackFailure.getMessage(), notices);
            }
            if (!localModelResolved) {
                return result(modelId, localPath, false, providerId, format, true,
                        "Failed to prepare model: " + preparationFailure.getMessage(), notices);
            }
            return result(modelId, localPath, true, providerId, format, false, null, notices);
        }
    }

    private static LocalModelIndex.Entry findRepositoryFallback(
            Options options,
            Function<ModelReference, LocalModelIndex.Entry> repositoryFallback,
            String modelId,
            String format,
            String providerId) {
        String reference = options.requestedModelRef() != null ? options.requestedModelRef() : modelId;
        String preferredFormat = ModelFormatResolver.preferredLocalIndexFormat(
                options.requestedModelRef(), format, providerId);
        return repositoryFallback.apply(new ModelReference(reference, preferredFormat));
    }

    private static boolean isUsable(LocalModelIndex.Entry entry) {
        return entry != null && entry.path != null && !entry.path.isBlank() && Files.exists(Path.of(entry.path));
    }

    private static String displayPath(String path) {
        String displayPath = path;
        String userHome = System.getProperty("user.home");
        if (userHome != null && displayPath.startsWith(userHome)) {
            displayPath = "~" + displayPath.substring(userHome.length());
        }
        return displayPath;
    }

    private static Result result(
            String modelId,
            String localPath,
            boolean localModelResolved,
            String providerId,
            String format,
            boolean stopExecution,
            String error,
            List<String> notices) {
        return new Result(modelId, localPath, localModelResolved, providerId, format, stopExecution, error, notices);
    }

    record ModelReference(String modelId, String preferredFormat) {
    }
}
