package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.sdk.route.RunnerRouteBenchmarkCache;
import tech.kayys.aqli.sdk.route.RunnerRoutePolicy;
import tech.kayys.aqli.sdk.route.RunnerRouteReportContract;
import tech.kayys.aqli.safetensor.engine.route.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiteRtLmFastRunDaemonTest {

    @Test
    void runHeaderMatchesFullCliShapeForIndexedModels() {
        LiteRtLmFastRun.FastArgs args = LiteRtLmFastRun.FastArgs.parse(
                new String[]{"run", "--model", "7c51c9", "--prompt", "hello"});
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        LiteRtLmFastRun.printRunHeader(
                Path.of("/tmp/gemma-4-E2B-it.litertlm"),
                args,
                new PrintStream(bytes, true, StandardCharsets.UTF_8));

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("_____"));
        assertTrue(output.contains("Resolved local model index entry: /tmp/gemma-4-E2B-it.litertlm"));
        assertTrue(output.contains("Model: /tmp/gemma-4-E2B-it.litertlm"));
        assertTrue(output.contains("Provider: litert, format=litertlm"));
        assertFalse(output.contains("Execution route:"));
    }

    @Test
    void runHeaderDoesNotPrintResolvedLineForExplicitModelFile() {
        LiteRtLmFastRun.FastArgs args = LiteRtLmFastRun.FastArgs.parse(
                new String[]{"run", "--modelFile", "/tmp/gemma-4-E2B-it.litertlm", "--prompt", "hello"});
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        LiteRtLmFastRun.printRunHeader(
                Path.of("/tmp/gemma-4-E2B-it.litertlm"),
                args,
                new PrintStream(bytes, true, StandardCharsets.UTF_8));

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertFalse(output.contains("Resolved local model index entry:"));
        assertTrue(output.contains("Model: /tmp/gemma-4-E2B-it.litertlm"));
        assertTrue(output.contains("Provider: litert, format=litertlm"));
    }

    @Test
    void runHeaderUsesConcreteLiteRtContainerFormat() {
        assertEquals("litertlm", LiteRtLmFastRun.liteRtModelFormat(Path.of("/tmp/model.litertlm")));
        assertEquals("tflite", LiteRtLmFastRun.liteRtModelFormat(Path.of("/tmp/model.tflite")));
        assertEquals("tflite", LiteRtLmFastRun.liteRtModelFormat(Path.of("/tmp/model.tfl")));
        assertEquals("task", LiteRtLmFastRun.liteRtModelFormat(Path.of("/tmp/model.task")));
        assertEquals("litert", LiteRtLmFastRun.liteRtModelFormat(Path.of("/tmp/model.bin")));
    }

    @Test
    void runCommandRoutesResolvedLiteRtLmArtifactToStandaloneFastPath(@TempDir Path tempDir) throws Exception {
        Path litert = Files.writeString(tempDir.resolve("gemma-4-E2B-it.litertlm"), "");
        RunCommand command = new RunCommand();
        command.prompt = "what is jakarta";
        command.providerId = "litert";
        command.maxTokens = 16;
        command.temperature = 0.0;
        command.topK = 1;
        command.topP = 1.0;

        assertTrue(command.shouldTryStandaloneLiteRtFastPath(litert.toString()));
        assertArrayEquals(new String[] {
                "run",
                "--modelFile", litert.toString(),
                "--prompt", "what is jakarta",
                "--max-tokens", "16",
                "--temperature", "0.0",
                "--top-k", "1",
                "--top-p", "1.0",
                "--provider", "litert"
        }, command.buildStandaloneLiteRtFastRunArgs(litert.toString()));
    }

    @Test
    void runCommandDoesNotStealLiteRtLmArtifactWhenRequestNeedsFullProvider(@TempDir Path tempDir) throws Exception {
        Path litert = Files.writeString(tempDir.resolve("gemma-4-E2B-it.litertlm"), "");
        RunCommand command = new RunCommand();
        command.prompt = "what is jakarta";
        command.providerId = "litert";
        command.ragContexts = java.util.List.of("context");

        assertFalse(command.shouldTryStandaloneLiteRtFastPath(litert.toString()));
    }

    @Test
    void runCommandAllowsCachedGemma4LiteRtFastPathForSimpleTextRun() {
        RunCommand command = new RunCommand();
        command.modelId = "0576e9";
        command.prompt = "what is jakarta";

        assertTrue(command.shouldTryCachedGemma4MobileQatLiteRtFastPath(false, null));
        assertTrue(command.shouldTryCachedGemma4MobileQatLiteRtFastPath(true, "litert"));
        assertFalse(command.shouldTryCachedGemma4MobileQatLiteRtFastPath(true, "safetensor"));
    }

    @Test
    void runCommandDoesNotUseCachedGemma4LiteRtFastPathWhenRequestNeedsFullProvider() {
        RunCommand command = new RunCommand();
        command.modelId = "0576e9";
        command.prompt = "what is jakarta";
        command.ragContexts = java.util.List.of("context");

        assertFalse(command.shouldTryCachedGemma4MobileQatLiteRtFastPath(false));
    }

    @Test
    void executionRouteNamesDaemonAndDirectFastPath() {
        assertEquals(
                "Execution route: litert (backend=GPU/Metal) [official_litert_lm_jvm_daemon]",
                LiteRtLmFastRun.executionRouteLine("GPU/Metal", "daemon"));
        assertEquals(
                "Execution route: litert (backend=CPU) [official_litert_lm_jvm_fast_path]",
                LiteRtLmFastRun.executionRouteLine("CPU", "fast path"));
    }

    @Test
    void safetensorShortIdCanResolveMatchingLiteRtEquivalentForSpeed(@TempDir Path tempHome) throws Exception {
        Path litertDir = tempHome.resolve("models/litert/gemma");
        Path litertFile = litertDir.resolve("gemma-4-E2B-it.litertlm");
        Path safetensorDir = tempHome.resolve("models/safetensors/gemma");
        Files.createDirectories(litertDir);
        Files.createDirectories(safetensorDir);
        Files.writeString(litertFile, "stub");
        writeModelIndex(tempHome, safetensorDir, litertDir);

        String previousHome = System.getProperty("user.home");
        String previousAutoEquivalent = System.getProperty("aqli.litert.fast_run.auto_equivalent");
        System.setProperty("user.home", tempHome.toString());
        System.clearProperty("aqli.litert.fast_run.auto_equivalent");
        try {
            LiteRtLmFastRun.FastArgs args = LiteRtLmFastRun.FastArgs.parse(
                    new String[]{"run", "--model", "97cbf2", "--prompt", "hello"});
            LiteRtLmFastRun.RouteResolution route = LiteRtLmFastRun.resolveLiteRtLmRoute(args).orElseThrow();

            assertEquals(litertFile.toAbsolutePath().normalize(), LiteRtLmFastRun.resolveLiteRtLmModel(args).orElseThrow());
            assertEquals(litertFile.toAbsolutePath().normalize(), route.path());
            assertEquals("fast-index-equivalent-litert", route.source());
            assertTrue(route.cacheHit());
            assertEquals("litertlm", route.selectedArtifact());
            assertTrue(route.durationNanos() >= 0L);
        } finally {
            restoreProperty("user.home", previousHome);
            restoreProperty("aqli.litert.fast_run.auto_equivalent", previousAutoEquivalent);
        }
    }

    @Test
    void gemma4MobileQatShortIdResolvesIndexedLiteRtEquivalentBeforeCachedFallback(@TempDir Path tempHome)
            throws Exception {
        Path litertDir = tempHome.resolve("models/litert/gemma");
        Path litertFile = litertDir.resolve("gemma-4-E2B-it.litertlm");
        Path safetensorDir = tempHome.resolve("models/safetensors/gemma4-mobile-qat");
        Files.createDirectories(litertDir);
        Files.createDirectories(safetensorDir);
        Files.writeString(litertFile, "stub");
        writeModelIndex(
                tempHome,
                "0576e9",
                "google/gemma-4-E2B-it-qat-mobile-transformers",
                "gemma-4-E2B-it-qat-mobile-transformers",
                safetensorDir,
                litertDir);

        String previousHome = System.getProperty("user.home");
        String previousAutoEquivalent = System.getProperty("aqli.litert.fast_run.auto_equivalent");
        System.setProperty("user.home", tempHome.toString());
        System.clearProperty("aqli.litert.fast_run.auto_equivalent");
        try {
            LiteRtLmFastRun.FastArgs args = LiteRtLmFastRun.FastArgs.parse(
                    new String[]{"run", "--model", "0576e9", "--prompt", "hello"});
            LiteRtLmFastRun.RouteResolution route = LiteRtLmFastRun.resolveLiteRtLmRoute(args).orElseThrow();

            assertEquals(litertFile.toAbsolutePath().normalize(), route.path());
            assertEquals("fast-index-equivalent-litert", route.source());
            assertTrue(route.cacheHit());
            assertEquals("litertlm", route.selectedArtifact());
        } finally {
            restoreProperty("user.home", previousHome);
            restoreProperty("aqli.litert.fast_run.auto_equivalent", previousAutoEquivalent);
        }
    }

    @Test
    void equivalentLiteRtResolutionCanBeDisabled(@TempDir Path tempHome) throws Exception {
        Path litertDir = tempHome.resolve("models/litert/gemma");
        Path litertFile = litertDir.resolve("gemma-4-E2B-it.litertlm");
        Path safetensorDir = tempHome.resolve("models/safetensors/gemma");
        Files.createDirectories(litertDir);
        Files.createDirectories(safetensorDir);
        Files.writeString(litertFile, "stub");
        writeModelIndex(tempHome, safetensorDir, litertDir);

        String previousHome = System.getProperty("user.home");
        String previousAutoEquivalent = System.getProperty("aqli.litert.fast_run.auto_equivalent");
        System.setProperty("user.home", tempHome.toString());
        System.setProperty("aqli.litert.fast_run.auto_equivalent", "false");
        try {
            LiteRtLmFastRun.FastArgs args = LiteRtLmFastRun.FastArgs.parse(
                    new String[]{"run", "--model", "97cbf2", "--prompt", "hello"});

            assertTrue(LiteRtLmFastRun.resolveLiteRtLmModel(args).isEmpty());
        } finally {
            restoreProperty("user.home", previousHome);
            restoreProperty("aqli.litert.fast_run.auto_equivalent", previousAutoEquivalent);
        }
    }

    @Test
    void daemonRouteResolutionCacheInvalidatesWhenModelIndexChanges(@TempDir Path tempHome) throws Exception {
        Path litertDir = tempHome.resolve("models/litert/gemma");
        Path litertFile = litertDir.resolve("gemma-4-E2B-it.litertlm");
        Path secondLiteRtDir = tempHome.resolve("models/litert/gemma-second-version");
        Path secondLiteRtFile = secondLiteRtDir.resolve("gemma-4-E2B-it.litertlm");
        Path safetensorDir = tempHome.resolve("models/safetensors/gemma");
        Files.createDirectories(litertDir);
        Files.createDirectories(secondLiteRtDir);
        Files.createDirectories(safetensorDir);
        Files.writeString(litertFile, "stub");
        Files.writeString(secondLiteRtFile, "stub");
        writeModelIndex(tempHome, safetensorDir, litertDir);

        String previousHome = System.getProperty("user.home");
        String previousAutoEquivalent = System.getProperty("aqli.litert.fast_run.auto_equivalent");
        String previousRouteCache = System.getProperty("aqli.litert.fast_run.daemon_route_cache");
        System.setProperty("user.home", tempHome.toString());
        System.clearProperty("aqli.litert.fast_run.auto_equivalent");
        System.clearProperty("aqli.litert.fast_run.daemon_route_cache");
        try {
            LiteRtLmFastRun.FastArgs args = LiteRtLmFastRun.FastArgs.parse(
                    new String[]{"run", "--model", "97cbf2", "--prompt", "hello"});
            LiteRtLmFastRun.RouteResolutionCache cache = new LiteRtLmFastRun.RouteResolutionCache();

            LiteRtLmFastRun.RouteResolution first = cache.resolve(args).orElseThrow();
            LiteRtLmFastRun.RouteResolution hot = cache.resolve(args).orElseThrow();
            assertEquals(litertFile.toAbsolutePath().normalize(), first.path());
            assertEquals(first.path(), hot.path());
            assertEquals("fast-index-equivalent-litert", hot.source());
            assertEquals(1, cache.size());

            writeModelIndex(tempHome, safetensorDir, secondLiteRtDir);

            LiteRtLmFastRun.RouteResolution invalidated = cache.resolve(args).orElseThrow();
            assertEquals(secondLiteRtFile.toAbsolutePath().normalize(), invalidated.path());
            assertEquals("fast-index-equivalent-litert", invalidated.source());
            assertEquals(1, cache.size());
        } finally {
            restoreProperty("user.home", previousHome);
            restoreProperty("aqli.litert.fast_run.auto_equivalent", previousAutoEquivalent);
            restoreProperty("aqli.litert.fast_run.daemon_route_cache", previousRouteCache);
        }
    }

    @Test
    void daemonRouteResolutionCacheCanBeDisabledForDiagnostics() {
        String previous = System.getProperty("aqli.litert.fast_run.daemon_route_cache");
        System.setProperty("aqli.litert.fast_run.daemon_route_cache", "false");
        try {
            assertFalse(LiteRtLmFastRun.daemonRouteCacheEnabled());
        } finally {
            restoreProperty("aqli.litert.fast_run.daemon_route_cache", previous);
        }
    }

    @Test
    void fastRunStatsUseCliPerformanceMetricShape() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        long start = TimeUnit.MILLISECONDS.toNanos(1_000);
        long beforeEngine = start + TimeUnit.MILLISECONDS.toNanos(25);
        long afterEngine = beforeEngine + TimeUnit.MILLISECONDS.toNanos(75);
        long conversationReady = afterEngine + TimeUnit.MILLISECONDS.toNanos(20);
        long firstChunk = conversationReady + TimeUnit.MILLISECONDS.toNanos(125);
        long end = start + TimeUnit.MILLISECONDS.toNanos(1_000);

        LiteRtLmFastRun.printFastRunStats(
                new PrintStream(bytes, true, StandardCharsets.UTF_8),
                4,
                8,
                start,
                beforeEngine,
                afterEngine,
                conversationReady,
                firstChunk,
                end);

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("[Stream updates: 4, Duration:"));
        assertTrue(output.contains("Performance Metrics:"));
        assertTrue(output.contains("  load time      ="));
        assertTrue(output.contains("  generation     ="));
        assertTrue(output.contains("  latency (ttft) ="));
        assertTrue(output.contains("  engine ttft    ="));
        assertTrue(output.contains("  token latency  ="));
    }

    @Test
    void staleDaemonForceKillIsEnabledByDefault() {
        String previous = System.getProperty("aqli.litert.fast_run.stale_daemon_force_kill");
        System.clearProperty("aqli.litert.fast_run.stale_daemon_force_kill");
        try {
            assertTrue(LiteRtLmFastRun.staleDaemonForceKill());
        } finally {
            restoreProperty("aqli.litert.fast_run.stale_daemon_force_kill", previous);
        }
    }

    @Test
    void staleDaemonForceKillCanBeDisabledForDiagnostics() {
        String previous = System.getProperty("aqli.litert.fast_run.stale_daemon_force_kill");
        System.setProperty("aqli.litert.fast_run.stale_daemon_force_kill", "false");
        try {
            assertFalse(LiteRtLmFastRun.staleDaemonForceKill());
        } finally {
            restoreProperty("aqli.litert.fast_run.stale_daemon_force_kill", previous);
        }
    }

    @Test
    void staleDaemonKillWaitHasSafetyFloor() {
        String previous = System.getProperty("aqli.litert.fast_run.stale_daemon_kill_wait_ms");
        System.setProperty("aqli.litert.fast_run.stale_daemon_kill_wait_ms", "1");
        try {
            assertEquals(100L, LiteRtLmFastRun.staleDaemonKillWaitMillis());
        } finally {
            restoreProperty("aqli.litert.fast_run.stale_daemon_kill_wait_ms", previous);
        }
    }

    @Test
    void daemonProcessAliveRejectsInvalidAndCurrentPid() {
        assertFalse(LiteRtLmFastRun.isDaemonProcessAlive(-1));
        assertFalse(LiteRtLmFastRun.isDaemonProcessAlive(ProcessHandle.current().pid()));
    }

    @Test
    void strictDaemonKeyIsEnabledByDefaultForLiteRtSafety() {
        String previous = System.getProperty("aqli.litert.fast_run.strict_daemon_key");
        System.clearProperty("aqli.litert.fast_run.strict_daemon_key");
        try {
            assertTrue(LiteRtLmFastRun.strictDaemonKey());
        } finally {
            restoreProperty("aqli.litert.fast_run.strict_daemon_key", previous);
        }
    }

    @Test
    void strictDaemonKeyCanBeDisabledForDiagnostics() {
        String previous = System.getProperty("aqli.litert.fast_run.strict_daemon_key");
        System.setProperty("aqli.litert.fast_run.strict_daemon_key", "false");
        try {
            assertFalse(LiteRtLmFastRun.strictDaemonKey());
        } finally {
            restoreProperty("aqli.litert.fast_run.strict_daemon_key", previous);
        }
    }

    @Test
    void daemonPidVerificationIsEnabledByDefaultForFastStaleCleanup() {
        String previous = System.getProperty("aqli.litert.fast_run.verify_daemon_pid");
        System.clearProperty("aqli.litert.fast_run.verify_daemon_pid");
        try {
            assertTrue(LiteRtLmFastRun.verifyDaemonPid());
        } finally {
            restoreProperty("aqli.litert.fast_run.verify_daemon_pid", previous);
        }
    }

    @Test
    void daemonPidVerificationCanBeDisabledForDiagnostics() {
        String previous = System.getProperty("aqli.litert.fast_run.verify_daemon_pid");
        System.setProperty("aqli.litert.fast_run.verify_daemon_pid", "false");
        try {
            assertFalse(LiteRtLmFastRun.verifyDaemonPid());
        } finally {
            restoreProperty("aqli.litert.fast_run.verify_daemon_pid", previous);
        }
    }

    @Test
    void keepAliveConversationIsDisabledByDefaultBecauseLiteRtLmAllowsOneSession() {
        String previous = System.getProperty("aqli.litert.fast_run.keepalive_conversation");
        System.clearProperty("aqli.litert.fast_run.keepalive_conversation");
        try {
            assertFalse(LiteRtLmFastRun.keepAliveConversationEnabled());
        } finally {
            restoreProperty("aqli.litert.fast_run.keepalive_conversation", previous);
        }
    }

    @Test
    void keepAliveConversationCanBeEnabledForDiagnostics() {
        String previous = System.getProperty("aqli.litert.fast_run.keepalive_conversation");
        System.setProperty("aqli.litert.fast_run.keepalive_conversation", "true");
        try {
            assertTrue(LiteRtLmFastRun.keepAliveConversationEnabled());
        } finally {
            restoreProperty("aqli.litert.fast_run.keepalive_conversation", previous);
        }
    }

    @Test
    void daemonLauncherDefaultsToAutoForMacOsPersistence() {
        String previous = System.getProperty("aqli.litert.fast_run.daemon_launcher");
        System.clearProperty("aqli.litert.fast_run.daemon_launcher");
        try {
            assertEquals("auto", LiteRtLmFastRun.daemonLauncherMode());
        } finally {
            restoreProperty("aqli.litert.fast_run.daemon_launcher", previous);
        }
    }

    @Test
    void daemonLauncherSupportsAutoOverride() {
        String previous = System.getProperty("aqli.litert.fast_run.daemon_launcher");
        System.setProperty("aqli.litert.fast_run.daemon_launcher", " auto ");
        try {
            assertEquals("auto", LiteRtLmFastRun.daemonLauncherMode());
        } finally {
            restoreProperty("aqli.litert.fast_run.daemon_launcher", previous);
        }
    }

    @Test
    void daemonLauncherSupportsLaunchctlOverride() {
        String previous = System.getProperty("aqli.litert.fast_run.daemon_launcher");
        System.setProperty("aqli.litert.fast_run.daemon_launcher", " launchctl ");
        try {
            assertEquals("launchctl", LiteRtLmFastRun.daemonLauncherMode());
        } finally {
            restoreProperty("aqli.litert.fast_run.daemon_launcher", previous);
        }
    }

    @Test
    void daemonLauncherFallsBackToNohupWhenInvalid() {
        String previous = System.getProperty("aqli.litert.fast_run.daemon_launcher");
        System.setProperty("aqli.litert.fast_run.daemon_launcher", "bogus");
        try {
            assertEquals("nohup", LiteRtLmFastRun.daemonLauncherMode());
        } finally {
            restoreProperty("aqli.litert.fast_run.daemon_launcher", previous);
        }
    }

    @Test
    void daemonRequestRetryDefaultsToShortBoundedWindow() {
        String previous = System.getProperty("aqli.litert.fast_run.daemon_request_retry_ms");
        System.clearProperty("aqli.litert.fast_run.daemon_request_retry_ms");
        try {
            assertEquals(2_000L, LiteRtLmFastRun.daemonRequestRetryMillis());
        } finally {
            restoreProperty("aqli.litert.fast_run.daemon_request_retry_ms", previous);
        }
    }

    @Test
    void daemonRequestRetryCanBeDisabledForDiagnostics() {
        String previous = System.getProperty("aqli.litert.fast_run.daemon_request_retry_ms");
        System.setProperty("aqli.litert.fast_run.daemon_request_retry_ms", "-1");
        try {
            assertEquals(0L, LiteRtLmFastRun.daemonRequestRetryMillis());
        } finally {
            restoreProperty("aqli.litert.fast_run.daemon_request_retry_ms", previous);
        }
    }

    @Test
    void daemonRequestRetrySleepHasSafetyFloor() {
        String previous = System.getProperty("aqli.litert.fast_run.daemon_request_retry_sleep_ms");
        System.setProperty("aqli.litert.fast_run.daemon_request_retry_sleep_ms", "1");
        try {
            assertEquals(10L, LiteRtLmFastRun.daemonRequestRetrySleepMillis());
        } finally {
            restoreProperty("aqli.litert.fast_run.daemon_request_retry_sleep_ms", previous);
        }
    }

    @Test
    void prewarmArgsAddLiteRtDefaults() {
        assertArrayEquals(
                new String[] {
                        "run",
                        "--model", "7c51c9",
                        "--prompt", "where is jakarta",
                        "--max-tokens", "10",
                        "--provider", "litert"
                },
                LiteRtLmFastRun.prewarmRunArgs(new String[] {"--model", "7c51c9"}));
    }

    @Test
    void prewarmArgsPreserveExplicitPromptAndBudget() {
        assertArrayEquals(
                new String[] {
                        "run",
                        "--model", "7c51c9",
                        "--prompt", "what is jakarta",
                        "--max-tokens", "64",
                        "--provider", "litert"
                },
                LiteRtLmFastRun.prewarmRunArgs(new String[] {
                        "--model", "7c51c9",
                        "--prompt", "what is jakarta",
                        "--max-tokens", "64",
                        "--provider", "litert"
                }));
    }

    @Test
    void prewarmArgsStripPublicCommandName() {
        assertArrayEquals(
                new String[] {
                        "run",
                        "--model", "7c51c9",
                        "--prompt", "where is jakarta",
                        "--max-tokens", "10",
                        "--provider", "litert"
                },
                LiteRtLmFastRun.prewarmRunArgs(new String[] {"prewarm", "--model", "7c51c9"}));
    }

    @Test
    void prewarmTokenCountDefaultsToEngineOpenOnly() {
        String previous = System.getProperty("aqli.litert.fast_run.prewarm_tokens");
        System.clearProperty("aqli.litert.fast_run.prewarm_tokens");
        try {
            assertEquals(0, LiteRtLmFastRun.prewarmTokenCount());
        } finally {
            restoreProperty("aqli.litert.fast_run.prewarm_tokens", previous);
        }
    }

    @Test
    void prewarmTokenCountAllowsDecodeWarmupOverride() {
        String previous = System.getProperty("aqli.litert.fast_run.prewarm_tokens");
        System.setProperty("aqli.litert.fast_run.prewarm_tokens", "2");
        try {
            assertEquals(2, LiteRtLmFastRun.prewarmTokenCount());
        } finally {
            restoreProperty("aqli.litert.fast_run.prewarm_tokens", previous);
        }
    }

    @Test
    void prewarmTokenCountHasZeroFloor() {
        String previous = System.getProperty("aqli.litert.fast_run.prewarm_tokens");
        System.setProperty("aqli.litert.fast_run.prewarm_tokens", "-1");
        try {
            assertEquals(0, LiteRtLmFastRun.prewarmTokenCount());
        } finally {
            restoreProperty("aqli.litert.fast_run.prewarm_tokens", previous);
        }
    }

    @Test
    void prewarmIterationCountDefaultsToThree() {
        String previous = System.getProperty("aqli.litert.fast_run.prewarm_iterations");
        System.clearProperty("aqli.litert.fast_run.prewarm_iterations");
        try {
            assertEquals(3, LiteRtLmFastRun.prewarmIterationCount());
        } finally {
            restoreProperty("aqli.litert.fast_run.prewarm_iterations", previous);
        }
    }

    @Test
    void prewarmIterationCountHasSafetyFloor() {
        String previous = System.getProperty("aqli.litert.fast_run.prewarm_iterations");
        System.setProperty("aqli.litert.fast_run.prewarm_iterations", "0");
        try {
            assertEquals(1, LiteRtLmFastRun.prewarmIterationCount());
        } finally {
            restoreProperty("aqli.litert.fast_run.prewarm_iterations", previous);
        }
    }

    @Test
    void dynamicEngineTokensAreEnabledByDefaultForShortRuns() {
        String previous = System.getProperty("aqli.litert.fast_run.dynamic_engine_tokens");
        System.clearProperty("aqli.litert.fast_run.dynamic_engine_tokens");
        try {
            assertTrue(LiteRtLmFastRun.dynamicEngineTokensEnabled());
        } finally {
            restoreProperty("aqli.litert.fast_run.dynamic_engine_tokens", previous);
        }
    }

    @Test
    void dynamicEngineTokensCanBeDisabledForDiagnostics() {
        String previous = System.getProperty("aqli.litert.fast_run.dynamic_engine_tokens");
        System.setProperty("aqli.litert.fast_run.dynamic_engine_tokens", "false");
        try {
            assertFalse(LiteRtLmFastRun.dynamicEngineTokensEnabled());
        } finally {
            restoreProperty("aqli.litert.fast_run.dynamic_engine_tokens", previous);
        }
    }

    @Test
    void shortDynamicEngineTokensDefaultToMeasuredStableFloor() {
        String previousMin = System.getProperty("aqli.litert.fast_run.min_engine_tokens");
        String previousMax = System.getProperty("aqli.litert.fast_run.max_engine_tokens");
        System.clearProperty("aqli.litert.fast_run.min_engine_tokens");
        System.clearProperty("aqli.litert.fast_run.max_engine_tokens");
        try {
            assertEquals(256, LiteRtLmFastRun.dynamicEngineTokenBudget("where is jakarta", 10));
        } finally {
            restoreProperty("aqli.litert.fast_run.min_engine_tokens", previousMin);
            restoreProperty("aqli.litert.fast_run.max_engine_tokens", previousMax);
        }
    }

    @Test
    void approximateTokenStreamLimiterMatchesChunkedFastRunLimit() {
        LiteRtLmFastRun.ApproximateTokenStreamLimiter limiter =
                new LiteRtLmFastRun.ApproximateTokenStreamLimiter(3);

        String output = limiter.offer("hello ")
                + limiter.offer("world\nfrom ")
                + limiter.offer("aqli now");

        assertEquals("hello world\nfrom", output);
        assertEquals(3, limiter.emittedTokenCount());
        assertTrue(limiter.atLimit());
    }

    @Test
    void approximateTokenStreamLimiterDefersTrailingWhitespace() {
        LiteRtLmFastRun.ApproximateTokenStreamLimiter limiter =
                new LiteRtLmFastRun.ApproximateTokenStreamLimiter(2);

        assertEquals("hello", limiter.offer("hello "));
        assertEquals("", limiter.offer("\n"));
        assertEquals(" \nworld", limiter.offer("world"));
        assertEquals(2, limiter.emittedTokenCount());
        assertFalse(limiter.atLimit());
    }

    @Test
    void bareQuestionPromptsAreNormalizedForGemmaChatQuality() {
        assertEquals(
                "Answer directly and concisely in one or two sentences. Do not ask a clarification question. "
                        + "If a term is ambiguous, answer the most common meaning first and mention common alternatives briefly.\n"
                        + "Question: Where is jakarta?",
                LiteRtLmFastRun.promptForModel("where is jakarta"));
    }

    @Test
    void promptNormalizationLeavesPunctuatedAndNonQuestionPromptsAlone() {
        assertEquals("Where is Jakarta?", LiteRtLmFastRun.promptForModel("Where is Jakarta?"));
        assertEquals("write a poem", LiteRtLmFastRun.promptForModel("write a poem"));
    }

    @Test
    void promptNormalizationCanBeDisabledForDiagnostics() {
        String previous = System.getProperty("aqli.litert.fast_run.normalize_short_questions");
        System.setProperty("aqli.litert.fast_run.normalize_short_questions", "false");
        try {
            assertEquals("where is jakarta", LiteRtLmFastRun.promptForModel("where is jakarta"));
        } finally {
            restoreProperty("aqli.litert.fast_run.normalize_short_questions", previous);
        }
    }

    @Test
    void warmupStateTracksOnlyMissingIterations() {
        LiteRtLmFastRun.WarmupState state = new LiteRtLmFastRun.WarmupState();

        assertEquals(3, state.remainingIterations(3));
        state.markCompleted(1);
        assertEquals(2, state.remainingIterations(3));
        state.markCompleted(3);
        assertEquals(0, state.remainingIterations(3));
    }

    @Test
    void warmupStateNeverRegresses() {
        LiteRtLmFastRun.WarmupState state = new LiteRtLmFastRun.WarmupState();

        state.markCompleted(3);
        state.markCompleted(1);

        assertEquals(0, state.remainingIterations(3));
        assertEquals(2, state.remainingIterations(5));
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static void writeModelIndex(Path home, Path safetensorDir, Path litertDir) throws Exception {
        writeModelIndex(
                home,
                "97cbf2",
                "google/gemma-4-E2B-it",
                "gemma-4-E2B-it",
                safetensorDir,
                litertDir);
    }

    private static void writeModelIndex(
            Path home,
            String safetensorShortId,
            String safetensorId,
            String safetensorName,
            Path safetensorDir,
            Path litertDir) throws Exception {
        Path index = home.resolve(".aqli/models/index.json");
        Files.createDirectories(index.getParent());
        Files.writeString(index, """
                [
                  {
                    "id": "%s",
                    "shortId": "%s",
                    "name": "%s",
                    "format": "safetensors",
                    "path": "%s",
                    "architecture": "gemma"
                  },
                  {
                    "id": "litert-community/gemma-4-E2B-it-litert-lm",
                    "shortId": "7c51c9",
                    "name": "gemma-4-E2B-it-litert-lm",
                    "format": "litert",
                    "path": "%s",
                    "architecture": "gemma"
                  }
                ]
                """.formatted(
                escapeJson(safetensorId),
                escapeJson(safetensorShortId),
                escapeJson(safetensorName),
                escapeJson(safetensorDir.toString()),
                escapeJson(litertDir.toString())));
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
