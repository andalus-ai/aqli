package tech.kayys.aqli.model.repo.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class LocalModelTaskClassifierTest {

    @Test
    void mapsPipelineTagsToAqliTaskTaxonomy() {
        assertEquals("text", LocalModelTaskClassifier.pipelineTagToTaskType("text-generation"));
        assertEquals("stt", LocalModelTaskClassifier.pipelineTagToTaskType("automatic-speech-recognition"));
        assertEquals("tts", LocalModelTaskClassifier.pipelineTagToTaskType("text-to-speech"));
        assertEquals("ocr", LocalModelTaskClassifier.pipelineTagToTaskType("optical-character-recognition"));
        assertEquals("vision", LocalModelTaskClassifier.pipelineTagToTaskType("image-classification"));
        assertEquals("embedding", LocalModelTaskClassifier.pipelineTagToTaskType("feature-extraction"));
        assertEquals("timeseries", LocalModelTaskClassifier.pipelineTagToTaskType("tabular-regression"));
        assertEquals("3d", LocalModelTaskClassifier.pipelineTagToTaskType("mesh-generation"));
        assertNull(LocalModelTaskClassifier.pipelineTagToTaskType("unknown-task"));
        assertNull(LocalModelTaskClassifier.pipelineTagToTaskType(" "));
    }

    @Test
    void usesPipelineTagWhenManifestTaskTypeIsAbsentOrUnspecified() {
        assertEquals("tts", LocalModelTaskClassifier.classify(
                null, false, "text-to-speech", "org/model", "model"));
        assertEquals("stt", LocalModelTaskClassifier.classify(
                "", true, "automatic-speech-recognition", "org/model", "model"));
    }

    @Test
    void preservesExplicitNonGenericManifestTaskType() {
        assertEquals("custom-task", LocalModelTaskClassifier.classify(
                "custom-task", true, "text-to-speech", "org/model", "model"));
    }

    @Test
    void legacyNameHeuristicsRefineGenericTextTask() {
        assertEquals("ocr", LocalModelTaskClassifier.classify(
                "text", true, null, "org/model", "Pix2Text"));
        assertEquals("3d", LocalModelTaskClassifier.classify(
                "text", true, null, "org/trellis", "model"));
    }

    @Test
    void defaultsMissingUnrecognizedTaskToText() {
        assertEquals("text", LocalModelTaskClassifier.classify(
                null, false, "unknown-task", "org/model", "model"));
    }

    @Test
    void preservesExplicitTaskEvenWhenIdentifierLooksLikeOcr() {
        assertEquals("embedding", LocalModelTaskClassifier.classify(
                "embedding", true, null, "org/ocr-model", "model"));
    }
}
