package tech.kayys.aqli.cli.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelSourceRoutingPolicyTest {

    @Test
    void recognizesRemoteProvidersCaseInsensitively() {
        assertTrue(ModelSourceRoutingPolicy.isRemoteProvider("OpenAI"));
        assertTrue(ModelSourceRoutingPolicy.isRemoteProvider("ollama"));
        assertFalse(ModelSourceRoutingPolicy.isRemoteProvider("onnx"));
        assertFalse(ModelSourceRoutingPolicy.isRemoteProvider(null));
        assertFalse(ModelSourceRoutingPolicy.isRemoteProvider(" "));
    }

    @Test
    void resolvesHuggingFaceRepositoriesLocallyForLocalExecution() {
        assertTrue(ModelSourceRoutingPolicy.requiresLocalHfRepositoryResolution(
                "hf:org/model", "onnx", false));
        assertTrue(ModelSourceRoutingPolicy.requiresLocalHfRepositoryResolution(
                "org/model", null, false));
    }

    @Test
    void skipsLocalResolutionForRemoteAndMcpProviders() {
        assertFalse(ModelSourceRoutingPolicy.requiresLocalHfRepositoryResolution(
                "hf:org/model", "openai", false));
        assertFalse(ModelSourceRoutingPolicy.requiresLocalHfRepositoryResolution(
                "hf:org/model", "onnx", true));
    }

    @Test
    void ignoresNonRepositoryModelReferences() {
        assertFalse(ModelSourceRoutingPolicy.requiresLocalHfRepositoryResolution(
                "/models/model.gguf", "gguf", false));
        assertFalse(ModelSourceRoutingPolicy.requiresLocalHfRepositoryResolution(
                "custom:model", "onnx", false));
    }
}
