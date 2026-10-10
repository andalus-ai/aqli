package tech.kayys.aqli.provider.onnx;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class OnnxProviderConfig {

    @ConfigProperty(name = "aqli.providers.onnx.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "aqli.providers.onnx.model-base-path", defaultValue = "${user.home}/.aqli/models")
    String modelBasePath;

    @ConfigProperty(name = "aqli.providers.onnx.threads", defaultValue = "4")
    int threads;

    public boolean enabled() {
        return enabled;
    }

    public String modelBasePath() {
        // Resolve ${user.home} manually if needed, though MicroProfile Config often handles it
        return modelBasePath.replace("${user.home}", System.getProperty("user.home"));
    }

    public int threads() {
        return threads;
    }
}
