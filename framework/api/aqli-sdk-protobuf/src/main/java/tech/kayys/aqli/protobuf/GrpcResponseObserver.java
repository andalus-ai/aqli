package tech.kayys.aqli.protobuf;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.smallrye.mutiny.Uni;

final class GrpcResponseObserver {
    private GrpcResponseObserver() {}
    static <T> void respond(Uni<T> result, StreamObserver<T> observer) {
        result.subscribe().with(value -> {
            observer.onNext(value);
            observer.onCompleted();
        }, observer::onError);
    }
    static <T> void unimplemented(String method, StreamObserver<T> observer) {
        observer.onError(Status.UNIMPLEMENTED.withDescription(method + " is not implemented").asRuntimeException());
    }
}