package tech.kayys.aqli.inference.libtorch.config;

import io.smallrye.config.WithDefault;

public interface ModelConfig {
    /**
     * Base directory for TorchScript model files.
     */
    @WithDefault("${user.home}/.aqli/models/libtorchscript")
    String basePath();

    /**
     * Supported file extensions.
     */
    @WithDefault(".pt,.pts,.pth,.bin")
    String extensions();
}
