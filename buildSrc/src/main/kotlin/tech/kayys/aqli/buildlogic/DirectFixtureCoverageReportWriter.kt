package tech.kayys.aqli.buildlogic

import java.io.File

object DirectFixtureCoverageReportWriter {
    fun writeReports(
        directModuleCount: Int,
        fixtureCount: Int,
        reportEntries: List<DirectFixtureCoverageModuleReport>,
        textReportFile: File,
        jsonReportFile: File,
        schemaFile: File,
    ): DirectFixtureCoverageWrittenReports {
        writeTextIfChanged(
            textReportFile,
            DirectFixtureCoverageReportRendering.textReport(
                directModuleCount = directModuleCount,
                fixtureCount = fixtureCount,
                reportEntries = reportEntries,
            ),
        )

        val reportPayload = DirectFixtureCoverageReportRendering.reportPayload(
            directModuleCount = directModuleCount,
            fixtureCount = fixtureCount,
            reportEntries = reportEntries,
        )
        writeTextIfChanged(
            jsonReportFile,
            DirectFixtureCoverageReportRendering.prettyJsonWithTrailingNewline(reportPayload),
        )

        val schemaPayload = DirectFixtureCoverageReportRendering.schemaPayload()
        writeTextIfChanged(
            schemaFile,
            DirectFixtureCoverageReportRendering.prettyJsonWithTrailingNewline(schemaPayload),
        )
        DirectFixtureCoverageReportContract.requireGeneratedReports(reportPayload, schemaPayload, jsonReportFile, schemaFile)

        return DirectFixtureCoverageWrittenReports(
            textReportFile = textReportFile,
            jsonReportFile = jsonReportFile,
            schemaFile = schemaFile,
        )
    }

    private fun writeTextIfChanged(file: File, text: String) {
        if (file.exists() && file.readText() == text) {
            return
        }

        file.parentFile.mkdirs()
        file.writeText(text)
    }
}
