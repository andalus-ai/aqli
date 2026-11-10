package tech.kayys.aqli.sdk.api;

/**
 * @deprecated Use {@link tech.kayys.aqli.sdk.AqliSdk}. This package-level
 * compatibility type will be removed in a future major release.
 */
@Deprecated(forRemoval = false)
public interface AqliSdk extends tech.kayys.aqli.sdk.AqliSdk {
    /**
     * @deprecated Use {@link tech.kayys.aqli.sdk.AqliSdk#builder()}.
     */
    @Deprecated(forRemoval = false)
    static AqliSdkBuilder builder() {
        return new AqliSdkBuilder();
    }
}
