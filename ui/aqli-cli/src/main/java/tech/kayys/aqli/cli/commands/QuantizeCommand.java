package tech.kayys.aqli.cli.commands;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import java.io.IOException;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tech.kayys.aqli.cli.audit.QuantAuditRenderer;
import tech.kayys.aqli.sdk.audit.QuantAuditTrail;

/**
 * Offline model quantization entry point.
 *
 * <p>Quantization must only report success after a real quantization engine has
 * transformed the tensors and the output has been validated. The previous
 * implementation copied source weight files unchanged and wrote metadata that
 * claimed they were quantized. That output was misleading and could be
 * registered as a usable quantized model, so the command now fails closed until
 * a real engine adapter is connected.
 */
@Dependent
@Unremovable
@Command(name = "quantize", description = "Quantize a model (requires a supported quantization engine)")
public class QuantizeCommand implements java.util.concurrent.Callable<Integer> {

    @Option(names = {"-m", "--model"},
            description = "Model ID or local model path/directory")
    String modelId;

    @Option(names = {"--strategy"},
            description = "Requested strategy (bnb, turbo, awq, gptq, autoround)",
            defaultValue = "bnb")
    String strategy;

    @Option(names = {"--bits"}, description = "Requested bit width (2, 3, 4, 8)", defaultValue = "4")
    int bits;

    @Option(names = {"--group-size"}, description = "Requested group size", defaultValue = "128")
    int groupSize;

    @Option(names = {"-o", "--output"}, description = "Output directory")
    String outputDir;

    @Option(names = {"--no-profile"}, description = "Do not print the audit trail summary")
    boolean noProfile;

    @Option(names = {"--audit-file"}, description = "Custom path for audit JSON output")
    String auditFile;

    @Option(names = {"--export-csv"}, description = "Export existing quantization audit records to CSV")
    String exportCsv;

    @Override
    public Integer call() {
        if (exportCsv != null && !exportCsv.isBlank()) {
            try {
                QuantAuditTrail.exportCsv(Path.of(exportCsv));
                System.out.println("Exported quantization audit trail to: " + Path.of(exportCsv).toAbsolutePath());
                return 0;
            } catch (IOException | RuntimeException e) {
                System.err.println("Failed to export quantization audit trail: " + e.getMessage());
                return 1;
            }
        }

        System.err.println("Quantization is currently unavailable: AQLI has no connected engine for strategy '"
                + strategy + "'. No model files were copied, modified, or registered.");
        System.err.println("Use a supported quantization tool externally, or retry after a real Aljabr/AQLI "
                + "quantization adapter is available.");
        return 2;
    }
}
