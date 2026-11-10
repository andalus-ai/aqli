package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tech.kayys.aqli.spi.inference.InferenceResponse;

class OcrOutputWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void identifiesOnlyPaddleOcrVlMetadata() {
        assertTrue(OcrOutputWriter.isPaddleOcrMetadata(Map.of("pipeline", "paddleocr-vl")));
        assertFalse(OcrOutputWriter.isPaddleOcrMetadata(Map.of("pipeline", "other")));
        assertFalse(OcrOutputWriter.isPaddleOcrMetadata(null));
    }

    @Test
    void createsOcrPayloadWithOptionalTimingFields() {
        InferenceResponse response = InferenceResponse.builder()
                .requestId("req-1")
                .content("recognized")
                .model("demo")
                .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                .inputTokens(3)
                .outputTokens(2)
                .durationMs(17)
                .build();
        Map<String, Object> metadata = Map.of(
                "image_path", "/tmp/input.png",
                "ocr_text_without_locations", "recognized",
                "vision_ms", 4.5,
                "pipeline", "paddleocr-vl");

        Map<String, Object> payload = new OcrOutputWriter().createPayload(response, metadata);

        assertEquals("aqli_ocr_result", payload.get("type"));
        assertEquals("req-1", payload.get("request_id"));
        assertEquals("recognized", payload.get("text"));
        assertEquals(17L, payload.get("duration_ms"));
        assertEquals(Map.of("vision_ms", 4.5), payload.get("timings"));
        assertEquals(metadata, payload.get("metadata"));
    }

    @Test
    void writesJsonAndOverlayArtifacts(@TempDir Path outputDir) throws Exception {
        Path inputImage = tempDir.resolve("source.png");
        BufferedImage source = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(source, "png", inputImage.toFile());

        Map<String, Object> payload = Map.of(
                "image_path", inputImage.toString(),
                "regions", List.of(Map.of(
                        "text", "hello",
                        "box", Map.of("box_pixel_x1", 2, "box_pixel_y1", 3,
                                "box_pixel_x2", 20, "box_pixel_y2", 24))));

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);
        OcrOutputWriter writer = new OcrOutputWriter();

        Path overlay = writer.writeOverlay(payload, outputDir.resolve("overlay").toString(), true, out, err);
        Path json = outputDir.resolve("result.json");
        writer.writeJson(payload, json.toString(), true, out, err);

        assertNotNull(overlay);
        assertTrue(Files.isRegularFile(overlay));
        assertTrue(overlay.getFileName().toString().endsWith(".png"));
        assertTrue(Files.isRegularFile(json));
        assertTrue(Files.readString(json).contains("\"image_path\""));
        assertEquals("", stderr.toString(StandardCharsets.UTF_8));
    }

    @Test
    void overlayFailureIsReportedWithoutThrowing() {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Path result = new OcrOutputWriter().writeOverlay(
                Map.of(), tempDir.resolve("overlay.png").toString(), true,
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(stderr));

        assertEquals(null, result);
        assertTrue(stderr.toString().contains("OCR payload does not include image_path"));
    }
}
