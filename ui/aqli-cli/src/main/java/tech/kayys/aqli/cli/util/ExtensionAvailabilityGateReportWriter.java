/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import tech.kayys.aqli.cli.util.ExtensionAvailabilityGateReportFields.Contract;
import tech.kayys.aqli.cli.util.ExtensionAvailabilityGateReportFields.ContractViolation;
import tech.kayys.aqli.cli.util.ExtensionAvailabilityGateReportFields.Extension;
import tech.kayys.aqli.cli.util.ExtensionAvailabilityGateReportFields.Gate;
import tech.kayys.aqli.cli.util.ExtensionAvailabilityGateReportFields.Policy;
import tech.kayys.aqli.cli.util.ExtensionAvailabilityGateReportFields.Root;
import tech.kayys.aqli.plugin.core.ExtensionAvailability;
import tech.kayys.aqli.plugin.core.ExtensionAvailabilityContractReport;
import tech.kayys.aqli.plugin.core.ExtensionAvailabilityContractViolation;
import tech.kayys.aqli.plugin.core.ExtensionAvailabilityRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the extension release gate as a build artifact for CI archival.
 */
public final class ExtensionAvailabilityGateReportWriter {
    private static final int SCHEMA_VERSION = ExtensionAvailabilityGateReportFields.SCHEMA_VERSION;
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private ExtensionAvailabilityGateReportWriter() {
    }

    public static void main(String[] args) throws Exception {
        Path output = args.length == 0
                ? Path.of("build/reports/aqli/extension-availability-gate.json")
                : Path.of(args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (ExternalPluginClasspathScope pluginScope =
                ExternalPluginClasspathScope.open(args, 1, ExtensionAvailabilityGateReportWriter.class)) {
            JSON.writeValue(output.toFile(), buildReport(pluginScope.discoveryClassLoader(), pluginScope.classpath()));
        }
        System.out.println("Extension availability gate report: " + output.toAbsolutePath());
    }

    static Map<String, Object> buildReport() {
        return buildReport(null);
    }

    static Map<String, Object> buildReport(ClassLoader pluginClassLoader) {
        return buildReport(pluginClassLoader, List.of());
    }

    static Map<String, Object> buildReport(ClassLoader pluginClassLoader, List<Path> pluginClasspath) {
        ExtensionAvailabilityRegistry registry =
                PluginAvailabilityChecker.getExtensionAvailabilityRegistry(pluginClassLoader);
        List<ExtensionAvailability> extensions = registry.availabilityReports();
        ExtensionAvailabilityPolicy.Result policy =
                ExtensionAvailabilityPolicy.fromRuntimeConfiguration().evaluate(extensions);
        ExtensionAvailabilityContractReport contract = registry.contractReport();
        ExtensionAvailabilityGate gate = ExtensionAvailabilityGate.evaluate(policy, contract);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put(Root.SCHEMA_VERSION, SCHEMA_VERSION);
        report.put(Root.GENERATED_AT, Instant.now().toString());
        report.put(Root.EXTERNAL_PLUGIN_CLASSPATH, ExternalPluginClasspath.display(pluginClasspath));
        report.put(Root.EXTENSION_REGISTRY_SCOPE, pluginClassLoader == null ? "global" : "scoped");
        report.put(Root.GATE, gateReport(gate));
        report.put(Root.POLICY, policyReport(policy));
        report.put(Root.CONTRACT, contractReport(contract));
        report.put(Root.EXTENSIONS, extensions.stream()
                .map(ExtensionAvailabilityGateReportWriter::extensionReport)
                .toList());
        return report;
    }

    private static Map<String, Object> gateReport(ExtensionAvailabilityGate gate) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(Gate.PASSED, gate.passed());
        report.put(Gate.FAILED, gate.failed());
        report.put(Gate.STATUS, gate.status());
        report.put(Gate.VIOLATION_COUNT, gate.violationCount());
        report.put(Gate.VIOLATIONS, gate.violations());
        return report;
    }

    private static Map<String, Object> policyReport(ExtensionAvailabilityPolicy.Result policy) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(Policy.CONFIGURED, policy.configured());
        report.put(Policy.PASSED, policy.passed());
        report.put(Policy.STATUS, policy.status());
        report.put(Policy.VIOLATIONS, policy.violations());
        report.put(Policy.REQUIRED_EXTENSIONS, policy.requiredExtensions());
        report.put(Policy.REQUIRED_PRODUCTION_EXTENSIONS, policy.requiredProductionExtensions());
        report.put(Policy.FORBIDDEN_EXTENSIONS, policy.forbiddenExtensions());
        return report;
    }

    private static Map<String, Object> contractReport(ExtensionAvailabilityContractReport contract) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(Contract.PASSED, contract.passed());
        report.put(Contract.FAILED, contract.failed());
        report.put(Contract.STATUS, contract.status());
        report.put(Contract.VIOLATION_COUNT, contract.violationCount());
        report.put(Contract.SUMMARIES, contract.summaries());
        report.put(Contract.VIOLATIONS, contract.violations().stream()
                .map(ExtensionAvailabilityGateReportWriter::contractViolationReport)
                .toList());
        return report;
    }

    private static Map<String, Object> contractViolationReport(ExtensionAvailabilityContractViolation violation) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(ContractViolation.EXTENSION_ID, violation.extensionId());
        report.put(ContractViolation.CODE, violation.code());
        report.put(ContractViolation.MESSAGE, violation.message());
        report.put(ContractViolation.SUMMARY, violation.summary());
        return report;
    }

    private static Map<String, Object> extensionReport(ExtensionAvailability availability) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put(Extension.ID, availability.id());
        report.put(Extension.NAME, availability.name());
        report.put(Extension.KIND, availability.kind());
        report.put(Extension.STATUS, availability.status());
        report.put(Extension.SUMMARY, availability.compactSummary());
        report.put(Extension.ATTACHED, availability.attached());
        report.put(Extension.DETACHED, availability.detached());
        report.put(Extension.HEALTHY, availability.healthy());
        report.put(Extension.PRODUCTION_READY, availability.productionReady());
        report.put(Extension.CAPABILITIES, availability.capabilities());
        report.put(Extension.FORMATS, availability.formats());
        report.put(Extension.ATTRIBUTES, availability.attributes());
        report.put(Extension.DIAGNOSTICS, availability.diagnostics());
        report.put(Extension.REMEDIATION_HINTS, availability.remediationHints());
        return report;
    }
}
