package tech.kayys.aqli.sdk.api;

/**
 * Legacy client connection selection.
 *
 * @deprecated Use {@link ClientTransport}; CLI process execution is not a
 * transport for the general-purpose SDK.
 */
@Deprecated(since = "0.2", forRemoval = false)
public enum DeploymentMode {
    EMBEDDED,
    GRPC,
    REST,
    CLI
}
