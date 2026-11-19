package tech.kayys.aqli.cli.commands.route;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.aljabr.spi.model.ModelConfig;

/**
 * Checks local Safetensors checkpoints for known Gemma 4 compatibility limits.
 *
 * <p>This validator diagnoses the current local runtime's support boundary. It
 * does not select a provider, mutate a model, or execute inference.</p>
 */
public final class SafetensorCompatibilityPreflightValidator {

    private SafetensorCompatibilityPreflightValidator() {
    }

    /**
     * Returns a compatibility diagnostic for an unsupported local checkpoint,
     * or {@code null} when this check does not apply or cannot establish a problem.
     */
    public static String detect(String providerId, String localPath) {
        if (!"safetensor".equalsIgnoreCase(providerId) || !isSafetensorCheckpointDir(localPath)) {
            return null;
        }

        try {
            Path configPath = Path.of(localPath).resolve("config.json");
            ModelConfig parsed = ModelConfig.load(configPath, new ObjectMapper());
            String modelType = parsed.modelType() == null ? "" : parsed.modelType().toLowerCase(Locale.ROOT);
            String architecture = parsed.primaryArchitecture() == null
                    ? ""
                    : parsed.primaryArchitecture().toLowerCase(Locale.ROOT);
            boolean gemma4UnifiedWrapper = isGemma4UnifiedWrapper(modelType, architecture);
            boolean resolvedGemma4Text = modelType.startsWith("gemma4")
                    && parsed.hiddenSize() > 0
                    && parsed.numHiddenLayers() > 0
                    && parsed.numAttentionHeads() > 0;
            if (resolvedGemma4Text) {
                return null;
            }
            if (gemma4UnifiedWrapper) {
                return "this local safetensor runtime build recognizes Gemma 4 unified checkpoints like "
                        + Path.of(localPath).getFileName()
                        + ", but does not run their unified multimodal embedder path yet. "
                        + "Use a runtime plugin with Gemma 4 unified support, or convert to a supported artifact.";
            }
        } catch (Exception ignored) {
            // Fall back to raw config heuristics only when structured parsing cannot
            // reconcile the checkpoint into a supported text model shape.
        }

        try {
            Path configPath = Path.of(localPath).resolve("config.json");
            String config = Files.readString(configPath);
            boolean gemma4Conditional = config.contains("\"Gemma4ForConditionalGeneration\"")
                    || config.contains("\"Gemma4ForMultimodalLM\"")
                    || config.contains("\"Gemma4ForImageTextToText\"")
                    || config.contains("\"model_type\": \"gemma4\"");
            boolean gemma4Unified = config.contains("\"model_type\": \"gemma4_unified\"")
                    || config.contains("\"Gemma4ForMultimodalLM\"")
                    || config.contains("\"Gemma4ForImageTextToText\"");
            boolean multimodalWrapper = config.contains("\"vision_config\"")
                    || config.contains("\"audio_config\"");
            boolean hasTextConfig = config.contains("\"text_config\"");

            if (gemma4Unified && multimodalWrapper && hasTextConfig) {
                return null;
            }
            if (gemma4Unified && multimodalWrapper) {
                return "this local safetensor runtime build recognizes Gemma 4 unified checkpoints like "
                        + Path.of(localPath).getFileName()
                        + ", but does not run their unified multimodal embedder path yet. "
                        + "Use a runtime plugin with Gemma 4 unified support, or convert to a supported artifact.";
            }
            if (gemma4Conditional && multimodalWrapper && !hasTextConfig) {
                return "this local safetensor runtime build does not reliably support Gemma4 multimodal text checkpoints like "
                        + Path.of(localPath).getFileName()
                        + ". It may run but produce incorrect output. Use a newer safetensor engine build or convert the model to a supported runtime first.";
            }
        } catch (Exception ignored) {
            return null;
        }

        return null;
    }

    /**
     * Identifies a local checkpoint directory with a config and Safetensors weights.
     */
    public static boolean isSafetensorCheckpointDir(String localPath) {
        if (localPath == null || localPath.isBlank()) {
            return false;
        }
        try {
            Path path = Path.of(localPath);
            if (!Files.isDirectory(path) || !Files.exists(path.resolve("config.json"))) {
                return false;
            }
            return Files.exists(path.resolve("model.safetensors"))
                    || Files.exists(path.resolve("model.safetensor"))
                    || Files.exists(path.resolve("model.safetensors.index.json"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isGemma4UnifiedWrapper(String modelType, String architecture) {
        return modelType.equals("gemma4_unified")
                || architecture.equals("gemma4formultimodallm")
                || architecture.equals("gemma4forimagetexttotext");
    }
}
