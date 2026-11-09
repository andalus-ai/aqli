package tech.kayys.aqli.cli.commands;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.aljabr.spi.inference.InferenceResponse;

/**
 * Builds and writes the CLI's PaddleOCR-VL result artifacts.
 *
 * <p>Owns OCR-specific JSON and image-overlay serialization. Inference routing,
 * response selection, and terminal mode decisions remain in {@link RunCommand}.</p>
 */
final class OcrOutputWriter {

    private final ObjectMapper objectMapper;

    OcrOutputWriter() {
        this(new ObjectMapper());
    }

    OcrOutputWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    static boolean isPaddleOcrMetadata(Map<String, Object> metadata) {
        return metadata != null && "paddleocr-vl".equals(String.valueOf(metadata.get("pipeline")));
    }

    Map<String, Object> createPayload(InferenceResponse response, Map<String, Object> metadata) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "aqli_ocr_result");
        payload.put("request_id", response.getRequestId());
        payload.put("model", response.getModel());
        payload.put("content", response.getContent());
        payload.put("timestamp", response.getTimestamp().toString());
        payload.put("duration_ms", response.getDurationMs());
        payload.put("input_tokens", response.getInputTokens());
        payload.put("output_tokens", response.getOutputTokens());
        payload.put("image_path", metadata.get("image_path"));
        payload.put("text", metadata.get("ocr_text_without_locations"));
        payload.put("raw_text", metadata.get("ocr_raw_text"));
        payload.put("regions", metadata.getOrDefault("ocr_regions", List.of()));
        payload.put("boxes", metadata.getOrDefault("ocr_location_boxes", List.of()));
        payload.put("location_tokens", metadata.getOrDefault("ocr_location_tokens", List.of()));
        payload.put("generated_token_ids", metadata.getOrDefault("generated_token_ids", List.of()));
        payload.put("finish_reason", metadata.get("finish_reason"));

        Map<String, Object> timings = new LinkedHashMap<>();
        putIfPresent(timings, "vision_ms", metadata.get("vision_ms"));
        putIfPresent(timings, "decoder_prefill_ms", metadata.get("decoder_prefill_ms"));
        putIfPresent(timings, "decoder_decode_ms", metadata.get("decoder_decode_ms"));
        putIfPresent(timings, "latency_ms", metadata.get("latency_ms"));
        payload.put("timings", timings);
        payload.put("metadata", new LinkedHashMap<>(metadata));
        return payload;
    }

    void writeJson(Map<String, Object> payload, String outputPath, boolean quiet, PrintStream out, PrintStream err) {
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(outputPath).toAbsolutePath().normalize();
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            Files.writeString(path, json);
            if (!quiet) {
                out.println(ChatUIRenderer.DIM + "OCR JSON saved to: " + path + ChatUIRenderer.RESET);
            }
        } catch (Exception e) {
            err.println("\nFailed to save OCR JSON: " + e.getMessage());
        }
    }

    Path writeOverlay(Map<String, Object> payload, String outputPath, boolean quiet, PrintStream out, PrintStream err) {
        if (outputPath == null || outputPath.isBlank()) {
            return null;
        }
        try {
            String imagePath = stringValue(payload.get("image_path"));
            if (imagePath == null || imagePath.isBlank()) {
                throw new IllegalArgumentException("OCR payload does not include image_path");
            }
            Path inputPath = Path.of(imagePath).toAbsolutePath().normalize();
            BufferedImage source = ImageIO.read(inputPath.toFile());
            if (source == null) {
                throw new IllegalArgumentException("unsupported image file: " + inputPath);
            }

            BufferedImage overlay = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = overlay.createGraphics();
            try {
                graphics.drawImage(source, 0, 0, null);
                drawOverlay(graphics, overlay.getWidth(), overlay.getHeight(), payload);
            } finally {
                graphics.dispose();
            }

            Path target = Path.of(outputPath).toAbsolutePath().normalize();
            String extension = extension(target);
            if (extension == null || extension.isBlank()) {
                target = target.resolveSibling(target.getFileName() + ".png");
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ImageIO.write(overlay, "png", target.toFile());
            if (!quiet) {
                out.println(ChatUIRenderer.DIM + "OCR overlay saved to: " + target + ChatUIRenderer.RESET);
            }
            return target;
        } catch (Exception e) {
            err.println("\nFailed to save OCR overlay: " + e.getMessage());
            return null;
        }
    }

    private static void drawOverlay(Graphics2D graphics, int imageWidth, int imageHeight, Map<String, Object> payload) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setStroke(new BasicStroke(Math.max(2.0f, Math.min(imageWidth, imageHeight) / 400.0f)));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(13, Math.min(imageWidth, imageHeight) / 42)));

        List<Map<String, Object>> regions = objectMapList(payload.get("regions"));
        if (!regions.isEmpty()) {
            for (Map<String, Object> region : regions) {
                drawBox(graphics, objectMap(region.get("box")), stringValue(region.get("text")), imageWidth, imageHeight);
            }
            return;
        }
        for (Map<String, Object> box : objectMapList(payload.get("boxes"))) {
            drawBox(graphics, box, null, imageWidth, imageHeight);
        }
    }

    private static void drawBox(
            Graphics2D graphics, Map<String, Object> box, String label, int imageWidth, int imageHeight) {
        if (box == null || box.isEmpty()) {
            return;
        }
        int x1 = clamp(numberValue(box.get("box_pixel_x1"), numberValue(box.get("pixel_x1"), -1)), 0, imageWidth);
        int y1 = clamp(numberValue(box.get("box_pixel_y1"), numberValue(box.get("pixel_y1"), -1)), 0, imageHeight);
        int x2 = clamp(numberValue(box.get("box_pixel_x2"), numberValue(box.get("pixel_x2"), -1)), 0, imageWidth);
        int y2 = clamp(numberValue(box.get("box_pixel_y2"), numberValue(box.get("pixel_y2"), -1)), 0, imageHeight);
        if (x1 < 0 || y1 < 0 || x2 <= x1 || y2 <= y1) {
            return;
        }

        graphics.setColor(new Color(0, 142, 255, 40));
        graphics.fillRect(x1, y1, x2 - x1, y2 - y1);
        graphics.setColor(new Color(0, 142, 255, 235));
        graphics.drawRect(x1, y1, x2 - x1, y2 - y1);

        if (label == null || label.isBlank()) {
            label = "#" + stringValue(box.getOrDefault("index", ""));
        }
        if (label == null || label.isBlank()) {
            return;
        }
        String compact = truncate(label.replaceAll("\\s+", " ").trim(), 48);
        FontMetrics metrics = graphics.getFontMetrics();
        int pad = Math.max(4, metrics.getHeight() / 4);
        int labelWidth = metrics.stringWidth(compact) + pad * 2;
        int labelHeight = metrics.getHeight() + pad;
        int labelX = Math.max(0, Math.min(x1, imageWidth - labelWidth));
        int labelY = y1 - labelHeight >= 0 ? y1 - labelHeight : Math.min(imageHeight - labelHeight, y2);
        graphics.setColor(new Color(0, 0, 0, 190));
        graphics.fillRoundRect(labelX, labelY, labelWidth, labelHeight, 6, 6);
        graphics.setColor(Color.WHITE);
        graphics.drawString(compact, labelX + pad, labelY + metrics.getAscent() + (pad / 2));
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int numberValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static List<Map<String, Object>> objectMapList(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : raw) {
            Map<String, Object> map = objectMap(item);
            if (!map.isEmpty()) {
                result.add(map);
            }
        }
        return result;
    }

    private static String extension(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? null : name.substring(dot + 1);
    }

    private static String truncate(String value, int width) {
        if (value == null || value.length() <= width) {
            return value;
        }
        return value.substring(0, Math.max(0, width - 1)) + "…";
    }
}
