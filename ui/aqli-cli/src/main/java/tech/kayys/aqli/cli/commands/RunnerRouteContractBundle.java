package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import tech.kayys.aqli.cli.util.PluginGates;
import tech.kayys.aqli.cli.util.RunnerRouteContractBundleReports;

import java.util.List;
import java.util.Map;

/**
 * Command-package facade for runner-route contract bundles exposed by `aqli modules --json`.
 */
final class RunnerRouteContractBundle {
    static final String CONTRACT_ID = RunnerRouteContractBundleReports.CONTRACT_ID;
    static final int SCHEMA_VERSION = RunnerRouteContractBundleReports.SCHEMA_VERSION;

    static final String FIELD_CONTRACT_ID = RunnerRouteContractBundleReports.FIELD_CONTRACT_ID;
    static final String FIELD_SCHEMA_VERSION = RunnerRouteContractBundleReports.FIELD_SCHEMA_VERSION;
    static final String FIELD_SCHEMA_FINGERPRINT = RunnerRouteContractBundleReports.FIELD_SCHEMA_FINGERPRINT;
    static final String FIELD_CONTRACT_IDS = RunnerRouteContractBundleReports.FIELD_CONTRACT_IDS;
    static final String FIELD_PAYLOAD_ROOTS = RunnerRouteContractBundleReports.FIELD_PAYLOAD_ROOTS;
    static final String FIELD_VALIDATION_ROOTS = RunnerRouteContractBundleReports.FIELD_VALIDATION_ROOTS;
    static final String FIELD_CONTRACTS = RunnerRouteContractBundleReports.FIELD_CONTRACTS;

    static final String CONTRACT_ROLE = RunnerRouteContractBundleReports.CONTRACT_ROLE;
    static final String CONTRACT_SCHEMA_VERSION = RunnerRouteContractBundleReports.CONTRACT_SCHEMA_VERSION;
    static final String CONTRACT_SCHEMA_FINGERPRINT =
            RunnerRouteContractBundleReports.CONTRACT_SCHEMA_FINGERPRINT;
    static final String CONTRACT_PAYLOAD_ROOTS = RunnerRouteContractBundleReports.CONTRACT_PAYLOAD_ROOTS;
    static final String CONTRACT_VALIDATION_ROOTS = RunnerRouteContractBundleReports.CONTRACT_VALIDATION_ROOTS;

    private RunnerRouteContractBundle() {
    }

    static Map<String, Object> report() {
        return RunnerRouteContractBundleReports.report();
    }

    static Map<String, Object> validationReport(Map<?, ?> report) {
        return RunnerRouteContractBundleReports.validationReport(report);
    }

    static PluginGates applyGate(PluginGates gates, Map<?, ?> report) {
        return RunnerRouteContractBundleReports.applyGate(gates, report);
    }

    static List<String> validateReport(Map<?, ?> report) {
        return RunnerRouteContractBundleReports.validateReport(report);
    }

    static String schemaFingerprint() {
        return RunnerRouteContractBundleReports.schemaFingerprint();
    }
}
