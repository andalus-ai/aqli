package tech.kayys.aqli.protobuf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.grpc.stub.StreamObserver;
import tech.kayys.aqli.sdk.model.ModelPullRequest;
import tech.kayys.aqli.sdk.model.PullProgress;
import tech.kayys.aqli.sdk.runtime.AqliRuntime;

class ModelGrpcServiceTest {

    private ModelGrpcService service;
    private AqliRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = mock(AqliRuntime.class);
        service = new ModelGrpcService();
        service.runtime = runtime;
        service.meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void streamsModelPullProgressThroughTheSharedRuntimeContract() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        List<PullModelProgress> events = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        doAnswer(invocation -> {
            Consumer<PullProgress> callback = invocation.getArgument(1);
            callback.accept(PullProgress.of("Downloading org/model", null, 100, 25));
            callback.accept(PullProgress.of("Completed org/model", null, 100, 100));
            return null;
        }).when(runtime).pullModel(any(ModelPullRequest.class), any());

        service.pullModel(PullModelRequest.newBuilder()
                .setModelSpec("hf:org/model")
                .setRevision("v2")
                .setFormat("onnx")
                .setForce(true)
                .build(), new StreamObserver<>() {
                    @Override
                    public void onNext(PullModelProgress value) {
                        events.add(value);
                    }

                    @Override
                    public void onError(Throwable error) {
                        failure.set(error);
                        completed.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        completed.countDown();
                    }
                });

        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertNull(failure.get());
        assertEquals(2, events.size());
        assertEquals("Downloading org/model", events.get(0).getStatus());
        assertEquals(25, events.get(0).getPercentComplete());
        assertTrue(events.get(1).getComplete());

        org.mockito.ArgumentCaptor<ModelPullRequest> request =
                org.mockito.ArgumentCaptor.forClass(ModelPullRequest.class);
        verify(runtime).pullModel(request.capture(), any());
        assertEquals("hf:org/model", request.getValue().modelSpec());
        assertEquals("v2", request.getValue().revision());
        assertEquals("onnx", request.getValue().format());
        assertTrue(request.getValue().force());
    }

    @Test
    void rejectsMissingModelSpecBeforeStartingBackgroundWork() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        service.pullModel(PullModelRequest.getDefaultInstance(), new StreamObserver<>() {
            @Override
            public void onNext(PullModelProgress value) {
            }

            @Override
            public void onError(Throwable error) {
                failure.set(error);
            }

            @Override
            public void onCompleted() {
            }
        });

        assertTrue(failure.get() instanceof io.grpc.StatusRuntimeException);
        assertEquals(io.grpc.Status.Code.INVALID_ARGUMENT,
                ((io.grpc.StatusRuntimeException) failure.get()).getStatus().getCode());
    }
}
