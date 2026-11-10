package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import tech.kayys.aqli.cli.chat.ChatUIRenderer;
import tech.kayys.aqli.sdk.util.QuantSuggestionDetector.QuantSuggestion;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Optional;

/**
 * CLI wrapper for SDK QuantSuggestionDetector.
 * Handles ANSI colored output.
 */
public final class QuantSuggestionDetector {

    private QuantSuggestionDetector() {}

    /**
     * Detect model size and print a quantization suggestion if appropriate.
     *
     * @param modelId       the model name or HuggingFace ID
     * @param modelPath     local path to the model (may be null)
     * @param quantizeFlag  current --quantize value (null if not set)
     * @param quiet         suppress output
     * @return true if a strong suggestion was made (7B+)
     */
    public static boolean suggestIfNeeded(String modelId, String modelPath,
                                          String quantizeFlag, boolean quiet) {
        if (quiet) return false;
        if (quantizeFlag != null && !quantizeFlag.isBlank()) return false;

        Long sizeBytes = modelPath != null ? calculateSize(modelPath) : null;
        Optional<QuantSuggestion> suggestion = tech.kayys.aqli.sdk.util.QuantSuggestionDetector.detect(modelId, sizeBytes);

        if (suggestion.isEmpty()) return false;

        QuantSuggestion s = suggestion.get();
        if (s.stronglyRecommended()) {
            printStrongSuggestion(modelId, s.estimatedParams(), s.recommendedStrategy(), s.recommendedBits());
            return true;
        } else {
            printMildSuggestion(modelId, s.estimatedParams(), s.recommendedStrategy());
            return false;
        }
    }

    public static double parseParamCount(String modelId) {
        return tech.kayys.aqli.sdk.util.QuantSuggestionDetector.parseParamCount(modelId);
    }

    private static void printStrongSuggestion(String modelId, double params, String strategy, int bits) {
        String Y = ChatUIRenderer.YELLOW;
        String B = ChatUIRenderer.BOLD;
        String C = ChatUIRenderer.CYAN;
        String D = ChatUIRenderer.DIM;
        String R = ChatUIRenderer.RESET;

        long estimatedMemGB = Math.round(params * 2);

        System.out.println();
        System.out.println(Y + B + "⚡ Large model detected: " + R + C + String.format("%.1fB parameters", params) + R);
        System.out.println(Y + "   Estimated FP16 memory: ~" + estimatedMemGB + " GB" + R);
        System.out.println(Y + "   Quantization is strongly recommended to reduce memory usage." + R);
        System.out.println();
        System.out.println(D + "   Add " + R + C + "--quantize " + strategy + R + D + " to enable " + bits + "-bit quantization:" + R);
        System.out.println(D + "   Example: " + R + C + "aqli run --model " + shortenModelId(modelId) + " --quantize " + strategy + R);
        System.out.println();
    }

    private static void printMildSuggestion(String modelId, double params, String strategy) {
        String D = ChatUIRenderer.DIM;
        String C = ChatUIRenderer.CYAN;
        String R = ChatUIRenderer.RESET;

        System.out.println(D + "💡 Tip: Model is ~" + String.format("%.1fB", params) +
                " params. Consider " + C + "--quantize " + strategy + R + D +
                " for lower memory usage." + R);
    }

    private static long calculateSize(String path) {
        Path p = Path.of(path);
        if (!Files.exists(p)) return 0;
        if (Files.isRegularFile(p)) {
            try { return Files.size(p); } catch (IOException e) { return 0; }
        }
        AtomicLong size = new AtomicLong(0);
        try {
            Files.walkFileTree(p, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    size.addAndGet(attrs.size());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {}
        return size.get();
    }

    private static String shortenModelId(String modelId) {
        if (modelId != null && modelId.length() > 40) {
            return "..." + modelId.substring(modelId.length() - 37);
        }
        return modelId;
    }
}
