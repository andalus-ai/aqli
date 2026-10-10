package tech.kayys.aqli.buildlogic

import java.io.File

object DirectFixtureCoverageTaskMessages {
    fun validationSummary(
        scan: DirectFixtureCoverageScanResult,
        writtenReports: DirectFixtureCoverageWrittenReports,
        projectDir: File,
    ): String =
        "Validated fixture coverage for ${scan.directModuleCount} direct model-family " +
                "module${pluralSuffix(scan.directModuleCount)}. " +
                "Reports: ${writtenReports.relativeSummary(projectDir)}"

    fun timingSummary(
        scan: DirectFixtureCoverageScanResult,
        startedAt: Long,
        scannedAt: Long,
        guardedAt: Long,
        reportedAt: Long,
    ): String =
        "Direct fixture coverage timings: modules=${scan.modelModuleCount}, " +
                "directModules=${scan.directModuleCount}, " +
                "fixtures=${scan.uniqueFixtureCount}, " +
                "scan=${elapsedMillis(startedAt, scannedAt)}ms, " +
                "guard=${elapsedMillis(scannedAt, guardedAt)}ms, " +
                "report=${elapsedMillis(guardedAt, reportedAt)}ms, " +
                "total=${elapsedMillis(startedAt, reportedAt)}ms"

    private fun elapsedMillis(startedAt: Long, finishedAt: Long): Long =
        (finishedAt - startedAt) / 1_000_000

    private fun pluralSuffix(count: Int): String =
        if (count == 1) "" else "s"
}
