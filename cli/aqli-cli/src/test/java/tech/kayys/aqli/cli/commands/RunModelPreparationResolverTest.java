package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tech.kayys.aqli.sdk.model.ModelResolution;
import tech.kayys.aqli.sdk.runtime.AqliRuntime;

class RunModelPreparationResolverTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void forcedHuggingFaceRefreshUsesRepositoryFallbackInsteadOfCachedRuntimeResolution() throws Exception {
        Path model = Files.createFile(temporaryDirectory.resolve("model.safetensors"));
        LocalModelIndex.Entry entry = new LocalModelIndex.Entry();
        entry.path = model.toString();
        entry.format = "safetensor";

        java.util.concurrent.atomic.AtomicBoolean runtimePreparationCalled =
                new java.util.concurrent.atomic.AtomicBoolean();
        AqliRuntime runtime = (AqliRuntime) Proxy.newProxyInstance(
                AqliRuntime.class.getClassLoader(),
                new Class<?>[] { AqliRuntime.class },
                (proxy, method, args) -> {
                    if ("prepareModel".equals(method.getName())) {
                        runtimePreparationCalled.set(true);
                        throw new AssertionError("forced refresh must bypass cached runtime preparation");
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        RunModelPreparationResolver.Result result = RunModelPreparationResolver.resolve(
                new RunModelPreparationResolver.Options(
                        "hf:org/model", "hf:org/model", null, null,
                        null, null, null, false, true, false, true, List.of()),
                runtime,
                reference -> entry,
                ignored -> "safetensor",
                ignored -> "safetensor",
                path -> null,
                (reference, provider) -> false,
                progress -> { });

        assertTrue(result.successful());
        assertTrue(result.customModelPathUsed());
        assertEquals(model.toString(), result.localPath());
        assertTrue(!runtimePreparationCalled.get());
    }

    @Test
    void acceptsSdkResolvedLocalModelAndPreservesRequestedProvider() throws Exception {
        Path model = Files.createDirectory(temporaryDirectory.resolve("model"));
        AqliRuntime runtime = (AqliRuntime) Proxy.newProxyInstance(
                AqliRuntime.class.getClassLoader(),
                new Class<?>[] { AqliRuntime.class },
                (proxy, method, args) -> {
                    if ("prepareModel".equals(method.getName())) {
                        return new ModelResolution("resolved-model", model.toString(), null, "runtime-provider", null);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        RunModelPreparationResolver.Result result = RunModelPreparationResolver.resolve(
                new RunModelPreparationResolver.Options(
                        "requested-model", "hf:org/model", null, "explicit-provider",
                        null, null, null, false, false, true, true, List.of()),
                runtime,
                reference -> null,
                entry -> null,
                entry -> null,
                path -> null,
                (reference, provider) -> false,
                progress -> { });

        assertTrue(result.successful());
        assertTrue(result.customModelPathUsed());
        assertEquals("resolved-model", result.modelId());
        assertEquals(model.toString(), result.localPath());
        assertEquals("explicit-provider", result.providerId());
        assertEquals(1, result.notices().size());
        assertTrue(result.notices().get(0).startsWith("Model ready at:"));
    }
}
