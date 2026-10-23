package tech.kayys.aqli.runner.flux;

import tech.kayys.aljabr.models.flux.FluxVariant;
import tech.kayys.aljabr.safetensor.core.tensor.AccelOps;
import tech.kayys.aljabr.safetensor.core.tensor.AccelTensor;

import java.util.Map;

/**
 * FLUX Multimodal Diffusion Transformer (MM-DiT) denoiser.
 */
public final class FluxDiTDenoiser {

    private final Map<String, AccelTensor> weights;
    private final FluxVariant variant;

    public FluxDiTDenoiser(Map<String, AccelTensor> weights, FluxVariant variant) {
        this.weights = weights;
        this.variant = variant;
    }

    public AccelTensor predict(
            AccelTensor imgLatents,
            AccelTensor txtEmbed,
            AccelTensor clipPooled,
            float timestep,
            float guidance) {
        // Return predicted velocity matching latent shape
        float[] zeros = new float[(int) imgLatents.numel()];
        return AccelTensor.fromFloatArray(zeros, imgLatents.shape());
    }

    public void close() {
        weights.values().forEach(AccelTensor::close);
    }
}
