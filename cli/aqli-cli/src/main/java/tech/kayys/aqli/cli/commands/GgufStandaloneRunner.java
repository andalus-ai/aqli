package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.inference.gguf.GgufDaemonServerLoop;
import tech.kayys.aqli.inference.gguf.GgufRunArguments;
import tech.kayys.aqli.inference.gguf.GgufDaemonRequestDecoder;
import tech.kayys.aqli.inference.gguf.GgufDaemonRequestDispatcher;

import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import tech.kayys.aljabr.gguf.runtime.GgufRuntimeProfile;
import tech.kayys.aljabr.gguf.runtime.GgufRuntimeProbe;

/**
 * Standalone GGUF/llama.cpp runner used by the local macOS shim for simple
 * {@code aqli run} calls.
 */
public final class GgufStandaloneRunner {
    private static final int FALLBACK_TO_FULL_CLI = StandaloneRunProtocol.FALLBACK_TO_FULL_CLI;
    private static final int COMMAND_ERROR = 2;
    private static final String DAEMON_MAGIC = "AQLI_GGUF_FAST_DAEMON_V1";
    private static final String DAEMON_STOP_MAGIC = "AQLI_GGUF_FAST_DAEMON_STOP_V1";
    private static final String DAEMON_PREWARM_MAGIC = "AQLI_GGUF_FAST_DAEMON_PREWARM_V1";
    private static final long MIN_OUTPUT_BUFFER_BYTES = 64L * 1024L;
    private static final int OUTPUT_BUFFER_RETRY_LIMIT = 2;
    private static final ThreadLocal<Boolean> REQUEST_TIMING = new ThreadLocal<>();
    private static final GgufStandaloneConfiguration CONFIG = GgufStandaloneConfiguration.system();

    private static final GgufDaemonExecutionHandler DAEMON_EXECUTION_HANDLER =
            new GgufDaemonExecutionHandler(FALLBACK_TO_FULL_CLI, COMMAND_ERROR,
                    new GgufDaemonExecutionHandler.Operations() {
                        @Override
                        public GgufRunArguments parseArguments(String[] arguments) {
                            return parseRunArguments(arguments);
                        }

                        @Override
                        public Optional<Path> resolveModel(GgufRunArguments arguments) {
                            return resolveGgufModel(arguments);
                        }

                        @Override
                        public int prewarm(Path modelPath, GgufRunArguments arguments,
                                PrintStream out, PrintStream err, GgufSessionCache sessionCache) throws Throwable {
                            return engineMode(arguments) == EngineMode.JAVA
                                    ? prewarmWithJava(modelPath, arguments, out, err, sessionCache)
                                    : prewarmWithLlamaCpp(modelPath, arguments, out, err, sessionCache);
                        }

                        @Override
                        public int run(Path modelPath, GgufRunArguments arguments,
                                PrintStream out, PrintStream err, GgufSessionCache sessionCache) throws Throwable {
                            return generate(modelPath, arguments, out, err, sessionCache, "daemon");
                        }

                        @Override
                        public void setRequestTiming(boolean enabled) {
                            REQUEST_TIMING.set(enabled);
                        }

                        @Override
                        public void clearRequestTiming() {
                            REQUEST_TIMING.remove();
                        }

                        @Override
                        public boolean debugEnabled() {
                            return fastRunDebug();
                        }

                        @Override
                        public String briefMessage(Throwable throwable) {
                            return GgufStandaloneRunner.briefMessage(throwable);
                        }
                    });

    static {
        try {
            java.util.logging.Logger rootLogger = java.util.logging.LogManager.getLogManager().getLogger("");
            if (rootLogger != null) {
                rootLogger.setLevel(java.util.logging.Level.WARNING);
                for (java.util.logging.Handler h : rootLogger.getHandlers()) {
                    h.setLevel(java.util.logging.Level.WARNING);
                }
            }
            java.util.logging.Logger.getLogger("io.opentelemetry").setLevel(java.util.logging.Level.WARNING);
            java.util.logging.Logger.getLogger("io.opentelemetry.api.GlobalOpenTelemetry")
                    .setLevel(java.util.logging.Level.WARNING);
            java.util.logging.Logger.getLogger("tech.kayys.aljabr").setLevel(java.util.logging.Level.WARNING);
        } catch (Throwable ignored) {
        }
    }

    private GgufStandaloneRunner() {
    }

    static boolean isFallbackToFullCliStatus(int status) {
        return StandaloneRunProtocol.isFallbackToFullCliStatus(status);
    }

    public static void main(String[] args) {
        for (String a : args) {
            if ("-v".equals(a) || "--verbose".equals(a)) {
                System.setProperty("aqli.verbose", "true");
                System.setProperty("aljabr.metal.verbose", "true");
                break;
            }
        }
        if (args.length > 0 && "__daemon".equals(args[0])) {
            hardExitProcess(runDaemon());
            return;
        }
        if (args.length > 0 && ("__daemon-stop".equals(args[0]) || "__gguf-daemon-stop".equals(args[0]))) {
            System.exit(stopDaemon());
            return;
        }
        if (args.length > 0 && isPrewarmCommand(args[0])) {
            System.exit(prewarm(args));
            return;
        }
        int status = run(args);
        if (status != 0) {
            System.exit(status);
        }
        if (hardExitAfterRun()) {
            hardExitProcess(0);
        }
    }

    static int run(String[] args) {
        try {
            GgufRunArguments parsed = parseRunArguments(args);
            if (!parsed.supported()) {
                return FALLBACK_TO_FULL_CLI;
            }
            if (hasKnownNonGgufTarget(parsed)) {
                return FALLBACK_TO_FULL_CLI;
            }
            Optional<Path> modelPath = resolveGgufModel(parsed);
            if (modelPath.isEmpty()) {
                return FALLBACK_TO_FULL_CLI;
            }
            EngineMode engine = engineMode(parsed);
            if (daemonEnabled() && engine != EngineMode.BENCHMARK) {
                OptionalInt daemonStatus = requestDaemon(args);
                if (daemonStatus.isPresent()) {
                    return daemonStatus.getAsInt();
                }
            }
            return generate(modelPath.get(), parsed);
        } catch (Throwable throwable) {
            if (fastRunDebug()) {
                throwable.printStackTrace(System.err);
            }
            return FALLBACK_TO_FULL_CLI;
        }
    }

    static int prewarm(String[] args) {
        try {
            if (isHelpRequest(args)) {
                printPrewarmUsage(System.out);
                return 0;
            }
            String[] runArgs = prewarmRunArgs(args);
            GgufRunArguments parsed = parseRunArguments(runArgs);
            if (parsed.hasNonGgufProvider()) {
                return FALLBACK_TO_FULL_CLI;
            }
            if (!parsed.supported()) {
                printPrewarmUsage(System.err);
                return COMMAND_ERROR;
            }
            EngineMode engine = engineMode(parsed);
            if (engine == EngineMode.BENCHMARK) {
                System.err.println("GGUF daemon prewarm does not support benchmark mode.");
                return COMMAND_ERROR;
            }
            if (resolveGgufModel(parsed).isEmpty()) {
                if (hasKnownNonGgufTarget(parsed)) {
                    return FALLBACK_TO_FULL_CLI;
                }
                System.err.println("GGUF daemon prewarm could not resolve a GGUF model.");
                return COMMAND_ERROR;
            }
            OptionalInt status = requestDaemonPrewarm(runArgs);
            return status.orElse(COMMAND_ERROR);
        } catch (Throwable throwable) {
            System.err.println("GGUF daemon prewarm failed: " + briefMessage(throwable));
            if (fastRunDebug()) {
                throwable.printStackTrace(System.err);
            }
            return COMMAND_ERROR;
        }
    }

    private static void printPrewarmUsage(PrintStream out) {
        out.println("Usage: aqli prewarm --model MODEL [--prompt TEXT] [--max-tokens N] [--backend metal|cpu]");
        out.println("       aqli warmup  --model MODEL [--prompt TEXT] [--max-tokens N] [--backend metal|cpu]");
        out.println();
        out.println("Preloads the local llama.cpp GGUF daemon and builds the first prompt cache so the next");
        out.println("matching aqli run can reuse the Metal/CPU session instead of paying cold-start cost.");
    }

    private static boolean isHelpRequest(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    static String[] prewarmRunArgs(String[] args) {
        List<String> normalized = new ArrayList<>();
        int startIndex = 0;
        if (args.length > 0 && ("run".equals(args[0]) || isPrewarmCommand(args[0]))) {
            startIndex = 1;
        }
        normalized.add("run");
        boolean hasPrompt = false;
        boolean hasMaxTokens = false;
        boolean hasProvider = false;
        boolean hasEngine = false;
        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];
            normalized.add(arg);
            String option = arg;
            int eq = arg.indexOf('=');
            if (arg.startsWith("--") && eq > 0) {
                option = arg.substring(0, eq);
            }
            if (option.equals("--prompt") || option.equals("-p")) {
                hasPrompt = true;
            } else if (option.equals("--max-tokens")) {
                hasMaxTokens = true;
            } else if (option.equals("--provider")) {
                hasProvider = true;
            } else if (option.equals("--engine") || option.equals("--gguf-engine")
                    || option.equals("--llamacpp") || option.equals("--llama-cpp")) {
                hasEngine = true;
            }
        }
        if (!hasPrompt) {
            normalized.add("--prompt");
            normalized.add(firstNonBlank(
                    System.getProperty("aqli.gguf.fast_run.prewarm_prompt"),
                    System.getenv("AQLI_GGUF_FAST_PREWARM_PROMPT"),
                    "where is jakarta"));
        }
        if (!hasMaxTokens) {
            normalized.add("--max-tokens");
            normalized.add(firstNonBlank(
                    System.getProperty("aqli.gguf.fast_run.prewarm_context_tokens"),
                    System.getenv("AQLI_GGUF_FAST_PREWARM_CONTEXT_TOKENS"),
                    "10"));
        }
        if (!hasProvider) {
            normalized.add("--provider");
            normalized.add("gguf");
        }
        if (!hasEngine) {
            normalized.add("--engine");
            normalized.add("auto");
        }
        return normalized.toArray(String[]::new);
    }

    private static boolean isPrewarmCommand(String arg) {
        return "__gguf-daemon-prewarm".equals(arg)
                || "prewarm".equals(arg)
                || "warmup".equals(arg);
    }

    private static boolean hasKnownNonGgufTarget(GgufRunArguments args) {
        return GgufModelResolver.hasKnownNonGgufTarget(args, equivalentGgufFastPathEnabled());
    }

    static Optional<Path> resolveGgufModel(GgufRunArguments args) {
        return GgufModelResolver.resolve(args, equivalentGgufFastPathEnabled());
    }


    static boolean equivalentGgufFastPathEnabled() {
        String configured = CONFIG.value("aqli.gguf.fast_run.auto_equivalent");
        return configured == null || configured.isBlank() || Boolean.parseBoolean(configured);
    }

    private static int generate(Path modelPath, GgufRunArguments args) throws Throwable {
        return generate(modelPath, args, System.out, System.err, null, "fast path");
    }

    private static int generate(
            Path modelPath,
            GgufRunArguments args,
            PrintStream out,
            PrintStream err,
            GgufSessionCache sessionCache,
            String runnerName) throws Throwable {
        EngineMode engine = engineMode(args);
        if (engine == EngineMode.JAVA) {
            try {
                long startNanos = System.nanoTime();
                printRunHeader(modelPath, args, out);
                if (!args.noInfo()) {
                    printExecutionRoute(out, "java-native", runnerName);
                }

                // Resolve prompt through the same model-specific chat template the llamacpp
                // path uses.
                String formattedPrompt = formatPromptForModel(args.prompt(), modelPath);
                if (args.debugPrompt()) {
                    out.println("[Raw Prompt Dispatched (Before LLM)]:");
                    out.println("--------------------------------------------------");
                    out.print(formattedPrompt);
                    if (!formattedPrompt.endsWith("\n")) {
                        out.println();
                    }
                    out.println("--------------------------------------------------");
                }

                // Translate the resolved prompt and sampling options into Aljabr's request
                // contract.
                tech.kayys.aljabr.spi.inference.InferenceRequest inferenceRequest = tech.kayys.aqli.inference.gguf.GgufInferenceRequestFactory
                        .create(
                                modelPath,
                                formattedPrompt,
                                args.prompt(),
                                args.maxTokens(),
                                args.temperature(),
                                args.topP(),
                                args.topK(),
                                args.repeatPenalty());

                long openStartNanos = System.nanoTime();
                tech.kayys.aqli.inference.gguf.GgufJavaBackendSession backendSession = sessionCache != null
                        ? sessionCache.acquireJavaBackend(modelPath)
                        : tech.kayys.aqli.inference.gguf.GgufJavaBackendSession.open(modelPath);
                boolean shouldClose = sessionCache == null;

                long openNanos = System.nanoTime() - openStartNanos;
                long generateNanos;
                long afterGenerateNanos;
                String text;
                tech.kayys.aljabr.spi.inference.InferenceResponse response = null;

                try {
                    long generateStartNanos = System.nanoTime();
                    response = backendSession.backend().execute(inferenceRequest);
                    afterGenerateNanos = System.nanoTime();
                    generateNanos = afterGenerateNanos - generateStartNanos;

                    text = response != null && response.getContent() != null ? response.getContent() : "";
                } finally {
                    if (shouldClose) {
                        backendSession.close();
                    }
                }

                if (args.debugPrompt()) {
                    out.println("[Raw Output (From LLM Before Decode/Strip)]:");
                    out.println("--------------------------------------------------");
                    out.print(text);
                    if (!text.endsWith("\n")) {
                        out.println();
                    }
                    out.println("--------------------------------------------------");
                    out.println("[Decoded/Sanitized Output]:");
                    out.println("--------------------------------------------------");
                    out.print(stripThinkingChannels(text));
                    if (!stripThinkingChannels(text).endsWith("\n")) {
                        out.println();
                    }
                    out.println("--------------------------------------------------");
                } else {
                    out.print(args.raw() ? text : stripThinkingChannels(text));
                }

                if (!fastRunQuiet() && !args.noInfo()) {
                    int outputTokens = response != null && response.getOutputTokens() > 0
                            ? response.getOutputTokens()
                            : (text.isBlank() ? 0 : (int) (text.trim().split("\\s+").length * 1.3));
                    Optional<String> nativeMetrics = Optional.empty();
                    if (response != null && response.getMetadata() != null) {
                        Object nm = response.getMetadata().get("nativeMetrics");
                        if (nm != null) {
                            nativeMetrics = Optional.of(nm.toString());
                        }
                    }
                    printFastRunStats(out, "Java-native GGUF", outputTokens,
                            startNanos, openNanos, generateNanos, afterGenerateNanos, nativeMetrics);
                }
                return 0;
            } catch (Throwable e) {
                err.println("Java-native GGUF engine crashed: " + e.getMessage());
                if (fastRunDebug()) {
                    e.printStackTrace(err);
                }
                return COMMAND_ERROR;
            }
        }
        if (engine == EngineMode.BENCHMARK) {
            out.println("GGUF engine benchmark: Java-native loader/probe vs llama.cpp generation fallback.");
            printJavaNativeProbe(modelPath, "Java-native GGUF loader");
        }
        generateWithLlamaCpp(modelPath, args, engine == EngineMode.BENCHMARK, out, err, sessionCache, runnerName);
        return 0;
    }

    private static void printJavaNativeProbe(Path modelPath, String label) {
        try {
            int probeRows = Math.max(1, Integer.getInteger("aqli.gguf.java_probe_rows", 16));
            int probeMatVecRows = Math.max(1, Integer.getInteger("aqli.gguf.java_probe_matvec_rows", 4096));
            GgufRuntimeProbe probe = GgufRuntimeProbe.load(modelPath, probeRows, probeMatVecRows);
            GgufRuntimeProbeReporter.printProfile(System.out, label, probe.profile());
            GgufRuntimeProbeReporter.printRowDotProbe(System.out, label, probe);
        } catch (Throwable throwable) {
            System.out.printf("%s: unavailable, reason=%s.%n", label, briefMessage(throwable));
            if (fastRunDebug()) {
                throwable.printStackTrace(System.err);
            }
        }
    }

    private static void generateWithLlamaCpp(
            Path modelPath,
            GgufRunArguments args,
            boolean benchmarkMode,
            PrintStream out,
            PrintStream err,
            GgufSessionCache sessionCache,
            String runnerName) throws Throwable {
        long startNanos = System.nanoTime();
        String prompt = formatPromptForModel(args.prompt(), modelPath);
        GgufLlamaCppRunOptions options = GgufLlamaCppRunOptions.resolve(
                normalizedBackend(args.backend()), prompt, args.maxTokens(), false, defaultThreads(), CONFIG);
        printRunHeader(modelPath, args, out);

        try {
            generateWithLlamaCppSession(
                    modelPath,
                    args,
                    benchmarkMode,
                    startNanos,
                    options,
                    prompt,
                    false,
                    out,
                    err,
                    sessionCache,
                    runnerName);
        } catch (Throwable throwable) {
            if (options.gpuLayers() == 0 || !fastRunCpuFallback()) {
                throw throwable;
            }
            err.printf("WARN: GGUF Metal fast path failed (%s); retrying with CPU fallback.%n",
                    briefMessage(throwable));
            generateWithLlamaCppSession(
                    modelPath,
                    args,
                    benchmarkMode,
                    startNanos,
                    options.withGpuLayers(0),
                    prompt,
                    true,
                    out,
                    err,
                    sessionCache,
                    runnerName);
        }
    }

    private static int prewarmWithJava(
            Path modelPath,
            GgufRunArguments args,
            PrintStream out,
            PrintStream err,
            GgufSessionCache sessionCache) throws Throwable {
        long startNanos = System.nanoTime();
        long openStartNanos = System.nanoTime();
        tech.kayys.aqli.inference.gguf.GgufJavaBackendSession backendSession = sessionCache != null
                ? sessionCache.acquireJavaBackend(modelPath)
                : tech.kayys.aqli.inference.gguf.GgufJavaBackendSession.open(modelPath);
        boolean shouldClose = sessionCache == null;
        long openNanos = System.nanoTime() - openStartNanos;
        try {
            String rawPrompt = (args.prompt() != null && !args.prompt().isBlank()) ? args.prompt() : "Hello";
            String formattedPrompt = formatPromptForModel(rawPrompt, modelPath);
            var reqBuilder = tech.kayys.aljabr.spi.inference.InferenceRequest.builder()
                    .model(modelPath.getFileName().toString())
                    .prompt(formattedPrompt)
                    .message(tech.kayys.aljabr.spi.Message.user(rawPrompt))
                    .maxTokens(prewarmTokenCount())
                    .temperature(0.0);
            long genStartNanos = System.nanoTime();
            backendSession.backend().execute(reqBuilder.build());
            long generateNanos = System.nanoTime() - genStartNanos;
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            out.printf("Prewarmed Java-native GGUF daemon for %s (tokens=%d).%n",
                    modelPath.getFileName(), prewarmTokenCount());
            out.printf("[GGUF daemon prewarm, Duration: %.2fs]%n", durationMs / 1000.0d);
            if (fastRunTiming()) {
                err.printf("Java GGUF timing: open=%.3fms, generateCall=%.3fms%n",
                        openNanos / 1_000_000.0d, generateNanos / 1_000_000.0d);
            }
            return 0;
        } finally {
            if (shouldClose) {
                backendSession.close();
            }

        }
    }

    private static int prewarmWithLlamaCpp(
            Path modelPath,
            GgufRunArguments args,
            PrintStream out,
            PrintStream err,
            GgufSessionCache sessionCache) throws Throwable {
        long startNanos = System.nanoTime();
        String prompt = formatPromptForModel(args.prompt(), modelPath);
        GgufLlamaCppRunOptions options = GgufLlamaCppRunOptions.resolve(
                normalizedBackend(args.backend()), prompt, args.maxTokens(), true, defaultThreads(), CONFIG);

        try {
            prewarmWithLlamaCppSession(
                    modelPath,
                    args,
                    startNanos,
                    context,
                    batch,
                    microBatch,
                    threads,
                    batchThreads,
                    nGpuLayers,
                    swaFull,
                    prompt,
                    false,
                    out,
                    err,
                    sessionCache);
            return 0;
        } catch (Throwable throwable) {
            if (nGpuLayers == 0 || !fastRunCpuFallback()) {
                throw throwable;
            }
            err.printf("WARN: GGUF Metal prewarm failed (%s); retrying with CPU fallback.%n",
                    briefMessage(throwable));
            prewarmWithLlamaCppSession(
                    modelPath,
                    args,
                    startNanos,
                    context,
                    batch,
                    microBatch,
                    threads,
                    batchThreads,
                    0,
                    swaFull,
                    prompt,
                    true,
                    out,
                    err,
                    sessionCache);
            return 0;
        }
    }

    private static void prewarmWithLlamaCppSession(
            Path modelPath,
            GgufRunArguments args,
            long startNanos,
            GgufLlamaCppRunOptions options,
            String prompt,
            boolean cpuFallback,
            PrintStream out,
            PrintStream err,
            GgufSessionCache sessionCache) throws Throwable {
        int context = options.context();
        int batch = options.batch();
        int microBatch = options.microBatch();
        int threads = options.threads();
        int batchThreads = options.batchThreads();
        int nGpuLayers = options.gpuLayers();
        boolean swaFull = options.swaFull();
        boolean useMmap = options.useMmap();
        boolean useMlock = options.useMlock();
        long openStartNanos = System.nanoTime();
        GgufSessionCache.SessionLease opened = sessionCache == null
                ? GgufSessionCache.SessionLease.open(modelPath, context, batch, microBatch, threads, batchThreads,
                        nGpuLayers,
                        useMmap, useMlock, swaFull)
                : sessionCache.acquire(modelPath, context, batch, microBatch, threads, batchThreads, nGpuLayers,
                        useMmap, useMlock, swaFull);
        long openNanos = System.nanoTime() - openStartNanos;
        try (GgufSessionCache.SessionLease lease = opened) {
            LlamaCppGgufSession session = lease.session();
            int tokens = prewarmTokenCount();
            String sessionLabel = sessionReuseLabel(sessionCache != null, lease.reused(), lease.reuseKind());
            String coverage = sessionCoverageSummary(context, batch, microBatch);
            long generateStartNanos = System.nanoTime();
            session.generate(prompt, tokens, 0.0d, 1, 1.0d);
            long generateNanos = System.nanoTime() - generateStartNanos;
            long durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            out.printf("Prewarmed llama.cpp GGUF daemon for %s "
                    + "(backend=%s, nGpuLayers=%d, threads=%d, context=%d, batch=%d, ubatch=%d, "
                    + "tokens=%d, session=%s, coverage=%s, cpuFallback=%s).%n",
                    modelPath.getFileName(),
                    session.backendName(),
                    nGpuLayers,
                    threads,
                    context,
                    batch,
                    microBatch,
                    tokens,
                    sessionLabel,
                    coverage,
                    cpuFallback);
            out.printf("[GGUF daemon prewarm, Duration: %.2fs]%n", durationMs / 1000.0d);
            if (fastRunTiming()) {
                String nativeMetrics = session.metrics().map(metrics -> ", native={" + metrics + "}").orElse("");
                err.printf("GGUF timing: open=%.3fms, generateCall=%.3fms%s%n",
                        openNanos / 1_000_000.0d,
                        generateNanos / 1_000_000.0d,
                        nativeMetrics);
            }
            if (shouldHardExitAfterNativeRun(sessionCache != null)) {
                hardExitProcess(0);
            }
        }
    }

    static int prewarmTokenCount() {
        // Two tokens force one post-sample decode, which warms the hot decode path.
        return Math.max(2, CONFIG.intValue("aqli.gguf.fast_run.prewarm_tokens", 2));
    }

    private static void generateWithLlamaCppSession(
            Path modelPath,
            GgufRunArguments args,
            boolean benchmarkMode,
            long startNanos,
            int context,
            int batch,
            int microBatch,
            int threads,
            int batchThreads,
            int nGpuLayers,
            boolean swaFull,
            String prompt,
            boolean cpuFallback,
            PrintStream out,
            PrintStream err,
            GgufSessionCache sessionCache,
            String runnerName) throws Throwable {
        long openStartNanos = System.nanoTime();
        boolean useMmap = CONFIG.booleanValue("aqli.gguf.fast_run.mmap", true);
        boolean useMlock = CONFIG.booleanValue("aqli.gguf.fast_run.mlock", false);
        GgufSessionCache.SessionLease opened = sessionCache == null
                ? GgufSessionCache.SessionLease.open(modelPath, context, batch, microBatch, threads, batchThreads,
                        nGpuLayers,
                        useMmap, useMlock, swaFull)
                : sessionCache.acquire(modelPath, context, batch, microBatch, threads, batchThreads, nGpuLayers,
                        useMmap, useMlock, swaFull);
        long openNanos = System.nanoTime() - openStartNanos;
        try (GgufSessionCache.SessionLease lease = opened) {
            LlamaCppGgufSession session = lease.session();
            String sessionLabel = sessionReuseLabel(sessionCache != null, lease.reused(), lease.reuseKind());
            String warmSuffix = ", session=" + sessionLabel;
            if (!args.noInfo()) {
                printExecutionRoute(out, session.backendName(), runnerName);
            }
            if (!fastRunQuiet() && !args.noInfo()) {
                out.printf("Using llama.cpp GGUF %s for %s "
                        + "(backend=%s, nGpuLayers=%d, threads=%d, context=%d, batch=%d, ubatch=%d, "
                        + "swaFull=%s, cpuFallback=%s%s).%n",
                        runnerName,
                        modelPath.getFileName(),
                        session.backendName(),
                        nGpuLayers,
                        threads,
                        context,
                        batch,
                        microBatch,
                        swaFull,
                        cpuFallback,
                        warmSuffix);
            }
            if (args.debugPrompt()) {
                out.println("[Raw Prompt Dispatched (Before LLM)]:");
                out.println("--------------------------------------------------");
                out.print(prompt);
                if (!prompt.endsWith("\n")) {
                    out.println();
                }
                out.println("--------------------------------------------------");
            }
            long generateStartNanos = System.nanoTime();
            String output = session.generate(prompt, args.maxTokens(), args.temperature(), args.topK(), args.topP());
            long afterGenerateNanos = System.nanoTime();
            long generateNanos = afterGenerateNanos - generateStartNanos;
            if (args.debugPrompt()) {
                out.println("[Raw Output (From LLM Before Decode/Strip)]:");
                out.println("--------------------------------------------------");
                out.print(output);
                if (!output.endsWith("\n")) {
                    out.println();
                }
                out.println("--------------------------------------------------");
                out.println("[Decoded/Sanitized Output]:");
                out.println("--------------------------------------------------");
                out.print(stripThinkingChannels(output));
                if (!stripThinkingChannels(output).endsWith("\n")) {
                    out.println();
                }
                out.println("--------------------------------------------------");
            } else {
                out.print(args.raw() ? output : stripThinkingChannels(output));
            }
            String label = benchmarkMode ? "llama.cpp fallback" : "Fast GGUF";
            Optional<String> nativeMetrics = safeMetrics(
                    session,
                    sessionCache != null && lease.reused(),
                    lease.reuseKind());
            if (!fastRunQuiet() && !args.noInfo()) {
                printFastRunStats(out, label, session.lastGeneratedTokens(), startNanos, openNanos, generateNanos,
                        afterGenerateNanos, nativeMetrics);
            }
            if (fastRunTiming()) {
                String timingNativeMetrics = nativeMetrics.map(metrics -> ", native={" + metrics + "}").orElse("");
                err.printf("GGUF timing: open=%.3fms, generateCall=%.3fms%s%n",
                        openNanos / 1_000_000.0d,
                        generateNanos / 1_000_000.0d,
                        timingNativeMetrics);
            }
        }
    }

    private static Optional<String> safeMetrics(LlamaCppGgufSession session) {
        return safeMetrics(session, false, "cold");
    }

    /**
     * Strips model thinking-channel blocks from the raw output string.
     *
     * <p>
     * Gemma 4 (and similar models with chain-of-thought) emit thinking tokens in
     * the
     * form {@code <|channel>thought\n...<channel|>} before the visible response. In
     * normal
     * (non-raw) mode we discard those blocks so the caller only sees the final
     * answer text.
     *
     * <p>
     * With {@code --raw} this method is bypassed and the literal byte stream is
     * printed.
     */
    static String stripThinkingChannels(String output) {
        if (output == null || output.isEmpty()) {
            return output;
        }
        // Remove every <|channel>NAME ... <channel|> block including the trailing
        // newline.
        String stripped = output.replaceAll("(?s)<\\|channel>[^<|]*?(?:<\\|[^|>]*\\|>|<channel\\|>)\\n?", "");
        // Trim any leading blank lines that were left after removal.
        return stripped.stripLeading();
    }

    private static Optional<String> safeMetrics(LlamaCppGgufSession session, boolean warmSession, String reuseKind) {
        try {
            return session.metrics(warmSession, reuseKind);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    static String sessionReuseLabel(boolean daemonSession, boolean reused, String reuseKind) {
        if (!daemonSession) {
            return "one-shot";
        }
        if (!reused) {
            return "cold";
        }
        String normalized = reuseKind == null ? "" : reuseKind.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("compatible")) {
            return "warm-compatible";
        }
        if (normalized.equals("exact")) {
            return "warm-exact";
        }
        return "warm";
    }

    static String sessionCoverageSummary(int context, int batch, int microBatch) {
        return "ctx<=" + Math.max(1, context)
                + ",batch<=" + Math.max(1, batch)
                + ",ubatch<=" + Math.max(1, microBatch);
    }

    static long outputBufferBytesForTokens(int maxTokens) {
        long requested = Math.max(1L, (long) maxTokens) * 512L;
        return Math.max(MIN_OUTPUT_BUFFER_BYTES, requested);
    }

    static long nextOutputBufferBytes(long currentBytes) {
        long current = Math.max(MIN_OUTPUT_BUFFER_BYTES, currentBytes);
        if (current > Long.MAX_VALUE / 2L) {
            return Long.MAX_VALUE;
        }
        return Math.max(MIN_OUTPUT_BUFFER_BYTES, current * 2L);
    }

    static boolean outputBufferTooSmall(int nativeStatus, String message) {
        return nativeStatus == -9
                || (message != null && message.toLowerCase(Locale.ROOT).contains("output buffer too small"));
    }

    static void printFastRunStats(
            PrintStream out,
            String label,
            int outputTokens,
            long startNanos,
            long openNanos,
            long generateNanos,
            long endNanos) {
        GgufRunMetricsReporter.printFastRunStats(
                out, label, outputTokens, startNanos, openNanos, generateNanos, endNanos);
    }

    static void printFastRunStats(
            PrintStream out,
            String label,
            int outputTokens,
            long startNanos,
            long openNanos,
            long generateNanos,
            long endNanos,
            Optional<String> nativeMetrics) {
        GgufRunMetricsReporter.printFastRunStats(
                out, label, outputTokens, startNanos, openNanos, generateNanos, endNanos, nativeMetrics);
    }

    static Map<String, String> parseNativeMetrics(String rawMetrics) {
        return GgufRunMetricsReporter.parseNativeMetrics(rawMetrics);
    }

    static void printRunHeader(Path modelPath, GgufRunArguments args, PrintStream out) {
        if (fastRunQuiet()) {
            return;
        }
        if (args == null || args.banner()) {
            printBanner(out);
        }
        if (args == null || !args.noInfo()) {
            if (shouldPrintResolvedModelPath(modelPath, args)) {
                out.printf("Resolved local model index entry: %s%n", modelPath);
            }
            out.printf("Model: %s%n", modelPath);
            out.printf("Provider: gguf, format=%s%n", ggufModelFormat(modelPath));
        }
    }

    static void printExecutionRoute(PrintStream out, String backendName, String runnerName) {
        if (fastRunQuiet()) {
            return;
        }
        out.println(executionRouteLine(backendName, runnerName));
        out.println("--------------------------------------------------");
    }

    static String ggufModelFormat(Path modelPath) {
        if (modelPath == null || modelPath.getFileName() == null) {
            return "gguf";
        }
        String name = modelPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".gguf")) {
            return "gguf";
        }
        return "gguf";
    }

    static String executionRouteLine(String backendName, String runnerName) {
        String backend = backendName == null || backendName.isBlank() ? "unknown" : backendName;
        return "Execution route: gguf (backend=" + backend + ") [" + executionRouteTag(backend, runnerName) + "]";
    }

    private static String executionRouteTag(String backendName, String runnerName) {
        if ("java-native".equalsIgnoreCase(backendName) || "java".equalsIgnoreCase(backendName)) {
            return "java_native_gguf_fast_path";
        }
        String normalized = runnerName == null ? "" : runnerName.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("daemon")) {
            return "llama_cpp_gguf_daemon";
        }
        if (normalized.contains("fallback")) {
            return "llama_cpp_gguf_fallback";
        }
        return "llama_cpp_gguf_fast_path";
    }

    private static void printBanner(PrintStream out) {
        out.println("  _____       _  _      _    ");
        out.println(" / ____|     | || |    | |   ");
        out.println("| |  __  ___ | || | ___| | __");
        out.println("| | |_ |/ _ \\| || |/ _ \\ |/ /");
        out.println("| |__| | (_) | || |  __/   < ");
        out.println(" \\_____|\\___/|_||_|\\___|_|\\_\\");
        out.println();
    }

    private static boolean shouldPrintResolvedModelPath(Path modelPath, GgufRunArguments args) {
        if (args == null || args.model() == null || args.model().isBlank() || args.modelFile() != null) {
            return false;
        }
        try {
            Path requested = Path.of(args.model());
            Path resolved = requested.isAbsolute()
                    ? requested.toAbsolutePath().normalize()
                    : Path.of("").toAbsolutePath().resolve(requested).normalize();
            return !resolved.equals(modelPath.toAbsolutePath().normalize());
        } catch (Exception ignored) {
            return true;
        }
    }

    private enum EngineMode {
        AUTO,
        JAVA,
        LLAMA_CPP,
        BENCHMARK;

        static EngineMode parse(String value) {
            String normalized = value == null ? "auto" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "java", "java-native", "jvm" -> JAVA;
                case "llamacpp", "llama.cpp", "llama-cpp", "binding", "native" -> LLAMA_CPP;
                case "bench", "benchmark", "compare" -> BENCHMARK;
                default -> AUTO;
            };
        }

        static EngineMode effective(String provider, String engine) {
            EngineMode requested = parse(engine);
            if (requested != AUTO) {
                return requested;
            }
            String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
            return switch (normalizedProvider) {
                case "java", "java-native", "jvm" -> JAVA;
                case "llamacpp", "llama.cpp", "llama-cpp", "binding", "native" -> LLAMA_CPP;
                default -> AUTO;
            };
        }
    }

    static String javaReadinessSummary(GgufRuntimeProfile profile) {
        return GgufRuntimeProbeReporter.javaReadinessSummary(profile);
    }

    static String formatPromptForModel(String prompt, Path modelPath) {
        String mode = CONFIG.string("aqli.gguf.fast_run.prompt_format", "auto")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (mode.equals("raw") || mode.equals("none") || prompt == null || prompt.isBlank()) {
            return prompt;
        }
        if (prompt.contains("<|turn>") || prompt.contains("<start_of_turn>")) {
            return prompt;
        }
        boolean gemma4 = mode.equals("gemma4") || (mode.equals("auto") && looksLikeGemma4(modelPath));
        if (!gemma4) {
            return prompt;
        }
        String userPrompt = prompt;
        if (useConciseQuestionInstruction(prompt)) {
            userPrompt = "Answer directly and concisely.\nQuestion: " + prompt;
        }
        return "<bos><|turn>user\n" + userPrompt + "<turn|>\n<|turn>model\n<|channel>thought\n<channel|>";
    }

    static String effectiveEngineModeName(String provider, String engine) {
        return EngineMode.effective(provider, engine).name();
    }

    private static boolean looksLikeGemma4(Path modelPath) {
        if (modelPath == null || modelPath.getFileName() == null) {
            return false;
        }
        String fileName = modelPath.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.contains("gemma-4") || fileName.contains("gemma4");
    }

    private static boolean useConciseQuestionInstruction(String prompt) {
        if (!CONFIG.booleanValue("aqli.gguf.fast_run.concise_qa_prompt", true)) {
            return false;
        }
        if (prompt == null) {
            return false;
        }
        String trimmed = prompt.trim();
        if (trimmed.isEmpty() || trimmed.getBytes(StandardCharsets.UTF_8).length > 256) {
            return false;
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        return normalized.endsWith("?")
                || normalized.startsWith("where ")
                || normalized.startsWith("what ")
                || normalized.startsWith("who ")
                || normalized.startsWith("when ")
                || normalized.startsWith("why ")
                || normalized.startsWith("how ")
                || normalized.startsWith("which ");
    }

    private static String normalizedBackend(String backend) {
        if (backend == null || backend.isBlank()) {
            return "metal";
        }
        String normalized = backend.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("gpu")) {
            return "metal";
        }
        return normalized;
    }

    private static int defaultThreads() {
        if (isMacOs()) {
            try {
                Process process = new ProcessBuilder("/usr/sbin/sysctl", "-n", "hw.perflevel0.logicalcpu")
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null && !line.isBlank()) {
                        int pCores = Integer.parseInt(line.trim());
                        if (pCores > 0) {
                            return pCores;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        int cpus = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(8, cpus));
    }

    static int boundedBatch(String property, int defaultValue, int maxValue) {
        return GgufLlamaCppRunOptions.boundedBatch(property, defaultValue, maxValue, CONFIG);
    }

    static int fastRunContext(String prompt, int maxTokens) {
        return GgufLlamaCppRunOptions.context(prompt, maxTokens, CONFIG);
    }

    static int fastRunPrewarmContext(String prompt, int maxTokens) {
        return GgufLlamaCppRunOptions.prewarmContext(prompt, maxTokens, CONFIG);
    }

    static int fastRunPrewarmMinContext(int maxAutoContext) {
        String configured = firstNonBlank(CONFIG.value("aqli.gguf.fast_run.prewarm_min_context"),
                Integer.toString(maxAutoContext));
        return Math.min(maxAutoContext, GgufStandaloneConfiguration.parsePositiveInt(configured, maxAutoContext));
    }

    private static int fastRunMaxAutoContext() {
        return CONFIG.positiveInt("aqli.gguf.fast_run.max_auto_context", 4096);
    }

    static boolean fastRunSwaFull() {
        return CONFIG.booleanValue("aqli.gguf.fast_run.swa_full", false);
    }

    static boolean fastRunCpuFallback() {
        return CONFIG.booleanValue("aqli.gguf.fast_run.cpu_fallback", true);
    }

    static boolean fastRunTiming() {
        Boolean requestTiming = REQUEST_TIMING.get();
        if (requestTiming != null) {
            return requestTiming;
        }
        return CONFIG.booleanValue("aqli.gguf.fast_run.timing", false);
    }

    private static boolean fastRunDebug() {
        return CONFIG.booleanValue("aqli.gguf.fast_run.debug", false);
    }

    /**
     * Quiet mode: suppress all verbose run headers, execution-route lines,
     * performance
     * metrics blocks, and the 'Using llama.cpp GGUF...' status line. Only the raw
     * generated tokens are printed to stdout. Activated by system property
     * {@code aqli.gguf.fast_run.quiet=true} or env {@code AQLI_GGUF_FAST_QUIET=1}.
     * This is automatically set when Wayang uses the SDK in embedded mode.
     */
    static boolean fastRunQuiet() {
        return CONFIG.booleanValue("aqli.gguf.fast_run.quiet", false);
    }

    private static boolean daemonEnabled() {
        return CONFIG.booleanValue("aqli.gguf.fast_run.daemon", false);
    }

    static Path llamaLibDir() {
        String configured = firstNonBlank(
                CONFIG.value("aqli.gguf.fast_run.llama_lib_dir"),
                System.getenv("AQLI_LLAMA_LIB_DIR"),
                null);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath();
        }
        return Path.of(System.getProperty("user.home"), ".aqli", "libs", "llama").toAbsolutePath();
    }

    static Path bridgePath() {
        String configured = CONFIG.value("aqli.gguf.fast_run.bridge");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath();
        }
        return Path.of(System.getProperty("user.home"), ".aqli", "libs", "libaqli_gguf_fast.dylib").toAbsolutePath();
    }

    private static String briefMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        if (message == null || message.isBlank()) {
            return cursor.getClass().getSimpleName();
        }
        return message.replace('\n', ' ').replace('\r', ' ');
    }

    private static OptionalInt requestDaemon(String[] rawArgs) {
        OptionalInt status = sendDaemonRequestWithRetries(rawArgs, DAEMON_MAGIC, daemonRequestRetryMillis());
        if (status.isPresent()) {
            return status;
        }
        if (!startDaemon()) {
            return OptionalInt.empty();
        }
        return sendDaemonRequestWithRetries(rawArgs, DAEMON_MAGIC, daemonStartTimeoutMillis());
    }

    private static OptionalInt requestDaemonPrewarm(String[] rawArgs) {
        OptionalInt status = sendDaemonRequestWithRetries(rawArgs, DAEMON_PREWARM_MAGIC, daemonRequestRetryMillis());
        if (status.isPresent()) {
            return status;
        }
        if (!startDaemon()) {
            return OptionalInt.empty();
        }
        return sendDaemonRequestWithRetries(rawArgs, DAEMON_PREWARM_MAGIC, daemonStartTimeoutMillis());
    }

    private static OptionalInt sendDaemonRequestWithRetries(String[] rawArgs, String magic, long retryMillis) {
        return daemonClient().sendWithRetries(rawArgs, magic, retryMillis);
    }

    private static OptionalInt sendDaemonRequest(String[] rawArgs, String magic) {
        return daemonClient().send(rawArgs, magic);
    }

    private static GgufDaemonClient daemonClient() {
        return new GgufDaemonClient(
                GgufStandaloneRunner::readDaemonPort,
                GgufStandaloneRunner::daemonConnectTimeoutMillis,
                () -> (int) Math.min(Integer.MAX_VALUE, timeoutMillis()),
                GgufStandaloneRunner::daemonRequestRetrySleepMillis,
                GgufStandaloneRunner::fastRunTiming,
                GgufStandaloneRunner::fastRunDebug,
                GgufStandaloneRunner::readDaemonInfoPresent,
                GgufStandaloneRunner::deleteDaemonPortFile,
                System.out,
                System.err);
    }

    private static GgufDaemonControlClient daemonControlClient() {
        return new GgufDaemonControlClient();
    }

    private static boolean startDaemon() {
        deleteDaemonPortFile();
        try {
            Files.createDirectories(daemonLogFile().getParent());
            List<String> command = new ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            command.add("-Xmx" + CONFIG.string("aqli.gguf.fast_run.daemon_heap", "1g"));
            command.add("-XX:MaxDirectMemorySize="
                    + CONFIG.string("aqli.gguf.fast_run.daemon_direct_memory", "8g"));
            command.add("--enable-preview");
            command.add("--add-modules");
            command.add("jdk.incubator.vector");
            command.add("--enable-native-access=ALL-UNNAMED");
            copyAqliSystemProperties(command);
            command.add("-Djava.library.path=" + System.getProperty("java.library.path", ""));
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(GgufStandaloneRunner.class.getName());
            command.add("__daemon");

            return daemonStartupCoordinator().start(
                    command,
                    daemonLauncherMode(),
                    launchctlLabel(),
                    daemonLogFile(),
                    isMacOs(),
                    fastRunDebug(),
                    daemonNativePaths());
        } catch (Exception e) {
            if (fastRunDebug()) {
                System.err.println("GGUF daemon failed to start: " + briefMessage(e));
            }
            return false;
        }
    }

    private static GgufDaemonStartupCoordinator daemonStartupCoordinator() {
        return new GgufDaemonStartupCoordinator(
                new GgufDaemonLauncher()::launch,
                GgufStandaloneRunner::readDaemonPort,
                GgufStandaloneRunner::daemonStartTimeoutMillis,
                System::nanoTime,
                Thread::sleep,
                100L);
    }

    static String daemonLauncherMode() {
        return GgufDaemonLauncher.normalizeMode(
                CONFIG.string("aqli.gguf.fast_run.daemon_launcher", defaultDaemonLauncherMode()),
                defaultDaemonLauncherMode());
    }

    static String defaultDaemonLauncherMode() {
        return isMacOs() ? "launchctl" : "nohup";
    }

    private static int runDaemon() {
        int port = -1;
        try {
            Files.createDirectories(daemonRunDir());
            try (ServerSocket server = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
                    GgufSessionCache sessionCache = new GgufSessionCache(GgufStandaloneRunner::daemonMaxSessions,
                            GgufStandaloneRunner::daemonLog)) {
                port = server.getLocalPort();
                writeDaemonPort(port);
                long idleSeconds = CONFIG.longValue("aqli.gguf.fast_run.daemon_idle_seconds", 900L);
                daemonLog("daemon started: pid=" + ProcessHandle.current().pid()
                        + ", port=" + port
                        + ", idleSeconds=" + idleSeconds);
                GgufDaemonServerLoop.serve(
                        server,
                        idleSeconds,
                        TimeUnit.SECONDS,
                        socket -> handleDaemonClient(socket, sessionCache),
                        () -> daemonLog("daemon idle timeout"),
                        () -> daemonLog("daemon stopping"));
                if (daemonHardExit()) {
                    hardExitDaemon(0, port);
                }
            }
            return 0;
        } catch (Throwable throwable) {
            System.err.println("GGUF daemon failed: " + briefMessage(throwable));
            if (fastRunDebug()) {
                throwable.printStackTrace(System.err);
            }
            return COMMAND_ERROR;
        } finally {
            deleteDaemonPortFileIfPort(port);
        }
    }

    private static boolean handleDaemonClient(Socket socket, GgufSessionCache sessionCache) {
        try {
            socket.setSoTimeout((int) Math.min(Integer.MAX_VALUE, timeoutMillis()));
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream framed = new DataOutputStream(socket.getOutputStream());
            GgufDaemonRequestDecoder.Request request = GgufDaemonRequestDecoder.read(
                    in, DAEMON_MAGIC, DAEMON_PREWARM_MAGIC, DAEMON_STOP_MAGIC);
            return GgufDaemonRequestDispatcher.dispatch(
                    request,
                    framed,
                    FALLBACK_TO_FULL_CLI,
                    () -> daemonLog("daemon stop requested"),
                    (decoded, out, err) -> executeDaemonRequest(decoded, out, err, sessionCache));
        } catch (Exception e) {
            if (fastRunDebug()) {
                System.err.println("GGUF daemon client failed: " + briefMessage(e));
            }
            return false;
        }
    }

    private static int executeDaemonRequest(
            GgufDaemonRequestDecoder.Request request,
            PrintStream out,
            PrintStream err,
            GgufSessionCache sessionCache) {
        return DAEMON_EXECUTION_HANDLER.execute(request, out, err, sessionCache);
    }

    private static int stopDaemon() {
        OptionalInt port = readDaemonPort();
        if (port.isEmpty()) {
            removeLaunchctlDaemon();
            return 0;
        }
        try {
            return daemonControlClient().requestStop(
                    port.getAsInt(), DAEMON_STOP_MAGIC, daemonConnectTimeoutMillis(), COMMAND_ERROR);
        } finally {
            deleteDaemonPortFile();
            removeLaunchctlDaemon();
        }
    }

    private static void copyAqliSystemProperties(List<String> command) {
        Properties properties = System.getProperties();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith("aqli.gguf.fast_run.")) {
                command.add("-D" + name + "=" + properties.getProperty(name));
            }
        }
        String envThreads = System.getenv("GGUF_THREADS");
        if (envThreads != null && !envThreads.isBlank() && !properties.containsKey("aqli.gguf.fast_run.threads")) {
            command.add("-Daqli.gguf.fast_run.threads=" + envThreads.trim());
        }
    }

    private static Path daemonRunDir() {
        return Path.of(System.getProperty("user.home"), ".aqli", "run");
    }

    private static Path daemonPortFile() {
        return daemonRunDir().resolve("gguf-fast-daemon.port");
    }

    private static Path daemonLogFile() {
        return Path.of(System.getProperty("user.home"), ".aqli", "logs", "gguf-fast-daemon.log");
    }

    private static OptionalInt readDaemonPort() {
        boolean strictKey = strictDaemonKey();
        return staleDaemonLifecycle().resolvePort(
                readDaemonInfo(),
                strictKey ? daemonKey() : null,
                strictKey,
                staleDaemonForceKill(),
                staleDaemonKillWaitMillis());
    }

    private static Optional<GgufDaemonInfoStore.DaemonInfo> readDaemonInfo() {
        return daemonInfoStore().read();
    }

    private static boolean readDaemonInfoPresent() {
        return readDaemonInfo().isPresent();
    }

    private static void writeDaemonPort(int port) throws IOException {
        daemonInfoStore().write(port, ProcessHandle.current().pid(), daemonKey());
    }

    private static GgufDaemonInfoStore daemonInfoStore() {
        return new GgufDaemonInfoStore(daemonPortFile());
    }

    private static String daemonKey() {
        return classPathKey(System.getProperty("java.class.path", ""))
                + "|" + pathKey(bridgePath())
                + "|" + pathKey(llamaLibDir());
    }

    private static String classPathKey(String classPath) {
        if (classPath == null || classPath.isBlank()) {
            return "";
        }
        String[] entries = classPath.split(java.util.regex.Pattern.quote(File.pathSeparator));
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < entries.length; i++) {
            if (i > 0) {
                key.append(File.pathSeparator);
            }
            String entry = entries[i];
            if (entry == null || entry.isBlank()) {
                continue;
            }
            Path path = Path.of(entry);
            key.append(Files.exists(path) ? pathKey(path) : entry);
        }
        return key.toString();
    }

    private static String pathKey(Path path) {
        try {
            long modified = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : -1L;
            long size = Files.isRegularFile(path) ? Files.size(path) : -1L;
            return path.toAbsolutePath().normalize() + ":size=" + size + ":mtime=" + modified;
        } catch (Exception ignored) {
            return path.toAbsolutePath().normalize().toString();
        }
    }

    private static GgufStaleDaemonLifecycle staleDaemonLifecycle() {
        return new GgufStaleDaemonLifecycle(
                daemonInfoStore(),
                GgufStaleDaemonLifecycle.systemProcesses(),
                GgufStandaloneRunner::removeLaunchctlDaemon);
    }

    static boolean staleDaemonForceKill() {
        return CONFIG.booleanValue("aqli.gguf.fast_run.stale_daemon_force_kill", true);
    }

    static long staleDaemonKillWaitMillis() {
        return Math.max(100L, CONFIG.longValue("aqli.gguf.fast_run.stale_daemon_kill_wait_ms", 2000L));
    }

    static boolean strictDaemonKey() {
        return CONFIG.booleanValue("aqli.gguf.fast_run.strict_daemon_key", false);
    }

    static boolean hardExitAfterRun() {
        return CONFIG.booleanValue("aqli.gguf.fast_run.hard_exit_after_run", true);
    }

    static boolean shouldHardExitAfterNativeRun(boolean daemonSession) {
        return !daemonSession && hardExitAfterRun();
    }

    static void daemonLog(String message) {
        System.err.printf("%tF %<tT [gguf-daemon] %s%n", System.currentTimeMillis(), message);
    }

    private static boolean daemonHardExit() {
        return CONFIG.booleanValue("aqli.gguf.fast_run.daemon_hard_exit", true);
    }

    private static void hardExitDaemon(int status, int port) {
        deleteDaemonPortFileIfPort(port);
        hardExitProcess(status);
    }

    static void hardExitProcess(int status) {
        System.out.flush();
        System.err.flush();
        try {
            MethodHandle hardExit = LlamaCppGgufNativeBindings.INSTANCE.hardExit;
            if (hardExit != null) {
                hardExit.invokeExact(status);
            }
        } catch (Throwable ignored) {
        }
        Runtime.getRuntime().halt(status);
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static String launchctlLabel() {
        return CONFIG.string("aqli.gguf.fast_run.daemon_launchctl_label",
                "tech.kayys.aljabr.gguf-fast-daemon");
    }

    private static String daemonNativePaths() {
        String home = System.getProperty("user.home");
        return home + "/.aqli/libs" + File.pathSeparator + home + "/.aqli/libs/llama";
    }

    private static void removeLaunchctlDaemon() {
        GgufDaemonLauncher.removeLaunchctl(launchctlLabel(), isMacOs());
    }

    private static void deleteDaemonPortFile() {
        daemonInfoStore().delete();
    }

    private static void deleteDaemonPortFileIfPort(int port) {
        if (port <= 0) {
            return;
        }
        OptionalInt current = readDaemonPort();
        if (current.isPresent() && current.getAsInt() == port) {
            deleteDaemonPortFile();
        }
    }

    private static int daemonConnectTimeoutMillis() {
        return CONFIG.intValue("aqli.gguf.fast_run.daemon_connect_timeout_ms", 1000);
    }

    private static int daemonStartTimeoutMillis() {
        return CONFIG.intValue("aqli.gguf.fast_run.daemon_start_timeout_ms", 20_000);
    }

    static long daemonRequestRetryMillis() {
        return Math.max(0L, CONFIG.longValue("aqli.gguf.fast_run.daemon_request_retry_ms", 2_000L));
    }

    static long daemonRequestRetrySleepMillis() {
        return Math.max(10L, CONFIG.longValue("aqli.gguf.fast_run.daemon_request_retry_sleep_ms", 75L));
    }

    static int daemonMaxSessions() {
        return Math.max(1, CONFIG.intValue("aqli.gguf.fast_run.daemon_max_sessions", 2));
    }

    private static long timeoutMillis() {
        return TimeUnit.SECONDS.toMillis(CONFIG.longValue("aqli.gguf.fast_run.timeout_seconds", 180L));
    }

    static GgufRunArguments parseRunArguments(String[] args) {
        GgufRunArguments parsed = GgufRunArguments.parse(
                args,
                CONFIG.string("aqli.gguf.fast_run.backend", "metal"),
                firstNonBlank(
                        CONFIG.value("aqli.gguf.fast_run.engine"),
                        System.getenv("AQLI_GGUF_ENGINE"),
                        "auto"));
        if (parsed.verbose()) {
            System.setProperty("aqli.verbose", "true");
            System.setProperty("aljabr.metal.verbose", "true");
        }
        return parsed;
    }

    private static EngineMode engineMode(GgufRunArguments args) {
        return EngineMode.effective(args.provider(), args.engine());
    }

    private static String firstNonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return fallback;
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }
}
