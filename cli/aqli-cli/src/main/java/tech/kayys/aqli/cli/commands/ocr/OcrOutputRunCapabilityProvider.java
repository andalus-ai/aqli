package tech.kayys.aqli.cli.commands.ocr;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for OCR output CLI options. */
public final class OcrOutputRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public RunCapability capability() {
        return new OcrOutputRunCapability();
    }
}
