package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.provider.litert.LiteRTRunArguments;
import tech.kayys.aqli.provider.litert.LiteRTRouteResolver;
import tech.kayys.aqli.provider.litert.LiteRTDaemonProtocol;
import tech.kayys.aqli.provider.litert.LiteRTTokenStreamLimiter;
import tech.kayys.aqli.provider.litert.LiteRTEngineCache;
import tech.kayys.aqli.provider.litert.LiteRTEngineLease;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;
import tech.kayys.aqli.observability.ObservabilityManager;
import tech.kayys.aqli.metrics.MetricsRegistry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.ExperimentalFlags;
import com.google.ai.edge.litertlm.LogSeverity;
import com.google.ai.edge.litertlm.MessageCallback;
import com.google.ai.edge.litertlm.SamplerConfig;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Tiny LiteRT-LM text runner used by the local macOS shim for simple
 * {@code aqli run} calls.
 *
 * <p>The full Quarkus CLI is still the source of truth for general commands.
 * This entrypoint intentionally supports only local text LiteRT-LM inference so
 * short prompts do not pay the whole CLI/SDK startup tax.</p>
 */
public final class LiteRtLmStandaloneRunner {
    private static final int FALLBACK_TO_FULL_CLI = StandaloneRunProtocol.FALLBACK_TO_FULL_CLI;
    private static final int DEFAULT_MAX_NUM_TOKENS = 2048;
    private static final int DEFAULT_MAX_NUM_TOKENS_CEILING = 2048;
    private static final int DEFAULT_MAX_NUM_TOKENS_FLOOR = 256;
    private static final Pattern BARE_QUESTION_PATTERN = Pattern.compile(
            "(?i)^(who|what|where|when|why|how|which|whom|whose|is|are|was|were|do|does|did|can|could|should|would|will|may|might)\\b.*[^.?!]$");
    private static final String SHORT_QUESTION_INSTRUCTION =
            "Answer directly and concisely in one or two sentences. Do not ask a clarification question. "
                    + "If a term is ambiguous, answer the most common meaning first and mention common alternatives briefly.\n"
                    + "Question: ";

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
            java.util.logging.Logger.getLogger("io.opentelemetry.api.GlobalOpenTelemetry").setLevel(java.util.logging.Level.WARNING);
            java.util.logging.Logger.getLogger("tech.kayys.aljabr").setLevel(java.util.logging.Level.WARNING);
        } catch (Throwable ignored) {
        }
    }

    private LiteRtLmStandaloneRunner() {
    }

    public static void main(String[] args) {
        for (String a : args) {
            if ("-v".equals(a) || "--verbose".equals(a)) {
                System.setProperty("aqli.verbose", "true");
                break;
            }
        }
        if (args.length > 0 && "__daemon".equals(args[0])) {
            System.exit(runDaemon());
            return;
        }
        if (args.length > 0 && "__daemon-stop".equals(args[0])) {
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
    }

    static int run(String[] args) {
        try {
            LiteRTRunArguments parsed = LiteRTRunArguments.parse(args, System.getProperty("aqli.litert.fast_run.backend", "gpu"));
            if (!parsed.isSupportedRequest()) {
                return FALLBACK_TO_FULL_CLI;
            }
            if (daemonEnabled()) {
                OptionalInt daemonStatus = requestExistingDaemon(args);
                if (daemonStatus.isPresent()) {
                    return daemonStatus.getAsInt();
                }
            }
            Optional<RouteResolution> route = resolveLiteRtLmRoute(parsed);
            if (route.isEmpty()) {
                return FALLBACK_TO_FULL_CLI;
            }
            if (daemonEnabled()) {
                OptionalInt daemonStatus = requestDaemon(args);
                if (daemonStatus.isPresent()) {
                    return daemonStatus.getAsInt();
                }
            }
            generate(route.get(), parsed);
            return 0;
        } catch (Throwable throwable) {
            if (Boolean.getBoolean("aqli.litert.fast_run.debug")) {
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
            LiteRTRunArguments parsed = LiteRTRunArguments.parse(runArgs, System.getProperty("aqli.litert.fast_run.backend", "gpu"));
            if (!parsed.isSupportedRequest()) {
                printPrewarmUsage(System.err);
                return 2;
            }
            if (resolveLiteRtLmRoute(parsed).isEmpty()) {
                System.err.println("LiteRT-LM daemon prewarm could not resolve a .litertlm model.");
                return 2;
            }
            OptionalInt status = requestDaemonPrewarm(runArgs);
            return status.orElse(2);
        } catch (Throwable throwable) {
            System.err.println("LiteRT-LM daemon prewarm failed: " + summarize(throwable));
            if (Boolean.getBoolean("aqli.litert.fast_run.debug")) {
                throwable.printStackTrace(System.err);
            }
            return 2;
        }
    }

    private static void printPrewarmUsage(PrintStream out) {
        out.println("Usage: aqli prewarm --provider litert --model MODEL [--backend metal|cpu]");
        out.println("       aqli warmup  --provider litert --model MODEL [--backend metal|cpu]");
        out.println();
        out.println("Preloads the official LiteRT-LM daemon and opens the selected Metal/CPU engine");
        out.println("so the next matching aqli run can skip cold engine initialization.");
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
            }
        }
        if (!hasPrompt) {
            normalized.add("--prompt");
            normalized.add(firstNonBlank(
                    System.getProperty("aqli.litert.fast_run.prewarm_prompt"),
                    System.getenv("AQLI_LITERT_FAST_PREWARM_PROMPT"),
                    "where is jakarta"));
        }
        if (!hasMaxTokens) {
            normalized.add("--max-tokens");
            normalized.add(firstNonBlank(
                    System.getProperty("aqli.litert.fast_run.prewarm_context_tokens"),
                    System.getenv("AQLI_LITERT_FAST_PREWARM_CONTEXT_TOKENS"),
                    "10"));
        }
        if (!hasProvider) {
            normalized.add("--provider");
            normalized.add("litert");
        }
        return normalized.toArray(String[]::new);
    }

    private static boolean isPrewarmCommand(String arg) {
        return "__litert-daemon-prewarm".equals(arg)
                || "prewarm".equals(arg)
                || "warmup".equals(arg);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    static Optional<Path> resolveLiteRtLmModel(LiteRTRunArguments args) {
        return resolveLiteRtLmRoute(args).map(RouteResolution::path);
    }

    /**
     * Resolves a model-index reference to a runnable LiteRT-LM artifact.
     * The runtime resolver owns format matching; the CLI supplies its index
     * location and the existing equivalent-artifact preference.
     */
    static Optional<Path> resolveIndexedLiteRtModelPath(String ref) {
        if (ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        return new LiteRTRouteResolver()
                .resolve(null, ref, modelIndexPath(), equivalentLiteRtRouteEnabled())
                .map(LiteRTRouteResolver.RouteMatch::path);
    }

    static Optional<Path> findIndexedLiteRtModelPath(String modelId) {
        return new LiteRTRouteResolver()
                .resolve(null, modelId, modelIndexPath(), equivalentLiteRtFastPathEnabled())
                .map(match -> match.path());
    }

    static boolean equivalentLiteRtFastPathEnabled() {
        String configured = System.getProperty("aqli.litert.fast_run.auto_equivalent");
        return configured == null || configured.isBlank() || Boolean.parseBoolean(configured);
    }

    static String canonicalModelKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String leaf = lastPathSegment(value)
                .replace("__", "/")
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);
        int slash = leaf.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < leaf.length()) {
            leaf = leaf.substring(slash + 1);
        }
        leaf = leaf
                .replaceAll("\\.(litertlm|litert|tflite|tfl|task|gguf|safetensors?|bin)$", "")
                .replaceAll("(?i)(-?litert-?lm|-?litertlm|-?tflite|-?safetensors?|\\.safetensors?)$", "");
        StringBuilder key = new StringBuilder();
        for (String token : leaf.split("[^a-z0-9]+")) {
            if (!token.isBlank() && !isCanonicalNoiseToken(token)) {
                key.append(token);
            }
        }
        return key.toString();
    }

    private static String lastPathSegment(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 && slash + 1 < normalized.length()
                ? normalized.substring(slash + 1)
                : normalized;
    }

    private static boolean isCanonicalNoiseToken(String token) {
        return switch (token) {
            case "google", "litert", "community", "lm", "gguf", "safetensor", "safetensors",
                    "qat", "mobile", "transformers",
                    "q4", "q5", "q6", "q8", "k", "m", "f16", "fp16", "bf16" -> true;
            default -> false;
        };
    }

    static Optional<RouteResolution> resolveLiteRtLmRoute(LiteRTRunArguments args) {
        long startNanos = System.nanoTime();
        Optional<RouteMatch> match = resolveLiteRtLmRouteMatch(args);
        long durationNanos = System.nanoTime() - startNanos;
        return match.map(found -> new RouteResolution(
                found.path(),
                found.source(),
                found.cacheHit(),
                liteRtModelFormat(found.path()),
                durationNanos));
    }

    private static Optional<RouteMatch> resolveLiteRtLmRouteMatch(LiteRTRunArguments args) {
        Optional<RouteMatch> resolved = new LiteRTRouteResolver()
                .resolve(
                        args.modelFile(),
                        args.model(),
                        modelIndexPath(),
                        equivalentLiteRtRouteEnabled())
                .map(match -> new RouteMatch(match.path(), match.source(), match.cacheHit()));
        if (resolved.isPresent() || args.model() == null) {
            return resolved;
        }

        // Preserve AQLI's legacy index and cached Gemma alternate-runtime fallback.
        // These policies depend on the CLI-owned LocalModelIndex and route policy.
        try {
            Optional<LocalModelIndex.Entry> indexed = LocalModelIndex.find(args.model());
            if (indexed.isPresent()
                    && isLiteRtIndexEntry(indexed.get())
                    && indexed.get().path != null
                    && !indexed.get().path.isBlank()) {
                return findLiteRtLmFromExplicitPath(Path.of(indexed.get().path))
                        .map(path -> new RouteMatch(path, "local-index-litert", true));
            }
            if (indexed.isPresent()
                    && isSafetensorIndexEntry(indexed.get())
                    && indexed.get().path != null
                    && !indexed.get().path.isBlank()) {
                Optional<Path> cachedGemma4LiteRt = findCachedGemma4LiteRtModelPath(indexed.get().path);
                if (cachedGemma4LiteRt.isPresent()) {
                    return Optional.of(new RouteMatch(
                            cachedGemma4LiteRt.get(),
                            "cached-gemma4-litert",
                            true));
                }
            }
        } catch (Throwable ignored) {
            // Full CLI has the complete resolver if the lightweight index read fails.
        }
        return Optional.empty();
    }

    static record RouteResolution(
            Path path,
            String source,
            boolean cacheHit,
            String selectedArtifact,
            long durationNanos) {

        RouteResolution withDurationNanos(long durationNanos) {
            return new RouteResolution(path, source, cacheHit, selectedArtifact, durationNanos);
        }
    }

    static record RouteMatch(
            Path path,
            String source,
            boolean cacheHit) {
    }

    private static boolean isLiteRtFormat(String format) {
        if (format == null || format.isBlank()) return false;
        return switch (format.trim().toLowerCase(Locale.ROOT)) {
            case "litert", "litertlm", "tflite", "tfl", "task" -> true;
            default -> false;
        };
    }

    private static boolean isLiteRtPathString(String path) {
        return path != null && path.replace("\\", "/").toLowerCase(Locale.ROOT).endsWith(".litertlm");
    }

    private static boolean isLiteRtIndexEntry(LocalModelIndex.Entry entry) {
        if (entry == null) {
            return false;
        }
        if (isLiteRtFormat(entry.format)) {
            return true;
        }
        return entry.path != null && isLiteRtPathString(entry.path);
    }

    private static boolean isSafetensorIndexEntry(LocalModelIndex.Entry entry) {
        if (entry == null) {
            return false;
        }
        String format = entry.format == null ? "" : entry.format.trim().toLowerCase(Locale.ROOT);
        if (format.equals("safetensor") || format.equals("safetensors")) {
            return true;
        }
        String path = entry.path == null ? "" : entry.path.trim().toLowerCase(Locale.ROOT);
        return path.endsWith(".safetensors") || path.contains("/safetensors/");
    }

    private static Optional<Path> findCachedGemma4LiteRtModelPath(String safetensorPath) {
        DirectSafetensorRoutePolicy.AlternateRuntimeSelection selection =
                DirectSafetensorRoutePolicy.selectCachedGemma4MobileQatLiteRtAlternateRuntime(
                        "safetensor",
                        safetensorPath,
                        safetensorPath,
                        false,
                        true);
        if (!selection.selected() || selection.localPath() == null || selection.localPath().isBlank()) {
            return Optional.empty();
        }
        return findLiteRtLmFromExplicitPath(Path.of(selection.localPath()));
    }

    private static Optional<Path> findLiteRtLmFromExplicitPath(Path path) {
        return new LiteRTRouteResolver().resolveArtifact(path);
    }

    private static void generate(RouteResolution route, LiteRTRunArguments args) throws Exception {
        generate(route, args, System.out, System.err, null, "fast path");
    }

    private static void generate(
            RouteResolution route,
            LiteRTRunArguments args,
            PrintStream out,
            PrintStream err,
            LiteRTEngineCache<Engine> engineCache,
            String runnerName) throws Exception {
        Path modelPath = route.path();
        long startNanos = System.nanoTime();
        Files.createDirectories(cacheDir());
        Engine.Companion.setNativeMinLogSeverity(nativeLogSeverity());

        int engineMaxNumTokens = maxNumTokens(args);
        if (args.banner()) {
            printRunHeader(modelPath, args, out);
        }
        long beforeEngineInitNanos = System.nanoTime();
        String requestedBackend = normalizedBackend(args.backend());
        try (LiteRTEngineLease<Engine> lease = engineCache == null
                ? openEngine(modelPath, requestedBackend, engineMaxNumTokens, true, false)
                : acquireCachedEngine(engineCache, modelPath, requestedBackend, engineMaxNumTokens)) {
            long afterEngineInitNanos = System.nanoTime();
            String warmSuffix = engineCache == null ? "" : ", warmEngine=" + lease.reused();
            if (args.banner()) {
                printExecutionRoute(out, lease, runnerName);
                out.printf("Using official LiteRT-LM JVM %s for %s (backend=%s, speculativeDecoding=%s, engineMaxNumTokens=%d%s).%n",
                        runnerName,
                        modelPath.getFileName(),
                        lease.backendName(),
                        lease.speculativeDecoding(),
                        engineMaxNumTokens,
                        warmSuffix);
            }
            if (lease.fallbackReason() != null && args.banner()) {
                err.printf("LiteRT-LM GPU backend failed; using official CPU fallback instead: %s%n",
                        lease.fallbackReason());
            }

            SamplerConfig sampler = new SamplerConfig(
                    Math.max(1, args.topK()),
                    Math.max(0.0d, Math.min(1.0d, args.topP())),
                    Math.max(0.0d, args.temperature()),
                    0);
            ConversationConfig conversationConfig = new ConversationConfig(
                    null,
                    List.of(),
                    List.of(),
                    sampler,
                    false,
                    null,
                    Map.of());
            try (Conversation conversation = lease.engine().createConversation(conversationConfig)) {
                long afterConversationCreateNanos = System.nanoTime();
                CountDownLatch done = new CountDownLatch(1);
                AtomicBoolean cancelled = new AtomicBoolean(false);
                AtomicInteger streamUpdates = new AtomicInteger(0);
                AtomicLong firstChunkNanos = new AtomicLong(0L);
                AtomicReference<Throwable> error = new AtomicReference<>();
                LiteRTTokenStreamLimiter tokenLimiter = new LiteRTTokenStreamLimiter(args.maxTokens());
                conversation.sendMessageAsync(Contents.Companion.of(promptForModel(args.prompt())), new MessageCallback() {
                    @Override
                    public void onMessage(com.google.ai.edge.litertlm.Message message) {
                        if (cancelled.get()) {
                            return;
                        }
                        streamUpdates.incrementAndGet();
                        firstChunkNanos.compareAndSet(0L, System.nanoTime());
                        String delta = tokenLimiter.offer(message.toString());
                        if (!delta.isEmpty()) {
                            out.print(delta);
                            out.flush();
                        }
                        if (tokenLimiter.atLimit() && cancelled.compareAndSet(false, true)) {
                            conversation.cancelProcess();
                            done.countDown();
                        }
                    }

                    @Override
                    public void onDone() {
                        done.countDown();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        if (!cancelled.get()) {
                            error.compareAndSet(null, throwable);
                        }
                        done.countDown();
                    }
                }, Map.of());
                if (!done.await(timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                    conversation.cancelProcess();
                    throw new IllegalStateException("LiteRT-LM fast path timed out");
                }
                if (error.get() != null && !cancelled.get()) {
                    throw new IllegalStateException(error.get());
                }
                lease.markPrewarmed(prewarmIterationCount());
                long endNanos = System.nanoTime();
                if (args.banner()) {
                    printFastRunStats(out,
                            streamUpdates.get(),
                            tokenLimiter.emittedTokenCount(),
                            startNanos,
                            beforeEngineInitNanos,
                            afterEngineInitNanos,
                            afterConversationCreateNanos,
                            firstChunkNanos.get(),
                            endNanos);
                }
                if (Boolean.getBoolean("aqli.litert.fast_run.profile")) {
                    long first = firstChunkNanos.get();
                    err.printf(Locale.ROOT,
                            "[Fast LiteRT-LM profile] routeResolve=%.3fs routeCacheHit=%s selectedArtifact=%s routeSource=%s cache+config=%.3fs engineInit=%.3fs conversation=%.3fs firstChunk=%.3fs total=%.3fs%n",
                            seconds(route.durationNanos()),
                            route.cacheHit(),
                            route.selectedArtifact(),
                            route.source(),
                            seconds(beforeEngineInitNanos - startNanos),
                            seconds(afterEngineInitNanos - beforeEngineInitNanos),
                            seconds(afterConversationCreateNanos - afterEngineInitNanos),
                            first == 0L ? -1.0d : seconds(first - afterConversationCreateNanos),
                            seconds(System.nanoTime() - startNanos));
                }
            }
        }
    }

    static void printFastRunStats(
            PrintStream out,
            int streamUpdates,
            int outputTokens,
            long startNanos,
            long beforeEngineInitNanos,
            long afterEngineInitNanos,
            long afterConversationCreateNanos,
            long firstChunkNanos,
            long endNanos) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, endNanos - startNanos));
        double durationSeconds = durationMs / 1000.0d;
        double speed = durationSeconds > 0.0d ? outputTokens / durationSeconds : 0.0d;
        double loadMs = nanosToMillis(Math.max(0L, afterEngineInitNanos - beforeEngineInitNanos));
        boolean hasTtft = firstChunkNanos > 0L;
        double ttftMs = hasTtft ? nanosToMillis(Math.max(0L, firstChunkNanos - startNanos)) : -1.0d;
        double engineTtftMs = hasTtft
                ? nanosToMillis(Math.max(0L, firstChunkNanos - afterConversationCreateNanos))
                : -1.0d;
        double tokenLatencyMs = outputTokens > 0
                ? nanosToMillis(Math.max(0L, endNanos - (hasTtft ? firstChunkNanos : startNanos))) / outputTokens
                : 0.0d;

        if (hasTtft) {
            out.printf("%n[Stream updates: %d, Duration: %.2fs, Speed: %.2f t/s, TTFT: %.2f ms]%n",
                    streamUpdates, durationSeconds, speed, ttftMs);
        } else {
            out.printf("%n[Stream updates: %d, Duration: %.2fs, Speed: %.2f t/s]%n",
                    streamUpdates, durationSeconds, speed);
        }
        out.println("Performance Metrics:");
        out.printf("  load time      = %9.2f ms%n", loadMs);
        out.printf("  generation     = %9.2f t/s (%d tokens)%n", speed, outputTokens);
        if (hasTtft) {
            out.printf("  latency (ttft) = %9.2f ms%n", ttftMs);
            out.printf("  engine ttft    = %9.2f ms (model-ready)%n", engineTtftMs);
        }
        out.printf("  token latency  = %9.2f ms/token%n%n", tokenLatencyMs);

        try {
            ObservabilityManager obs = ObservabilityManager.global();
            if (obs != null) {
                MetricsRegistry reg = obs.getMetricsRegistry();
                AttributesBuilder attrs = Attributes.builder()
                        .put("engine", "litert")
                        .put("fast_path", true);

                Attributes finalAttrs = attrs.build();
                reg.record("aqli.llm.generation.duration", durationMs, finalAttrs);
                reg.record("aqli.llm.generation.speed", speed, finalAttrs);
                reg.record("aqli.llm.generation.token_latency", tokenLatencyMs, finalAttrs);
                reg.record("aqli.llm.model.open_time", loadMs, finalAttrs);
                if (hasTtft) {
                    reg.record("aqli.llm.native.prefill", ttftMs, finalAttrs);
                }
            }
        } catch (Exception e) {
            // Ignore telemetry exceptions
        }
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    static void printRunHeader(Path modelPath, LiteRTRunArguments args, PrintStream out) {
        printBanner(out);
        if (shouldPrintResolvedModelPath(modelPath, args)) {
            out.printf("Resolved local model index entry: %s%n", modelPath);
        }
        out.printf("Model: %s%n", modelPath);
        out.printf("Provider: litert, format=%s%n", liteRtModelFormat(modelPath));
    }

    static void printExecutionRoute(PrintStream out, LiteRTEngineLease<Engine> lease, String runnerName) {
        out.println(executionRouteLine(lease.backendName(), runnerName));
        out.println("--------------------------------------------------");
    }

    static String liteRtModelFormat(Path modelPath) {
        if (modelPath == null || modelPath.getFileName() == null) {
            return "litert";
        }
        String name = modelPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".litertlm")) {
            return "litertlm";
        }
        if (name.endsWith(".tflite") || name.endsWith(".tfl")) {
            return "tflite";
        }
        if (name.endsWith(".task")) {
            return "task";
        }
        return "litert";
    }

    static String executionRouteLine(String backendName, String runnerName) {
        String backend = backendName == null || backendName.isBlank() ? "unknown" : backendName;
        return "Execution route: litert (backend=" + backend + ") [" + executionRouteTag(runnerName) + "]";
    }

    private static String executionRouteTag(String runnerName) {
        String normalized = runnerName == null ? "" : runnerName.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("daemon")) {
            return "official_litert_lm_jvm_daemon";
        }
        return "official_litert_lm_jvm_fast_path";
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

    private static boolean shouldPrintResolvedModelPath(Path modelPath, LiteRTRunArguments args) {
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

    private static LiteRTEngineLease<Engine> openEngine(
            Path modelPath,
            String requestedBackend,
            int engineMaxNumTokens,
            boolean closeOnClose,
            boolean reused) {
        boolean speculativeDecoding = speculativeDecodingEnabled(requestedBackend);
        ExperimentalFlags.INSTANCE.setEnableSpeculativeDecoding(speculativeDecoding);
        EngineConfig engineConfig = new EngineConfig(
                modelPath.toString(),
                backend(requestedBackend),
                null,
                null,
                engineMaxNumTokens,
                null,
                cacheDir().toString());

        try {
            return new LiteRTEngineLease<>(
                    initializeEngine(engineConfig),
                    backendName(requestedBackend),
                    speculativeDecoding,
                    null,
                    closeOnClose,
                    reused);
        } catch (Throwable gpuFailure) {
            if (!"gpu".equals(requestedBackend)
                    || !Boolean.parseBoolean(System.getProperty("aqli.litert.fast_run.cpu_fallback", "true"))) {
                throw gpuFailure;
            }
            String fallbackReason = summarize(gpuFailure);
            speculativeDecoding = speculativeDecodingEnabled("cpu");
            ExperimentalFlags.INSTANCE.setEnableSpeculativeDecoding(speculativeDecoding);
            EngineConfig cpuEngineConfig = new EngineConfig(
                    modelPath.toString(),
                    new Backend.CPU(),
                    null,
                    null,
                    engineMaxNumTokens,
                    null,
                    cacheDir().toString());
            return new LiteRTEngineLease<>(
                    initializeEngine(cpuEngineConfig),
                    "CPU",
                    speculativeDecoding,
                    fallbackReason,
                    closeOnClose,
                    reused);
        }
    }

    private static Path cacheDir() {
        String configured = System.getProperty("aqli.litert.lm_jvm_bridge.cache_dir");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath();
        }
        return Path.of(System.getProperty("user.home"), ".aqli", "cache", "litert-lm-jvm").toAbsolutePath();
    }

    private static Engine initializeEngine(EngineConfig config) {
        Engine engine = new Engine(config);
        try {
            engine.initialize();
            return engine;
        } catch (Throwable failure) {
            try {
                engine.close();
            } catch (Throwable ignored) {
            }
            throw failure;
        }
    }

    private static LogSeverity nativeLogSeverity() {
        String configured = System.getProperty("aqli.litert.fast_run.native_log_severity", "ERROR");
        try {
            return LogSeverity.valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return LogSeverity.ERROR;
        }
    }

    private static String metalAwareGpuBackendName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("mac") ? "GPU/Metal" : "GPU";
    }

    private static Backend backend(String normalizedBackend) {
        return "cpu".equals(normalizedBackend) ? new Backend.CPU() : new Backend.GPU();
    }

    private static String backendName(String normalizedBackend) {
        return "cpu".equals(normalizedBackend) ? "CPU" : metalAwareGpuBackendName();
    }

    private static String normalizedBackend(String configured) {
        String normalized = configured == null ? "gpu" : configured.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "cpu" -> "cpu";
            case "gpu", "metal", "auto", "" -> "gpu";
            default -> "gpu";
        };
    }

    private static boolean speculativeDecodingEnabled(String normalizedBackend) {
        String configured = System.getProperty("aqli.litert.fast_run.speculative_decoding");
        if (configured != null && !configured.isBlank()) {
            return Boolean.parseBoolean(configured);
        }
        return "gpu".equals(normalizedBackend);
    }

    private static String summarize(Throwable throwable) {
        if (throwable == null) {
            return "unknown failure";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getClass().getSimpleName();
        }
        return message.replace('\n', ' ').strip();
    }

    private static int maxNumTokens(LiteRTRunArguments args) {
        Integer configured = Integer.getInteger("aqli.litert.lm_jvm_bridge.max_num_tokens");
        if (configured != null && configured > 0) {
            return configured;
        }
        if (!dynamicEngineTokensEnabled()) {
            return DEFAULT_MAX_NUM_TOKENS;
        }
        return dynamicEngineTokenBudget(args.prompt(), args.maxTokens());
    }

    static int dynamicEngineTokenBudget(String prompt, int maxTokens) {
        int requested = Math.max(1, maxTokens);
        int promptEstimate = approximateTokenCount(prompt);
        int dynamic = requested + promptEstimate + 64;
        int floor = Integer.getInteger("aqli.litert.fast_run.min_engine_tokens", DEFAULT_MAX_NUM_TOKENS_FLOOR);
        int ceiling = Integer.getInteger("aqli.litert.fast_run.max_engine_tokens", DEFAULT_MAX_NUM_TOKENS_CEILING);
        return Math.max(1, Math.min(Math.max(floor, dynamic), Math.max(floor, ceiling)));
    }

    static boolean dynamicEngineTokensEnabled() {
        String configured = System.getProperty("aqli.litert.fast_run.dynamic_engine_tokens");
        return configured == null || configured.isBlank() || Boolean.parseBoolean(configured);
    }

    static String promptForModel(String prompt) {
        if (prompt == null || !questionNormalizationEnabled()) {
            return prompt;
        }
        String stripped = prompt.strip();
        if (stripped.isEmpty() || !BARE_QUESTION_PATTERN.matcher(stripped).matches()) {
            return prompt;
        }
        return SHORT_QUESTION_INSTRUCTION + Character.toUpperCase(stripped.charAt(0)) + stripped.substring(1) + "?";
    }

    static boolean questionNormalizationEnabled() {
        String configured = System.getProperty("aqli.litert.fast_run.normalize_short_questions");
        return configured == null || configured.isBlank() || Boolean.parseBoolean(configured);
    }

    private static Duration timeout() {
        return Duration.ofSeconds(Long.getLong("aqli.litert.lm_jvm_bridge.timeout_seconds", 180L));
    }

    private static double seconds(long nanos) {
        return nanos / 1_000_000_000.0d;
    }

    private static int approximateTokenCount(CharSequence text) {
        int tokens = 0;
        boolean inToken = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                inToken = false;
            } else if (!inToken) {
                tokens++;
                inToken = true;
            }
        }
        return tokens;
    }

    private static String limitByApproximateTokens(String text, int maxTokens) {
        int tokens = 0;
        boolean inToken = false;
        int end = text.length();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                inToken = false;
            } else if (!inToken) {
                tokens++;
                inToken = true;
                if (tokens > maxTokens) {
                    end = i;
                    break;
                }
            }
        }
        return text.substring(0, end).stripTrailing();
    }

    private static boolean daemonEnabled() {
        return Boolean.parseBoolean(System.getProperty("aqli.litert.fast_run.daemon", "false"));
    }

    private static OptionalInt requestExistingDaemon(String[] rawArgs) {
        return sendDaemonRequestWithRetries(LiteRTDaemonProtocol.RUN_MAGIC, rawArgs, daemonRequestRetryMillis());
    }

    private static OptionalInt requestDaemon(String[] rawArgs) {
        OptionalInt status = requestExistingDaemon(rawArgs);
        if (status.isPresent()) {
            return status;
        }
        if (!startDaemon()) {
            return OptionalInt.empty();
        }
        return sendDaemonRequestWithRetries(LiteRTDaemonProtocol.RUN_MAGIC, rawArgs, daemonStartTimeoutMillis());
    }

    private static OptionalInt requestDaemonPrewarm(String[] rawArgs) {
        OptionalInt status = sendDaemonRequestWithRetries(LiteRTDaemonProtocol.PREWARM_MAGIC, rawArgs, daemonRequestRetryMillis());
        if (status.isPresent()) {
            return status;
        }
        if (!startDaemon()) {
            return OptionalInt.empty();
        }
        return sendDaemonRequestWithRetries(LiteRTDaemonProtocol.PREWARM_MAGIC, rawArgs, daemonStartTimeoutMillis());
    }

    private static OptionalInt sendDaemonRequestWithRetries(String magic, String[] rawArgs, long retryMillis) {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, retryMillis));
        while (true) {
            OptionalInt status = sendDaemonRequest(magic, rawArgs);
            if (status.isPresent()) {
                return status;
            }
            if (readDaemonInfo().isEmpty() || System.nanoTime() >= deadlineNanos) {
                return OptionalInt.empty();
            }
            try {
                Thread.sleep(daemonRequestRetrySleepMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return OptionalInt.empty();
            }
        }
    }

    private static OptionalInt sendDaemonRequest(String magic, String[] rawArgs) {
        OptionalInt port = readDaemonPort();
        if (port.isEmpty()) {
            return OptionalInt.empty();
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port.getAsInt()),
                    daemonConnectTimeoutMillis());
            socket.setSoTimeout((int) timeout().toMillis());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            LiteRTDaemonProtocol.writeString(out, magic);
            out.writeInt(rawArgs.length);
            for (String arg : rawArgs) {
                LiteRTDaemonProtocol.writeString(out, arg);
            }
            out.flush();
            while (true) {
                int channel = in.read();
                if (channel < 0) {
                    return OptionalInt.empty();
                }
                int length = in.readInt();
                if (channel == 0) {
                    return OptionalInt.of(length);
                }
                byte[] payload = in.readNBytes(length);
                PrintStream target = channel == 2 ? System.err : System.out;
                target.write(payload);
                target.flush();
            }
        } catch (ConnectException ignored) {
            deleteDaemonPortFile();
            return OptionalInt.empty();
        } catch (Exception e) {
            if (Boolean.getBoolean("aqli.litert.fast_run.debug")) {
                System.err.println("LiteRT-LM daemon request failed: " + summarize(e));
            }
            return OptionalInt.empty();
        }
    }

    private static boolean startDaemon() {
        deleteDaemonPortFile();
        try {
            Files.createDirectories(daemonRunDir());
            Files.createDirectories(daemonLogFile().getParent());
            List<String> command = new ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            command.add("-Xmx" + System.getProperty("aqli.litert.fast_run.daemon_heap",
                    System.getProperty("aqli.litert.fast_run.heap", "8g")));
            command.add("-XX:MaxDirectMemorySize=" + System.getProperty("aqli.litert.fast_run.daemon_direct_memory", "24g"));
            addDaemonCrashDiagnostics(command);
            command.add("--enable-preview");
            command.add("--add-modules");
            command.add("jdk.incubator.vector");
            command.add("--enable-native-access=ALL-UNNAMED");
            copyAqliSystemProperties(command);
            command.add("-Djava.library.path=" + System.getProperty("java.library.path", ""));
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(LiteRtLmStandaloneRunner.class.getName());
            command.add("__daemon");

            if (!startDetachedDaemon(command)) {
                return false;
            }
            long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(daemonStartTimeoutMillis());
            while (System.nanoTime() < deadlineNanos) {
                OptionalInt port = readDaemonPort();
                if (port.isPresent()) {
                    return true;
                }
                Thread.sleep(100L);
            }
            return readDaemonPort().isPresent();
        } catch (Exception e) {
            if (Boolean.getBoolean("aqli.litert.fast_run.debug")) {
                System.err.println("LiteRT-LM daemon failed to start: " + summarize(e));
            }
            return false;
        }
    }

    private static void addDaemonCrashDiagnostics(List<String> command) {
        String defaultErrorFile = daemonLogFile().getParent()
                .resolve("litert-fast-daemon-hs_err_pid%p.log")
                .toString();
        command.add("-XX:ErrorFile=" + System.getProperty(
                "aqli.litert.fast_run.daemon_error_file",
                defaultErrorFile));
        if (Boolean.parseBoolean(System.getProperty("aqli.litert.fast_run.daemon_signal_log", "false"))) {
            String defaultSignalLog = daemonLogFile().getParent()
                    .resolve("litert-fast-daemon-signal-%p.log")
                    .toString();
            command.add("-Xlog:os+signal=info:file=" + System.getProperty(
                    "aqli.litert.fast_run.daemon_signal_log_file",
                    defaultSignalLog)
                    + ":time,uptime,level,tags");
        }
    }

    private static boolean startDetachedDaemon(List<String> command) throws IOException, InterruptedException {
        String launcher = daemonLauncherMode();
        if (Boolean.getBoolean("aqli.litert.fast_run.debug")) {
            System.err.println("LiteRT-LM daemon launcher: " + launcher);
        }
        if (launcher.equals("launchctl")) {
            return isMacOs() && startDetachedDaemonWithLaunchctl(command);
        }
        if (launcher.equals("auto") && isMacOs() && startDetachedDaemonWithLaunchctl(command)) {
            return true;
        }
        return startDetachedDaemonWithNohup(command);
    }

    static String daemonLauncherMode() {
        String launcher = System.getProperty("aqli.litert.fast_run.daemon_launcher", "auto")
                .trim()
                .toLowerCase(Locale.ROOT);
        return switch (launcher) {
            case "auto", "launchctl", "nohup" -> launcher;
            default -> "nohup";
        };
    }

    private static boolean startDetachedDaemonWithLaunchctl(List<String> command) throws IOException, InterruptedException {
        String launchctl = "/bin/launchctl";
        if (!Files.isExecutable(Path.of(launchctl))) {
            return false;
        }
        removeLaunchctlDaemon();
        String shellCommand = daemonShellCommand(command, false);
        Process launcher = new ProcessBuilder(
                launchctl,
                "submit",
                "-l",
                launchctlLabel(),
                "--",
                "/bin/sh",
                "-c",
                shellCommand)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(daemonLogFile().toFile()))
                .redirectError(ProcessBuilder.Redirect.appendTo(daemonLogFile().toFile()))
                .start();
        if (!launcher.waitFor(3, TimeUnit.SECONDS)) {
            return true;
        }
        boolean launched = launcher.exitValue() == 0;
        if (!launched && Boolean.getBoolean("aqli.litert.fast_run.debug")) {
            System.err.println("LiteRT-LM launchctl daemon submit failed; falling back to nohup");
        }
        return launched;
    }

    private static boolean startDetachedDaemonWithNohup(List<String> command) throws IOException, InterruptedException {
        Process launcher = new ProcessBuilder("/bin/sh", "-c", daemonShellCommand(command, true)).start();
        launcher.waitFor(2, TimeUnit.SECONDS);
        return true;
    }

    private static String daemonShellCommand(List<String> command, boolean useNohup) {
        String home = System.getProperty("user.home");
        StringBuilder shellCommand = new StringBuilder();
        shellCommand.append("export DYLD_LIBRARY_PATH=")
                .append(shellQuote(home + "/.aqli/libs"))
                .append("${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}; ");
        shellCommand.append("export DYLD_FALLBACK_LIBRARY_PATH=")
                .append(shellQuote(home + "/.aqli/libs"))
                .append("${DYLD_FALLBACK_LIBRARY_PATH:+:$DYLD_FALLBACK_LIBRARY_PATH}; ");
        if (useNohup) {
            shellCommand.append("nohup ");
        } else {
            shellCommand.append("exec ");
        }
        for (int i = 0; i < command.size(); i++) {
            if (i > 0 || useNohup) {
                shellCommand.append(' ');
            }
            shellCommand.append(shellQuote(command.get(i)));
        }
        shellCommand.append(" >> ").append(shellQuote(daemonLogFile().toString()))
                .append(" 2>&1 < /dev/null");
        if (useNohup) {
            shellCommand.append(" &");
        }
        return shellCommand.toString();
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static String launchctlLabel() {
        return System.getProperty("aqli.litert.fast_run.daemon_launchctl_label",
                "tech.kayys.aqli.litert-fast-daemon");
    }

    private static void removeLaunchctlDaemon() {
        if (!isMacOs()) {
            return;
        }
        try {
            new ProcessBuilder("/bin/launchctl", "remove", launchctlLabel())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void copyAqliSystemProperties(List<String> command) {
        Properties properties = System.getProperties();
        for (String name : properties.stringPropertyNames()) {
            if (!name.startsWith("aqli.litert.")) {
                continue;
            }
            String value = properties.getProperty(name);
            if (value != null) {
                command.add("-D" + name + "=" + value);
            }
        }
    }

    private static int runDaemon() {
        try {
            Files.createDirectories(daemonRunDir());
            Files.createDirectories(cacheDir());
        } catch (IOException e) {
            System.err.println("Failed to create LiteRT-LM daemon directories: " + summarize(e));
            return 1;
        }
        AtomicBoolean stop = new AtomicBoolean(false);
        int port = -1;
        try (ServerSocket server = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
             LiteRTEngineCache<Engine> engineCache = new LiteRTEngineCache<>()) {
            RouteResolutionCache routeCache = new RouteResolutionCache();
            port = server.getLocalPort();
            writeDaemonPort(port);
            final int daemonPort = port;
            Runtime.getRuntime().addShutdownHook(new Thread(
                    () -> daemonLog("shutdown hook port=%d", daemonPort),
                    "aqli-litert-fast-daemon-shutdown-log"));
            server.setSoTimeout(1000);
            long lastRequestNanos = System.nanoTime();
            long idleSeconds = Long.getLong("aqli.litert.fast_run.daemon_idle_seconds", 900L);
            long idleNanos = TimeUnit.SECONDS.toNanos(idleSeconds);
            daemonLog("started port=%d pid=%d idleSeconds=%d keyHash=%s",
                    port,
                    ProcessHandle.current().pid(),
                    idleSeconds,
                    daemonKeyHash());
            while (!stop.get()) {
                try (Socket socket = server.accept()) {
                    lastRequestNanos = System.nanoTime();
                    boolean shouldStop = handleDaemonClient(socket, engineCache, routeCache);
                    stop.set(shouldStop);
                    daemonLog("request complete port=%d stop=%s", port, shouldStop);
                } catch (SocketTimeoutException ignored) {
                    if (System.nanoTime() - lastRequestNanos > idleNanos) {
                        daemonLog("idle timeout after %ds", idleSeconds);
                        break;
                    }
                }
            }
            return 0;
        } catch (Throwable throwable) {
            System.err.println("LiteRT-LM daemon failed: " + summarize(throwable));
            if (Boolean.getBoolean("aqli.litert.fast_run.debug")) {
                throwable.printStackTrace(System.err);
            }
            return 1;
        } finally {
            daemonLog("stopped port=%d", port);
            deleteDaemonPortFileIfPort(port);
        }
    }

    private static boolean handleDaemonClient(
            Socket socket,
            LiteRTEngineCache<Engine> engineCache,
            RouteResolutionCache routeCache) {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream framed = new DataOutputStream(socket.getOutputStream());
            String magic = LiteRTDaemonProtocol.readString(in);
            if (LiteRTDaemonProtocol.STOP_MAGIC.equals(magic)) {
                daemonLog("stop requested");
                LiteRTDaemonProtocol.writeStatus(framed, 0);
                return true;
            }
            boolean prewarmRequest = LiteRTDaemonProtocol.PREWARM_MAGIC.equals(magic);
            if (!LiteRTDaemonProtocol.RUN_MAGIC.equals(magic) && !prewarmRequest) {
                LiteRTDaemonProtocol.writeStatus(framed, FALLBACK_TO_FULL_CLI);
                return false;
            }
            int argCount = in.readInt();
            if (argCount < 0 || argCount > 256) {
                LiteRTDaemonProtocol.writeStatus(framed, FALLBACK_TO_FULL_CLI);
                return false;
            }
            String[] rawArgs = new String[argCount];
            for (int i = 0; i < argCount; i++) {
                rawArgs[i] = LiteRTDaemonProtocol.readString(in);
            }
            LiteRTRunArguments parsed = LiteRTRunArguments.parse(rawArgs);
            if (!parsed.isSupportedRequest()) {
                LiteRTDaemonProtocol.writeStatus(framed, FALLBACK_TO_FULL_CLI);
                return false;
            }
            Optional<RouteResolution> route = routeCache.resolve(parsed);
            if (route.isEmpty()) {
                LiteRTDaemonProtocol.writeStatus(framed, FALLBACK_TO_FULL_CLI);
                return false;
            }
            daemonLog("%s request model=%s backend=%s maxTokens=%d",
                    prewarmRequest ? "prewarm" : "run",
                    route.get().path().getFileName(),
                    normalizedBackend(parsed.backend()),
                    parsed.maxTokens());
            try (PrintStream out = new PrintStream(new LiteRTDaemonProtocol.FramedOutputStream(framed, 1), true, StandardCharsets.UTF_8);
                 PrintStream err = new PrintStream(new LiteRTDaemonProtocol.FramedOutputStream(framed, 2), true, StandardCharsets.UTF_8)) {
                if (prewarmRequest) {
                    prewarmEngine(route.get().path(), parsed, out, engineCache);
                } else {
                    generate(route.get(), parsed, out, err, engineCache, "daemon");
                }
                out.flush();
                err.flush();
            }
            LiteRTDaemonProtocol.writeStatus(framed, 0);
        } catch (Throwable throwable) {
            daemonLog("request failed reason=%s", summarize(throwable));
            try {
                DataOutputStream framed = new DataOutputStream(socket.getOutputStream());
                LiteRTDaemonProtocol.writeFrame(framed, 2, ("LiteRT-LM daemon request failed: " + summarize(throwable) + System.lineSeparator())
                        .getBytes(StandardCharsets.UTF_8));
                LiteRTDaemonProtocol.writeStatus(framed, FALLBACK_TO_FULL_CLI);
            } catch (IOException ignored) {
            }
        }
        return false;
    }

    private static void prewarmEngine(
            Path modelPath,
            LiteRTRunArguments args,
            PrintStream out,
            LiteRTEngineCache<Engine> engineCache) throws Exception {
        long startNanos = System.nanoTime();
        Files.createDirectories(cacheDir());
        Engine.Companion.setNativeMinLogSeverity(nativeLogSeverity());
        int engineMaxNumTokens = maxNumTokens(args);
        String requestedBackend = normalizedBackend(args.backend());
        try (LiteRTEngineLease<Engine> lease =
                     acquireCachedEngine(engineCache, modelPath, requestedBackend, engineMaxNumTokens)) {
            int decodeWarmupTokens = prewarmTokenCount();
            int targetWarmupIterations = prewarmIterationCount();
            int decodeWarmupIterations = 0;
            if (decodeWarmupTokens > 0) {
                decodeWarmupIterations = lease.remainingPrewarmIterations(targetWarmupIterations);
                for (int i = 0; i < decodeWarmupIterations; i++) {
                    warmConversation(lease, args, decodeWarmupTokens);
                }
            }
            lease.markPrewarmed(targetWarmupIterations);
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            out.printf("Using official LiteRT-LM JVM daemon for %s (backend=%s, speculativeDecoding=%s, engineMaxNumTokens=%d, warmEngine=%s, decodeWarmupTokens=%d, decodeWarmupIterations=%d, warmupTargetIterations=%d).%n",
                    modelPath.getFileName(),
                    lease.backendName(),
                    lease.speculativeDecoding(),
                    engineMaxNumTokens,
                    lease.reused(),
                    decodeWarmupTokens,
                    decodeWarmupIterations,
                    targetWarmupIterations);
            out.printf("[LiteRT-LM daemon prewarm, Duration: %.2fs]%n", durationMs / 1000.0d);
        }
    }

    private static void warmConversation(
            LiteRTEngineLease<Engine> lease,
            LiteRTRunArguments args,
            int maxWarmupTokens) throws Exception {
        SamplerConfig sampler = new SamplerConfig(
                Math.max(1, args.topK()),
                Math.max(0.0d, Math.min(1.0d, args.topP())),
                Math.max(0.0d, args.temperature()),
                0);
        ConversationConfig conversationConfig = new ConversationConfig(
                null,
                List.of(),
                List.of(),
                sampler,
                false,
                null,
                Map.of());
        try (Conversation conversation = lease.engine().createConversation(conversationConfig)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicBoolean cancelled = new AtomicBoolean(false);
            AtomicReference<Throwable> error = new AtomicReference<>();
            LiteRTTokenStreamLimiter tokenLimiter = new LiteRTTokenStreamLimiter(maxWarmupTokens);
            conversation.sendMessageAsync(Contents.Companion.of(promptForModel(args.prompt())), new MessageCallback() {
                @Override
                public void onMessage(com.google.ai.edge.litertlm.Message message) {
                    if (cancelled.get()) {
                        return;
                    }
                    tokenLimiter.offer(message.toString());
                    if (tokenLimiter.atLimit() && cancelled.compareAndSet(false, true)) {
                        conversation.cancelProcess();
                        done.countDown();
                    }
                }

                @Override
                public void onDone() {
                    done.countDown();
                }

                @Override
                public void onError(Throwable throwable) {
                    if (!cancelled.get()) {
                        error.compareAndSet(null, throwable);
                    }
                    done.countDown();
                }
            }, Map.of());
            if (!done.await(timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                conversation.cancelProcess();
                throw new IllegalStateException("LiteRT-LM prewarm timed out");
            }
            if (error.get() != null && !cancelled.get()) {
                throw new IllegalStateException(error.get());
            }
        }
    }

    private static ConversationConfig daemonKeepAliveConversationConfig() {
        SamplerConfig sampler = new SamplerConfig(1, 1.0d, 0.0d, 0);
        return new ConversationConfig(
                null,
                List.of(),
                List.of(),
                sampler,
                false,
                null,
                Map.of());
    }

    static boolean keepAliveConversationEnabled() {
        return Boolean.parseBoolean(System.getProperty("aqli.litert.fast_run.keepalive_conversation", "false"));
    }

    static int prewarmTokenCount() {
        return Math.max(0, Integer.getInteger("aqli.litert.fast_run.prewarm_tokens", 0));
    }

    static int prewarmIterationCount() {
        return Math.max(1, Integer.getInteger("aqli.litert.fast_run.prewarm_iterations", 3));
    }

    private static int stopDaemon() {
        OptionalInt port = readDaemonPort();
        if (port.isEmpty()) {
            return 0;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port.getAsInt()),
                    daemonConnectTimeoutMillis());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            LiteRTDaemonProtocol.writeString(out, LiteRTDaemonProtocol.STOP_MAGIC);
            out.flush();
            while (true) {
                int channel = in.read();
                if (channel < 0) {
                    break;
                }
                int length = in.readInt();
                if (channel == 0) {
                    break;
                }
                in.skipNBytes(length);
            }
        } catch (Exception ignored) {
        } finally {
            deleteDaemonPortFile();
            removeLaunchctlDaemon();
        }
        return 0;
    }

    private static Path daemonRunDir() {
        return Path.of(System.getProperty("user.home"), ".aqli", "run");
    }

    private static Path daemonPortFile() {
        return daemonRunDir().resolve("litert-fast-daemon.port");
    }

    private static Path daemonLogFile() {
        return Path.of(System.getProperty("user.home"), ".aqli", "logs", "litert-fast-daemon.log");
    }

    private static OptionalInt readDaemonPort() {
        Optional<DaemonInfo> info = readDaemonInfo();
        if (info.isEmpty()) {
            return OptionalInt.empty();
        }
        if (strictDaemonKey() && !daemonKey().equals(info.get().key())) {
            discardStaleDaemon(info.get());
            return OptionalInt.empty();
        }
        if (verifyDaemonPid() && info.get().pid() > 0 && !isDaemonProcessAlive(info.get().pid())) {
            discardStaleDaemon(info.get());
            return OptionalInt.empty();
        }
        return OptionalInt.of(info.get().port());
    }

    private static Optional<DaemonInfo> readDaemonInfo() {
        try {
            Path portFile = daemonPortFile();
            if (!Files.isRegularFile(portFile)) {
                return Optional.empty();
            }
            List<String> lines = Files.readAllLines(portFile, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return Optional.empty();
            }
            int port = Integer.parseInt(lines.get(0).trim());
            long pid = lines.size() > 1 ? Long.parseLong(lines.get(1).trim()) : -1L;
            String key = lines.size() > 2 ? lines.get(2).trim() : "";
            return port > 0 && port <= 65535
                    ? Optional.of(new DaemonInfo(port, pid, key))
                    : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static void writeDaemonPort(int port) throws IOException {
        Files.writeString(daemonPortFile(),
                port + System.lineSeparator()
                        + ProcessHandle.current().pid() + System.lineSeparator()
                        + daemonKey() + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private static String daemonKey() {
        String classPath = System.getProperty("java.class.path", "");
        if (!classPath.isBlank() && classPath.indexOf(File.pathSeparatorChar) < 0) {
            try {
                Path jar = Path.of(classPath);
                if (Files.isRegularFile(jar)) {
                    Path normalized = jar.toAbsolutePath().normalize();
                    return normalized
                            + "|size=" + Files.size(normalized)
                            + "|mtime=" + Files.getLastModifiedTime(normalized).toMillis();
                }
            } catch (Exception ignored) {
                // Fall through to the raw classpath identity below.
            }
        }
        return classPath;
    }

    private static String daemonKeyHash() {
        return Integer.toHexString(daemonKey().hashCode());
    }

    static boolean isDaemonProcessAlive(long pid) {
        if (pid <= 0 || pid == ProcessHandle.current().pid()) {
            return false;
        }
        try {
            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            return handle.isPresent() && handle.get().isAlive();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void daemonLog(String format, Object... args) {
        System.err.printf("%tF %<tT [litert-daemon] %s%n",
                System.currentTimeMillis(),
                String.format(Locale.ROOT, format, args));
        System.err.flush();
    }

    private static void discardStaleDaemon(DaemonInfo info) {
        deleteDaemonPortFile();
        try {
            terminateStaleDaemonProcess(info.pid());
        } catch (Exception ignored) {
        } finally {
            removeLaunchctlDaemon();
        }
    }

    private static void terminateStaleDaemonProcess(long pid) {
        if (!isDaemonProcessAlive(pid)) {
            return;
        }
        try {
            ProcessHandle process = ProcessHandle.of(pid).orElse(null);
            if (process == null || !process.isAlive()) {
                return;
            }
            if (staleDaemonForceKill()) {
                process.destroyForcibly();
            } else {
                process.destroy();
            }
            process.onExit().get(staleDaemonKillWaitMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
        }
    }

    static boolean staleDaemonForceKill() {
        return Boolean.parseBoolean(System.getProperty("aqli.litert.fast_run.stale_daemon_force_kill", "true"));
    }

    static long staleDaemonKillWaitMillis() {
        return Math.max(100L, Long.getLong("aqli.litert.fast_run.stale_daemon_kill_wait_ms", 2000L));
    }

    static boolean strictDaemonKey() {
        return Boolean.parseBoolean(System.getProperty("aqli.litert.fast_run.strict_daemon_key", "true"));
    }

    static boolean verifyDaemonPid() {
        return Boolean.parseBoolean(System.getProperty("aqli.litert.fast_run.verify_daemon_pid", "true"));
    }

    private static void deleteDaemonPortFile() {
        try {
            Files.deleteIfExists(daemonPortFile());
        } catch (IOException ignored) {
        }
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
        return Integer.getInteger("aqli.litert.fast_run.daemon_connect_timeout_ms", 1000);
    }

    private static int daemonStartTimeoutMillis() {
        return Integer.getInteger("aqli.litert.fast_run.daemon_start_timeout_ms", 20_000);
    }

    static long daemonRequestRetryMillis() {
        return Math.max(0L, Long.getLong("aqli.litert.fast_run.daemon_request_retry_ms", 2_000L));
    }

    static long daemonRequestRetrySleepMillis() {
        return Math.max(10L, Long.getLong("aqli.litert.fast_run.daemon_request_retry_sleep_ms", 75L));
    }

    private record DaemonInfo(int port, long pid, String key) {
    }

    static final class RouteResolutionCache {
        private final Map<String, RouteResolution> routes = new HashMap<>();
        private String modelIndexFingerprint = "";

        synchronized Optional<RouteResolution> resolve(LiteRTRunArguments args) {
            if (!daemonRouteCacheEnabled()) {
                return resolveLiteRtLmRoute(args);
            }
            long startNanos = System.nanoTime();
            String currentIndexFingerprint = modelIndexFingerprint();
            if (!currentIndexFingerprint.equals(modelIndexFingerprint)) {
                routes.clear();
                modelIndexFingerprint = currentIndexFingerprint;
                daemonLog("route cache cleared after model index change");
            }
            String key = routeCacheKey(args, currentIndexFingerprint);
            RouteResolution cached = routes.get(key);
            if (cached != null && routeStillAvailable(cached)) {
                daemonLog("route cache hit source=%s artifact=%s path=%s",
                        cached.source(),
                        cached.selectedArtifact(),
                        cached.path().getFileName());
                return Optional.of(cached.withDurationNanos(System.nanoTime() - startNanos));
            }
            if (cached != null) {
                routes.remove(key);
                daemonLog("route cache evicted stale path=%s", cached.path().getFileName());
            }
            Optional<RouteResolution> resolved = resolveLiteRtLmRoute(args);
            resolved.ifPresent(route -> {
                routes.put(key, route);
                daemonLog("route cache stored source=%s artifact=%s path=%s",
                        route.source(),
                        route.selectedArtifact(),
                        route.path().getFileName());
            });
            return resolved;
        }

        synchronized int size() {
            return routes.size();
        }
    }

    private static LiteRTEngineLease<Engine> acquireCachedEngine(
            LiteRTEngineCache<Engine> engineCache,
            Path modelPath,
            String requestedBackend,
            int engineMaxNumTokens) {
        String key = engineCacheKey(modelPath, requestedBackend, engineMaxNumTokens);
        String label = engineCacheLabel(modelPath, requestedBackend, engineMaxNumTokens);
        try {
            LiteRTEngineLease<Engine> lease = engineCache.acquire(key, () -> {
                daemonLog("engine cache miss %s", label);
                LiteRTEngineLease<Engine> created =
                        openEngine(modelPath, requestedBackend, engineMaxNumTokens, false, false);
                attachKeepAliveConversation(created);
                daemonLog("engine cache stored %s resolvedBackend=%s speculativeDecoding=%s",
                        label,
                        created.backendName(),
                        created.speculativeDecoding());
                return created;
            });
            if (lease.reused()) {
                daemonLog("engine cache hit %s", label);
            }
            return lease;
        } catch (Throwable throwable) {
            daemonLog("engine cache open failed %s reason=%s", label, summarize(throwable));
            throw throwable;
        }
    }

    private static void attachKeepAliveConversation(LiteRTEngineLease<Engine> lease) {
        if (!keepAliveConversationEnabled()) {
            return;
        }
        try {
            lease.attachKeepAlive(lease.engine().createConversation(daemonKeepAliveConversationConfig()));
            daemonLog("engine keepalive conversation opened backend=%s", lease.backendName());
        } catch (Throwable throwable) {
            daemonLog("engine keepalive conversation unavailable backend=%s reason=%s",
                    lease.backendName(),
                    summarize(throwable));
        }
    }

    static boolean daemonRouteCacheEnabled() {
        return Boolean.parseBoolean(System.getProperty("aqli.litert.fast_run.daemon_route_cache", "true"));
    }

    private static boolean routeStillAvailable(RouteResolution route) {
        return route != null && route.path() != null && Files.isRegularFile(route.path());
    }

    private static String routeCacheKey(LiteRTRunArguments args, String indexFingerprint) {
        return "model=" + nullToEmpty(args.model())
                + "|modelFile=" + nullToEmpty(args.modelFile())
                + "|cwd=" + Path.of("").toAbsolutePath().normalize()
                + "|home=" + System.getProperty("user.home", "")
                + "|autoEquivalent=" + equivalentLiteRtFastPathEnabled()
                + "|index=" + indexFingerprint;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Path modelIndexPath() {
        return Path.of(System.getProperty("user.home"), ".aqli", "models", "index.json");
    }

    private static String modelIndexFingerprint() {
        Path indexPath = modelIndexPath();
        try {
            Path normalized = indexPath.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return normalized + "|size=" + Files.size(normalized)
                        + "|mtime=" + Files.getLastModifiedTime(normalized).toMillis();
            }
            return normalized + "|missing";
        } catch (Exception ignored) {
            return indexPath.toString() + "|unknown";
        }
    }

    private static String engineCacheKey(Path modelPath, String requestedBackend, int engineMaxNumTokens) {
        return modelPath.toAbsolutePath().normalize()
                + "|" + requestedBackend
                + "|" + engineMaxNumTokens
                + "|" + cacheDir()
                + "|" + System.getProperty("aqli.litert.fast_run.speculative_decoding", "");
    }

    private static String engineCacheLabel(Path modelPath, String requestedBackend, int engineMaxNumTokens) {
        return "model=" + modelPath.getFileName()
                + " backend=" + requestedBackend
                + " engineMaxNumTokens=" + engineMaxNumTokens
                + " cacheHash=" + Integer.toHexString(cacheDir().hashCode())
                + " spec=" + System.getProperty("aqli.litert.fast_run.speculative_decoding", "");
    }

}
