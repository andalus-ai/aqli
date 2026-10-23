package tech.kayys.aqli.cli;

import io.quarkus.runtime.annotations.RegisterForReflection;
import tech.kayys.aljabr.spi.model.ModelConfig;
import tech.kayys.aljabr.safetensor.loader.SafetensorHeader;
import tech.kayys.aljabr.safetensor.loader.SafetensorTensorInfo;

@RegisterForReflection(targets = {
    ModelConfig.class,
    ModelConfig.RopeScaling.class,
    SafetensorHeader.class,
    SafetensorTensorInfo.class
})
public class NativeRegistrations {
}
