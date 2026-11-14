package tech.kayys.aqli.cli.commands;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ModelFormatResolverTest {

    @Test
    void canonicalizesFormatAliases() {
        assertEquals("safetensors", ModelFormatResolver.canonicalFormat(" SafeTensor "));
        assertEquals("litert", ModelFormatResolver.canonicalFormat("tflite"));
        assertEquals("torchscript", ModelFormatResolver.canonicalFormat("PTH"));
        assertEquals("onnx", ModelFormatResolver.canonicalFormat("ONNX"));
        assertNull(ModelFormatResolver.canonicalFormat(" "));
    }

    @Test
    void detectsFormatsFromLocalFileNames() {
        assertEquals("gguf", ModelFormatResolver.formatForPath(Path.of("/models/model.GGUF")));
        assertEquals("litert", ModelFormatResolver.formatForPath(Path.of("model.task")));
        assertEquals("onnx", ModelFormatResolver.formatForPath(Path.of("decoder.onnx")));
        assertEquals("safetensors", ModelFormatResolver.formatForPath(Path.of("model.safetensors")));
        assertEquals("torchscript", ModelFormatResolver.formatForPath(Path.of("weights.pth")));
        assertNull(ModelFormatResolver.formatForPath(Path.of("config.json")));
        assertNull(ModelFormatResolver.formatForPath(null));
    }

    @Test
    void recognizesOnnxRepositoryHintsIncludingHfPrefix() {
        assertTrue(ModelFormatResolver.looksLikeOnnxModelReference("hf:onnx-community/model"));
        assertTrue(ModelFormatResolver.looksLikeOnnxModelReference("huggingface:onnx-community/model"));
        assertTrue(ModelFormatResolver.looksLikeOnnxModelReference("org/model-onnx"));
        assertFalse(ModelFormatResolver.looksLikeOnnxModelReference("org/model"));
        assertFalse(ModelFormatResolver.looksLikeOnnxModelReference(null));
    }


    @Test
    void appliesOnnxResolutionPrecedenceWithoutOverridingExplicitChoices() {
        assertTrue(ModelFormatResolver.shouldPreferOnnxResolution("hf:onnx-community/model", null, null));
        assertTrue(ModelFormatResolver.shouldPreferOnnxResolution("org/model", "onnx", null));
        assertTrue(ModelFormatResolver.shouldPreferOnnxResolution("org/model", null, "ONNX"));
        assertFalse(ModelFormatResolver.shouldPreferOnnxResolution("hf:onnx-community/model", "gguf", null));
        assertFalse(ModelFormatResolver.shouldPreferOnnxResolution("hf:onnx-community/model", null, "native"));

        assertEquals("onnx", ModelFormatResolver.preferredLocalIndexFormat("hf:onnx-community/model", null, null));
        assertEquals("gguf", ModelFormatResolver.preferredLocalIndexFormat("hf:onnx-community/model", "GGUF", null));
        assertEquals("onnx", ModelFormatResolver.preferredLocalIndexFormat("org/model", null, "onnx"));
        assertNull(ModelFormatResolver.preferredLocalIndexFormat("org/model", null, null));
    }

    @Test
    void mapsFormatsToDefaultProvidersAndNormalizesProviderAliases() {
        assertEquals("safetensor", ModelFormatResolver.providerForFormat("safetensors"));
        assertEquals("libtorch", ModelFormatResolver.providerForFormat("torchscript"));
        assertEquals("litert", ModelFormatResolver.providerForFormat("tflite"));
        assertNull(ModelFormatResolver.providerForFormat("unknown"));
        assertEquals("libtorch", ModelFormatResolver.normalizeProvider(" PyTorch "));
        assertEquals("cuda", ModelFormatResolver.normalizeProvider("CUDA"));
        assertNull(ModelFormatResolver.normalizeProvider(null));
    }
}
