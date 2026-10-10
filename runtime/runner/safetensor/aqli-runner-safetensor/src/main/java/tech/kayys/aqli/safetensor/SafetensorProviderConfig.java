package tech.kayys.aqli.safetensor;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "safetensor.provider")
public interface SafetensorProviderConfig {

    @WithDefault("true")
    boolean enabled();

    @WithDefault("${user.home}/.aqli/models/safetensors")
    String basePath();

    @WithDefault(".safetensors,.safetensor")
    String extensions();

    @WithDefault("direct")
    String backend();

    @WithDefault("${user.home}/.aqli/models/gguf/converted")
    String ggufOutputDir();
}
