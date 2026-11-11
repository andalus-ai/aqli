package tech.kayys.aqli.sdk.api;

/**
 * Service-provider boundary implemented by optional SDK adapter modules.
 *
 * <p>This SPI lives beside the public client contract so the API artifact can
 * discover an implementation without depending on local engines or transports.</p>
 */
public interface AqliClientProvider {
    AqliClient.Builder createBuilder();
}
