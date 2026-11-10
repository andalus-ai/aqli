package tech.kayys.aqli.sdk.api;

/** Network or in-process transport used by an AQLI client implementation. */
public enum ClientTransport {
    IN_PROCESS,
    GRPC,
    REST
}
