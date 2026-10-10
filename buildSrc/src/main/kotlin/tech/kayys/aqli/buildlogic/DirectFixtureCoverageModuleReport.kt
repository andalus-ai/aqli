package tech.kayys.aqli.buildlogic

data class DirectFixtureCoverageModuleReport(
    val moduleName: String,
    val fixtures: List<DirectFixtureCoverage>,
)
