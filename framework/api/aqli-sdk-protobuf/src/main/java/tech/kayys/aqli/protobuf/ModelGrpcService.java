package tech.kayys.aqli.protobuf;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.grpc.GrpcService;
import io.grpc.stub.StreamObserver;
import io.grpc.Status;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kayys.aqli.registry.service.ModelRegistryService;
import tech.kayys.aqli.sdk.model.ModelPullRequest;
import tech.kayys.aqli.sdk.model.PullProgress;
import tech.kayys.aqli.sdk.runtime.AqliRuntime;
import java.util.concurrent.CompletableFuture;

import java.util.stream.Collectors;

@GrpcService
public class ModelGrpcService extends ModelServiceGrpc.ModelServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(ModelGrpcService.class);

    @Inject
    ModelRegistryService registryService;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    AqliRuntime runtime;

    @Override
    public void pullModel(PullModelRequest request, StreamObserver<PullModelProgress> observer) {
        if (request.getModelSpec() == null || request.getModelSpec().isBlank()) {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription("model_spec is required")
                    .asRuntimeException());
            return;
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        meterRegistry.counter("aqli.grpc.model.pull.count").increment();
        ModelPullRequest pullRequest = ModelPullRequest.builder()
                .modelSpec(request.getModelSpec())
                .revision(blankToNull(request.getRevision()))
                .format(blankToNull(request.getFormat()))
                .force(request.getForce())
                .build();

        CompletableFuture.runAsync(() -> {
            try {
                runtime.pullModel(pullRequest, progress -> observer.onNext(toGrpcProgress(progress)));
                sample.stop(meterRegistry.timer("aqli.grpc.model.pull.duration"));
                observer.onCompleted();
            } catch (Exception error) {
                sample.stop(meterRegistry.timer("aqli.grpc.model.pull.duration"));
                meterRegistry.counter("aqli.grpc.model.pull.error").increment();
                LOG.error("Failed to pull model {}", request.getModelSpec(), error);
                observer.onError(Status.INTERNAL
                        .withDescription("Model pull failed: " + error.getMessage())
                        .withCause(error)
                        .asRuntimeException());
            }
        });
    }

    private static PullModelProgress toGrpcProgress(PullProgress progress) {
        PullModelProgress.Builder response = PullModelProgress.newBuilder()
                .setStatus(progress.getStatus() == null ? "" : progress.getStatus())
                .setTotal(progress.getTotal())
                .setCompleted(progress.getCompleted())
                .setPercentComplete(progress.getPercentComplete())
                .setComplete(progress.isComplete());
        if (progress.getDigest() != null) {
            response.setDigest(progress.getDigest());
        }
        return response.build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Override
    public void listModels(ListModelsRequest request, StreamObserver<ListModelsResponse> observer) {
        Timer.Sample sample = Timer.start(meterRegistry);
        LOG.info("Received listModels request with query: {}", request.getQuery());
        
        GrpcResponseObserver.respond(registryService.findByTenant("default", null)
                .map(models -> {
                    sample.stop(meterRegistry.timer("aqli.grpc.model.list.duration"));
                    meterRegistry.counter("aqli.grpc.model.list.count").increment();
                    LOG.info("Returning {} models for listModels request", models.size());
                    
                    return ListModelsResponse.newBuilder()
                            .addAllModels(models.stream()
                                    .map(m -> ModelMetadata.newBuilder()
                                            .setId(m.modelId())
                                            .setName(m.name())
                                            .setDescription(m.architecture() != null ? m.architecture() : "")
                                            .setFormat(m.artifacts() != null && !m.artifacts().isEmpty() ? m.artifacts().keySet().iterator().next().name() : "UNKNOWN")
                                            .build())
                                    .collect(Collectors.toList()))
                            .build();
                })
                .onFailure().invoke(th -> {
                    LOG.error("Failed listModels request", th);
                    meterRegistry.counter("aqli.grpc.model.list.error").increment();
                }), observer);
    }

    @Override
    public void getModelManifest(GetModelManifestRequest request, StreamObserver<GetModelManifestResponse> observer) {
        Timer.Sample sample = Timer.start(meterRegistry);
        LOG.info("Received getModelManifest request for model: {}", request.getModelId());
        
        GrpcResponseObserver.respond(registryService.getManifest("default", request.getModelId(), "latest")
                .map(manifest -> {
                    sample.stop(meterRegistry.timer("aqli.grpc.model.manifest.duration"));
                    meterRegistry.counter("aqli.grpc.model.manifest.count").increment();
                    LOG.info("Returning manifest for model: {}", request.getModelId());
                    
                    return GetModelManifestResponse.newBuilder()
                            .setManifestJson(manifest.toString())
                            .build();
                })
                .onFailure().invoke(th -> {
                    LOG.error("Failed getModelManifest request for model: {}", request.getModelId(), th);
                    meterRegistry.counter("aqli.grpc.model.manifest.error").increment();
                }), observer);
    }
}
