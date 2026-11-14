package tech.kayys.aqli.model.repo.local;

import java.util.Locale;

/**
 * Resolves the task label recorded for a locally installed model.
 *
 * <p>This applies AQLI's repository-level task taxonomy to manifest metadata
 * and legacy model identifiers. It does not inspect model capabilities or
 * determine whether a runtime can execute a task.</p>
 */
public final class LocalModelTaskClassifier {

    private LocalModelTaskClassifier() {
    }

    /**
     * Resolves the task type while preserving explicit manifest values.
     *
     * <p>A pipeline tag is used when the manifest task type is absent or blank.
     * Legacy OCR and 3D identifier heuristics may refine an absent or generic
     * {@code text} value. Unknown absent values default to {@code text}, matching
     * the historical local-index behavior.</p>
     */
    public static String classify(
            String manifestTaskType,
            boolean taskTypeSpecified,
            String pipelineTag,
            String modelId,
            String modelName) {
        String taskType = manifestTaskType;

        if ((!taskTypeSpecified || taskType == null || taskType.isBlank())
                && pipelineTag != null) {
            String mapped = pipelineTagToTaskType(pipelineTag);
            if (mapped != null) {
                taskType = mapped;
            }
        }

        if (taskType == null || taskType.isBlank() || "text".equals(taskType)) {
            String lowerId = lower(modelId);
            String lowerName = lower(modelName);
            if (containsAny(lowerId, "ocr", "pix2text", "trocr", "nougat")
                    || lowerName.contains("ocr")) {
                taskType = "ocr";
            } else if (containsAny(lowerId, "3d", "block3d", "trellis", "shap-e", "mesh", "splat")
                    || containsAny(lowerName, "3d", "block3d", "trellis", "shap-e", "mesh", "splat")) {
                taskType = "3d";
            } else if (taskType == null || taskType.isBlank()) {
                taskType = "text";
            }
        }
        return taskType;
    }

    /**
     * Maps a Hugging Face {@code pipeline_tag} to an AQLI task label.
     * Returns {@code null} for blank or unrecognized tags.
     */
    public static String pipelineTagToTaskType(String pipelineTag) {
        if (pipelineTag == null || pipelineTag.isBlank()) {
            return null;
        }
        return switch (pipelineTag.trim().toLowerCase(Locale.ROOT)) {
            case "text-generation", "text2text-generation", "conversational", "fill-mask" -> "text";
            case "automatic-speech-recognition", "audio-classification" -> "stt";
            case "text-to-audio", "text-to-speech" -> "tts";
            case "ocr", "optical-character-recognition" -> "ocr";
            case "image-to-text", "document-question-answering", "visual-question-answering",
                    "image-text-to-text", "image-classification", "object-detection",
                    "image-segmentation", "image-to-image" -> "vision";
            case "feature-extraction", "sentence-similarity" -> "embedding";
            case "time-series-forecasting", "time-series-classification",
                    "tabular-regression", "tabular-classification" -> "timeseries";
            case "text-to-3d", "image-to-3d", "3d-generation", "mesh-generation", "3d" -> "3d";
            default -> null;
        };
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
