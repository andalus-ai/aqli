package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GgufFastRunContractTest {

    @AfterEach
    void clearPrewarmOverrides() {
        System.clearProperty("aqli.gguf.fast_run.prewarm_prompt");
        System.clearProperty("aqli.gguf.fast_run.prewarm_context_tokens");
    }

    @Test
    void prewarmAddsRunCommandAndStableDefaults() {
        System.setProperty("aqli.gguf.fast_run.prewarm_prompt", "where is jakarta");
        System.setProperty("aqli.gguf.fast_run.prewarm_context_tokens", "10");
        assertArrayEquals(
                new String[] {
                    "run",
                    "--model", "models/example.gguf",
                    "--prompt", "where is jakarta",
                    "--max-tokens", "10",
                    "--provider", "gguf",
                    "--engine", "auto"
                },
                GgufFastRun.prewarmRunArgs(new String[] {"prewarm", "--model", "models/example.gguf"}));
    }

    @Test
    void prewarmPreservesExplicitEqualsStyleOptionsWithoutAppendingDefaults() {
        assertArrayEquals(
                new String[] {
                    "run",
                    "--model=models/example.gguf",
                    "--prompt=hello",
                    "--max-tokens=24",
                    "--provider=java",
                    "--engine=java"
                },
                GgufFastRun.prewarmRunArgs(new String[] {
                    "prewarm",
                    "--model=models/example.gguf",
                    "--prompt=hello",
                    "--max-tokens=24",
                    "--provider=java",
                    "--engine=java"
                }));
    }

    @Test
    void prewarmRecognizesAliasesAndDoesNotDuplicateExplicitValues() {
        assertArrayEquals(
                new String[] {
                    "run",
                    "--model", "models/example.gguf",
                    "-p", "hello",
                    "--max-tokens", "24",
                    "--provider", "gguf",
                    "--llamacpp"
                },
                GgufFastRun.prewarmRunArgs(new String[] {
                    "warmup",
                    "--model", "models/example.gguf",
                    "-p", "hello",
                    "--max-tokens", "24",
                    "--provider", "gguf",
                    "--llamacpp"
                }));
    }

    @Test
    void fallbackStatusIsDistinctFromCommandError() {
        assertTrue(GgufFastRun.isFallbackToFullCliStatus(42));
        assertFalse(GgufFastRun.isFallbackToFullCliStatus(2));
    }
}
