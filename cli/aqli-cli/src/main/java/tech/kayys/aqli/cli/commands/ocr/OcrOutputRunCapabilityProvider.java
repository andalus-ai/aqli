package tech.kayys.aqli.cli.commands.ocr;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for the OCR result-output CLI capability. */
public final class OcrOutputRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "ocr-output";
    }

    @Override
    public RunCapability create() {
        return new OcrOutputRunCapability();
    }
}
