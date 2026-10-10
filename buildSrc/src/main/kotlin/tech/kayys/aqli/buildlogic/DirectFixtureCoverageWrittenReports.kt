package tech.kayys.aqli.buildlogic

import java.io.File

data class DirectFixtureCoverageWrittenReports(
    val textReportFile: File,
    val jsonReportFile: File,
    val schemaFile: File,
) {
    fun relativeSummary(rootDir: File): String =
        listOf(textReportFile, jsonReportFile, schemaFile)
            .joinToString(separator = ", ") { it.relativeTo(rootDir).path }
}
