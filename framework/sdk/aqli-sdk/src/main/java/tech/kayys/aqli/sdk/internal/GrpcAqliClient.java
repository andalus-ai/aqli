package tech.kayys.aqli.sdk.internal;

import tech.kayys.aqli.sdk.api.AqliClient;
import tech.kayys.alkhawarizm.spi.model.ModelInfo;
import tech.kayys.aqli.grpc.InferenceServiceGrpc;
import tech.kayys.aqli.grpc.GenerateRequest;
import tech.kayys.aqli.grpc.GenerateResponse;
import tech.kayys.aqli.grpc.EmbedRequest;
import tech.kayys.aqli.grpc.EmbedResponse;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GrpcAqliClient implements AqliClient {

    private final ManagedChannel channel;
    private final InferenceServiceGrpc.InferenceServiceBlockingStub blockingStub;
    private final InferenceServiceGrpc.InferenceServiceStub asyncStub;
    
    private final String model;
    private final String backend;
    private final int maxTokens;
    private final float temperature;

    public GrpcAqliClient(String endpoint, String model, String backend, int maxTokens, float temperature) {
        this.model = model;
        this.backend = backend;
        this.maxTokens = maxTokens;
        this.temperature = temperature;

        // Parse endpoint (e.g., "grpc://localhost:9090" or "localhost:9090")
        String host = endpoint.replace("grpc://", "").replace("http://", "");
        
        this.channel = ManagedChannelBuilder.forTarget(host)
                .usePlaintext() // Assume plaintext for now
                .build();
                
        this.blockingStub = InferenceServiceGrpc.newBlockingStub(channel);
        this.asyncStub = InferenceServiceGrpc.newStub(channel);
    }

    @Override
    public GenerationResult generate(String prompt) {
        return generate(tech.kayys.aqli.sdk.api.AqliClient.GenerationRequest.of(prompt));
    }

    @Override
    public GenerationResult generate(tech.kayys.aqli.sdk.api.AqliClient.GenerationRequest request) {
        GenerateRequest grpcRequest = GenerateRequest.newBuilder()
                .setPrompt(request.prompt())
                .setModel(model)
                .setMaxTokens(request.maxTokens() > 0 ? request.maxTokens() : maxTokens)
                .setTemperature(request.temperature() > 0 ? request.temperature() : temperature)
                .build();

        GenerateResponse response = blockingStub.generate(grpcRequest);

        return new GenerationResult(
                response.getText(),
                response.getTokenCount(),
                response.getPromptTokens(),
                response.getDurationMs()
        );
    }

    @Override
    public GenerationStream generateStream(String prompt) {
        tech.kayys.aqli.sdk.api.AqliClient.GenerationRequest request = 
            tech.kayys.aqli.sdk.api.AqliClient.GenerationRequest.of(prompt);
            
        GenerateRequest grpcRequest = GenerateRequest.newBuilder()
                .setPrompt(request.prompt())
                .setModel(model)
                .setMaxTokens(request.maxTokens() > 0 ? request.maxTokens() : maxTokens)
                .setTemperature(request.temperature() > 0 ? request.temperature() : temperature)
                .build();

        EmbeddedGenerationStream stream = new EmbeddedGenerationStream();
        
        asyncStub.generateStream(grpcRequest, new StreamObserver<GenerateResponse>() {
            @Override
            public void onNext(GenerateResponse value) {
                stream.emitToken(value.getText());
            }

            @Override
            public void onError(Throwable t) {
                stream.emitError(t);
            }

            @Override
            public void onCompleted() {
                stream.emitComplete();
            }
        });

        return stream;
    }

    @Override
    public List<GenerationResult> generateBatch(List<String> prompts) {
        return prompts.stream().map(this::generate).toList();
    }

    @Override
    public float[] embed(String text) {
        EmbedRequest request = EmbedRequest.newBuilder()
                .setText(text)
                .setModel(model)
                .build();
                
        EmbedResponse response = blockingStub.embed(request);
        
        float[] vector = new float[response.getVectorCount()];
        for (int i = 0; i < response.getVectorCount(); i++) {
            vector[i] = response.getVector(i);
        }
        return vector;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    @Override
    public ModelInfo modelInfo() {
        return ModelInfo.builder().modelId(model).format(backend).build();
    }

    @Override
    public boolean supports(Feature feature) {
        return feature == Feature.STREAMING || feature == Feature.BATCH_INFERENCE;
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.shutdown();
        }
    }
}
