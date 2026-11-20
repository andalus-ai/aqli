package tech.kayys.aqli.cli.commands;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.aqli.cli.chat.ChatUIRenderer;
import tech.kayys.aqli.spi.inference.InferenceResponse;

/**
 * Renders the response content modes supported by the {@code run} CLI command.
 *
 * <p>Structured OCR/JSON output is handled before route diagnostics; ordinary
 * text output is rendered after those diagnostics. Statistics and media
 * persistence remain responsibilities of the command.</p>
 */
final class RunResponseContentRenderer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RunResponseContentRenderer() {
    }

    /**
     * Renders OCR artifacts and JSON output when requested.
     *
     * @return {@code true} when a structured output mode completed rendering
     */
    static boolean renderStructured(
            InferenceResponse response,
            Map<String, Object> metadata,
            boolean jsonMode,
            boolean ocrJsonOutput,
            String ocrJsonOutputPath,
            String ocrOverlayOutputPath,
            PrintStream out,
            PrintStream err) {
        if (OcrOutputWriter.isPaddleOcrMetadata(metadata)) {
            OcrOutputWriter writer = new OcrOutputWriter();
            Map<String, Object> payload = writer.createPayload(response, metadata);
            java.nio.file.Path overlayPath = writer.writeOverlay(
                    payload, ocrOverlayOutputPath, ocrJsonOutput || jsonMode, out, err);
            if (overlayPath != null) {
                payload.put("overlay_path", overlayPath.toString());
            }
            writer.writeJson(payload, ocrJsonOutputPath, ocrJsonOutput || jsonMode, out, err);
            if (ocrJsonOutput || jsonMode) {
                printJsonPayload(payload, out, err);
                return true;
            }
        }
        if (!jsonMode) {
            return false;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", response.getContent());
        payload.put("tokenCount", response.getTokensUsed());
        payload.put("promptTokens", response.getInputTokens());
        payload.put("durationMs", response.getDurationMs());
        printJsonPayload(payload, out, err);
        return true;
    }

    static void renderText(InferenceResponse response, boolean raw, boolean debugPrompt, PrintStream out) {
        String content = response.getContent();
        String decoded = GgufStandaloneRunner.stripThinkingChannels(content);
        if (debugPrompt) {
            out.println("[Raw Output (From LLM Before Decode/Strip)]:");
            out.println("--------------------------------------------------");
            out.println(content);
            out.println("--------------------------------------------------");
            out.println("[Decoded/Sanitized Output]:");
            out.println("--------------------------------------------------");
            out.println(ChatUIRenderer.GREEN + decoded + ChatUIRenderer.RESET);
            out.println("--------------------------------------------------");
        } else {
            out.println(ChatUIRenderer.GREEN + (raw ? content : decoded) + ChatUIRenderer.RESET);
        }
        if (response.getSessionId() != null && !response.getSessionId().isBlank()) {
            out.println(ChatUIRenderer.DIM + "Session: " + response.getSessionId() + ChatUIRenderer.RESET);
        }
    }

    private static void printJsonPayload(Map<String, Object> payload, PrintStream out, PrintStream err) {
        try {
            out.println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
        } catch (Exception e) {
            err.println("\nFailed to render JSON: " + e.getMessage());
        }
    }
}
