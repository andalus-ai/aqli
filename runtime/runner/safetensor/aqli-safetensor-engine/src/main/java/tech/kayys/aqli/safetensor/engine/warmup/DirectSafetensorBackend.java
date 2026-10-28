/*
 * Aqli Inference Engine - SafeTensor Module
 * Copyright (c) 2026 Kayys.tech
 * SPDX-License-Identifier: Apache-2.0
 */
package tech.kayys.aqli.safetensor.engine.warmup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import tech.kayys.aljabr.safetensor.generation.GenerationConfig;
import tech.kayys.aljabr.spi.Message;
import tech.kayys.aljabr.spi.inference.InferenceRequest;
import tech.kayys.aljabr.spi.inference.InferenceResponse;
import tech.kayys.aljabr.spi.inference.LocalInferenceEngine;
import tech.kayys.aljabr.spi.inference.StreamingInferenceChunk;
import tech.kayys.aljabr.spi.model.HealthStatus;
import tech.kayys.aljabr.spi.tokenizer.ChatTemplateFormatter;
import tech.kayys.aqli.safetensor.engine.generation.DirectInferenceEngine;
import tech.kayys.aqli.safetensor.engine.route.DirectSafetensorRunProfile;
import tech.kayys.aqli.safetensor.engine.route.DirectSafetensorTextPolicy;

/**
 * Pure-Java safetensors backend: runs a HuggingFace checkpoint directory (or a single
 * {@code .safetensors} file inside one) on {@link DirectInferenceEngine}. No libtorch, no GGUF
 * conversion, no llama.cpp.
 *
 * <p>The model architecture is not decided here. {@code DirectInferenceEngine} resolves it through
 * the model-family plugin registry from the checkpoint's own {@code config.json}, so this class
 * stays architecture-neutral. It mirrors the CLI's direct-run driver
 * ({@code RunCommand#runDirectSafetensorCompletion}) so a request through this backend and
 * {@code aqli run --java} behave the same.
 *
 * <p>Request mapping:
 * <ul>
 *   <li>checkpoint: {@code model_path} / {@code modelPath} parameter or metadata entry, else
 *       {@link InferenceRequest#getModel()} when it is a checkpoint directory or
 *       {@code .safetensors} file;</li>
 *   <li>sampling: {@code temperature}, {@code topK}, {@code topP}, {@code repeatPenalty},
 *       {@code maxTokens} from the request, {@code seed} from parameters;</li>
 *   <li>optional parameters: {@code quantize} (weight quantisation strategy) and
 *       {@code quantize_kv} / {@code kv_cache_quant} ({@code int8}, {@code int4},
 *       {@code turbo});</li>
 *   <li>prompt: a single system+user turn (or {@code getPrompt()}) goes through
 *       {@link DirectSafetensorTextPolicy#preparePrompt}, exactly like the CLI; longer
 *       conversations are formatted with the model family's chat template.</li>
 * </ul>
 *
 * <p>Checkpoints on the guarded native-BF16 profile (Gemma 4 unified) are generated in one shot and
 * returned sanitised, because the channel-marker clean-up is a whole-text operation and cannot be
 * applied to individual streamed deltas.
 */
@ApplicationScoped
public class DirectSafetensorBackend implements LocalInferenceEngine {

    private static final Logger LOG = Logger.getLogger(DirectSafetensorBackend.class);

    private static final int DEFAULT_MAX_TOKENS = 256;

    @Inject
    Instance<DirectInferenceEngine> engines;

    // ------------------------------------------------------------------ lifecycle / health

    @Override
    public void initialize() {
        // The engine is resolved lazily; nothing to warm up here.
    }

    @Override
    public void shutdown() {
        // Model residency is owned by DirectInferenceEngine.
    }

    public String name() {
        return "DirectSafetensorBackend";
    }

    @Override
    public boolean isHealthy() {
        return engines != null && engines.isResolvable();
    }

    @Override
    public HealthStatus health() {
        return isHealthy()
                ? HealthStatus.healthy("Pure-Java direct safetensors engine available")
                : HealthStatus.unhealthy("DirectInferenceEngine bean is not available");
    }

    // ------------------------------------------------------------------ inference

    @Override
    public Uni<InferenceResponse> infer(InferenceRequest request) {
        final Plan plan;
        final DirectInferenceEngine engine;
        try {
            plan = plan(request);
            engine = engine();
        } catch (RuntimeException e) {
            return Uni.createFrom().failure(e);
        }
        return loaded(engine, plan)
                .chain(p -> engine.generate(p.prompt(), p.modelPath(), p.config()))
                .map(response -> finish(response, plan, request));
    }

    @Override
    public Multi<StreamingInferenceChunk> stream(InferenceRequest request) {
        final Plan plan;
        final DirectInferenceEngine engine;
        try {
            plan = plan(request);
            engine = engine();
        } catch (RuntimeException e) {
            return Multi.createFrom().failure(e);
        }
        final String requestId = requestId(request);

        if (plan.profile().nativeBf16Matvec()) {
            // Whole-text sanitising cannot run per delta: generate, clean, emit once.
            return loaded(engine, plan)
                    .chain(p -> engine.generate(p.prompt(), p.modelPath(), p.config()))
                    .map(response -> finish(response, plan, request))
                    .onItem().transformToMulti(response -> Multi.createFrom().items(
                            StreamingInferenceChunk.textDelta(requestId, 0, nz(response.getContent())),
                            StreamingInferenceChunk.finalChunk(requestId, 1, "")));
        }

        final AtomicInteger index = new AtomicInteger();
        final AtomicBoolean finished = new AtomicBoolean();
        return loaded(engine, plan)
                .onItem().transformToMulti(p -> engine.generateStream(p.prompt(), p.modelPath(), p.config()))
                .map(response -> {
                    if (response.getFinishReason() != null) {
                        // Final element: the deltas already carried the text, do not repeat it.
                        finished.set(true);
                        return StreamingInferenceChunk.finalChunk(requestId, index.getAndIncrement(), "");
                    }
                    return StreamingInferenceChunk.textDelta(requestId, index.getAndIncrement(),
                            nz(response.getContent()));
                })
                .onCompletion().switchTo(() -> finished.get()
                        ? Multi.createFrom().<StreamingInferenceChunk>empty()
                        : Multi.createFrom().item(
                                StreamingInferenceChunk.finalChunk(requestId, index.getAndIncrement(), "")));
    }

    /** Same stream, expressed in the aqli SPI chunk type (kept for callers written against it). */
    public Multi<tech.kayys.aqli.spi.inference.StreamingInferenceChunk> inferStream(InferenceRequest request) {
        return stream(request).map(DirectSafetensorBackend::toAqliChunk);
    }

    // ------------------------------------------------------------------ request planning

    /** Everything derived from a request before the engine is touched. */
    record Plan(Path modelPath,
            DirectSafetensorRunProfile profile,
            GenerationConfig config,
            String prompt,
            String quantizeStrategy) {
    }

    Plan plan(InferenceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        Path modelPath = resolveRequestCheckpoint(request);
        DirectSafetensorRunProfile profile = DirectSafetensorRunProfile.load(modelPath);

        double temperature = request.getTemperature();
        int topK = request.getTopK();
        double topP = request.getTopP();
        int maxTokens = request.getMaxTokens() > 0 ? request.getMaxTokens() : DEFAULT_MAX_TOKENS;
        double requestedPenalty = request.getRepeatPenalty() > 0 ? request.getRepeatPenalty() : 1.0d;

        Object seedParam = param(request, "seed");
        long seed = seedParam instanceof Number n ? n.longValue() : -1L;

        GenerationConfig config = GenerationConfig.builder()
                .maxNewTokens(maxTokens)
                .strategy(samplingStrategy(temperature, topK, topP))
                .temperature((float) temperature)
                .topK(topK)
                .topP((float) topP)
                .repetitionPenalty(DirectSafetensorTextPolicy.normalizeRepeatPenalty(profile, requestedPenalty))
                .kvCacheQuant(parseKvQuant(asString(param(request, "quantize_kv", "kv_cache_quant"))))
                .seed(seed)
                .build();

        String quantize = asString(param(request, "quantize"));
        return new Plan(modelPath, profile, config, buildPrompt(request, profile), quantize);
    }

    /** Checkpoint directory from an explicit path parameter, else from the request's model field. */
    private static Path resolveRequestCheckpoint(InferenceRequest request) {
        String explicit = asString(param(request, "model_path", "modelPath"));
        Path resolved = resolveCheckpointPath(explicit != null ? explicit : request.getModel());
        if (resolved == null) {
            throw new IllegalArgumentException(
                    "Direct safetensors backend needs a checkpoint directory or .safetensors file "
                            + "(request model='" + request.getModel()
                            + "'; pass it as the model or as the 'model_path' parameter)");
        }
        return resolved;
    }

    /**
     * A directory is used as is; a {@code .safetensors} file resolves to its parent directory
     * (config.json and tokenizer.json live there). Anything else is not a checkpoint.
     */
    static Path resolveCheckpointPath(String localPath) {
        if (localPath == null || localPath.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(localPath).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                return path;
            }
            if (!Files.isRegularFile(path)
                    || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".safetensors")) {
                return null;
            }
            Path parent = path.getParent();
            return parent != null && Files.isDirectory(parent) ? parent : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Same decision table as the CLI's {@code resolveDirectSamplingStrategy}. */
    static GenerationConfig.SamplingStrategy samplingStrategy(double temperature, int topK, double topP) {
        if (temperature < 1.0e-4d || topK == 1) {
            return GenerationConfig.SamplingStrategy.GREEDY;
        }
        boolean hasTopK = topK > 0;
        boolean hasTopP = topP > 0.0d && topP < 1.0d;
        if (hasTopK && hasTopP) {
            return GenerationConfig.SamplingStrategy.TOP_K_TOP_P;
        }
        if (hasTopP) {
            return GenerationConfig.SamplingStrategy.TOP_P;
        }
        if (hasTopK) {
            return GenerationConfig.SamplingStrategy.TOP_K;
        }
        return GenerationConfig.SamplingStrategy.GREEDY;
    }

    static GenerationConfig.KvCacheQuantization parseKvQuant(String value) {
        if ("int8".equalsIgnoreCase(value)) {
            return GenerationConfig.KvCacheQuantization.INT8;
        }
        if ("int4".equalsIgnoreCase(value) || "turbo".equalsIgnoreCase(value)) {
            return GenerationConfig.KvCacheQuantization.INT4;
        }
        return GenerationConfig.KvCacheQuantization.NONE;
    }

    /**
     * One system+user turn (or a bare prompt) is prepared exactly like the CLI does. Longer
     * conversations use the family chat template; a model family without one is reported rather
     * than guessed at.
     */
    static String buildPrompt(InferenceRequest request, DirectSafetensorRunProfile profile) {
        List<Message> messages = request.getMessages() == null ? List.of() : request.getMessages();
        if (messages.isEmpty()) {
            String prompt = request.getPrompt();
            if (prompt == null || prompt.isBlank()) {
                throw new IllegalArgumentException("Request has neither messages nor a prompt");
            }
            return DirectSafetensorTextPolicy.preparePrompt(profile, null, prompt);
        }

        String system = null;
        List<Message> turns = new ArrayList<>();
        for (Message m : messages) {
            if (m.getRole() == Message.Role.SYSTEM && system == null) {
                system = m.getContent();
            } else {
                turns.add(m);
            }
        }
        boolean singleUserTurn = turns.size() == 1 && turns.get(0).getRole() == Message.Role.USER;
        if (singleUserTurn) {
            return DirectSafetensorTextPolicy.preparePrompt(profile, system, turns.get(0).getContent());
        }
        if (turns.isEmpty()) {
            throw new IllegalArgumentException("Request has a system message but no user message");
        }

        String modelType = profile.modelType();
        boolean templated = !profile.nativeBf16Matvec() && modelType != null && !modelType.isBlank()
                && (profile.requiresChatTemplateFormatting() || ChatTemplateFormatter.supportsModelType(modelType));
        if (!templated) {
            throw new IllegalArgumentException(
                    "Multi-turn conversation needs a chat template for model type '" + modelType
                            + "', which is not available for this checkpoint; send a single prompt instead");
        }
        return ChatTemplateFormatter.format(messages, modelType);
    }

    // ------------------------------------------------------------------ helpers

    private DirectInferenceEngine engine() {
        if (engines == null || !engines.isResolvable()) {
            throw new IllegalStateException(
                    "DirectInferenceEngine is not available; the pure-Java safetensors engine cannot run");
        }
        return engines.get();
    }

    /** Loads (or reuses) the checkpoint off the caller thread; model load is blocking and heavy. */
    private Uni<Plan> loaded(DirectInferenceEngine engine, Plan plan) {
        return Uni.createFrom().item(() -> {
            LOG.debugf("Direct safetensors: loading %s", plan.modelPath());
            engine.loadModel(plan.modelPath(), (Path) null, plan.quantizeStrategy());
            return plan;
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private static InferenceResponse finish(InferenceResponse response, Plan plan, InferenceRequest request) {
        InferenceResponse sanitized = DirectSafetensorTextPolicy.sanitizeResponse(response, plan.profile());
        String id = request.getRequestId();
        return id == null || id.isBlank() ? sanitized : sanitized.toBuilder().requestId(id).build();
    }

    private static tech.kayys.aqli.spi.inference.StreamingInferenceChunk toAqliChunk(StreamingInferenceChunk c) {
        String delta = nz(c.delta());
        if (!c.finished()) {
            return tech.kayys.aqli.spi.inference.StreamingInferenceChunk.textDelta(c.requestId(), c.index(), delta);
        }
        return "error".equals(c.finishReason())
                ? tech.kayys.aqli.spi.inference.StreamingInferenceChunk.errorChunk(c.requestId(), c.index(), delta)
                : tech.kayys.aqli.spi.inference.StreamingInferenceChunk.finalChunk(c.requestId(), c.index(), delta);
    }

    private static String requestId(InferenceRequest request) {
        String id = request.getRequestId();
        return id == null || id.isBlank() ? "direct-" + System.nanoTime() : id;
    }

    /** First non-null value among the given keys, looking at parameters before metadata. */
    private static Object param(InferenceRequest request, String... keys) {
        Object fromParameters = lookup(request.getParameters(), keys);
        return fromParameters != null ? fromParameters : lookup(request.getMetadata(), keys);
    }

    private static Object lookup(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String asString(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
