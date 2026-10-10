package tech.kayys.aqli.inference.libtorch.config;

import java.util.Optional;

public interface NativeConfig {
    /**
     * Path to the LibTorch shared library directory.
     * If not set, defaults to AQLI_LIBTORCH_LIB_PATH (CLI config) and then
     * loader fallbacks like LIBTORCH_PATH / ~/.aqli/source/vendor/libtorch.
     */
    Optional<String> libraryPath();
}
