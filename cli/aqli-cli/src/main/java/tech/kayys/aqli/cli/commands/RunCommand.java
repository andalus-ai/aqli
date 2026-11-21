package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.cli.commands.audio.AudioRunStatsRenderer;
import tech.kayys.aqli.cli.commands.audio.TtsCliOptionsValidator;
import tech.kayys.aqli.cli.commands.route.LiteRtRoutePreflightValidator;
import tech.kayys.aqli.cli.commands.route.RouteReportRunOptions;
import tech.kayys.aqli.cli.commands.route.SafetensorCompatibilityPreflightValidator;
import tech.kayys.aqli.cli.commands.route.ExecutionRouteTerminalRenderer;
import tech.kayys.aqli.cli.commands.route.GgufRouteAliasPolicy;
import tech.kayys.aqli.cli.commands.route.SafetensorEngineHintPolicy;
import tech.kayys.aqli.cli.commands.route.GgufCliEngineOptionResolver;
import tech.kayys.aqli.cli.commands.route.GgufCliBackendOptionResolver;
import tech.kayys.aqli.cli.commands.audio.TtsCliOptionsValidator.AudioQualityPreset;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.Quarkus;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Parameters;
import tech.kayys.aqli.cli.spi.run.RunCapabilityRegistry;
import tech.kayys.aqli.cli.AqliCommand;

import tech.kayys.aqli.model.repo.local.ManifestStore;
import tech.kayys.aqli.onnx.runner.OnnxModelDiagnostics;
import tech.kayys.aqli.onnx.runner.tts.moss.MossTtsOnnxRunner;
import tech.kayys.aqli.onnx.runner.tts.moss.model.MossTtsModelLayout;
import tech.kayys.aqli.onnx.runner.tts.moss.diagnostics.MossTtsModelDiagnostics;
import tech.kayys.aqli.onnx.runner.tts.moss.voice.MossTtsVoiceCatalog;
import tech.kayys.aqli.onnx.runner.tts.moss.codec.MossAudioTokenizerModelLayout;
import tech.kayys.aqli.onnx.runner.tts.moss.codec.MossAudioTokenizerModelLocator;
import tech.kayys.aqli.onnx.runner.PaddleOcrVlOnnxPlanner;
import tech.kayys.aqli.onnx.runner.PaddleOcrVlOnnxProbe;
import tech.kayys.aqli.sdk.model.ModelResolver;
import tech.kayys.aqli.sdk.runtime.AqliRuntime;
import tech.kayys.aljabr.spi.model.ModelLoader;
import tech.kayys.aqli.sdk.model.ModelResolution;
import tech.kayys.aqli.sdk.model.HuggingFacePullRequest;
import tech.kayys.aqli.sdk.model.PullProgress;
import tech.kayys.aqli.sdk.exception.SdkException;
import tech.kayys.aqli.safetensor.engine.generation.DirectInferenceEngine;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.inference.InferenceResponse;
import tech.kayys.aqli.spi.inference.StreamingInferenceChunk;
import tech.kayys.aqli.cli.commands.request.RunRequestBaseAssembler;
import tech.kayys.aqli.cli.commands.request.RunInferenceRequestAssembler;
import tech.kayys.aqli.cli.commands.request.RuntimeRoutingRunRequestConfiguration;
import tech.kayys.aqli.cli.commands.request.ModelSourceRunRequestParameterMapper;
import tech.kayys.aqli.cli.commands.request.RuntimeRoutingRunRequestConfiguration;
import tech.kayys.aqli.cli.commands.runtime.RuntimeExecutionRunRequestParameterMapper;
import tech.kayys.aqli.cli.commands.request.RuntimeQuantizationRequestConfiguration;
import tech.kayys.aqli.cli.commands.request.RuntimeProfilingRequestConfiguration;
import tech.kayys.aqli.cli.commands.request.RuntimeResponseCacheRequestConfiguration;
import tech.kayys.aqli.cli.commands.request.RuntimeOutputFormatRequestConfiguration;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.UUID;
import java.util.stream.Stream;
import tech.kayys.aqli.cli.chat.ChatUIRenderer;
import tech.kayys.aqli.cli.commands.audio.AudioMetadataReplayCommandBuilder;
import tech.kayys.aqli.audio.output.LivePcmAudioPlayer;
import tech.kayys.aqli.audio.metadata.AudioMetadataSidecarStore;
import tech.kayys.aqli.media.metadata.ImageMetadataSidecarStore;
import tech.kayys.aqli.audio.output.StreamingAudioOutputSession;
import tech.kayys.aqli.onnx.runner.tts.moss.cache.MossTtsCacheSettings;
import tech.kayys.aqli.onnx.runner.tts.moss.MossTtsOnnxGraphResolver;
import tech.kayys.aqli.onnx.runner.tts.moss.cache.MossTtsOnnxCacheDiagnostics;
import tech.kayys.aqli.onnx.runner.tts.moss.cache.OptimizedCacheDirectoryScanner;
import tech.kayys.aqli.onnx.runner.tts.moss.cache.OptimizedCachePruner;
import tech.kayys.aqli.onnx.runner.tts.moss.cache.MossTtsOptimizedCacheMaintenance;
import tech.kayys.aqli.cli.commands.audio.AudioMetadataReplayParser;
import tech.kayys.aqli.cli.commands.audio.AudioRunRequestParameterMapper;
import tech.kayys.aqli.cli.commands.audio.AudioRunOptions;
import tech.kayys.aqli.cli.commands.audio.AudioReplayRunOptions;
import tech.kayys.aqli.cli.commands.image.ImageRunRequestParameterMapper;
import tech.kayys.aqli.cli.commands.text.TextRunRequestParameterMapper;
import tech.kayys.aqli.cli.commands.audio.AudioReplayOutputPathResolver;
import tech.kayys.aqli.cli.commands.image.ImageMetadataReplayCommandBuilder;
import tech.kayys.aqli.cli.commands.image.ImageGenerationRunOptions;
import tech.kayys.aqli.cli.commands.image.ImageMetadataRunOptions;
import tech.kayys.aqli.cli.commands.image.ImageGenerationRunRequestParameterMapper;
import tech.kayys.aqli.cli.commands.image.VisionInputRunOptions;
import tech.kayys.aqli.cli.commands.ocr.OcrOutputRunOptions;
import tech.kayys.aqli.cli.commands.tts.TtsOperationsRunOptions;
import tech.kayys.aqli.cli.commands.tts.TtsVoiceDiscoveryRunOptions;
import tech.kayys.aqli.cli.commands.plugin.ExternalPluginLoadingRunOptions;
import tech.kayys.aqli.cli.commands.retrieval.RetrievalContextRunOptions;
import tech.kayys.aqli.cli.commands.tools.ToolInvocationRunOptions;
import tech.kayys.aqli.cli.commands.session.ConversationSessionRunOptions;
import tech.kayys.aqli.cli.commands.conversation.SystemPromptRunOptions;
import tech.kayys.aqli.cli.commands.model.FallbackModelRunOptions;
import tech.kayys.aqli.cli.commands.model.ModelRepositoryRunOptions;
import tech.kayys.aqli.cli.commands.model.ModelConversionRunOptions;
import tech.kayys.aqli.cli.commands.runtime.QuantizationRunOptions;
import tech.kayys.aqli.cli.commands.runtime.RuntimeProfileRunOptions;
import tech.kayys.aqli.cli.commands.media.MediaMetadataRunOptions;
import tech.kayys.aqli.cli.commands.diagnostics.OnnxDiagnosticsRunOptions;
import tech.kayys.aqli.cli.commands.replay.ReplayShellCommandFormatter;
import tech.kayys.aqli.cli.commands.stream.RunStreamExecutionCoordinator;
import tech.kayys.aqli.cli.runtime.CliMetalRuntime;
import tech.kayys.aqli.cli.commands.rag.RagRunRequestConfiguration;
import tech.kayys.aqli.cli.commands.tools.ToolRunRequestConfiguration;
import tech.kayys.aqli.cli.util.ExternalModelFamilyPluginScope;
import tech.kayys.aqli.cli.util.PluginAvailabilityChecker;
import tech.kayys.aqli.plugin.kernel.KernelPlatform;
import tech.kayys.aqli.sdk.util.AqliHome;
import tech.kayys.suling.FlacLibraryCheck;
import tech.kayys.suling.audio.FfmpegAudioEncoder;
import tech.kayys.suling.audio.Suling;

/**
 * Run inference command using the AQLI runtime contract.
 * Usage: aqli run --model <model> --prompt <prompt> [--provider
 * litert|llamacpp|gemini] [--stream]
 */
@Dependent
@Unremovable
@Command(name = "run", description = "Run inference using a specified model")
public class RunCommand implements Runnable {
    private Map<String, Object> forcedExecutionRouteMetadata = Map.of();

    @ParentCommand
    AqliCommand parentCommand;

    @Inject
    AqliRuntime sdk;
    @Inject
    PluginAvailabilityChecker pluginChecker;
    @Inject
    ChatUIRenderer uiRenderer;
    @Inject
    DirectInferenceEngine directInferenceEngine;
    @Inject
    Instance<MossTtsOnnxRunner> mossTtsWarmupRunner;

    @Inject
    RunCapabilityRegistry runCapabilityRegistry;

    @picocli.CommandLine.Spec
    private picocli.CommandLine.Model.CommandSpec commandSpec;

    private tech.kayys.aqli.cli.commands.text.TextRunOptions textRunOptions;
    private AudioRunOptions audioRunOptions;
    private AudioReplayRunOptions audioReplayOptions;
    private ImageGenerationRunOptions imageGenerationOptions;
    private VisionInputRunOptions visionInputOptions;
    private OcrOutputRunOptions ocrOutputOptions;
    private TtsOperationsRunOptions ttsOperationsOptions;
    private TtsVoiceDiscoveryRunOptions ttsVoiceDiscoveryOptions;
    private ExternalPluginLoadingRunOptions externalPluginLoadingOptions;
    private RetrievalContextRunOptions retrievalContextOptions;
    private ToolInvocationRunOptions toolInvocationOptions;
    private ConversationSessionRunOptions conversationSessionOptions;
    private SystemPromptRunOptions systemPromptOptions;
    private FallbackModelRunOptions fallbackModelOptions;
    private ModelRepositoryRunOptions modelRepositoryOptions;
    private ModelConversionRunOptions modelConversionOptions;
    private QuantizationRunOptions quantizationOptions;
    private RuntimeProfileRunOptions runtimeProfileOptions;
    private MediaMetadataRunOptions mediaMetadataOptions;
    private ImageMetadataRunOptions imageMetadataOptions;
    private RouteReportRunOptions routeReportOptions;
    private tech.kayys.aqli.cli.commands.engine.EngineSelectionRunOptions engineSelectionOptions;
    private OnnxDiagnosticsRunOptions onnxDiagnosticsOptions;

    @Option(names = { "-m", "--model" }, description = "Model ID for repository resolution (e.g., huggingface ID)")
    public String modelId;

    @Option(names = { "--no-banner" }, description = "Suppress header and footer output")
    public boolean noBanner;

    @Option(names = { "--no-info" }, description = "Suppress performance statistic output")
    public boolean noInfo;

    @Option(names = { "--only-text" }, description = "Only print generated text, equivalent to --no-banner --no-info")
    public boolean onlyText;

    @Option(names = { "--raw" }, description = "Raw response from inference calculation before json mapping")
    public boolean raw;

    @Option(names = { "--debug-prompt" }, description = "Print raw formatted prompt before dispatch, and raw vs decoded output")
    public boolean debugPrompt;

    @Option(names = { "--modelFile" }, description = "Path to a local model file (.gguf, .tflite, .task, .litertlm)")
    public String modelFile;

    @Option(names = { "--modelDir" }, description = "Path to a local model directory (Safetensors)")
    public String modelDir;

    @Option(names = { "-p", "--prompt" }, description = "Input prompt")
    public String prompt;

    @Option(names = {
            "--provider" }, description = "Provider: native, litert, llamacpp, safetensor, libtorch(experimental), gemini, openai, anthropic, cerebras. Omit for auto-detection.", arity = "0..1", fallbackValue = "")
    String providerId;

    @Option(names = { "--runner" },
            description = "Runner route: auto, safetensor, gguf, litert, or hybrid. Omit for auto-detection.",
            arity = "0..1",
            fallbackValue = "auto")
    String runner;

    @Option(names = { "-s", "--stream" }, description = "Stream output", defaultValue = "true")
    boolean stream;

    @Option(names = { "--json" }, description = "Enable JSON output mode", defaultValue = "false")
    boolean jsonMode;

    @Option(names = {
            "--enable-json" }, description = "Emit OpenAI-compatible SSE JSON for streamed chunks", defaultValue = "false")
    boolean enableJsonSse;

    @Option(names = { "--no-cache" }, description = "Bypass response cache")
    boolean noCache;

    @Option(names = {
            "--model-path" }, description = "Path to a custom model file (bypasses repository lookup, prefer --modelFile)")
    String modelPath;

    String convertMode;

    String ggufOutType;

    @Option(names = { "--force" }, description = "Force re-download and replace existing files", defaultValue = "false")
    boolean force;

    @Option(names = { "--format" }, description = "Preferred model format (e.g., safetensors, gguf, onnx)")
    String format;

    @Option(names = { "--pipeline", "--feature" }, description = "Explicit feature pipeline id for out-of-core model orchestration")
    String featurePipelineId;

    @Option(names = { "--direct" }, description = "Force use of native Safetensor direct engine", defaultValue = "false")
    boolean direct;

    String ggufQuant;

    @Option(names = { "--gguf" }, description = "Force GGUF conversion and usage", defaultValue = "false")
    boolean forceGguf;


    String quantizeStrategy;
    int quantizeBits;

    // Stable Diffusion specific parameters
    @Option(names = { "--seed" }, description = "Random seed for image generation and TTS (MOSS TTS default: 1234)")
    Long seed;

    @Option(names = { "--output", "-o" }, description = "Output image file path (default: output.png)")
    String outputPath;

    @Option(names = { "--onnx-variant", "--onnx-precision" }, description = "Preferred ONNX graph variant for multi-graph models (auto, q4, q8, full, uint8)")
    String onnxVariant;

    @Option(names = { "--ext" }, description = "Explicit output file extension (mp3, wav, flac, png, jpg, mp4). Overrides extension in --output if provided.")
    String extension;

    String quantizeKv;

    @Option(names = { "--plugin" }, description = "Explicit plugin/engine to use (e.g. llamacpp, java, bnb)")
    public String pluginId;

    boolean runtimeProfile;

    boolean preferAlternateRuntime;

    @Parameters(arity = "0..*", hidden = true)
    List<String> positionalArgs = new ArrayList<>();

    @Override
    public void run() {
        preferAlternateRuntime = engineSelectionOptions.preferAlternateRuntime();

        runtimeProfileOptions = runCapabilityRegistry.snapshot(
                "runtime-profile",
                commandSpec.commandLine().getParseResult(),
                RuntimeProfileRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The runtime-profile run capability is not registered or does not provide RuntimeProfileRunOptions"));
        runtimeProfile = runtimeProfileOptions.enabled();

        quantizationOptions = runCapabilityRegistry.snapshot(
                "quantization",
                commandSpec.commandLine().getParseResult(),
                QuantizationRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The quantization run capability is not registered or does not provide QuantizationRunOptions"));
        quantizeStrategy = quantizationOptions.strategy();
        quantizeBits = quantizationOptions.bits();
        quantizeKv = quantizationOptions.kvCacheMode();

        textRunOptions = runCapabilityRegistry.snapshot(
                "text",
                commandSpec.commandLine().getParseResult(),
                tech.kayys.aqli.cli.commands.text.TextRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'text' run capability is not registered or does not provide TextRunOptions"));

        audioRunOptions = runCapabilityRegistry.snapshot(
                "audio",
                commandSpec.commandLine().getParseResult(),
                AudioRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'audio' run capability is not registered or does not provide AudioRunOptions"));

        audioReplayOptions = runCapabilityRegistry.snapshot(
                "audio-replay",
                commandSpec.commandLine().getParseResult(),
                AudioReplayRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'audio-replay' run capability is not registered or does not provide AudioReplayRunOptions"));

        imageGenerationOptions = runCapabilityRegistry.snapshot(
                "image-generation",
                commandSpec.commandLine().getParseResult(),
                ImageGenerationRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'image-generation' run capability is not registered or does not provide ImageGenerationRunOptions"));

        visionInputOptions = runCapabilityRegistry.snapshot(
                "vision-input",
                commandSpec.commandLine().getParseResult(),
                VisionInputRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'vision-input' run capability is not registered or does not provide VisionInputRunOptions"));

        ocrOutputOptions = runCapabilityRegistry.snapshot(
                "ocr-output",
                commandSpec.commandLine().getParseResult(),
                OcrOutputRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'ocr-output' run capability is not registered or does not provide OcrOutputRunOptions"));

        ttsOperationsOptions = runCapabilityRegistry.snapshot(
                "tts-operations",
                commandSpec.commandLine().getParseResult(),
                TtsOperationsRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'tts-operations' run capability is not registered or does not provide TtsOperationsRunOptions"));

        ttsVoiceDiscoveryOptions = runCapabilityRegistry.snapshot(
                "tts-voice-discovery",
                commandSpec.commandLine().getParseResult(),
                TtsVoiceDiscoveryRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'tts-voice-discovery' run capability is not registered or does not provide TtsVoiceDiscoveryRunOptions"));

        externalPluginLoadingOptions = runCapabilityRegistry.snapshot(
                "external-plugin-loading",
                commandSpec.commandLine().getParseResult(),
                ExternalPluginLoadingRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'external-plugin-loading' run capability is not registered or does not provide ExternalPluginLoadingRunOptions"));

        retrievalContextOptions = runCapabilityRegistry.snapshot(
                "retrieval-context",
                commandSpec.commandLine().getParseResult(),
                RetrievalContextRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'retrieval-context' run capability is not registered or does not provide RetrievalContextRunOptions"));

        fallbackModelOptions = runCapabilityRegistry.snapshot(
                "model-fallback",
                commandSpec.commandLine().getParseResult(),
                FallbackModelRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The model-fallback run capability is not registered or does not provide FallbackModelRunOptions"));

        modelRepositoryOptions = runCapabilityRegistry.snapshot(
                "model-repository",
                commandSpec.commandLine().getParseResult(),
                ModelRepositoryRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'model-repository' run capability is not registered or does not provide ModelRepositoryRunOptions"));

        modelConversionOptions = runCapabilityRegistry.snapshot(
                "model-conversion",
                commandSpec.commandLine().getParseResult(),
                ModelConversionRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'model-conversion' run capability is not registered or does not provide ModelConversionRunOptions"));
        convertMode = modelConversionOptions.convertMode();
        ggufOutType = modelConversionOptions.ggufOutType();
        ggufQuant = modelConversionOptions.ggufQuant();

        systemPromptOptions = runCapabilityRegistry.snapshot(
                "system-prompt",
                commandSpec.commandLine().getParseResult(),
                SystemPromptRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'system-prompt' run capability is not registered or does not provide SystemPromptRunOptions"));

        conversationSessionOptions = runCapabilityRegistry.snapshot(
                "conversation-session",
                commandSpec.commandLine().getParseResult(),
                ConversationSessionRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'conversation-session' run capability is not registered or does not provide ConversationSessionRunOptions"));

        toolInvocationOptions = runCapabilityRegistry.snapshot(
                "tool-invocation",
                commandSpec.commandLine().getParseResult(),
                ToolInvocationRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'tool-invocation' run capability is not registered or does not provide ToolInvocationRunOptions"));

        onnxDiagnosticsOptions = runCapabilityRegistry.snapshot(
                "onnx-diagnostics",
                commandSpec.commandLine().getParseResult(),
                OnnxDiagnosticsRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'onnx-diagnostics' run capability is not registered or does not provide OnnxDiagnosticsRunOptions"));

        mediaMetadataOptions = runCapabilityRegistry.snapshot(
                "media-metadata",
                commandSpec.commandLine().getParseResult(),
                MediaMetadataRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'media-metadata' run capability is not registered or does not provide MediaMetadataRunOptions"));

        imageMetadataOptions = runCapabilityRegistry.snapshot(
                "image-metadata",
                commandSpec.commandLine().getParseResult(),
                ImageMetadataRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'image-metadata' run capability is not registered or does not provide ImageMetadataRunOptions"));

        routeReportOptions = runCapabilityRegistry.snapshot(
                "route-report",
                commandSpec.commandLine().getParseResult(),
                RouteReportRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'route-report' run capability is not registered or does not provide RouteReportRunOptions"));

        engineSelectionOptions = runCapabilityRegistry.snapshot(
                "engine-selection",
                commandSpec.commandLine().getParseResult(),
                tech.kayys.aqli.cli.commands.engine.EngineSelectionRunOptions.class)
                .orElseThrow(() -> new IllegalStateException(
                        "The 'engine-selection' run capability is not registered or does not provide EngineSelectionRunOptions"));

        if (onlyText) {
            noBanner = true;
            noInfo = true;
        }
        
        try (ExternalModelFamilyPluginScope ignored =
                ExternalModelFamilyPluginScope.attach(
                        externalPluginLoadingOptions.classpathEntries(),
                        externalPluginLoadingOptions.pluginDirectoryEntries(),
                        RunCommand.class,
                        pluginChecker)) {
            if (parentCommand != null) {
                parentCommand.bootstrapInheritedEnvironment();
            }

            if (!repairOrRejectPositionalArgs()) {
                requestProcessExit(1);
                return;
            }
            if (mediaMetadataOptions.audioRefreshPath() != null && !mediaMetadataOptions.audioRefreshPath().isBlank()) {
                requestProcessExit(refreshAudioMetadataSidecar() ? 0 : 1);
                return;
            }
            if (mediaMetadataOptions.audioVerifyPath() != null && !mediaMetadataOptions.audioVerifyPath().isBlank()) {
                requestProcessExit(verifyAudioMetadataSidecar() ? 0 : 1);
                return;
            }
            if (imageMetadataOptions.imageRefreshPath() != null && !imageMetadataOptions.imageRefreshPath().isBlank()) {
                requestProcessExit(refreshImageMetadataSidecar() ? 0 : 1);
                return;
            }
            if (imageMetadataOptions.imageVerifyPath() != null && !imageMetadataOptions.imageVerifyPath().isBlank()) {
                requestProcessExit(verifyImageMetadataSidecar() ? 0 : 1);
                return;
            }
            if (!loadAudioMetadataReplayIfRequested()) {
                requestProcessExit(1);
                return;
            }
            if (!validateTtsAudioCliOptions()) {
                requestProcessExit(1);
                return;
            }

            providerId = normalizeRequestedProvider(providerId);
            boolean providerExplicit = providerId != null && !providerId.isBlank();
            boolean requestedProviderExplicit = providerExplicit;
            String requestedProviderId = providerId;
            String requestedFormat = format;
            RunnerRoutePolicy.Selection runnerSelection = RunnerRoutePolicy.select(
                    runner,
                    providerId,
                    format,
                    providerExplicit,
                    preferAlternateRuntime,
                    forceGguf);
            if (!runnerSelection.valid()) {
                System.err.println("Error: " + runnerSelection.error());
                requestProcessExit(1);
                return;
            }
            RunnerRouteReport runnerRouteReport = RunnerRouteReport.from(
                    runner,
                    requestedProviderId,
                    requestedProviderExplicit,
                    requestedFormat,
                    runnerSelection);
            providerId = runnerSelection.providerId();
            format = runnerSelection.format();
            preferAlternateRuntime = runnerSelection.preferAlternateRuntime();
            forceGguf = runnerSelection.forceGguf();
            providerExplicit = providerId != null && !providerId.isBlank();
            boolean preferOnnxResolution = ModelFormatResolver.shouldPreferOnnxResolution(modelId, format, providerId);
            if (preferOnnxResolution) {
                if (!providerExplicit) {
                    providerId = "onnx";
                }
                if (format == null || format.isBlank()) {
                    format = "onnx";
                }
            }

            if (!routeReportOptions.enabled() && tryCachedGemma4MobileQatLiteRtRun(requestedProviderExplicit, requestedProviderId)) {
                return;
            }

            if (!routeReportOptions.enabled() && explicitLlamaCppEngine() && !prepareLlamaCppFromLocalSafetensor()) {
                requestProcessExit(1);
                return;
            }

            if (!routeReportOptions.enabled() && tryStandaloneGgufRun()) {
                return;
            }

            // Check plugin availability first
            if (!pluginChecker.hasProviders() && !pluginChecker.hasRunnerPlugins()) {
                System.err.println(pluginChecker.getNoPluginsError());
                System.exit(1);
                return;
            }

            // Auto-detect and display kernel platform
            KernelPlatform detectedPlatform;
            try {
                detectedPlatform = sdk.getPlatformInfo();
            } catch (Throwable t) {
                System.err.println("CRITICAL: Platform detection failed: " + t.getMessage());
                return;
            }
            if (!routeReportOptions.enabled() && CliMetalRuntime.isMetal(detectedPlatform)) {
                ensureMetalRuntimeInitialized();
            }

            if (!routeReportOptions.enabled() && parentCommand != null && parentCommand.verbose) {
                System.out.println(ChatUIRenderer.CYAN + "Platform: " + detectedPlatform.getDisplayName() + ChatUIRenderer.RESET);
                if (detectedPlatform.isCpu()) {
                    System.out.println(ChatUIRenderer.YELLOW + "⚠️  Running on CPU (GPU acceleration not available)" + ChatUIRenderer.RESET);
                } else {
                    boolean isMetalPlatform = CliMetalRuntime.isMetal(detectedPlatform);
                    if (isMetalPlatform && !isMetalNativeRuntimeActive()) {
                        System.out.println(ChatUIRenderer.YELLOW
                                + "⚠️  Metal selected but native runtime is not active (CPU fallback likely)"
                                + ChatUIRenderer.RESET);
                    } else {
                        System.out.println(ChatUIRenderer.GREEN + "✓ GPU acceleration enabled" + ChatUIRenderer.RESET);
                    }
                }
                printMetalRuntimeStatus();
                System.out.println();
            }

            boolean isMetalPlatform = CliMetalRuntime.isMetal(detectedPlatform);
            if (!routeReportOptions.enabled()
                    && isMetalPlatform
                    && !isMetalNativeRuntimeActive()
                    && !allowCpuFallbackWhenMetalRequested()) {
                System.err.println("Error: Metal platform selected but native Metal runtime is not active.");
                System.err.println("Refusing CPU fallback for this run so performance behavior stays explicit.");
                System.err.println("Set AQLI_ALLOW_CPU_FALLBACK=true to override.");
                System.exit(1);
                return;
            }

            long startTime = System.currentTimeMillis();

            // Check if specific provider is requested but not available
            if (providerExplicit) {
                if (!pluginChecker.hasProvider(providerId)) {
                    System.err.println(pluginChecker.getProviderNotFoundError(providerId));
                    System.exit(1);
                    return;
                }
            }

            boolean customModelPathUsed = false;
            String finalLocalPath = null;
            String requestedModelRef = modelId;
            uiRenderer.setJsonMode(enableJsonSse || jsonMode || ttsVoiceDiscoveryOptions.listVoicesJson() || ocrOutputOptions.structuredJson() || routeReportOptions.enabled());
            if (!quietRouteResolutionOutput()) {
                uiRenderer.printBanner();
            }

            if (isMcpProvider()) {
                if (!quietRouteResolutionOutput()) {
                    System.out.println("MCP provider selected; skipping local model lookup.");
                }
            } else {
                ExplicitModelInputResolver.Result explicitModelInput =
                        ExplicitModelInputResolver.resolve(
                                new ExplicitModelInputResolver.Options(
                                        modelFile,
                                        modelDir,
                                        modelPath,
                                        modelRepositoryOptions.importModel(),
                                        modelRepositoryOptions.copyModel(),
                                        providerExplicit,
                                        providerId,
                                        format),
                                sdk);
                if (!explicitModelInput.successful()) {
                    System.err.println(explicitModelInput.error());
                    return;
                }
                if (explicitModelInput.resolved()) {
                    modelId = explicitModelInput.modelId();
                    finalLocalPath = explicitModelInput.localPath();
                    customModelPathUsed = true;
                    providerId = explicitModelInput.providerId();
                    format = explicitModelInput.format();
                    if (!quietRouteResolutionOutput()) {
                        explicitModelInput.messages().forEach(System.out::println);
                    }
                }
            }

            if (!customModelPathUsed && modelId != null && !modelId.isBlank()) {
                LocalModelIndexInputResolver.Result indexedModel =
                        LocalModelIndexInputResolver.resolve(
                                new LocalModelIndexInputResolver.Options(
                                        modelId,
                                        format,
                                        providerId,
                                        providerExplicit,
                                        quietRouteResolutionOutput(),
                                        jsonMode,
                                        ocrOutputOptions.structuredJson()),
                                LocalModelIndexMetadataInference::providerFor,
                                LocalModelIndexMetadataInference::formatFor);
                if (indexedModel.disabled()) {
                    indexedModel.messages().forEach(System.err::println);
                    return;
                }
                if (indexedModel.resolved()) {
                    modelId = indexedModel.modelId();
                    finalLocalPath = indexedModel.localPath();
                    customModelPathUsed = true;
                    providerId = indexedModel.providerId();
                    format = indexedModel.format();
                    indexedModel.messages().forEach(System.out::println);
                }
            }

            boolean routeReportRepositoryResolutionSkipped = false;
            if (!customModelPathUsed && (modelFile == null || modelFile.isBlank())
                    && (modelDir == null || modelDir.isBlank())
                    && (modelPath == null || modelPath.isEmpty())) {
                if (!shouldAllowRepositoryResolutionDuringRouteReport()) {
                    routeReportRepositoryResolutionSkipped = true;
                } else {                    String preparationProviderId = providerId;                    RunModelPreparationResolver.Result preparation =
                            RunModelPreparationResolver.resolve(
                                    new RunModelPreparationResolver.Options(
                                            modelId, requestedModelRef, format, providerId, pluginId,
                                            ggufQuant, ggufOutType, forceGguf, providerExplicit,
                                            quietRouteResolutionOutput(), fallbackModelOptions.modelIds()),
                                    sdk,
                                    reference -> autoPullHfRepositoryForRunIfNeeded(
                                            reference.modelId(), reference.preferredFormat()),
                                    LocalModelIndexMetadataInference::providerFor,
                                    LocalModelIndexMetadataInference::formatFor,
                                    path -> SafetensorCompatibilityPreflightValidator.detect(preparationProviderId, path),
                                    (reference, selectedProvider) ->
                                            ModelSourceRoutingPolicy.requiresLocalHfRepositoryResolution(
                                                    reference, selectedProvider, isMcpProvider()),
                                    progress -> {
                                        if (quietRouteResolutionOutput()) {
                                            return;
                                        }
                                        if (progress.getTotal() > 0) {
                                            System.out.printf("\r%s %s %3d%% (%d/%d MB)",
                                                    ChatUIRenderer.CYAN + progress.getStatus() + ChatUIRenderer.RESET,
                                                    progress.getProgressBar(20), progress.getPercentComplete(),
                                                    progress.getCompleted() / 1024 / 1024,
                                                    progress.getTotal() / 1024 / 1024);
                                        } else {
                                            System.out.print("\r" + ChatUIRenderer.CYAN
                                                    + progress.getStatus() + ChatUIRenderer.RESET);
                                        }
                                    });
                    if (!quietRouteResolutionOutput()) {
                        System.out.println();
                        preparation.notices().forEach(System.out::println);
                    }
                    if (preparation.error() != null) {
                        System.err.println("\nError: " + preparation.error());
                        return;
                    }
                    if (preparation.stopExecution()) {
                        return;
                    }
                    modelId = preparation.modelId();
                    finalLocalPath = preparation.localPath();
                    customModelPathUsed = preparation.customModelPathUsed();
                    providerId = preparation.providerId();
                    format = preparation.format();
                }
            }

            if (!quietRouteResolutionOutput()) {
                tech.kayys.aqli.cli.util.QuantSuggestionDetector.suggestIfNeeded(
                        modelId, finalLocalPath, quantizeStrategy, false);
            }

            ProviderLocalPathResolver.Result providerLocalPathResolution =
                    ProviderLocalPathResolver.resolve(
                            sdk,
                            providerId,
                            modelId,
                            finalLocalPath,
                            SafetensorCompatibilityPreflightValidator::isSafetensorCheckpointDir,
                            SafetensorCompatibilityPreflightValidator::isSafetensorWeightFile,
                            System.out::println,
                            System.err::println);
            if (!providerLocalPathResolution.ok()) {
                return;
            }
            finalLocalPath = providerLocalPathResolution.localPath();
            CliLocalModelResolver localModelResolver = new CliLocalModelResolver();
            AlternateRuntimeSelectionCoordinator.Result alternateRuntimeSelection =
                    AlternateRuntimeSelectionCoordinator.select(
                            new AlternateRuntimeSelectionCoordinator.Options(
                                    providerId, modelId, finalLocalPath, format,
                                    providerExplicit, preferAlternateRuntime, runnerRouteReport),
                            localModelResolver,
                            this::isProviderActive,
                            this::isRuntimeRouteActive);
            if (!quietRouteResolutionOutput()) {
                alternateRuntimeSelection.notices().forEach(System.out::println);
            }
            providerId = alternateRuntimeSelection.providerId();
            modelId = alternateRuntimeSelection.modelId();
            finalLocalPath = alternateRuntimeSelection.localPath();
            format = alternateRuntimeSelection.format();
            runnerRouteReport = alternateRuntimeSelection.routeReport();
            DirectSafetensorRoutePolicy.RouteValidation gemma3RouteValidation =
                    RunRouteValidationCoordinator.validateExecutionRoute(providerId, modelId, finalLocalPath);
            if (!gemma3RouteValidation.allowed()) {
                gemma3RouteValidation.messages().forEach(System.err::println);
                return;
            }

            List<String> normalizedInputImages = normalizedInputImagePaths();
            if (routeReportOptions.enabled()) {
                RunRouteValidationCoordinator.RouteReportResult routeReportResult =
                        RunRouteValidationCoordinator.prepareRouteReport(
                                runnerRouteReport,
                                requestedModelRef,
                                modelId,
                                finalLocalPath,
                                routeExecutionProviderId(providerId, finalLocalPath),
                                format,
                                effectiveDirect(finalLocalPath),
                                routeReportOptions.allowPull(),
                                routeReportRepositoryResolutionSkipped,
                                routeReportOptions.requireLocal(),
                                !normalizedInputImages.isEmpty(),
                                visionInputOptions.ocrMode(),
                                report -> RouteBenchmarkProfileCoordinator.applyCachedProfile(
                                        report, requestedModelRef, modelId, finalLocalPath));
                printJsonPayload(routeReportResult.payload());
                requestProcessExit(routeReportResult.exitCode());
                return;
            }

            if (ttsVoiceDiscoveryOptions.listVoices() || ttsVoiceDiscoveryOptions.listVoicesJson()) {
                requestProcessExit(printTtsVoices(modelId, finalLocalPath));
                return;
            }

            if (ttsOperationsOptions.diagnostics()) {
                requestProcessExit(printTtsDiagnostics(modelId, finalLocalPath));
                return;
            }

            if (ttsOperationsOptions.warmup()) {
                if (!ensureMossTtsCodecAvailableIfNeeded(modelId, finalLocalPath)) {
                    requestProcessExit(1);
                    return;
                }
                requestProcessExit(warmupMossTts(modelId, finalLocalPath));
                return;
            }

            if (ttsOperationsOptions.cachePrune() || ttsOperationsOptions.cachePruneApply()) {
                if (!ensureMossTtsCodecAvailableIfNeeded(modelId, finalLocalPath)) {
                    requestProcessExit(1);
                    return;
                }
                requestProcessExit(pruneMossTtsOptimizedCache(modelId, finalLocalPath));
                return;
            }

            if (onnxDiagnosticsOptions.diagnostics()) {
                int diagnosticsExitCode =
                        printOnnxDiagnostics(requestedModelRef != null ? requestedModelRef : modelId, finalLocalPath);
                if (onnxDiagnosticsOptions.visionProbe() || onnxDiagnosticsOptions.pipelineProbe()) {
                    diagnosticsExitCode = Math.max(diagnosticsExitCode,
                            printOnnxVisionProbe(
                                    requestedModelRef != null ? requestedModelRef : modelId,
                                    finalLocalPath,
                                    onnxDiagnosticsOptions.pipelineProbe()));
                }
                requestProcessExit(diagnosticsExitCode);
                return;
            }

            if (onnxDiagnosticsOptions.visionProbe() || onnxDiagnosticsOptions.pipelineProbe()) {
                requestProcessExit(printOnnxVisionProbe(
                        requestedModelRef != null ? requestedModelRef : modelId,
                        finalLocalPath,
                        onnxDiagnosticsOptions.pipelineProbe()));
                return;
            }

            if (!validateInputImages(normalizedInputImages)) {
                requestProcessExit(1);
                return;
            }
            if ((visionInputOptions.ocrMode() || !normalizedInputImages.isEmpty()) && (prompt == null || prompt.isBlank())) {
                prompt = "Extract all text from the image.";
            }
            boolean structuredOcrOutput =
                    (jsonMode
                            || ocrOutputOptions.structuredJson()
                            || (ocrOutputOptions.jsonOutputPath() != null && !ocrOutputOptions.jsonOutputPath().isBlank())
                            || (ocrOutputOptions.overlayOutputPath() != null && !ocrOutputOptions.overlayOutputPath().isBlank()))
                            && (visionInputOptions.ocrMode() || !normalizedInputImages.isEmpty());
            boolean effectiveStream = stream && !structuredOcrOutput;

            boolean promptlessExtensionRun = allowsPromptlessExtensionRun(modelId, finalLocalPath);
            if ((prompt == null || prompt.isBlank()) && !promptlessExtensionRun) {
                System.err.println("Error: --prompt is required for inference.");
                System.err.println("Use --list-voices, --voices-json, --tts-diagnostics, --tts-warmup, --tts-cache-prune, or --onnx-diagnostics to inspect, warm, or maintain without generating output.");
                System.err.println("For OCR/vision models, pass --image /path/to/image.png or --ocr with an image.");
                requestProcessExit(1);
                return;
            }
            if ((prompt == null || prompt.isBlank()) && promptlessExtensionRun) {
                prompt = "";
            }

            if (tryStandaloneLiteRtRun(finalLocalPath)) {
                return;
            }

            if (tryStandaloneGgufRun(true)) {
                return;
            }

            if (!ensureMossTtsCodecAvailableIfNeeded(modelId, finalLocalPath)) {
                requestProcessExit(1);
                return;
            }

            String executionProviderId = routeExecutionProviderId(providerId, finalLocalPath);
            RunnerRouteReport effectiveRunnerRouteReport =
                    runnerRouteReport.withEffectiveRoute(executionProviderId, format);
            forcedExecutionRouteMetadata = effectiveRunnerRouteReport.toMetadata();

            if (effectiveDirect(finalLocalPath)) {
                providerId = "safetensor";
                
                executionProviderId = "safetensor";
                effectiveRunnerRouteReport = effectiveRunnerRouteReport.withEffectiveRoute(executionProviderId, format);
                forcedExecutionRouteMetadata = effectiveRunnerRouteReport.toMetadata();
            } else if (executionProviderId != null && !executionProviderId.isEmpty()) {
                
            }
            effectiveRunnerRouteReport = RouteBenchmarkProfileCoordinator.applyCachedProfile(
                    effectiveRunnerRouteReport,
                    requestedModelRef,
                    modelId,
                    finalLocalPath);
            forcedExecutionRouteMetadata = effectiveRunnerRouteReport.toMetadata();

            String litertPreflightFailure = LiteRtRoutePreflightValidator.detectUnsupported(executionProviderId, finalLocalPath);
            if (litertPreflightFailure != null) {
                if (!enableJsonSse) {
                    uiRenderer.printModelInfo(modelId, providerId, format, null, false);
                    printQuantizationInfo();
                }
                System.err.println(litertPreflightFailure);
                requestProcessExit();
                return;
            }

            DirectSafetensorRoutePolicy.RouteValidation gemma4UnifiedMultimodalRouteValidation =
                    DirectSafetensorRoutePolicy.validateGemma4UnifiedMultimodalExecutionRoute(
                            executionProviderId,
                            modelId,
                            finalLocalPath,
                            !normalizedInputImages.isEmpty(),
                            visionInputOptions.ocrMode());
            if (!gemma4UnifiedMultimodalRouteValidation.allowed()) {
                if (!enableJsonSse) {
                    uiRenderer.printModelInfo(modelId, providerId, format, null, false);
                    printQuantizationInfo();
                }
                gemma4UnifiedMultimodalRouteValidation.messages().forEach(System.err::println);
                requestProcessExit(1);
                return;
            }

            DirectSafetensorRoutePolicy.RouteValidation gemma4UnifiedRouteValidation =
                    DirectSafetensorRoutePolicy.validateGemma4UnifiedExecutionRoute(
                            executionProviderId, modelId, finalLocalPath);
            if (!gemma4UnifiedRouteValidation.allowed()) {
                if (!enableJsonSse) {
                    uiRenderer.printModelInfo(modelId, providerId, format, null, false);
                    printQuantizationInfo();
                }
                gemma4UnifiedRouteValidation.messages().forEach(System.err::println);
                requestProcessExit(1);
                return;
            }
            printCompatibilityHintBeforeInference();

            String requestModelTarget = shouldUseLocalModelPath(executionProviderId, finalLocalPath)
                    ? finalLocalPath
                    : modelId;
            List<tech.kayys.aqli.spi.tool.ToolDefinition> requestedTools =
                    ToolRunRequestConfiguration.loadTools(toolInvocationOptions.toolFiles(), toolInvocationOptions.mcpTools());
            Object requestedToolChoice = ToolRunRequestConfiguration.parseToolChoice(toolInvocationOptions.toolChoice());
            RagRunRequestConfiguration.RagContext ragContext =
                    RagRunRequestConfiguration.load(retrievalContextOptions.contexts(), retrievalContextOptions.files(), retrievalContextOptions.maxChars(), prompt);

            InferenceRequest.Builder requestBuilder = RunInferenceRequestFactory.createBase(
                    new RunInferenceRequestFactory.Options(
                            requestModelTarget,
                            prompt,
                            textRunOptions.temperature(),
                            textRunOptions.topP(),
                            textRunOptions.topK(),
                            textRunOptions.repeatPenalty(),
                            jsonMode,
                            textRunOptions.maxTokens(),
                            pluginId,
                            effectiveStream,
                            conversationSessionOptions.sessionId(),
                            textRunOptions.mirostat(),
                            textRunOptions.grammar(),
                            noCache));

            ModelSourceRunRequestParameterMapper.apply(
                    requestBuilder,
                    new ModelSourceRunRequestParameterMapper.Options(
                            customModelPathUsed,
                            finalLocalPath,
                            requestedModelRef));
            TextRunRequestParameterMapper.apply(requestBuilder, textRunOptions);

            RuntimeExecutionRunRequestParameterMapper.apply(
                    requestBuilder,
                    new RuntimeExecutionRunRequestParameterMapper.Options(
                            quantizeStrategy,
                            quantizeBits,
                            quantizeKv,
                            runtimeProfile));
            RuntimeQuantizationRequestConfiguration.apply(
                    requestBuilder,
                    quantizeStrategy,
                    quantizeBits,
                    quantizeKv);
            RuntimeProfilingRequestConfiguration.apply(requestBuilder, runtimeProfile);

            boolean libtorchSafetensorCliBridge = "libtorch".equalsIgnoreCase(providerId)
                    && "safetensor".equalsIgnoreCase(executionProviderId);
            String ggufEngineHint = GgufCliEngineOptionResolver.resolve(engineSelectionOptions.benchmark(), engineSelectionOptions.javaNative(), engineSelectionOptions.llamaCpp(), engineSelectionOptions.engine());
            RuntimeRoutingRunRequestConfiguration.apply(
                    requestBuilder,
                    new RuntimeRoutingRunRequestConfiguration.Options(
                            effectiveRunnerRouteReport.toMetadata(),
                            runner != null && !runner.isBlank(),
                            effectiveRunnerRouteReport.normalizedRunner(),
                            ggufEngineHint,
                            null,
                            executionProviderId,
                            libtorchSafetensorCliBridge));
            ImageGenerationRunRequestParameterMapper.apply(
                    requestBuilder,
                    new ImageGenerationRunRequestParameterMapper.Options(
                            featurePipelineId,
                            imageGenerationOptions.guidanceScale() == null
                                    ? null
                                    : imageGenerationOptions.guidanceScale().doubleValue(),
                            outputPath,
                            imageGenerationOptions.width(),
                            imageGenerationOptions.height()));
            String effectiveOnnxVariant = onnxVariant == null || onnxVariant.isBlank()
                    ? null
                    : PaddleOcrVlOnnxPlanner.normalizeVariant(onnxVariant);
            String effectiveTtsVoice = effectiveTtsVoiceSelector();
            String effectiveTtsCodec = audioRunOptions.codec() == null || audioRunOptions.codec().isBlank()
                    ? null
                    : resolveTtsCodecReference(audioRunOptions.codec());
            AudioQualityPreset audioPreset = audioQualityPreset();
            String effectiveAudioFormat = effectiveAudioFormat(audioPreset);
            Integer effectiveAudioBitrateKbps = effectiveAudioBitrateKbps(audioPreset, effectiveAudioFormat);
            Integer effectiveFlacCompression = effectiveFlacCompression(audioPreset, effectiveAudioFormat);
            AudioRunRequestParameterMapper.apply(requestBuilder, new AudioRunRequestParameterMapper.Options(
                    effectiveTtsVoice,
                    effectiveTtsCodec,
                    audioRunOptions.maxFrames(),
                    audioRunOptions.maxSeconds(),
                    audioRunOptions.quality(),
                    effectiveAudioFormat,
                    effectiveAudioBitrateKbps,
                    audioRunOptions.channels(),
                    audioRunOptions.codecDecode(),
                    audioRunOptions.randomSeed(),
                    audioRunOptions.disablePolish(),
                    audioRunOptions.disableNormalize(),
                    audioRunOptions.gainDb(),
                    audioRunOptions.peakDb(),
                    audioRunOptions.fadeMs(),
                    audioRunOptions.trimSilence(),
                    audioRunOptions.trimDbfs(),
                    audioRunOptions.trimPaddingMs(),
                    audioRunOptions.streamChunkKb(),
                    audioRunOptions.streamProgressFrames(),
                    audioRunOptions.disableStreamProgress(),
                    audioRunOptions.streamPcm(),
                    audioRunOptions.liveAudio(),
                    audioRunOptions.disableLowLatency(),
                    effectiveFlacCompression));

            RuntimeOutputFormatRequestConfiguration.apply(requestBuilder, extension);

            ToolRunRequestConfiguration.apply(requestBuilder, requestedTools, requestedToolChoice);
            RagRunRequestConfiguration.apply(requestBuilder, ragContext, retrievalContextOptions.embeddingModel());

            boolean directSafetensorEligible = shouldUseDirectSafetensorRunPath(providerId, finalLocalPath);
            boolean directSafetensorSdkRoute = directSafetensorEligible && !effectiveStream;
            String effectiveSystemPrompt = effectiveRunSystemPrompt(executionProviderId, finalLocalPath);
            if (directSafetensorSdkRoute) {
                DirectSafetensorRunProfile directProfile =
                        directSafetensorRunProfile(executionProviderId, finalLocalPath);
                if (!DirectSafetensorRoutePolicy.shouldForwardSystemPromptToDirectPath(systemPromptOptions.systemPrompt(), directProfile)) {
                    // Match the former direct runner: retain explicit prompts and Qwen's default,
                    // but do not inject the generic CLI default for other direct model families.
                    effectiveSystemPrompt = null;
                }
            }
            String ragSystemPrompt = RagRunRequestConfiguration.systemPrompt(ragContext);

            InferenceRequest request = RunInferenceRequestAssembler.assemble(
                    new RunInferenceRequestAssembler.Options(
                            new RunRequestBaseAssembler.Options(
                                    requestModelTarget,
                                    prompt,
                                    pluginId,
                                    effectiveStream,
                                    customModelPathUsed,
                                    finalLocalPath,
                                    requestedModelRef),
                            textRunOptions,
                            new RunInferenceRequestAssembler.QuantizationOptions(
                                    quantizeStrategy,
                                    quantizeBits,
                                    quantizeKv),
                            runtimeProfile,
                            new RuntimeRoutingRunRequestConfiguration.Options(
                                    effectiveRunnerRouteReport.toMetadata(),
                                    runner != null && !runner.isBlank(),
                                    effectiveRunnerRouteReport.normalizedRunner(),
                                    ggufEngineHint,
                                    featurePipelineId,
                                    executionProviderId,
                                    libtorchSafetensorCliBridge),
                            new ImageRunRequestParameterMapper.Options(
                                    seed,
                                    imageGenerationOptions.steps(),
                                    format,
                                    imageGenerationOptions.guidanceScale(),
                                    outputPath,
                                    imageGenerationOptions.width(),
                                    imageGenerationOptions.height(),
                                    normalizedInputImages,
                                    visionInputOptions.ocrMode(),
                                    visionInputOptions.ocrImageTokens(),
                                    visionInputOptions.ocrRawOutput(),
                                    effectiveOnnxVariant,
                                    raw,
                                    debugPrompt),
                            new AudioRunRequestParameterMapper.Options(
                                    effectiveTtsVoice,
                                    effectiveTtsCodec,
                                    audioRunOptions.maxFrames(),
                                    audioRunOptions.maxSeconds(),
                                    audioRunOptions.quality(),
                                    effectiveAudioFormat,
                                    effectiveAudioBitrateKbps,
                                    audioRunOptions.channels(),
                                    audioRunOptions.codecDecode(),
                                    audioRunOptions.randomSeed(),
                                    audioRunOptions.disablePolish(),
                                    audioRunOptions.disableNormalize(),
                                    audioRunOptions.gainDb(),
                                    audioRunOptions.peakDb(),
                                    audioRunOptions.fadeMs(),
                                    audioRunOptions.trimSilence(),
                                    audioRunOptions.trimDbfs(),
                                    audioRunOptions.trimPaddingMs(),
                                    audioRunOptions.streamChunkKb(),
                                    audioRunOptions.streamProgressFrames(),
                                    audioRunOptions.disableStreamProgress(),
                                    audioRunOptions.streamPcm(),
                                    audioRunOptions.liveAudio(),
                                    audioRunOptions.disableLowLatency(),
                                    effectiveFlacCompression),
                            extension,
                            requestedTools,
                            requestedToolChoice,
                            ragContext,
                            retrievalContextOptions.embeddingModel(),
                            new RunInferenceRequestAssembler.ConversationOptions(
                                    conversationSessionOptions.sessionId(),
                                    effectiveSystemPrompt,
                                    ragSystemPrompt,
                                    prompt),
                            noCache));

            if (!quietRouteResolutionOutput() && !enableJsonSse) {
                uiRenderer.printModelInfo(modelId, providerId, format, null, false);
                printQuantizationInfo();
                if (libtorchSafetensorCliBridge) {
                    forcedExecutionRouteMetadata = ExecutionRouteMetadata.bridgeMetadata();
                    forcedExecutionRouteMetadata =
                            ExecutionRouteMetadata.merge(effectiveRunnerRouteReport.toMetadata(),
                                    forcedExecutionRouteMetadata);
                    printExecutionRouteInfo(forcedExecutionRouteMetadata);
                } else {
                    forcedExecutionRouteMetadata = effectiveRunnerRouteReport.toMetadata();
                }
            }

            if (explicitJavaEngine() && isSafetensorModelPath(finalLocalPath)
                    && !directSafetensorEligible) {
                System.err.println("Error: --java requested the pure-Java safetensor engine, but it cannot run this request: "
                        + directSafetensorDeclineReason(finalLocalPath)
                        + ". Not falling back to libtorch or GGUF; drop --java to allow other routes.");
                requestProcessExit(1);
                return;
            }
            if (directSafetensorSdkRoute) {
                System.err.println("Running pure-Java Safetensors completion through the AQLI SDK...");
                InferenceResponse response = sdk.createCompletion(request);
                if (response == null) {
                    System.err.println("Error: inference returned an empty response.");
                    requestProcessExit(1);
                    return;
                }
                printResponse(response, startTime);
                requestProcessExit();
            } else if (effectiveStream) {
                java.io.ByteArrayOutputStream imageBuffer = new java.io.ByteArrayOutputStream();
                StreamingAudioOutputSession audioOutput = new StreamingAudioOutputSession();
                LivePcmAudioPlayer liveAudioSink = new LivePcmAudioPlayer(audioRunOptions.liveAudio());
                java.util.concurrent.atomic.AtomicReference<java.util.Map<String, Object>> metricsRef = new java.util.concurrent.atomic.AtomicReference<>();
                java.util.concurrent.atomic.AtomicInteger tokenCount = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicLong firstTokenTime = new java.util.concurrent.atomic.AtomicLong(0);
                java.util.concurrent.atomic.AtomicLong lastTokenTime = new java.util.concurrent.atomic.AtomicLong(0);
                tech.kayys.aqli.audio.output.StreamingAudioChunkRouter audioChunkRouter =
                        new tech.kayys.aqli.audio.output.StreamingAudioChunkRouter(
                                audioOutput, mediaOutputHandler()::resolveAudioOutputPath, liveAudioSink::write);
                tech.kayys.aqli.cli.commands.stream.RunStreamChunkHandler chunkHandler =
                        new tech.kayys.aqli.cli.commands.stream.RunStreamChunkHandler(
                                imageBuffer,
                                chunk -> audioChunkRouter.accept(chunk.getDelta(), chunk.metadata()),
                                delta -> {
                                    if (enableJsonSse) {
                                        printOpenAiSseDelta(request.getRequestId(), request.getModel(), delta);
                                    } else {
                                        System.out.print(delta);
                                        System.out.flush();
                                    }
                                },
                                delta -> {
                                    if (!enableJsonSse) {
                                        System.out.print("\r" + ChatUIRenderer.CYAN + delta + ChatUIRenderer.RESET + "  ");
                                        System.out.flush();
                                    }
                                },
                                metricsRef::set,
                                tokenCount,
                                firstTokenTime,
                                lastTokenTime);
                long streamStartTime = System.currentTimeMillis();
                String streamFinalLocalPath = finalLocalPath;

                var stream = sdk.streamCompletion(request);
                if (stream == null) {
                    System.err.println("Error: No provider available to handle inference for model '" + modelId
                            + "' (format: " + format + ", provider: " + providerId + ").");
                    requestProcessExit(1);
                    return;
                }

                RunStreamExecutionCoordinator.Result streamResult =
                        new RunStreamExecutionCoordinator().execute(
                                stream,
                                () -> sdk.streamCompletion(llamaCppFallbackRequest(request)),
                                () -> pluginChecker != null && pluginChecker.hasProvider("llamacpp"),
                                chunkHandler::accept,
                                () -> {
                                    liveAudioSink.close();
                                    long duration = observedStreamDurationMillis(
                                            streamStartTime, System.currentTimeMillis(), lastTokenTime);
                                    handleOutputs(imageBuffer, audioOutput, metricsRef.get());
                                    double tps = tokenCount.get() / (Math.max(1, duration) / 1000.0);
                                    Double ttftMs = ttftMillis(metricsRef.get(), streamStartTime, firstTokenTime);
                                    Map<String, Object> streamMetrics = observedStreamMetrics(
                                            effectiveExecutionRouteMetadata(metricsRef.get()),
                                            tokenCount.get(),
                                            duration,
                                            ttftMs);
                                    RouteBenchmarkProfileCoordinator.record(
                                            request.getModel(),
                                            streamFinalLocalPath,
                                            streamMetrics,
                                            tokenCount.get(),
                                            duration,
                                            audioOutput.hasOutput(),
                                            AudioRunStatsRenderer::isAudioMetadata);
                                    if (enableJsonSse) {
                                        printOpenAiSseFinal(request.getRequestId(), request.getModel());
                                    } else {
                                        printCompletionStatsForOutput(
                                                tokenCount.get(),
                                                duration,
                                                tps,
                                                ttftMs,
                                                streamMetrics,
                                                audioOutput.hasOutput());
                                    }
                                },
                                error -> {
                                    liveAudioSink.close();
                                    closeAudioOutputQuietly(audioOutput);
                                    uiRenderer.printError(
                                            error == null ? "Stream failed" : error.getMessage(), false);
                                    printProviderHintFromError(error);
                                },
                                () -> {
                                    liveAudioSink.close();
                                    closeAudioOutputQuietly(audioOutput);
                                },
                                error -> {
                                    System.err.println("Warning: stream provider failed: "
                                            + (error == null ? "unknown" : error.getMessage()));
                                    System.err.println(
                                            "Attempting streaming fallback to provider 'llamacpp' (llama.cpp)...");
                                });
                requestProcessExit(streamResult.failed() ? 1 : 0);
            } else {
                try {
                    InferenceResponse response = sdk.createCompletion(request);
                    if (response == null) {
                        System.err.println("Error: No provider available to handle inference for model '" + modelId
                                + "' (format: " + format + ", provider: " + providerId + ").");
                        requestProcessExit(1);
                        return;
                    }
                    printResponse(response, startTime);
                    requestProcessExit();
                } catch (Throwable createEx) {
                    // Attempt fallback to local llama.cpp provider if available
                    if (tryFallbackToLlamaCpp(createEx, request, startTime)) {
                        return;
                    }
                    throw createEx;
                }
            }

        } catch (Throwable e) {
            System.err.println("\n[FATAL] RunCommand failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static InferenceRequest llamaCppFallbackRequest(InferenceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Inference request is required for llama.cpp fallback");
        }
        return request.toBuilder().preferredProvider("llamacpp").build();
    }

    private boolean tryFallbackToLlamaCpp(Throwable cause, InferenceRequest request, long startTime) {
        try {
            if (pluginChecker == null) return false;
            if (!pluginChecker.hasProvider("llamacpp")) return false;
        } catch (Exception e) {
            return false;
        }

        System.err.println("Warning: primary provider failed: " + (cause == null ? "unknown" : cause.getMessage()));
        System.err.println("Attempting fallback to provider 'llamacpp' (llama.cpp)...");
        try {
            try {
                
            } catch (Throwable ignore) {}
            InferenceResponse fallbackResponse = sdk.createCompletion(llamaCppFallbackRequest(request));
            printResponse(fallbackResponse, startTime);
            requestProcessExit();
            return true;
        } catch (Throwable fallbackErr) {
            System.err.println("Fallback to 'llamacpp' failed: " + fallbackErr.getMessage());
            return false;
        }
    }

    private void printMetalRuntimeStatus() {
        var status = CliMetalRuntime.status();
        if (status.errorMessage() != null) {
            System.out.println(ChatUIRenderer.YELLOW + "⚠️  Unable to verify Metal runtime status: " + status.errorMessage() + ChatUIRenderer.RESET);
        } else if (status.active()) {
            System.out.println(ChatUIRenderer.GREEN + "✓ Metal native backend active: " + status.deviceName() + ChatUIRenderer.RESET);
        } else {
            System.out.println(ChatUIRenderer.YELLOW + "⚠️  Metal native backend NOT active (device: " + status.deviceName() + "), using CPU fallback path" + ChatUIRenderer.RESET);
        }
    }

    private boolean isMetalNativeRuntimeActive() {
        return CliMetalRuntime.isNativeActive();
    }

    private void ensureMetalRuntimeInitialized() {
        CliMetalRuntime.initialize();
    }

    private boolean repairOrRejectPositionalArgs() {
        if (positionalArgs == null || positionalArgs.isEmpty()) {
            return true;
        }

        if (positionalArgs.size() == 1
                && outputPath == null
                && audioRunOptions.voice() != null
                && audioRunOptions.voice().contains("--output")) {
            String repairedVoice = audioRunOptions.voice().substring(0, audioRunOptions.voice().indexOf("--output")).trim();
            if (!repairedVoice.isBlank()) {
                outputPath = positionalArgs.get(0);
                audioRunOptions = audioRunOptions.withVoice(repairedVoice);
                positionalArgs = List.of();
                System.err.println("Warning: detected a missing space before --output; interpreting as "
                        + "--voice \"" + repairedVoice + "\" --output " + outputPath);
                return true;
            }
        }
        System.err.println("Error: unexpected positional argument"                + (positionalArgs.size() == 1 ? "" : "s")
                + ": " + String.join(" ", positionalArgs));
        if (audioRunOptions.voice() != null && audioRunOptions.voice().contains("--output")) {
            System.err.println("Hint: add a space before --output, for example:");
            System.err.println("  --voice \"" + audioRunOptions.voice().substring(0, audioRunOptions.voice().indexOf("--output")).trim()
                    + "\" --output " + positionalArgs.get(0));
        }
        return false;
    }

    private boolean verifyAudioMetadataSidecar() {
        return MediaMetadataSidecarCliHandler.verifyAudioMetadataSidecar(mediaMetadataOptions.audioVerifyPath(), outputPath);
    }

    private boolean refreshAudioMetadataSidecar() {
        return MediaMetadataSidecarCliHandler.refreshAudioMetadataSidecar(mediaMetadataOptions.audioRefreshPath(), outputPath);
    }

    private boolean verifyImageMetadataSidecar() {
        return MediaMetadataSidecarCliHandler.verifyImageMetadataSidecar(imageMetadataOptions.imageVerifyPath(), outputPath);
    }

    private boolean refreshImageMetadataSidecar() {
        return MediaMetadataSidecarCliHandler.refreshImageMetadataSidecar(imageMetadataOptions.imageRefreshPath(), outputPath);
    }

    private boolean loadAudioMetadataReplayIfRequested() {
        if (audioReplayOptions.replayPath() == null || audioReplayOptions.replayPath().isBlank()) {
            return true;
        }
        try {
            Path sidecarPath = Path.of(audioReplayOptions.replayPath());
            if (!Files.isRegularFile(sidecarPath)) {
                System.err.println("Error: audio metadata replay file not found: " + audioReplayOptions.replayPath());
                return false;
            }
            AudioMetadataReplayParser.Plan plan = new AudioMetadataReplayParser().parse(sidecarPath);
            if (plan == null) {
                System.err.println("Error: invalid audio metadata replay file: " + audioReplayOptions.replayPath());
                return false;
            }
            boolean outputExplicit = outputPath != null && !outputPath.isBlank();
            applyAudioMetadataReplayPlan(plan);
            if (!outputExplicit && (outputPath == null || outputPath.isBlank())) {
                outputPath = defaultAudioReplayOutputPath(plan.outputPath(), audioRunOptions.format());
            }
            if (!ttsVoicesJsonOutput()) {
                System.out.println("Loaded TTS replay metadata: " + sidecarPath.toAbsolutePath().normalize());
            }
            return true;
        } catch (Exception e) {
            System.err.println("Error: failed to load audio metadata replay file: " + e.getMessage());
            return false;
        }
    }

    private void applyAudioMetadataReplayPlan(AudioMetadataReplayParser.Plan plan) {
        audioRunOptions = audioRunOptions.withReplayPlan(plan);
        for (AudioMetadataReplayParser.ReplayOption option : plan.options()) {
            Object value = option.value();
            switch (option.option()) {
                case MODEL -> applyReplayModelTarget((String) value);
                case MODEL_DIR -> {
                    if (modelDir == null || modelDir.isBlank()) modelDir = (String) value;
                }
                case MODEL_FILE -> {
                    if (modelFile == null || modelFile.isBlank()) modelFile = (String) value;
                }
                case MODEL_PATH -> {
                    if (modelPath == null || modelPath.isBlank()) modelPath = (String) value;
                }
                case PROVIDER -> {
                    if (providerId == null || providerId.isBlank()) providerId = (String) value;
                }
                case RUNNER -> {
                    if (runner == null || runner.isBlank()) runner = (String) value;
                }
                case PROMPT -> {
                    if (prompt == null || prompt.isBlank()) prompt = (String) value;
                }
            }
        }
    }

    private void applyReplayModelTarget(String value) {
        if (value == null || value.isBlank()
                || (modelId != null && !modelId.isBlank())
                || (modelDir != null && !modelDir.isBlank())
                || (modelFile != null && !modelFile.isBlank())
                || (modelPath != null && !modelPath.isBlank())) {
            return;
        }
        try {
            Path path = Path.of(value);
            if (Files.isDirectory(path)) {
                modelDir = value;
                return;
            }
            if (Files.isRegularFile(path)) {
                modelFile = value;
                return;
            }
        } catch (Exception ignored) {
        }
        modelId = value;
    }


    private static String jsonNodeText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.asText();
    }

    private String defaultAudioReplayOutputPath(String originalOutputPath, String replayAudioFormat) {
        return AudioReplayOutputPathResolver.resolve(originalOutputPath, replayAudioFormat).toString();
    }

    AudioRunOptions audioRunOptions() {
        return audioRunOptions;
    }

    TtsOperationsRunOptions ttsOperationsOptions() {
        return ttsOperationsOptions;
    }

    TtsVoiceDiscoveryRunOptions ttsVoiceDiscoveryOptions() {
        return ttsVoiceDiscoveryOptions;
    }

    private boolean validateTtsAudioCliOptions() {
        boolean ok = true;
        if (audioRunOptions.format() != null && !audioRunOptions.format().isBlank()) {
            ok &= TtsCliOptionsValidator.validateChoice(
                    "--audio-format",
                    audioRunOptions.format(),
                    Set.of("wav", "flac", "mp3"),
                    "wav, flac, mp3");
        }
        if (audioRunOptions.quality() != null && !audioRunOptions.quality().isBlank()) {
            ok &= TtsCliOptionsValidator.validateChoice(
                    "--audio-quality",
                    audioRunOptions.quality(),
                    Set.of("auto", "default", "compact", "small", "speech", "balanced", "standard", "high",
                            "quality", "lossless", "archive", "flac"),
                    "compact, speech, balanced, high, lossless");
        }
        if (audioRunOptions.channels() != null && !audioRunOptions.channels().isBlank()) {
            ok &= TtsCliOptionsValidator.validateChoice(
                    "--audio-channels",
                    audioRunOptions.channels(),
                    Set.of("auto", "1", "mono", "single", "2", "stereo", "dual-mono", "dual_mono",
                            "native", "source", "raw"),
                    "auto, mono, stereo, dual-mono, native");
        }
        if (audioRunOptions.codecDecode() != null && !audioRunOptions.codecDecode().isBlank()) {
            ok &= TtsCliOptionsValidator.validateChoice(
                    "--tts-codec-decode",
                    audioRunOptions.codecDecode(),
                    Set.of("auto", "full", "full_decode", "decode_full", "stream", "streaming", "step",
                            "decode_step"),
                    "auto, streaming, full");
        }
        if (audioRunOptions.bitrateKbps() != null) {
            ok &= TtsCliOptionsValidator.validateIntRange("--audio-bitrate", audioRunOptions.bitrateKbps(), 8, 320, "kbps");
        }
        if (audioRunOptions.flacCompression() != null) {
            ok &= TtsCliOptionsValidator.validateIntRange("--flac-compression", audioRunOptions.flacCompression(), 0, 8, "");
        }
        if (audioRunOptions.maxFrames() != null) {
            ok &= TtsCliOptionsValidator.validateIntRange("--tts-max-frames", audioRunOptions.maxFrames(), 1, 4096, "frames");
        }
        if (audioRunOptions.maxSeconds() != null) {
            ok &= TtsCliOptionsValidator.validateDoubleRange("--tts-max-seconds", audioRunOptions.maxSeconds(), 0.01, 300.0, "seconds");
        }
        if (audioRunOptions.streamChunkKb() != null) {
            ok &= TtsCliOptionsValidator.validateIntRange("--audio-stream-chunk-kb", audioRunOptions.streamChunkKb(), 4, 1024, "KiB");
        }
        if (audioRunOptions.streamProgressFrames() != null) {
            ok &= TtsCliOptionsValidator.validateIntRange("--tts-stream-progress-frames", audioRunOptions.streamProgressFrames(), 1, 4096, "frames");
        }
        if (audioRunOptions.fadeMs() != null) {
            ok &= TtsCliOptionsValidator.validateDoubleRange("--audio-fade-ms", audioRunOptions.fadeMs(), 0.0, 10_000.0, "ms");
        }
        if (audioRunOptions.trimDbfs() != null) {
            ok &= TtsCliOptionsValidator.validateDoubleRange("--audio-trim-db", audioRunOptions.trimDbfs(), -120.0, -0.1, "dBFS");
        }
        if (audioRunOptions.trimPaddingMs() != null) {
            ok &= TtsCliOptionsValidator.validateDoubleRange("--audio-trim-padding-ms", audioRunOptions.trimPaddingMs(), 0.0, 5_000.0, "ms");
        }
        if (audioRunOptions.peakDb() != null) {
            ok &= TtsCliOptionsValidator.validateDoubleRange("--audio-peak-db", audioRunOptions.peakDb(), -60.0, 0.0, "dBFS");
        }
        if (audioRunOptions.gainDb() != null) {
            ok &= TtsCliOptionsValidator.validateDoubleRange("--audio-gain-db", audioRunOptions.gainDb(), -60.0, 60.0, "dB");
        }
        return ok;
    }

    AudioQualityPreset audioQualityPreset() {
        return TtsCliOptionsValidator.audioQualityPreset(audioRunOptions.quality());
    }

    String effectiveAudioFormat(AudioQualityPreset preset) {
        return TtsCliOptionsValidator.effectiveAudioFormat(audioRunOptions.format(), preset);
    }

    Integer effectiveAudioBitrateKbps(AudioQualityPreset preset, String effectiveAudioFormat) {
        return TtsCliOptionsValidator.effectiveAudioBitrateKbps(audioRunOptions.bitrateKbps(), preset, effectiveAudioFormat);
    }

    Integer effectiveFlacCompression(AudioQualityPreset preset, String effectiveAudioFormat) {
        return TtsCliOptionsValidator.effectiveFlacCompression(audioRunOptions.flacCompression(), preset, effectiveAudioFormat);
    }

    String effectiveTtsVoiceSelector() {
        return audioRunOptions.effectiveVoiceSelector();
    }

    private int printTtsVoices(String modelId, String finalLocalPath) {
        return new MossTtsCliDiagnosticsHandler(this).printTtsVoices(modelId, finalLocalPath);
    }

    private boolean ttsVoicesJsonOutput() {
        return jsonMode || ttsVoiceDiscoveryOptions.listVoicesJson();
    }

    private boolean quietRouteResolutionOutput() {
        return ttsVoicesJsonOutput() || routeReportOptions.enabled() || noBanner;
    }

    boolean shouldAllowRepositoryResolutionDuringRouteReport() {
        return !routeReportOptions.enabled() || routeReportOptions.allowPull();
    }

    private String routeExecutionProviderId(String provider, String localPath) {
        return RouteExecutionProviderResolver.resolve(
                provider, localPath, SafetensorCompatibilityPreflightValidator::isSafetensorCheckpointDir, SafetensorCompatibilityPreflightValidator::isSafetensorWeightFile);
    }

    private int printOnnxDiagnostics(String modelId, String finalLocalPath) {
        return new OnnxDiagnosticsCliHandler(prompt, onnxVariant, onnxDiagnosticsOptions.probeImageTokens(), onnxDiagnosticsOptions.probeDecodeTokens())
                .printOnnxDiagnostics(modelId, finalLocalPath);
    }

    private int printOnnxVisionProbe(String modelId, String finalLocalPath, boolean includePipelineGraphs) {
        return new OnnxDiagnosticsCliHandler(prompt, onnxVariant, onnxDiagnosticsOptions.probeImageTokens(), onnxDiagnosticsOptions.probeDecodeTokens())
                .printOnnxVisionProbe(modelId, finalLocalPath, normalizedInputImagePaths(), includePipelineGraphs);
    }

    private List<String> normalizedInputImagePaths() {
        List<String> images = new ArrayList<>();
        addInputImage(images, visionInputOptions.inputImagePath());
        if (visionInputOptions.inputImagePaths() != null) {
            for (String image : visionInputOptions.inputImagePaths()) {
                addInputImage(images, image);
            }
        }
        return List.copyOf(images);
    }

    private void addInputImage(List<String> images, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Path path = expandCliPath(value.trim()).toAbsolutePath().normalize();
        String normalized = path.toString();
        if (!images.contains(normalized)) {
            images.add(normalized);
        }
    }

    private boolean validateInputImages(List<String> images) {
        boolean ok = true;
        for (String image : images) {
            Path path = Path.of(image);
            if (!Files.isRegularFile(path)) {
                System.err.println("Error: input image not found: " + image);
                ok = false;
            }
        }
        return ok;
    }

    private Path existingCliPath(String value) {
        return CliPathUtils.existingCliPath(value);
    }

    private Path expandCliPath(String value) {
        return CliPathUtils.expandCliPath(value);
    }



    private int printTtsDiagnostics(String modelId, String finalLocalPath) {
        return new MossTtsCliDiagnosticsHandler(this).printTtsDiagnostics(modelId, finalLocalPath);
    }

    private int warmupMossTts(String modelId, String finalLocalPath) {
        return new MossTtsCliDiagnosticsHandler(this).warmupMossTts(modelId, finalLocalPath);
    }

    private int pruneMossTtsOptimizedCache(String modelId, String finalLocalPath) {
        return new MossTtsCliDiagnosticsHandler(this).pruneMossTtsOptimizedCache(modelId, finalLocalPath);
    }


    private boolean allowsPromptlessExtensionRun(String modelId, String finalLocalPath) {
        return looksLikeWebWorldReference(modelId) || looksLikeWebWorldReference(finalLocalPath);
    }

    private boolean looksLikeWebWorldReference(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains("webworld")) {
            return true;
        }
        try {
            Path path = Paths.get(value);
            if (Files.isRegularFile(path)) {
                path = path.getParent();
            }
            if (path != null && Files.isDirectory(path)) {
                if (path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT).contains("webworld")) {
                    return true;
                }
                Path readme = path.resolve("README.md");
                if (Files.isRegularFile(readme)) {
                    String text = Files.readString(readme).toLowerCase(Locale.ROOT);
                    return text.contains("webworld") && text.contains("world model");
                }
            }
        } catch (Exception ignored) {
            // Treat non-path model references with the text checks above.
        }
        return false;
    }


    boolean isOffline() {
        return modelRepositoryOptions != null && modelRepositoryOptions.offline();
    }

    private LocalModelIndex.Entry autoPullHfRepositoryForRunIfNeeded(String requestedModel, String preferredFormat)
            throws Exception {
        String repoId = ModelSourceResolver.huggingFaceRepositoryId(requestedModel);
        if (repoId == null || ModelSourceRoutingPolicy.isRemoteProvider(providerId) || isMcpProvider()) {
            return null;
        }

        Optional<LocalModelIndex.Entry> existing = findLocalIndexEntry(repoId, preferredFormat);
        if (existing.isPresent()) {
            return existing.get();
        }

        if (modelRepositoryOptions.offline()) {
            System.err.println("Error: Model " + repoId
                    + " is not installed locally, and --offline/--local disables automatic downloads.");
            System.err.println("Run once online: aqli pull " + repoId);
            return null;
        }
        String branch = modelRepositoryOptions.branch();
        String revision = branch == null || branch.isBlank() ? "main" : branch.trim();
        if (!ttsVoicesJsonOutput()) {
            System.out.println("Model not found locally; pulling hf:" + repoId
                    + ("main".equalsIgnoreCase(revision) ? "" : "@" + revision) + "...");
        }
        sdk.pullHuggingFaceModel(
                HuggingFacePullRequest.builder()
                        .modelSpec("hf:" + repoId)
                        .revision(revision)
                        .format(preferredFormat)
                        .build(),
                progress -> {
                    if (quietRouteResolutionOutput()) {
                        return;
                    }
                    if (progress.getTotal() > 0) {
                        System.out.printf("\r  Downloading %s: %3d%% (%d/%d MB)",
                                repoId,
                                progress.getPercentComplete(),
                                progress.getCompleted() / 1024 / 1024,
                                progress.getTotal() / 1024 / 1024);
                    } else {
                        System.out.printf("\r  %s", progress.getStatus());
                    }
                    System.out.flush();
                });
        if (!quietRouteResolutionOutput()) {
            System.out.println();
        }
        LocalModelIndex.refreshFromDisk();

        Optional<LocalModelIndex.Entry> downloaded = findLocalIndexEntry(repoId, preferredFormat);
        if (downloaded.isPresent()) {
            return downloaded.get();
        }
        throw new IllegalStateException("download finished, but no local"
                + (preferredFormat == null ? "" : " " + preferredFormat)
                + " artifact was indexed for " + repoId);
    }

    private Optional<LocalModelIndex.Entry> findLocalIndexEntry(String repoId, String preferredFormat) {
        return LocalModelIndexResolver.find(
                repoId,
                preferredFormat,
                (ref, format) -> format == null
                        ? LocalModelIndex.find(ref)
                        : LocalModelIndex.find(ref, format));
    }


    private String resolveTtsCodecReference(String ref) {
        return new MossTtsCliDiagnosticsHandler(this).resolveTtsCodecReference(ref);
    }

    private boolean ensureMossTtsCodecAvailableIfNeeded(String modelId, String finalLocalPath) {
        return new MossTtsCliDiagnosticsHandler(this).ensureMossTtsCodecAvailableIfNeeded(modelId, finalLocalPath);
    }

    private String displayPath(Path path) {
        return CliPathUtils.displayPath(path);
    }

    private boolean allowCpuFallbackWhenMetalRequested() {
        return CliMetalRuntime.allowCpuFallbackWhenMetalRequested();
    }

    private void printQuantizationInfo() {
        if (quantizeStrategy == null || quantizeStrategy.isBlank() || noBanner) {
            return;
        }

        String effective = effectiveQuantizationStrategy(quantizeStrategy);
        String effectiveKv = effectiveKvQuantization(quantizeKv);
        System.out.println("Quantization: " + quantizeStrategy.toLowerCase()
                + (effective.equalsIgnoreCase(quantizeStrategy) ? "" : " -> " + effective)
                + " (" + quantizeBits + "-bit)");
        if (quantizeKv != null && !quantizeKv.equalsIgnoreCase("none")) {
            System.out.println("KV cache quantization: " + quantizeKv.toLowerCase()
                    + (effectiveKv.equalsIgnoreCase(quantizeKv) ? "" : " -> " + effectiveKv));
        } else {
            System.out.println("KV cache quantization: off");
        }
        System.out.println("--------------------------------------------------");
    }

    private boolean shouldUseLocalModelPath(String providerId, String localPath) {
        if (localPath == null || localPath.isBlank()) {
            return false;
        }
        if (providerId == null || providerId.isBlank()) {
            return false;
        }
        String normalized = providerId.trim().toLowerCase();
        return normalized.equals("safetensor")
                || normalized.equals("gguf")
                || normalized.equals("native")
                || normalized.equals("litert")
                || normalized.equals("onnx")
                || normalized.equals("libtorch");
    }

    private boolean shouldUseDirectSafetensorRunPath(String currentProvider, String localPath) {
        Path checkpointPath = resolveSafetensorCheckpointPath(localPath);
        return DirectSafetensorRoutePolicy.shouldUseDirectRun(
                currentProvider,
                checkpointPath,
                checkpointPath != null && SafetensorCompatibilityPreflightValidator.isSafetensorCheckpointDir(checkpointPath.toString()),
                conversationSessionOptions.sessionId(),
                textRunOptions.grammar(),
                prompt,
                directInferenceEngine() != null);
    }

    private String effectiveRunSystemPrompt(String providerId, String localPath) {
        return DirectSafetensorRoutePolicy.effectiveSystemPrompt(
                systemPromptOptions.systemPrompt(),
                directSafetensorRunProfile(providerId, localPath));
    }

    private DirectSafetensorRunProfile directSafetensorRunProfile(String providerId, String localPath) {
        return DirectSafetensorRoutePolicy.profileForProvider(
                providerId,
                resolveSafetensorCheckpointPath(localPath));
    }

    private Path resolveSafetensorCheckpointPath(String localPath) {
        if (localPath == null || localPath.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(localPath).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                return path;
            }
            if (!Files.isRegularFile(path)) {
                return null;
            }
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".safetensors")) {
                return null;
            }
            Path parent = path.getParent();
            return parent != null && Files.isDirectory(parent) ? parent : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private DirectInferenceEngine directInferenceEngine() {
        if (directInferenceEngine != null) {
            return directInferenceEngine;
        }
        try {
            var instance = Arc.container().instance(DirectInferenceEngine.class);
            if (instance != null && instance.isAvailable()) {
                return instance.get();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String effectiveQuantizationStrategy(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase();
        return switch (normalized) {
            case "turbo" -> "bnb";
            case "gptq", "autoround" -> "int4";
            default -> normalized;
        };
    }

    private String effectiveKvQuantization(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase();
        return switch (normalized) {
            case "", "none" -> "off";
            case "int8" -> "int8";
            case "int4" -> "int4";
            case "turbo" -> "int4";
            default -> normalized;
        };
    }

    private boolean isMcpProvider() {
        return providerId != null && "mcp".equalsIgnoreCase(providerId.trim());
    }

    boolean shouldUseStandaloneLiteRtRun(String localPath) {
        return StandaloneRunPolicy.shouldUseLiteRtStandalone(
                routeReportOptions.enabled(),
                Boolean.parseBoolean(System.getProperty("aqli.cli.enable_standalone_litert_fast_path", "true")),
                prompt,
                providerId,
                looksLikeLiteRtLmPath(localPath),
                isSimpleStandaloneTextRun());
    }

    String[] buildStandaloneLiteRtRunArgs(String localPath) {
        return LiteRtStandaloneRunArguments.toArgs(
                localPath, prompt, textRunOptions.maxTokens(), textRunOptions.temperature(), textRunOptions.topK(), textRunOptions.topP());
    }

    private boolean tryStandaloneLiteRtRun(String localPath) {
        if (!shouldUseStandaloneLiteRtRun(localPath)) {
            return false;
        }
        return StandaloneLiteRtRunExecutor.execute(
                buildStandaloneLiteRtRunArgs(localPath),
                LiteRtLmStandaloneRunner::run,
                this::requestProcessExit,
                System::exit);
    }

    private boolean tryCachedGemma4MobileQatLiteRtRun(boolean providerExplicit) {
        return tryCachedGemma4MobileQatLiteRtRun(providerExplicit, providerId);
    }

    private boolean tryCachedGemma4MobileQatLiteRtRun(boolean providerExplicit, String requestedProviderId) {
        if (!shouldUseCachedGemma4MobileQatLiteRtRun(providerExplicit, requestedProviderId)) {
            return false;
        }
        Optional<LocalModelIndex.Entry> indexed = findSafetensorIndexEntry(modelId);
        if (indexed.isEmpty()) {
            return false;
        }
        LocalModelIndex.Entry entry = indexed.get();
        DirectSafetensorRoutePolicy.AlternateRuntimeSelection selection =
                DirectSafetensorRoutePolicy.selectCachedGemma4MobileQatLiteRtAlternateRuntime(
                        "safetensor",
                        entry.path,
                        entry.path,
                        providerExplicit,
                        preferAlternateRuntime);
        if (!selection.selected()) {
            return false;
        }
        providerId = selection.provider();
        return StandaloneLiteRtRunExecutor.execute(
                buildStandaloneLiteRtRunArgs(selection.localPath()),
                LiteRtLmStandaloneRunner::run,
                this::requestProcessExit,
                System::exit);
    }

    boolean shouldUseCachedGemma4MobileQatLiteRtRun(boolean providerExplicit) {
        return shouldUseCachedGemma4MobileQatLiteRtRun(providerExplicit, providerId);
    }

    boolean shouldUseCachedGemma4MobileQatLiteRtRun(boolean providerExplicit, String requestedProviderId) {
        return StandaloneRunPolicy.shouldUseCachedGemma4LiteRtStandalone(
                Boolean.parseBoolean(System.getProperty("aqli.cli.enable_cached_gemma4_litert_fast_path", "true")),
                modelId,
                modelFile,
                modelDir,
                modelPath,
                requestedProviderId,
                providerExplicit,
                isSimpleStandaloneTextRun());
    }

    private Optional<LocalModelIndex.Entry> findSafetensorIndexEntry(String modelRef) {
        return LocalModelIndexResolver.findExactFormat(
                modelRef,
                "safetensor",
                (reference, preferredFormat) -> preferredFormat == null
                        ? LocalModelIndex.find(reference)
                        : LocalModelIndex.find(reference, preferredFormat));
    }

    boolean shouldUseStandaloneGgufRun() {
        return StandaloneRunPolicy.shouldUseGgufStandalone(
                routeReportOptions.enabled(),
                prompt,
                providerId,
                GgufRouteAliasPolicy.isProviderAlias(providerId),
                GgufRouteAliasPolicy.isPluginAlias(pluginId),
                hasExplicitGgufEngine(),
                requestsSafetensorModel(),
                explicitLlamaCppEngine(),
                forceGguf,
                isGgufFormat(format),
                looksLikeGgufPath(modelFile),
                looksLikeGgufPath(modelPath),
                looksLikeGgufPath(modelId));
    }

    String[] buildStandaloneGgufStandaloneRunnerArgs() {
        return buildStandaloneGgufStandaloneRunnerArgs(false);
    }

    String[] buildStandaloneGgufStandaloneRunnerArgs(boolean suppressBanner) {
        List<String> args = new java.util.ArrayList<>();
        args.add("run");
        if (suppressBanner || noBanner) {
            args.add("--no-banner");
        }
        if (noInfo) {
            args.add("--no-info");
        }
        if (raw) {
            args.add("--raw");
        }
        if (debugPrompt) {
            args.add("--debug-prompt");
        }
        if (modelFile != null && !modelFile.isBlank()) {
            args.add("--modelFile");
            args.add(modelFile);
        } else if (modelPath != null && !modelPath.isBlank()) {
            args.add("--model-path");
            args.add(modelPath);
        } else if (modelId != null && !modelId.isBlank()) {
            args.add("--model");
            args.add(modelId);
        }
        if (prompt != null) {
            args.add("--prompt");
            args.add(prompt);
        }
        args.add("--max-tokens");
        args.add(Integer.toString(textRunOptions.maxTokens()));
        args.add("--temperature");
        args.add(Double.toString(textRunOptions.temperature()));
        args.add("--top-k");
        args.add(Integer.toString(textRunOptions.topK()));
        args.add("--top-p");
        args.add(Double.toString(textRunOptions.topP()));

        String engine = GgufCliEngineOptionResolver.resolve(engineSelectionOptions.benchmark(), engineSelectionOptions.javaNative(), engineSelectionOptions.llamaCpp(), engineSelectionOptions.engine());
        if (engine != null && !engine.isBlank()) {
            args.add("--engine");
            args.add(engine);
        }
        String backend = GgufCliBackendOptionResolver.resolve(engineSelectionOptions.backend(), parentCommand != null && parentCommand.isUseCpu(), parentCommand == null ? null : parentCommand.platform());
        if (backend != null && !backend.isBlank()) {
            args.add("--backend");
            args.add(backend);
        }
        if (providerId != null && !providerId.isBlank()) {
            args.add("--provider");
            args.add(providerId);
        } else {
            args.add("--provider");
            args.add("gguf");
        }
        return args.toArray(String[]::new);
    }
    private boolean tryStandaloneGgufRun() {
        return tryStandaloneGgufRun(false);    }

    private boolean tryStandaloneGgufRun(boolean suppressBanner) {
        if (!shouldUseStandaloneGgufRun()) {
            return false;
        }
        int status = GgufStandaloneRunner.run(buildStandaloneGgufStandaloneRunnerArgs(suppressBanner));
        if (GgufStandaloneRunner.isFallbackToFullCliStatus(status)) {
            return false;
        }
        if (status == 0) {
            if (GgufStandaloneRunner.hardExitAfterRun()) {
                GgufStandaloneRunner.hardExitProcess(0);
            }
            requestProcessExit();
            return true;
        }
        System.exit(status);
        return true;
    }

    private boolean hasExplicitGgufEngine() {
        return engineSelectionOptions.javaNative()
                || engineSelectionOptions.llamaCpp()
                || engineSelectionOptions.benchmark()
                || (engineSelectionOptions.engine() != null && !engineSelectionOptions.engine().isBlank());
    }

    private boolean isSimpleStandaloneTextRun() {
        return isBlank(systemPromptOptions.systemPrompt())
                && isBlank(textRunOptions.grammar())
                && isBlank(conversationSessionOptions.sessionId())
                && isBlank(toolInvocationOptions.toolChoice())
                && isBlank(retrievalContextOptions.embeddingModel())
                && isBlank(visionInputOptions.inputImagePath())
                && isEmpty(toolInvocationOptions.toolFiles())
                && isEmpty(toolInvocationOptions.mcpTools())
                && isEmpty(retrievalContextOptions.contexts())
                && isEmpty(retrievalContextOptions.files())
                && isEmpty(visionInputOptions.inputImagePaths())
                && !jsonMode
                && !enableJsonSse
                && !visionInputOptions.ocrMode()
                && !ocrOutputOptions.structuredJson()
                && isBlank(ocrOutputOptions.jsonOutputPath())
                && isBlank(ocrOutputOptions.overlayOutputPath());
    }


    private static boolean looksLikeLiteRtLmPath(String localPath) {
        if (localPath == null || localPath.isBlank()) {
            return false;
        }
        try {
            Path path = Path.of(localPath);
            return Files.isRegularFile(path)
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".litertlm");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private boolean explicitJavaEngine() {
        return "direct".equals(SafetensorEngineHintPolicy.backendFor(GgufCliEngineOptionResolver.resolve(engineSelectionOptions.benchmark(), engineSelectionOptions.javaNative(), engineSelectionOptions.llamaCpp(), engineSelectionOptions.engine())));
    }

    private boolean explicitLlamaCppEngine() {
        return "gguf".equals(SafetensorEngineHintPolicy.backendFor(GgufCliEngineOptionResolver.resolve(engineSelectionOptions.benchmark(), engineSelectionOptions.javaNative(), engineSelectionOptions.llamaCpp(), engineSelectionOptions.engine())));
    }

    /** {@code --direct}, or {@code --java} on a safetensor checkpoint (same engine, same flag spirit as GGUF). */
    private boolean effectiveDirect(String localPath) {
        return direct || (explicitJavaEngine() && isSafetensorModelPath(localPath));
    }

    /** Why {@link DirectSafetensorRoutePolicy#shouldUseDirectRun} would decline, for an explicit --java. */
    private String directSafetensorDeclineReason(String localPath) {
        if (directInferenceEngine() == null) {
            return "the DirectInferenceEngine bean is not available in this runtime";
        }
        if (prompt == null || prompt.isBlank()) {
            return "the direct engine currently needs --prompt";
        }
        if (conversationSessionOptions.sessionId() != null && !conversationSessionOptions.sessionId().isBlank()) {
            return "--session is not supported by the direct engine";
        }
        if (textRunOptions.grammar() != null && !textRunOptions.grammar().isBlank()) {
            return "--grammar is not supported by the direct engine";
        }
        Path checkpoint = resolveSafetensorCheckpointPath(localPath);
        if (checkpoint != null) {
            DirectSafetensorRunProfile profile = DirectSafetensorRunProfile.load(checkpoint);
            if (profile.nativeBf16Matvec()) {
                return "this checkpoint uses the guarded native-BF16 profile, which is not enabled for direct CLI runs";
            }
            if (!DirectSafetensorRoutePolicy.isArchitectureSupported(profile.config())) {
                return DirectSafetensorRoutePolicy.architectureDeclineReason(profile.config());
            }
        }
        return "the direct run policy declined this checkpoint";
    }

    /**
     * {@code --llamacpp} on a local safetensors checkpoint: llama.cpp cannot read safetensors, so
     * convert once (cached) and continue exactly as {@code --modelFile <that.gguf> --llamacpp}.
     *
     * @return {@code false} only when a conversion was needed and failed (message already printed)
     */
    private boolean prepareLlamaCppFromLocalSafetensor() {
        Path checkpoint = localSafetensorCheckpointForConversion();
        if (checkpoint == null) {
            return true;
        }
        try {
            String requestedType = ggufQuant != null && !ggufQuant.isBlank() ? ggufQuant : ggufOutType;
            LocalSafetensorGgufConverter.Config config = LocalSafetensorGgufConverter.resolveConfig(
                    System.getenv(), System.getProperties(), Path.of(System.getProperty("user.home")));
            LocalSafetensorGgufConverter.Result result = LocalSafetensorGgufConverter.convert(
                    checkpoint, requestedType, config, line -> {
                        if (!quietRouteResolutionOutput()) {
                            System.err.println(line);
                        }
                    });
            modelFile = result.gguf().toString();
            modelId = modelFile;
            modelDir = null;
            modelPath = null;
            format = "gguf";
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Error: GGUF conversion for --llamacpp was interrupted");
            return false;
        } catch (Exception e) {
            System.err.println("Error: --llamacpp needs a GGUF, and converting this safetensors checkpoint failed: "
                    + e.getMessage());
            return false;
        }
    }

    /** The local safetensors checkpoint the user pointed at, or {@code null} if it is not one. */
    private Path localSafetensorCheckpointForConversion() {
        for (String ref : new String[] { modelDir, modelFile, modelPath, modelId }) {
            Path p = resolveSafetensorCheckpointPath(ref);
            if (p != null && SafetensorCompatibilityPreflightValidator.isSafetensorCheckpointDir(p.toString())) {
                return p;
            }
        }
        if (modelId != null && !modelId.isBlank() && !looksLikeGgufPath(modelId)) {
            try {
                Optional<LocalModelIndex.Entry> indexed = findSafetensorIndexEntry(modelId);
                if (indexed.isPresent() && LocalModelIndex.find(modelId, "gguf").isEmpty()) {
                    Path p = resolveSafetensorCheckpointPath(indexed.get().path);
                    if (p != null && SafetensorCompatibilityPreflightValidator.isSafetensorCheckpointDir(p.toString())) {
                        return p;
                    }
                }
            } catch (Exception ignored) {
                // fall through: not an indexed safetensors model
            }
        }
        return null;
    }

    private boolean isSafetensorModelPath(String localPath) {
        Path p = resolveSafetensorCheckpointPath(localPath);
        return p != null && SafetensorCompatibilityPreflightValidator.isSafetensorCheckpointDir(p.toString());
    }

    /**
     * Cheap, pre-resolution check for "the user pointed at a safetensors model", used so an
     * explicit {@code --java}/{@code --llamacpp} is not mistaken for a GGUF-only request.
     * An indexed reference counts only if no GGUF entry exists for it (e.g. {@code hf:org/x-GGUF}
     * stays on the GGUF path).
     */
    private boolean requestsSafetensorModel() {
        if (format != null && format.toLowerCase(Locale.ROOT).contains("safetensor")) {
            return true;
        }
        if (modelDir != null && !modelDir.isBlank()) {
            return true;
        }
        for (String ref : new String[] { modelFile, modelPath, modelId }) {
            if (ref == null || ref.isBlank()) {
                continue;
            }
            if (ref.toLowerCase(Locale.ROOT).endsWith(".safetensors") || SafetensorCompatibilityPreflightValidator.isSafetensorCheckpointDir(ref)) {
                return true;
            }
        }
        if (modelId != null && !modelId.isBlank() && !looksLikeGgufPath(modelId)) {
            try {
                return findSafetensorIndexEntry(modelId).isPresent()
                        && LocalModelIndex.find(modelId, "gguf").isEmpty();
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }




    private boolean isGgufFormat(String value) {
        return ModelFormatResolver.isGgufFormat(value);
    }

    private boolean looksLikeGgufPath(String value) {
        return value != null && value.trim().toLowerCase(Locale.ROOT).endsWith(".gguf");
    }

    private String normalizeRequestedProvider(String rawProviderId) {
        return ModelFormatResolver.normalizeProvider(rawProviderId);
    }


    private record TtsVoiceListFilter(String language, String gender, List<String> terms) {
        private boolean hasFilters() {
            return language != null || gender != null || (terms != null && !terms.isEmpty());
        }

        private String describe() {
            List<String> parts = new ArrayList<>();
            if (language != null) {
                parts.add("lang=" + language);
            }
            if (gender != null) {
                parts.add("gender=" + gender);
            }
            if (terms != null && !terms.isEmpty()) {
                parts.add("search=" + String.join(" ", terms));
            }
            return parts.isEmpty() ? "(none)" : String.join(", ", parts);
        }
    }

    private boolean isProviderActive(String provider) {
        if (provider == null || provider.isBlank()) {
            return false;
        }
        try {
            java.util.List<Object> providers = java.util.List.of();
            if (providers == null || providers.isEmpty()) {
                return false;
            }
            String normalized = provider.trim().toLowerCase(Locale.ROOT);
            return providers.stream()
                    .map(Object::toString)
                    .filter(Objects::nonNull)
                    .map(id -> id.trim().toLowerCase(Locale.ROOT))
                    .anyMatch(normalized::equals);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isRuntimeRouteActive(String runtimeId) {
        if (isProviderActive(runtimeId)) {
            return true;
        }
        String normalized = runtimeId == null ? "" : runtimeId.trim().toLowerCase(Locale.ROOT);
        if (!"gguf".equals(normalized)) {
            return false;
        }
        try {
            return pluginChecker != null
                    && pluginChecker.getRunnerPluginIds().stream()
                    .map(id -> id == null ? "" : id.toLowerCase(Locale.ROOT))
                    .anyMatch(id -> id.equals("gguf")
                            || id.equals("gguf-runner")
                            || id.contains("gguf"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void printResponse(InferenceResponse response, long startTime) {
        Map<String, Object> metadata = effectiveExecutionRouteMetadata(response.getMetadata());
        if (RunResponseContentRenderer.renderStructured(
                response, metadata, jsonMode, ocrJsonOutput, ocrJsonOutputPath, ocrOverlayOutputPath,
                System.out, System.err)) {
        if (OcrOutputWriter.isPaddleOcrMetadata(metadata)) {
            OcrOutputWriter ocrOutputWriter = new OcrOutputWriter();
            Map<String, Object> ocrPayload = ocrOutputWriter.createPayload(response, metadata);
            Path overlayPath = ocrOutputWriter.writeOverlay(
                    ocrPayload, ocrOutputOptions.overlayOutputPath(), ocrOutputOptions.structuredJson() || jsonMode, System.out, System.err);
            if (overlayPath != null) {
                ocrPayload.put("overlay_path", overlayPath.toString());
            }
            ocrOutputWriter.writeJson(ocrPayload, ocrOutputOptions.jsonOutputPath(), ocrOutputOptions.structuredJson() || jsonMode, System.out, System.err);
            if (ocrOutputOptions.structuredJson() || jsonMode) {
                printJsonPayload(ocrPayload);
                return;
            }
        }
        if (jsonMode) {
            Map<String, Object> jsonResult = new LinkedHashMap<>();
            jsonResult.put("text", response.getContent());
            jsonResult.put("tokenCount", response.getTokensUsed());
            jsonResult.put("promptTokens", response.getInputTokens());
            jsonResult.put("durationMs", response.getDurationMs());
            printJsonPayload(jsonResult);
            return;
        }

        printExecutionRouteInfo(metadata);
        printQuantCacheInfo(metadata);
        printKvCacheQuantizationInfo(metadata);
        System.out.println();
        RunResponseContentRenderer.renderText(response, raw, debugPrompt, System.out);

        boolean audioResponse = metadata.containsKey("audio") || AudioRunStatsRenderer.isAudioMetadata(metadata);
        if (metadata.containsKey("audio")) {
            saveAudio(metadata.get("audio").toString(), audioDefaultExtension(metadata), metadata);
        } else if (metadata.containsKey("image")) {
            saveImage(metadata.get("image").toString(), metadata);
        }

        double seconds = Math.max(response.getDurationMs() / 1000.0, 0.001);
        double tps = response.getTokensUsed() / seconds;
        printCompletionStatsForOutput(
                response.getTokensUsed(),
                response.getDurationMs(),
                tps,
                ttftMillis(metadata),
                metadata,
                audioResponse);
        int outputTokens = response.getOutputTokens() > 0 ? response.getOutputTokens() : response.getTokensUsed();
        RouteBenchmarkProfileCoordinator.record(
                response.getModel(),
                response.getModel(),
                metadata,
                outputTokens,
                response.getDurationMs(),
                audioResponse,
                AudioRunStatsRenderer::isAudioMetadata);
                
        RunResponseTelemetryRecorder.record(metadata, outputTokens, response.getDurationMs(), tps);
    }

    private static Double ttftMillis(Map<String, Object> metadata) {
        return metadataDouble(metadata, "bench.ttft_ms");
    }

    private static Double ttftMillis(
            Map<String, Object> metadata,
            long streamStartTimeMs,
            java.util.concurrent.atomic.AtomicLong firstTokenTimeMs) {
        Double metadataTtft = ttftMillis(metadata);
        if (metadataTtft != null) {
            return metadataTtft;
        }
        long first = firstTokenTimeMs != null ? firstTokenTimeMs.get() : 0L;
        if (first <= 0L || first < streamStartTimeMs) {
            return null;
        }
        return (double) (first - streamStartTimeMs);
    }

    private static Map<String, Object> observedStreamMetrics(
            Map<String, Object> metadata,
            int outputTokens,
            long durationMs,
            Double ttftMs) {
        Map<String, Object> metrics = metadata == null || metadata.isEmpty()
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(metadata);
        if (outputTokens > 0) {
            metrics.putIfAbsent("tokens.output", outputTokens);
            double durationSeconds = Math.max(durationMs / 1000.0, 0.001);
            metrics.putIfAbsent("bench.generation_tps", outputTokens / durationSeconds);
            if (ttftMs != null) {
                metrics.putIfAbsent("bench.ttft_ms", ttftMs);
                if (outputTokens > 1 && durationMs > ttftMs) {
                    metrics.putIfAbsent("bench.tpot_ms", (durationMs - ttftMs) / (outputTokens - 1));
                }
            }
        } else if (ttftMs != null) {
            metrics.putIfAbsent("bench.ttft_ms", ttftMs);
        }
        return metrics;
    }

    private void printCompletionStatsForOutput(
            int tokenCount,
            long durationMs,
            double tps,
            Double ttftMs,
            Map<String, Object> metadata,
            boolean audioOutput) {
        if (audioOutput || AudioRunStatsRenderer.isAudioMetadata(metadata)) {
            printAudioRunStats(metadata, durationMs);
            return;
        }
        uiRenderer.printStats(tokenCount, durationMs / 1000.0, tps, ttftMs, noBanner || noInfo);
        uiRenderer.printBenchmarks(metadata, noBanner || noInfo);

    }

    private void printAudioRunStats(Map<String, Object> metadata, long fallbackDurationMs) {
        if (enableJsonSse || noBanner) {
            return;
        }
        AudioRunStatsRenderer.render(metadata, fallbackDurationMs).ifPresent(line ->
                System.out.println(ChatUIRenderer.DIM + line + ChatUIRenderer.RESET));
    }
    private static long observedStreamDurationMillis(
            long streamStartTimeMs,
            long completionTimeMs,
            java.util.concurrent.atomic.AtomicLong lastTokenTimeMs) {
        long last = lastTokenTimeMs != null ? lastTokenTimeMs.get() : 0L;
        if (last >= streamStartTimeMs) {
            return Math.max(1L, last - streamStartTimeMs);
        }
        return Math.max(1L, completionTimeMs - streamStartTimeMs);
    }

    private static Double metadataDouble(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String metadataText(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private void printExecutionRouteInfo(Map<String, Object> metadata) {
        ExecutionRouteTerminalRenderer.print(metadata, noBanner, System.out::println);
    }

    private Map<String, Object> effectiveExecutionRouteMetadata(Map<String, Object> responseMetadata) {
        return ExecutionRouteMetadata.effective(forcedExecutionRouteMetadata, responseMetadata);
    }

    private void printQuantCacheInfo(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        Object state = metadata.get("quant_cache_state");
        if (state == null) {
            return;
        }
        StringBuilder line = new StringBuilder("Quant cache: ").append(state);
        Object path = metadata.get("quant_cache_path");
        if (path != null) {
            line.append(" (").append(path).append(")");
        }
        System.out.println(line);
        System.out.println("--------------------------------------------------");
    }

    private void printKvCacheQuantizationInfo(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        Object state = metadata.get("kv_cache_quantization");
        if (state == null) {
            return;
        }
        System.out.println("Effective KV cache quantization: " + state);
        System.out.println("--------------------------------------------------");
    }

    private MediaOutputCliHandler mediaOutputHandler() {
        return new MediaOutputCliHandler(
                outputPath,
                extension,
                RunCommand::audioDefaultExtension,
                this::maybeWriteAudioMetadataSidecar,
                this::maybeWriteImageMetadataSidecar);
    }

    private void handleOutputs(
            java.io.ByteArrayOutputStream imageBuffer,
            StreamingAudioOutputSession audioOutput,
            Map<String, Object> metadata) {
        mediaOutputHandler().handleOutputs(imageBuffer, audioOutput, metadata);
    }

    private void closeAudioOutputQuietly(StreamingAudioOutputSession audioOutput) {
        MediaOutputCliHandler.closeAudioOutputQuietly(audioOutput);
    }

    private void saveBuffer(java.io.ByteArrayOutputStream buffer, String defaultExt, String label) {
        saveBuffer(buffer, defaultExt, label, null);
    }

    private void saveBuffer(
            java.io.ByteArrayOutputStream buffer,
            String defaultExt,
            String label,
            Map<String, Object> metadata) {
        mediaOutputHandler().saveBuffer(buffer, defaultExt, label, metadata);
    }




    private static double defaultedDouble(Map<String, Object> metadata, String key) {
        Double value = metadataDouble(metadata, key);
        return value == null ? 0.0 : value;
    }

    private void maybeWriteAudioMetadataSidecar(Path audioPath, Map<String, Object> metadata) {
        MediaMetadataSidecarCliHandler.maybeWriteAudioMetadataSidecar(new MediaMetadataSidecarCliHandler.AudioSidecarRequest(
                audioPath,
                mediaMetadataOptions.audioOutputPath(),
                mediaMetadataOptions.audioSidecar(),
                modelId,
                providerId,
                runner,
                prompt,
                effectiveTtsVoiceSelector(),
                audioDefaultExtension(metadata),
                audioMetadataReplayArgs(audioPath.toAbsolutePath().normalize(), metadata),
                metadata
        ));
    }

    private void maybeWriteImageMetadataSidecar(Path imagePath, Map<String, Object> metadata) {
        MediaMetadataSidecarCliHandler.maybeWriteImageMetadataSidecar(new MediaMetadataSidecarCliHandler.ImageSidecarRequest(
                imagePath,
                imageMetadataOptions.imageOutputPath(),
                imageMetadataOptions.imageSidecar(),
                modelId,
                providerId,
                runner,
                prompt,
                imageReplaySeed(metadata),
                imageGenerationOptions.steps(),
                imageGenerationOptions.guidanceScale(),
                imageGenerationOptions.width(),
                imageGenerationOptions.height(),
                metadata
        ));
    }

    private String imageReplaySeed(Map<String, Object> metadata) {
        for (String key : List.of("image_seed", "seed", "generation_seed")) {
            String metadataSeed = metadataText(metadata, key);
            if (metadataSeed != null && !metadataSeed.isBlank()) {
                return metadataSeed;
            }
        }
        return seed == null ? null : seed.toString();
    }

    private List<String> audioMetadataReplayArgs(Path audioPath, Map<String, Object> metadata) {
        return AudioMetadataReplayCommandBuilder.build(new AudioMetadataReplayCommandBuilder.Request(
                modelId,
                providerId,
                runner,
                prompt,
                effectiveTtsVoiceSelector(),
                audioDefaultExtension(metadata),
                replaySeed(metadata),
                audioPath == null ? null : audioPath.toString(),
                audioRunOptions.quality(),
                audioRunOptions.bitrateKbps(),
                audioRunOptions.channels(),
                audioRunOptions.codecDecode(),
                audioRunOptions.maxFrames(),
                audioRunOptions.maxSeconds(),
                audioRunOptions.gainDb(),
                audioRunOptions.peakDb(),
                audioRunOptions.fadeMs(),
                audioRunOptions.trimDbfs(),
                audioRunOptions.trimPaddingMs(),
                audioRunOptions.flacCompression(),                audioRunOptions.disablePolish(),
                audioRunOptions.disableNormalize(),
                audioRunOptions.trimSilence()));
    }

    private String replaySeed(Map<String, Object> metadata) {
        String metadataSeed = metadataText(metadata, "tts_seed");
        if (metadataSeed != null && !metadataSeed.isBlank()) {
            return metadataSeed;
        }
        return seed == null ? null : seed.toString();
    }



    private void saveAudio(String base64) {
        saveAudio(base64, "flac");
    }

    private void saveAudio(String base64, String defaultExt) {
        saveAudio(base64, defaultExt, null);
    }

    private void saveAudio(String base64, String defaultExt, Map<String, Object> metadata) {
        saveBufferFromBase64(base64, defaultExt, "Audio", metadata);
    }

    private void saveImage(String base64) {
        saveImage(base64, null);
    }

    private void saveImage(String base64, Map<String, Object> metadata) {
        saveBufferFromBase64(base64, "png", "Image", metadata);
    }

    private void saveBufferFromBase64(String base64, String defaultExt, String label) {
        saveBufferFromBase64(base64, defaultExt, label, null);
    }

    private void saveBufferFromBase64(
            String base64,
            String defaultExt,
            String label,
            Map<String, Object> metadata) {
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(base64);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            bos.write(decoded);
            saveBuffer(bos, defaultExt, label, metadata);
        } catch (Exception ignored) {}
    }
    private Path replaceExtension(Path path, String newExt) {
        return CliPathUtils.replaceExtension(path, newExt);
    }

    static String normalizeExtension(String ext) {
        return CliPathUtils.normalizeExtension(ext);
    }

    private static String audioDefaultExtension(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "flac";
        }
        Object format = metadata.get("audio_format");
        if (format == null) {
            format = metadata.get("audio_ext");
        }
        if (format == null) {
            Object mime = metadata.get("audio_mime");
            if (mime != null && String.valueOf(mime).toLowerCase(Locale.ROOT).contains("wav")) {
                return "wav";
            }
        }
        String ext = format == null ? "" : String.valueOf(format).trim().toLowerCase(Locale.ROOT);
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        return ext.isBlank() ? "flac" : ext;
    }

    static String pathExtension(Path path) {
        return CliPathUtils.pathExtension(path);
    }

    static boolean isKnownAudioExtension(String ext) {
        return CliPathUtils.isKnownAudioExtension(ext);
    }

    private void printOpenAiSseDelta(String requestId, String model, String delta) {
        long created = System.currentTimeMillis() / 1000L;
        String id = "chatcmpl-" + (requestId != null ? requestId : UUID.randomUUID().toString());
        String payload = String.format("{\"id\":\"%s\",\"object\":\"chat.completion.chunk\",\"created\":%d,\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"%s\"},\"finish_reason\":null}]}",
                escapeJson(id), created, escapeJson(model != null ? model : ""), escapeJson(delta));
        System.out.println("data: " + payload);
    }

    private void printOpenAiSseFinal(String requestId, String model) {
        long created = System.currentTimeMillis() / 1000L;
        String id = "chatcmpl-" + (requestId != null ? requestId : UUID.randomUUID().toString());
        String payload = String.format("{\"id\":\"%s\",\"object\":\"chat.completion.chunk\",\"created\":%d,\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}",
                escapeJson(id), created, escapeJson(model != null ? model : ""));
        System.out.println("data: " + payload);
        System.out.println("data: [DONE]");
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\r").replace("\t", "\\t");
    }

    private void printCompatibilityHintBeforeInference() {
        if (providerId == null || modelId == null) return;
        String provider = providerId.toLowerCase();
        String model = modelId.toLowerCase();
        if ("safetensor".equals(provider)) {
            if (model.contains("vlm") || model.contains("vision")) {
                System.err.println("Hint: 'safetensor' provider is text-oriented; VLM models may fail.");
            }
        } else if ("llamacpp".equals(provider) && !model.endsWith(".gguf")) {
            System.err.println("Hint: 'llamacpp' works best with GGUF models.");
        }
    }

    private void printProviderHintFromError(Throwable throwable) {
        String detail = throwable.getMessage() != null ? throwable.getMessage().toLowerCase() : "";
        if (detail.contains("429") || detail.contains("quota")) {
            System.err.println("Hint: Provider quota reached. Wait or switch provider.");
        }
    }

    private void requestProcessExit() {
        requestProcessExit(0);
    }

    private void requestProcessExit(int status) {
        try {
            Quarkus.asyncExit(status);
        } catch (Throwable ignored) {
        }
        System.exit(status);
    }

    private record ProviderLocalPathResolution(boolean ok, String localPath) {
    }
}