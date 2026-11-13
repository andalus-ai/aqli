package tech.kayys.aqli.protobuf;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.grpc.GrpcService;
import io.grpc.stub.StreamObserver;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kayys.aqli.registry.service.ModelRegistryService;

import java.util.stream.Collectors;

@GrpcService
public class ModelGrpcService extends ModelServiceGrpc.ModelServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(ModelGrpcService.class);

    @Inject
    ModelRegistryService registryService;

    @Inject
    MeterRegistry meterRegistry;

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
