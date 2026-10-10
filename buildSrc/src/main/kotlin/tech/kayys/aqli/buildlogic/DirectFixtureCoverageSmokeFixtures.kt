package tech.kayys.aqli.buildlogic

object DirectFixtureCoverageSmokeFixtures {
    const val moduleName = "aqli-model-smoke"
    const val fixtureId = "smoke"
    const val fixtureModelType = "smoke"
    const val architecture = "SmokeForCausalLM"
    const val fixtureTokenizerMarker = "tokenizer.json"
    const val mismatchedFixtureDetailId = "other"
    const val unsupportedTokenizerMarker = "merges.txt"

    fun reportEntries(): List<DirectFixtureCoverageModuleReport> =
        listOf(
            DirectFixtureCoverageModuleReport(
                moduleName = moduleName,
                fixtures = listOf(fixture()),
            ),
        )

    fun incompleteReportEntries(): List<DirectFixtureCoverageModuleReport> =
        listOf(
            DirectFixtureCoverageModuleReport(
                moduleName = moduleName,
                fixtures = listOf(fixture(modelType = "")),
            ),
        )

    fun fixture(modelType: String = fixtureModelType): DirectFixtureCoverage =
        DirectFixtureCoverage(
            id = fixtureId,
            modelType = modelType,
            architectures = listOf(architecture),
            tokenizerMarker = fixtureTokenizerMarker,
        )

    fun reportModuleMap(
        fixtureDetailId: String = fixtureId,
        tokenizerMarker: String = fixtureTokenizerMarker,
    ): Map<String, Any> =
        mapOf(
            "module" to moduleName,
            "fixtureCount" to 1,
            "fixtures" to listOf(fixtureId),
            "fixtureDetails" to listOf(
                mapOf(
                    "id" to fixtureDetailId,
                    "modelType" to fixtureModelType,
                    "architectures" to listOf(architecture),
                    "tokenizerMarker" to tokenizerMarker,
                ),
            ),
        )

    fun mismatchedFixtureDetailModuleMap(): Map<String, Any> =
        reportModuleMap(fixtureDetailId = mismatchedFixtureDetailId)

    fun unsupportedTokenizerModuleMap(): Map<String, Any> =
        reportModuleMap(tokenizerMarker = unsupportedTokenizerMarker)
}
