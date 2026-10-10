package tech.kayys.aqli.buildlogic

object DirectFixtureCoverageReportSpec {
    const val schemaVersion = 2

    val schemaId =
        "https://aqli.ai/schemas/build/model-family-direct-fixtures.v$schemaVersion.schema.json"

    const val moduleNamePattern = "^aqli-model-[a-z0-9-]+$"
    const val moduleNamePrefix = "aqli-model-"
    const val mainJavaSourcePath = "src/main/java"
    const val testJavaSourcePath = "src/test/java"
    const val fixtureRootPath = "src/test/resources/model-family-fixtures"
    const val directInferenceMarker = "DIRECT_SAFETENSOR_INFERENCE"
    const val fixtureValidatorCall = "ModelFamilyFixtureValidator.validate"

    val fixtureInputIncludes = listOf(
        "$moduleNamePrefix*/$mainJavaSourcePath/**/*.java",
        "$moduleNamePrefix*/$testJavaSourcePath/**/*.java",
        "$moduleNamePrefix*/$fixtureRootPath/**",
    )

    val moduleNameRegex = Regex(moduleNamePattern)
    val rootFields = listOf("schemaVersion", "directModuleCount", "fixtureCount", "modules")
    val moduleFields = listOf("module", "fixtureCount", "fixtures", "fixtureDetails")
    val detailFields = listOf("id", "modelType", "architectures", "tokenizerMarker")
    val rootFieldSet = rootFields.toSet()
    val moduleFieldSet = moduleFields.toSet()
    val detailFieldSet = detailFields.toSet()

    val tokenizerMarkers = listOf(
        "tokenizer.json",
        "vocab.json",
        "tokenizer/tokenizer.json",
        "tokenizer/vocab.json",
    )
}
