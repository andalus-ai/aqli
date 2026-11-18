package tech.kayys.aqli.cli.commands.request;

import java.util.List;

import tech.kayys.aqli.cli.commands.audio.AudioRunRequestParameterMapper;
import tech.kayys.aqli.cli.commands.image.ImageRunRequestParameterMapper;
import tech.kayys.aqli.cli.commands.rag.RagRunRequestConfiguration;
import tech.kayys.aqli.cli.commands.text.TextRunRequestParameterMapper;
import tech.kayys.aqli.cli.commands.tools.ToolRunRequestConfiguration;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.tool.ToolDefinition;

/**
 * Composes the CLI run command's already-resolved inputs into one inference request.
 *
 * <p>This class is an orchestration boundary for CLI request assembly. It does not
 * resolve models, select execution routes, execute inference, or define capability
 * semantics. Capability mappings and runtime hints remain delegated to their
 * focused adapters; the transport-neutral SDK/application contract remains the
 * shared semantic contract for CLI, REST, and gRPC.</p>
 */
public final class RunInferenceRequestAssembler {

    private RunInferenceRequestAssembler() {
    }

    public record QuantizationOptions(String strategy, int bits, String kvStrategy) {
    }

    public record ConversationOptions(
            String sessionId,
            String systemPrompt,
            String retrievalSystemPrompt,
            String userPrompt) {
    }

    public record Options(
            RunRequestBaseAssembler.Options base,
            TextRunRequestParameterMapper.Options text,
            QuantizationOptions quantization,
            boolean profiling,
            RuntimeRoutingRunRequestConfiguration.Options routing,
            ImageRunRequestParameterMapper.Options image,
            AudioRunRequestParameterMapper.Options audio,
            String outputFormat,
            List<ToolDefinition> tools,
            Object toolChoice,
            RagRunRequestConfiguration.RagContext ragContext,
            String embeddingModel,
            ConversationOptions conversation,
            boolean bypassResponseCache) {
    }

    public static InferenceRequest assemble(Options options) {
        if (options == null) {
            throw new IllegalArgumentException("run inference request options are required");
        }

        InferenceRequest.Builder request = RunRequestBaseAssembler.create(options.base());
        TextRunRequestParameterMapper.apply(request, options.text());

        QuantizationOptions quantization = options.quantization();
        RuntimeQuantizationRequestConfiguration.apply(
                request,
                quantization.strategy(),
                quantization.bits(),
                quantization.kvStrategy());
        RuntimeProfilingRequestConfiguration.apply(request, options.profiling());

        RuntimeRoutingRunRequestConfiguration.apply(request, options.routing());
        ImageRunRequestParameterMapper.apply(request, options.image());
        AudioRunRequestParameterMapper.apply(request, options.audio());
        RuntimeOutputFormatRequestConfiguration.apply(request, options.outputFormat());

        ToolRunRequestConfiguration.apply(request, options.tools(), options.toolChoice());
        RagRunRequestConfiguration.apply(request, options.ragContext(), options.embeddingModel());

        ConversationOptions conversation = options.conversation();
        RunConversationRequestAssembler.apply(
                request,
                conversation.sessionId(),
                conversation.systemPrompt(),
                conversation.retrievalSystemPrompt(),
                conversation.userPrompt());

        RuntimeResponseCacheRequestConfiguration.apply(request, options.bypassResponseCache());
        return request.build();
    }
}
