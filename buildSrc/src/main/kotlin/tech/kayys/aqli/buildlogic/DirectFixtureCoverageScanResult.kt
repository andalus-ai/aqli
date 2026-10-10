package tech.kayys.aqli.buildlogic

data class DirectFixtureCoverageScanResult(
    val modelModuleCount: Int,
    val directModuleCount: Int,
    val uniqueFixtureCount: Int,
    val reportEntries: List<DirectFixtureCoverageModuleReport>,
    val problems: List<String>,
)
