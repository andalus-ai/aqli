package tech.kayys.aqli.inference.libtorch.nn;

import tech.kayys.aqli.inference.libtorch.core.TorchTensor;
import tech.kayys.aljabr.core.tensor.Tensor;

/**
 * Sigmoid activation module wrapper.
 * Equivalent to {@code libtorch::nn::Sigmoid}.
 */
public class Sigmoid extends Module {

    @Override
    public TorchTensor forward(TorchTensor input) {
        return Functional.sigmoid(input);
    }

    @Override
    public String toString() {
        return "Sigmoid()";
    }
}
