import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

val trainingModuleChecksReportSchemaVersion = 2
val trainingModuleChecksReportFormat =
    "aqli.training.module-checks.v$trainingModuleChecksReportSchemaVersion"
val trainingModuleChecksReportSchemaId =
    "https://aqli.ai/schemas/build/training-module-checks.v$trainingModuleChecksReportSchemaVersion.schema.json"
val trainingModuleChecksReportFile =
    layout.buildDirectory.file("reports/aqli/training-module-checks.json")
val trainingModuleChecksSchemaFile =
    layout.buildDirectory.file("reports/aqli/training-module-checks.v$trainingModuleChecksReportSchemaVersion.schema.json")
val trainingModuleChecksLockSchemaVersion = 1
val trainingModuleChecksLockFormat =
    "aqli.training.module-checks.lock.v$trainingModuleChecksLockSchemaVersion"
val trainingModuleChecksLockFile =
    layout.projectDirectory.file("gradle/training-module-checks.lock.json")
val trainingModuleChecksLockDriftReportFile =
    layout.buildDirectory.file("reports/aqli/training-module-checks-lock-drift.json")
val trainingModuleCoverageReportSchemaVersion = 2
val trainingModuleCoverageReportFormat =
    "aqli.training.module-coverage.v$trainingModuleCoverageReportSchemaVersion"
val trainingModuleCoverageReportFile =
    layout.buildDirectory.file("reports/aqli/training-module-coverage.json")
val trainingModuleCandidateSelectionProperty = "aqliTrainingCandidate"
val trainingModuleCandidateSelection =
    providers.gradleProperty(trainingModuleCandidateSelectionProperty).orElse("")
val trainingModuleCandidateSelectionReportSchemaVersion = 2
val trainingModuleCandidateSelectionReportFormat =
    "aqli.training.module-candidate-selection.v$trainingModuleCandidateSelectionReportSchemaVersion"
val trainingModuleCandidateSelectionReportFile =
    layout.buildDirectory.file("reports/aqli/training-module-candidate-selection.json")
val trainingModuleCandidateLockSchemaVersion = 1
val trainingModuleCandidateLockFormat =
    "aqli.training.module-candidates.lock.v$trainingModuleCandidateLockSchemaVersion"
val trainingModuleCandidateLockFile =
    layout.projectDirectory.file("gradle/training-module-candidates.lock.json")
val trainingModuleCandidateLockDriftReportFile =
    layout.buildDirectory.file("reports/aqli/training-module-candidates-lock-drift.json")
val trainingModuleChecksRootFields = listOf(
    "format",
    "schemaVersion",
    "registryFingerprintAlgorithm",
    "registryFingerprint",
    "checkCount",
    "checks",
)
val trainingModuleChecksEntryFields =
    listOf("label", "projectPath", "taskName", "taskPath", "projectExists", "taskExists")
val trainingModuleChecksLockRootFields = listOf(
    "format",
    "schemaVersion",
    "reportFormat",
    "registryFingerprintAlgorithm",
    "registryFingerprint",
    "checkCount",
    "checks",
)
val trainingModuleChecksLockEntryFields = listOf("label", "projectPath", "taskName", "taskPath")
val trainingModuleChecksLockDriftRootFields = listOf(
    "format",
    "schemaVersion",
    "passed",
    "missingLock",
    "lockFile",
    "expectedFingerprint",
    "actualFingerprint",
    "fingerprintDrift",
    "addedLabels",
    "removedLabels",
    "changedChecks",
)
val trainingModuleChecksLockDriftChangedCheckFields = listOf("label", "expected", "actual")
val trainingModuleCoverageRootFields = listOf(
    "format",
    "schemaVersion",
    "projectCount",
    "registeredProjectCount",
    "testProjectCount",
    "unregisteredTestProjectCount",
    "unregisteredTestProjects",
    "candidateCheckCount",
    "candidateChecks",
    "projects",
)
val trainingModuleCoverageCandidateFields = listOf("label", "projectPath", "taskName", "taskPath")
val trainingModuleCandidateSelectionRootFields = listOf(
    "format",
    "schemaVersion",
    "selectionProperty",
    "selectionValue",
    "selectionFingerprintAlgorithm",
    "selectionFingerprint",
    "selectedAll",
    "selectedLabelCount",
    "selectedLabels",
    "candidateCount",
    "taskPaths",
    "candidates",
    "promotionSnippets",
    "nextCommands",
)
val trainingModuleCandidateLockRootFields = listOf(
    "format",
    "schemaVersion",
    "coverageFormat",
    "candidateFingerprintAlgorithm",
    "candidateFingerprint",
    "candidateCount",
    "candidates",
)
val trainingModuleCandidateLockDriftRootFields = listOf(
    "format",
    "schemaVersion",
    "passed",
    "missingLock",
    "lockFile",
    "expectedFingerprint",
    "actualFingerprint",
    "fingerprintDrift",
    "addedLabels",
    "removedLabels",
    "changedCandidates",
)
val trainingModuleCandidateLockDriftChangedCandidateFields = listOf("label", "expected", "actual")
val trainingModuleCoverageEntryFields = listOf(
    "projectPath",
    "projectDirectory",
    "hasTestSources",
    "testTaskExists",
    "registered",
    "labels",
    "taskPaths",
)
val trainingModuleChecksRootFieldSet = trainingModuleChecksRootFields.toSet()
val trainingModuleChecksEntryFieldSet = trainingModuleChecksEntryFields.toSet()
val trainingModuleChecksLockRootFieldSet = trainingModuleChecksLockRootFields.toSet()
val trainingModuleChecksLockEntryFieldSet = trainingModuleChecksLockEntryFields.toSet()
val trainingModuleChecksLockDriftRootFieldSet = trainingModuleChecksLockDriftRootFields.toSet()
val trainingModuleChecksLockDriftChangedCheckFieldSet = trainingModuleChecksLockDriftChangedCheckFields.toSet()
val trainingModuleCoverageRootFieldSet = trainingModuleCoverageRootFields.toSet()
val trainingModuleCoverageCandidateFieldSet = trainingModuleCoverageCandidateFields.toSet()
val trainingModuleCandidateSelectionRootFieldSet = trainingModuleCandidateSelectionRootFields.toSet()
val trainingModuleCandidateLockRootFieldSet = trainingModuleCandidateLockRootFields.toSet()
val trainingModuleCandidateLockDriftRootFieldSet = trainingModuleCandidateLockDriftRootFields.toSet()
val trainingModuleCandidateLockDriftChangedCandidateFieldSet =
    trainingModuleCandidateLockDriftChangedCandidateFields.toSet()
val trainingModuleCoverageEntryFieldSet = trainingModuleCoverageEntryFields.toSet()

data class TrainingModuleCheck(
    val label: String,
    val projectPath: String,
    val taskName: String = "test",
) {
    val taskPath: String = "$projectPath:$taskName"
}

val trainingModuleChecks = listOf(
    // Add new Gradle-wired training module checks here as modules mature.
    TrainingModuleCheck(
        label = "recursive-reasoning",
        projectPath = ":ml:aqli-ml-recursive-reasoning",
    ),
)

fun trainingModuleCheck(label: String): TrainingModuleCheck =
    trainingModuleChecks.firstOrNull { it.label == label }
        ?: error("Unknown training module check: $label")

fun trainingModuleCheckReportRows(): List<Map<String, Any?>> =
    trainingModuleChecks.map { check ->
        val registeredProject = findProject(check.projectPath)
        mapOf(
            "label" to check.label,
            "projectPath" to check.projectPath,
            "taskName" to check.taskName,
            "taskPath" to check.taskPath,
            "projectExists" to (registeredProject != null),
            "taskExists" to (registeredProject?.tasks?.names?.contains(check.taskName) == true),
        )
    }

fun trainingModuleCoverageRows(): List<Map<String, Any?>> =
    allprojects
        .filter { trainingProject ->
            trainingProject.projectDir.toPath().normalize()
                .startsWith(projectDir.toPath().resolve("training").normalize())
        }
        .sortedBy { it.path }
        .map { trainingProject ->
            val registeredChecks = trainingModuleChecks.filter { it.projectPath == trainingProject.path }
            mapOf(
                "projectPath" to trainingProject.path,
                "projectDirectory" to trainingProject.projectDir.relativeTo(projectDir).invariantSeparatorsPath,
                "hasTestSources" to trainingProject.projectDir.resolve("src/test").isDirectory,
                "testTaskExists" to ("test" in trainingProject.tasks.names),
                "registered" to registeredChecks.isNotEmpty(),
                "labels" to registeredChecks.map { it.label },
                "taskPaths" to registeredChecks.map { it.taskPath },
            )
        }

fun trainingModuleSuggestedCheckLabel(projectPath: String): String =
    projectPath
        .substringAfterLast(":")
        .removePrefix("aqli-")
        .removePrefix("ml-")
        .removePrefix("train-")
        .takeIf { it.isNotBlank() }
        ?: projectPath.trim(':').replace(':', '-')

fun trainingModuleCoverageCandidateRow(projectPath: String): Map<String, Any?> {
    val taskName = "test"
    return mapOf(
        "label" to trainingModuleSuggestedCheckLabel(projectPath),
        "projectPath" to projectPath,
        "taskName" to taskName,
        "taskPath" to "$projectPath:$taskName",
    )
}

fun trainingModuleCoverageCandidateRows(projects: List<Map<String, Any?>>): List<Map<String, Any?>> =
    projects
        .filter { it["hasTestSources"] == true && it["testTaskExists"] == true && it["registered"] != true }
        .map { trainingModuleCoverageCandidateRow(it["projectPath"]?.toString().orEmpty()) }
        .sortedBy { it["label"]?.toString().orEmpty() }

fun trainingModuleCandidateRowsFromValue(value: Any?): List<Map<String, Any?>> =
    (value as? List<*>)
        ?.mapNotNull { it as? Map<*, *> }
        ?.map { candidate ->
            mapOf(
                "label" to candidate["label"]?.toString().orEmpty(),
                "projectPath" to candidate["projectPath"]?.toString().orEmpty(),
                "taskName" to candidate["taskName"]?.toString().orEmpty(),
                "taskPath" to candidate["taskPath"]?.toString().orEmpty(),
            )
        }
        ?.sortedBy { it["label"] as? String ?: "" }
        ?: emptyList()

fun trainingModuleCoverageCandidateRowsFromPayload(coveragePayload: Map<*, *>): List<Map<String, Any?>> =
    trainingModuleCandidateRowsFromValue(coveragePayload["candidateChecks"])

fun trainingModuleCoverageCandidateRowsFromReport(): List<Map<String, Any?>> {
    val coveragePayload = JsonSlurper().parse(trainingModuleCoverageReportFile.get().asFile) as Map<*, *>
    requireTrainingModuleCoverageReportContract(coveragePayload)
    return trainingModuleCoverageCandidateRowsFromPayload(coveragePayload)
}

fun trainingModuleCandidateSelectionLabels(selectionValue: String): Set<String> {
    val labels = selectionValue
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
    return if (labels.isEmpty() || labels.any { it == "*" || it.equals("all", ignoreCase = true) }) {
        emptySet()
    } else {
        labels.toSet()
    }
}

fun trainingModuleCoverageCandidateRowsForSelection(
    selectionValue: String,
    candidates: List<Map<String, Any?>> = trainingModuleCoverageCandidateRows(trainingModuleCoverageRows()),
): List<Map<String, Any?>> {
    val selectedLabels = trainingModuleCandidateSelectionLabels(selectionValue)
    if (selectedLabels.isEmpty()) {
        return candidates
    }

    val candidatesByLabel = candidates.associateBy { it["label"]?.toString().orEmpty() }
    val missingLabels = selectedLabels.minus(candidatesByLabel.keys).sorted()
    if (missingLabels.isNotEmpty()) {
        val availableLabels = candidatesByLabel.keys.sorted().takeIf { it.isNotEmpty() }?.joinToString()
            ?: "none"
        throw GradleException(
            "Unknown advisory training module candidate(s): ${missingLabels.joinToString()}. "
                + "Available labels: $availableLabels"
        )
    }
    return selectedLabels.sorted().mapNotNull(candidatesByLabel::get)
}

fun trainingModuleCoverageCandidateTaskPaths(selectionValue: String): List<String> =
    trainingModuleCoverageCandidateRowsForSelection(selectionValue)
        .map { it["taskPath"]?.toString().orEmpty() }
        .filter { it.isNotBlank() }

fun trainingModuleCandidateSummaryLine(candidate: Map<*, *>): String =
    "${candidate["label"]}: ${candidate["taskPath"]}"

fun trainingModuleCandidatePromotionSnippet(candidate: Map<*, *>): String =
    buildString {
        append("TrainingModuleCheck(\n")
        append("    label = \"${candidate["label"]}\",\n")
        append("    projectPath = \"${candidate["projectPath"]}\",\n")
        append("),")
    }

fun trainingModuleCandidateSelectionCommandSuffix(selectionValue: String): String {
    val trimmedSelection = selectionValue.trim()
    return if (trimmedSelection.isBlank()) {
        ""
    } else {
        " -P$trainingModuleCandidateSelectionProperty=$trimmedSelection"
    }
}

fun trainingModuleCandidateSelectionNextCommands(selectionValue: String): List<String> {
    val selectionSuffix = trainingModuleCandidateSelectionCommandSuffix(selectionValue)
    return listOf(
        "./gradlew --no-daemon validateTrainingModuleCandidateSelection$selectionSuffix",
        "./gradlew --no-daemon checkTrainingModuleCandidateTests$selectionSuffix",
        "./gradlew --no-daemon printTrainingModuleCandidatePromotionSnippets$selectionSuffix",
        "./gradlew --no-daemon writeTrainingModuleChecksLock",
        "./gradlew --no-daemon verifyTrainingGates",
    )
}

fun trainingModuleCandidateSelectionReportPayload(
    selectionValue: String,
    availableCandidates: List<Map<String, Any?>> = trainingModuleCoverageCandidateRows(trainingModuleCoverageRows()),
): Map<String, Any?> {
    val selectedLabels = trainingModuleCandidateSelectionLabels(selectionValue).sorted()
    val candidates = trainingModuleCoverageCandidateRowsForSelection(selectionValue, availableCandidates)
    return mapOf(
        "format" to trainingModuleCandidateSelectionReportFormat,
        "schemaVersion" to trainingModuleCandidateSelectionReportSchemaVersion,
        "selectionProperty" to trainingModuleCandidateSelectionProperty,
        "selectionValue" to selectionValue,
        "selectionFingerprintAlgorithm" to "SHA-256",
        "selectionFingerprint" to trainingModuleChecksFingerprintFromRows(candidates),
        "selectedAll" to selectedLabels.isEmpty(),
        "selectedLabelCount" to selectedLabels.size,
        "selectedLabels" to selectedLabels,
        "candidateCount" to candidates.size,
        "taskPaths" to candidates.map { it["taskPath"]?.toString().orEmpty() },
        "candidates" to candidates,
        "promotionSnippets" to candidates.map(::trainingModuleCandidatePromotionSnippet),
        "nextCommands" to trainingModuleCandidateSelectionNextCommands(selectionValue),
    )
}

fun trainingModuleCandidateLockPayload(
    candidates: List<Map<String, Any?>> = trainingModuleCoverageCandidateRows(trainingModuleCoverageRows()),
) =
    candidates.let {
        mapOf(
            "format" to trainingModuleCandidateLockFormat,
            "schemaVersion" to trainingModuleCandidateLockSchemaVersion,
            "coverageFormat" to trainingModuleCoverageReportFormat,
            "candidateFingerprintAlgorithm" to "SHA-256",
            "candidateFingerprint" to trainingModuleChecksFingerprintFromRows(candidates),
            "candidateCount" to candidates.size,
            "candidates" to candidates,
        )
    }

fun trainingModuleCandidateRowsByLabel(candidates: Any?): Map<String, Map<*, *>> =
    (candidates as? List<*>)
        ?.mapNotNull { it as? Map<*, *> }
        ?.mapNotNull { candidate ->
            val label = candidate["label"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            label to candidate
        }
        ?.toMap()
        ?: emptyMap()

fun trainingModuleCandidateLockDriftPayload(
    lockFile: File,
    lockPayload: Map<*, *>?,
    currentPayload: Map<String, Any?>,
): Map<String, Any?> {
    val currentCandidates = trainingModuleCandidateRowsByLabel(currentPayload["candidates"])
    val lockedCandidates = trainingModuleCandidateRowsByLabel(lockPayload?.get("candidates"))
    val added = currentCandidates.keys.minus(lockedCandidates.keys).sorted()
    val removed = lockedCandidates.keys.minus(currentCandidates.keys).sorted()
    val changed = currentCandidates.keys.intersect(lockedCandidates.keys)
        .filter { label ->
            val current = currentCandidates.getValue(label)
            val locked = lockedCandidates.getValue(label)
            trainingModuleCoverageCandidateFields.any { field -> current[field]?.toString() != locked[field]?.toString() }
        }
        .sorted()
        .map { label ->
            val current = currentCandidates.getValue(label)
            val locked = lockedCandidates.getValue(label)
            mapOf(
                "label" to label,
                "expected" to trainingModuleCoverageCandidateFields.associateWith { field ->
                    locked[field]?.toString().orEmpty()
                },
                "actual" to trainingModuleCoverageCandidateFields.associateWith { field ->
                    current[field]?.toString().orEmpty()
                },
            )
        }
    val expectedFingerprint = lockPayload?.get("candidateFingerprint")?.toString()
    val actualFingerprint = currentPayload["candidateFingerprint"]?.toString().orEmpty()
    val missingLock = lockPayload == null
    val fingerprintDrift = !missingLock && expectedFingerprint != actualFingerprint
    val passed = !missingLock && !fingerprintDrift && added.isEmpty() && removed.isEmpty() && changed.isEmpty()

    return mapOf(
        "format" to "aqli.training.module-candidates.lock-drift.v1",
        "schemaVersion" to 1,
        "passed" to passed,
        "missingLock" to missingLock,
        "lockFile" to lockFile.absolutePath,
        "expectedFingerprint" to expectedFingerprint,
        "actualFingerprint" to actualFingerprint,
        "fingerprintDrift" to fingerprintDrift,
        "addedLabels" to added,
        "removedLabels" to removed,
        "changedCandidates" to changed,
    )
}

fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return HexFormat.of().formatHex(digest.digest(value.toByteArray(StandardCharsets.UTF_8)))
}

fun trainingModuleChecksFingerprintFromRows(checks: List<Map<String, Any?>>): String =
    sha256Hex(
        checks.joinToString(separator = "\n", postfix = "\n") { check ->
            listOf(
                check["label"]?.toString().orEmpty(),
                check["projectPath"]?.toString().orEmpty(),
                check["taskName"]?.toString().orEmpty(),
                check["taskPath"]?.toString().orEmpty(),
            ).joinToString(separator = "|")
        }
    )

fun trainingModuleCheckLockRowsFromRows(checks: List<Map<String, Any?>>): List<Map<String, Any?>> =
    checks.map { check ->
        mapOf(
            "label" to check["label"],
            "projectPath" to check["projectPath"],
            "taskName" to check["taskName"],
            "taskPath" to check["taskPath"],
        )
    }

fun trainingModuleCheckReportPayload() =
    trainingModuleCheckReportRows().let { checks ->
        mapOf(
            "format" to trainingModuleChecksReportFormat,
            "schemaVersion" to trainingModuleChecksReportSchemaVersion,
            "registryFingerprintAlgorithm" to "SHA-256",
            "registryFingerprint" to trainingModuleChecksFingerprintFromRows(checks),
            "checkCount" to checks.size,
            "checks" to checks,
        )
    }

fun trainingModuleCheckLockPayload() =
    trainingModuleCheckLockRowsFromRows(trainingModuleCheckReportRows()).let { checks ->
        mapOf(
            "format" to trainingModuleChecksLockFormat,
            "schemaVersion" to trainingModuleChecksLockSchemaVersion,
            "reportFormat" to trainingModuleChecksReportFormat,
            "registryFingerprintAlgorithm" to "SHA-256",
            "registryFingerprint" to trainingModuleChecksFingerprintFromRows(checks),
            "checkCount" to checks.size,
            "checks" to checks,
        )
    }

fun trainingModuleCoverageReportPayload() =
    trainingModuleCoverageRows().let { projects ->
        val testProjects = projects.filter { it["hasTestSources"] == true && it["testTaskExists"] == true }
        val registeredProjects = projects.filter { it["registered"] == true }
        val unregisteredTestProjects = testProjects
            .filter { it["registered"] != true }
            .map { it["projectPath"]?.toString().orEmpty() }
        val candidateChecks = trainingModuleCoverageCandidateRows(projects)
        mapOf(
            "format" to trainingModuleCoverageReportFormat,
            "schemaVersion" to trainingModuleCoverageReportSchemaVersion,
            "projectCount" to projects.size,
            "registeredProjectCount" to registeredProjects.size,
            "testProjectCount" to testProjects.size,
            "unregisteredTestProjectCount" to unregisteredTestProjects.size,
            "unregisteredTestProjects" to unregisteredTestProjects,
            "candidateCheckCount" to candidateChecks.size,
            "candidateChecks" to candidateChecks,
            "projects" to projects,
        )
    }

fun trainingModuleCheckReportSchemaPayload() = mapOf(
    "\$schema" to "https://json-schema.org/draft/2020-12/schema",
    "\$id" to trainingModuleChecksReportSchemaId,
    "title" to "Aqli training module checks report",
    "type" to "object",
    "additionalProperties" to false,
    "required" to trainingModuleChecksRootFields,
    "properties" to mapOf(
        "format" to mapOf("const" to trainingModuleChecksReportFormat),
        "schemaVersion" to mapOf("const" to trainingModuleChecksReportSchemaVersion),
        "registryFingerprintAlgorithm" to mapOf("const" to "SHA-256"),
        "registryFingerprint" to mapOf("type" to "string", "pattern" to "^[0-9a-f]{64}$"),
        "checkCount" to mapOf("type" to "integer", "minimum" to 1),
        "checks" to mapOf(
            "type" to "array",
            "minItems" to 1,
            "items" to mapOf(
                "type" to "object",
                "additionalProperties" to false,
                "required" to trainingModuleChecksEntryFields,
                "properties" to mapOf(
                    "label" to mapOf("type" to "string", "minLength" to 1),
                    "projectPath" to mapOf("type" to "string", "pattern" to "^:"),
                    "taskName" to mapOf("type" to "string", "minLength" to 1),
                    "taskPath" to mapOf("type" to "string", "minLength" to 1),
                    "projectExists" to mapOf("const" to true),
                    "taskExists" to mapOf("const" to true),
                ),
            ),
        ),
    ),
)

fun trainingModuleCheckRowsByLabel(checks: Any?): Map<String, Map<*, *>> =
    (checks as? List<*>)
        ?.mapNotNull { it as? Map<*, *> }
        ?.mapNotNull { check ->
            val label = check["label"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            label to check
        }
        ?.toMap()
        ?: emptyMap()

fun requireTrainingModuleChecksLockContract(lock: Map<*, *>) {
    val problems = mutableListOf<String>()
    if (trainingModuleFieldSet(lock) != trainingModuleChecksLockRootFieldSet) {
        problems += "lock root fields drifted"
    }
    if (lock["format"] != trainingModuleChecksLockFormat) {
        problems += "lock format should be $trainingModuleChecksLockFormat"
    }
    if ((lock["schemaVersion"] as? Number)?.toInt() != trainingModuleChecksLockSchemaVersion) {
        problems += "lock schemaVersion should be $trainingModuleChecksLockSchemaVersion"
    }
    if (lock["reportFormat"] != trainingModuleChecksReportFormat) {
        problems += "lock reportFormat should be $trainingModuleChecksReportFormat"
    }
    if (lock["registryFingerprintAlgorithm"] != "SHA-256") {
        problems += "lock registryFingerprintAlgorithm should be SHA-256"
    }
    val registryFingerprint = lock["registryFingerprint"]?.toString().orEmpty()
    if (!registryFingerprint.matches(Regex("^[0-9a-f]{64}$"))) {
        problems += "lock registryFingerprint should be a lowercase SHA-256 hex digest"
    }

    val checkCount = (lock["checkCount"] as? Number)?.toInt()
    val checks = lock["checks"] as? List<*>
    if (checkCount == null || checkCount < 1) {
        problems += "lock checkCount should be a positive integer"
    }
    if (checks == null || checks.isEmpty()) {
        problems += "lock checks should be a non-empty list"
    } else {
        if (checkCount != checks.size) {
            problems += "lock checkCount=$checkCount but checks.size=${checks.size}"
        }
        val rows = mutableListOf<Map<String, Any?>>()
        checks.forEach { checkEntry ->
            val check = checkEntry as? Map<*, *>
            if (check == null) {
                problems += "lock check entry should be an object"
                return@forEach
            }
            if (trainingModuleFieldSet(check) != trainingModuleChecksLockEntryFieldSet) {
                problems += "lock check entry fields drifted for ${check["label"]}"
            }
            val label = check["label"]?.toString().orEmpty()
            val projectPath = check["projectPath"]?.toString().orEmpty()
            val taskName = check["taskName"]?.toString().orEmpty()
            val taskPath = check["taskPath"]?.toString().orEmpty()
            if (label.isBlank() || taskName.isBlank()) {
                problems += "lock check label/taskName should be non-blank"
            }
            if (!projectPath.startsWith(":")) {
                problems += "lock check '$label' projectPath should be absolute: $projectPath"
            }
            if (taskPath != "$projectPath:$taskName") {
                problems += "lock check '$label' taskPath should equal projectPath:taskName"
            }
            rows += mapOf(
                "label" to label,
                "projectPath" to projectPath,
                "taskName" to taskName,
                "taskPath" to taskPath,
            )
        }
        val expectedFingerprint = trainingModuleChecksFingerprintFromRows(rows)
        if (registryFingerprint != expectedFingerprint) {
            problems += "lock registryFingerprint should be $expectedFingerprint"
        }
    }

    if (problems.isNotEmpty()) {
        throw GradleException("Training module check lock contract problems:\n"
            + problems.joinToString(separator = "\n") { "- $it" })
    }
}

fun trainingModuleCheckLockDriftPayload(lockFile: File, lockPayload: Map<*, *>?, currentPayload: Map<String, Any?>): Map<String, Any?> {
    val currentChecks = trainingModuleCheckRowsByLabel(currentPayload["checks"])
    val lockedChecks = trainingModuleCheckRowsByLabel(lockPayload?.get("checks"))
    val added = currentChecks.keys.minus(lockedChecks.keys).sorted()
    val removed = lockedChecks.keys.minus(currentChecks.keys).sorted()
    val changed = currentChecks.keys.intersect(lockedChecks.keys)
        .filter { label ->
            val current = currentChecks.getValue(label)
            val locked = lockedChecks.getValue(label)
            trainingModuleChecksLockEntryFields.any { field -> current[field]?.toString() != locked[field]?.toString() }
        }
        .sorted()
        .map { label ->
            val current = currentChecks.getValue(label)
            val locked = lockedChecks.getValue(label)
            mapOf(
                "label" to label,
                "expected" to trainingModuleChecksLockEntryFields.associateWith { field -> locked[field]?.toString().orEmpty() },
                "actual" to trainingModuleChecksLockEntryFields.associateWith { field -> current[field]?.toString().orEmpty() },
            )
        }
    val expectedFingerprint = lockPayload?.get("registryFingerprint")?.toString()
    val actualFingerprint = currentPayload["registryFingerprint"]?.toString().orEmpty()
    val missingLock = lockPayload == null
    val fingerprintDrift = !missingLock && expectedFingerprint != actualFingerprint
    val passed = !missingLock && !fingerprintDrift && added.isEmpty() && removed.isEmpty() && changed.isEmpty()

    return mapOf(
        "format" to "aqli.training.module-checks.lock-drift.v1",
        "schemaVersion" to 1,
        "passed" to passed,
        "missingLock" to missingLock,
        "lockFile" to lockFile.absolutePath,
        "expectedFingerprint" to expectedFingerprint,
        "actualFingerprint" to actualFingerprint,
        "fingerprintDrift" to fingerprintDrift,
        "addedLabels" to added,
        "removedLabels" to removed,
        "changedChecks" to changed,
    )
}

fun trainingModuleStringList(value: Any?) =
    (value as? List<*>)?.map { it?.toString().orEmpty() }

fun requireTrainingModuleChecksLockDriftReportContract(report: Map<*, *>) {
    val problems = mutableListOf<String>()
    if (trainingModuleFieldSet(report) != trainingModuleChecksLockDriftRootFieldSet) {
        problems += "lock drift report root fields drifted"
    }
    if (report["format"] != "aqli.training.module-checks.lock-drift.v1") {
        problems += "lock drift report format should be aqli.training.module-checks.lock-drift.v1"
    }
    if ((report["schemaVersion"] as? Number)?.toInt() != 1) {
        problems += "lock drift report schemaVersion should be 1"
    }

    val passed = report["passed"] as? Boolean
    val missingLock = report["missingLock"] as? Boolean
    val fingerprintDrift = report["fingerprintDrift"] as? Boolean
    if (passed == null) {
        problems += "lock drift report passed should be boolean"
    }
    if (missingLock == null) {
        problems += "lock drift report missingLock should be boolean"
    }
    if (fingerprintDrift == null) {
        problems += "lock drift report fingerprintDrift should be boolean"
    }

    val lockFile = report["lockFile"]?.toString().orEmpty()
    if (lockFile.isBlank()) {
        problems += "lock drift report lockFile should be non-blank"
    }

    val expectedFingerprint = report["expectedFingerprint"]?.toString()
    val actualFingerprint = report["actualFingerprint"]?.toString().orEmpty()
    if (missingLock == false && !expectedFingerprint.orEmpty().matches(Regex("^[0-9a-f]{64}$"))) {
        problems += "lock drift report expectedFingerprint should be a lowercase SHA-256 hex digest"
    }
    if (!actualFingerprint.matches(Regex("^[0-9a-f]{64}$"))) {
        problems += "lock drift report actualFingerprint should be a lowercase SHA-256 hex digest"
    }
    if (missingLock == true && expectedFingerprint != null) {
        problems += "lock drift report expectedFingerprint should be null when missingLock=true"
    }
    if (missingLock == false && fingerprintDrift != (expectedFingerprint != actualFingerprint)) {
        problems += "lock drift report fingerprintDrift should match expected/actual fingerprint comparison"
    }

    val addedLabels = trainingModuleStringList(report["addedLabels"])
    val removedLabels = trainingModuleStringList(report["removedLabels"])
    val changedChecks = report["changedChecks"] as? List<*>
    if (addedLabels == null || addedLabels.any { it.isBlank() }) {
        problems += "lock drift report addedLabels should be a list of non-blank strings"
    }
    if (removedLabels == null || removedLabels.any { it.isBlank() }) {
        problems += "lock drift report removedLabels should be a list of non-blank strings"
    }
    if (changedChecks == null) {
        problems += "lock drift report changedChecks should be a list"
    } else {
        changedChecks.forEach { changedEntry ->
            val changed = changedEntry as? Map<*, *>
            if (changed == null) {
                problems += "lock drift report changedChecks entries should be objects"
                return@forEach
            }
            if (trainingModuleFieldSet(changed) != trainingModuleChecksLockDriftChangedCheckFieldSet) {
                problems += "lock drift report changed check fields drifted for ${changed["label"]}"
            }
            val label = changed["label"]?.toString().orEmpty()
            val expected = changed["expected"] as? Map<*, *>
            val actual = changed["actual"] as? Map<*, *>
            if (label.isBlank()) {
                problems += "lock drift report changed check label should be non-blank"
            }
            if (expected == null || trainingModuleFieldSet(expected) != trainingModuleChecksLockEntryFieldSet) {
                problems += "lock drift report changed check '$label' expected fields drifted"
            }
            if (actual == null || trainingModuleFieldSet(actual) != trainingModuleChecksLockEntryFieldSet) {
                problems += "lock drift report changed check '$label' actual fields drifted"
            }
        }
    }

    if (passed == true) {
        if (missingLock != false || fingerprintDrift != false) {
            problems += "passing lock drift report should have missingLock=false and fingerprintDrift=false"
        }
        if (!addedLabels.isNullOrEmpty() || !removedLabels.isNullOrEmpty() || !changedChecks.isNullOrEmpty()) {
            problems += "passing lock drift report should not contain added, removed, or changed checks"
        }
    }

    if (problems.isNotEmpty()) {
        throw GradleException("Training module check lock drift report contract problems:\n"
            + problems.joinToString(separator = "\n") { "- $it" })
    }
}

fun trainingModuleFieldSet(value: Map<*, *>) =
    value.keys.map { it.toString() }.toSet()

fun trainingModuleRequiredFieldSet(value: Any?) =
    (value as? List<*>)?.map { it.toString() }?.toSet()

fun requireTrainingModuleChecksReportContract(report: Map<*, *>, schema: Map<*, *>) {
    val problems = mutableListOf<String>()
    val schemaProperties = schema["properties"] as? Map<*, *>
    val schemaFormatProperty = schemaProperties?.get("format") as? Map<*, *>
    val schemaVersionProperty = schemaProperties?.get("schemaVersion") as? Map<*, *>
    val schemaFingerprintAlgorithmProperty = schemaProperties?.get("registryFingerprintAlgorithm") as? Map<*, *>
    val schemaFingerprintProperty = schemaProperties?.get("registryFingerprint") as? Map<*, *>
    val schemaChecksProperty = schemaProperties?.get("checks") as? Map<*, *>
    val schemaCheckItem = schemaChecksProperty?.get("items") as? Map<*, *>

    if (schema["\$id"] != trainingModuleChecksReportSchemaId) {
        problems += "schema \$id should be $trainingModuleChecksReportSchemaId"
    }
    if (trainingModuleRequiredFieldSet(schema["required"]) != trainingModuleChecksRootFieldSet) {
        problems += "schema root required fields drifted"
    }
    if (schemaFormatProperty?.get("const") != trainingModuleChecksReportFormat) {
        problems += "schema format const should be $trainingModuleChecksReportFormat"
    }
    if ((schemaVersionProperty?.get("const") as? Number)?.toInt() != trainingModuleChecksReportSchemaVersion) {
        problems += "schemaVersion const should be $trainingModuleChecksReportSchemaVersion"
    }
    if (schemaFingerprintAlgorithmProperty?.get("const") != "SHA-256") {
        problems += "schema registryFingerprintAlgorithm const should be SHA-256"
    }
    if (schemaFingerprintProperty?.get("pattern") != "^[0-9a-f]{64}$") {
        problems += "schema registryFingerprint pattern drifted"
    }
    if (trainingModuleRequiredFieldSet(schemaCheckItem?.get("required")) != trainingModuleChecksEntryFieldSet) {
        problems += "schema check entry required fields drifted"
    }

    if (trainingModuleFieldSet(report) != trainingModuleChecksRootFieldSet) {
        problems += "report root fields drifted"
    }
    if (report["format"] != trainingModuleChecksReportFormat) {
        problems += "report format should be $trainingModuleChecksReportFormat"
    }
    if ((report["schemaVersion"] as? Number)?.toInt() != trainingModuleChecksReportSchemaVersion) {
        problems += "report schemaVersion should be $trainingModuleChecksReportSchemaVersion"
    }
    if (report["registryFingerprintAlgorithm"] != "SHA-256") {
        problems += "report registryFingerprintAlgorithm should be SHA-256"
    }
    val registryFingerprint = report["registryFingerprint"]?.toString().orEmpty()
    if (!registryFingerprint.matches(Regex("^[0-9a-f]{64}$"))) {
        problems += "report registryFingerprint should be a lowercase SHA-256 hex digest"
    }

    val checkCount = (report["checkCount"] as? Number)?.toInt()
    val checks = report["checks"] as? List<*>
    if (checkCount == null || checkCount < 1) {
        problems += "report checkCount should be a positive integer"
    }
    if (checks == null || checks.isEmpty()) {
        problems += "report checks should be a non-empty list"
    } else {
        if (checkCount != checks.size) {
            problems += "report checkCount=$checkCount but checks.size=${checks.size}"
        }
        val reportCheckRows = mutableListOf<Map<String, Any?>>()
        checks.forEach { checkEntry ->
            val check = checkEntry as? Map<*, *>
            if (check == null) {
                problems += "report check entry should be an object"
                return@forEach
            }
            if (trainingModuleFieldSet(check) != trainingModuleChecksEntryFieldSet) {
                problems += "report check entry fields drifted for ${check["label"]}"
            }

            val label = check["label"]?.toString().orEmpty()
            val projectPath = check["projectPath"]?.toString().orEmpty()
            val taskName = check["taskName"]?.toString().orEmpty()
            val taskPath = check["taskPath"]?.toString().orEmpty()
            if (label.isBlank() || taskName.isBlank()) {
                problems += "report check label/taskName should be non-blank"
            }
            if (!projectPath.startsWith(":")) {
                problems += "report check '$label' projectPath should be absolute: $projectPath"
            }
            if (taskPath != "$projectPath:$taskName") {
                problems += "report check '$label' taskPath should equal projectPath:taskName"
            }
            if (check["projectExists"] != true || check["taskExists"] != true) {
                problems += "report check '$label' should have projectExists=true and taskExists=true"
            }
            reportCheckRows += mapOf(
                "label" to label,
                "projectPath" to projectPath,
                "taskName" to taskName,
                "taskPath" to taskPath,
            )
        }

        val expectedFingerprint = trainingModuleChecksFingerprintFromRows(reportCheckRows)
        if (registryFingerprint != expectedFingerprint) {
            problems += "report registryFingerprint should be $expectedFingerprint"
        }
    }

    if (problems.isNotEmpty()) {
        throw GradleException("Training module check report contract problems:\n"
            + problems.joinToString(separator = "\n") { "- $it" })
    }
}

fun requireTrainingModuleCoverageReportContract(report: Map<*, *>) {
    val problems = mutableListOf<String>()
    if (trainingModuleFieldSet(report) != trainingModuleCoverageRootFieldSet) {
        problems += "coverage report root fields drifted"
    }
    if (report["format"] != trainingModuleCoverageReportFormat) {
        problems += "coverage report format should be $trainingModuleCoverageReportFormat"
    }
    if ((report["schemaVersion"] as? Number)?.toInt() != trainingModuleCoverageReportSchemaVersion) {
        problems += "coverage report schemaVersion should be $trainingModuleCoverageReportSchemaVersion"
    }

    val projectCount = (report["projectCount"] as? Number)?.toInt()
    val registeredProjectCount = (report["registeredProjectCount"] as? Number)?.toInt()
    val testProjectCount = (report["testProjectCount"] as? Number)?.toInt()
    val unregisteredTestProjectCount = (report["unregisteredTestProjectCount"] as? Number)?.toInt()
    val unregisteredTestProjects = trainingModuleStringList(report["unregisteredTestProjects"])
    val candidateCheckCount = (report["candidateCheckCount"] as? Number)?.toInt()
    val candidateChecks = report["candidateChecks"] as? List<*>
    val projects = report["projects"] as? List<*>

    if (projectCount == null || projectCount < 0) {
        problems += "coverage report projectCount should be a non-negative integer"
    }
    if (registeredProjectCount == null || registeredProjectCount < 0) {
        problems += "coverage report registeredProjectCount should be a non-negative integer"
    }
    if (testProjectCount == null || testProjectCount < 0) {
        problems += "coverage report testProjectCount should be a non-negative integer"
    }
    if (unregisteredTestProjectCount == null || unregisteredTestProjectCount < 0) {
        problems += "coverage report unregisteredTestProjectCount should be a non-negative integer"
    }
    if (unregisteredTestProjects == null || unregisteredTestProjects.any { !it.startsWith(":") }) {
        problems += "coverage report unregisteredTestProjects should be a list of absolute project paths"
    }
    if (candidateCheckCount == null || candidateCheckCount < 0) {
        problems += "coverage report candidateCheckCount should be a non-negative integer"
    }

    val normalizedCandidateChecks = mutableListOf<Map<String, String>>()
    if (candidateChecks == null) {
        problems += "coverage report candidateChecks should be a list"
    } else {
        if (candidateCheckCount != null && candidateCheckCount != candidateChecks.size) {
            problems += buildString {
                append("coverage report candidateCheckCount=")
                append(candidateCheckCount)
                append(" but candidateChecks.size=")
                append(candidateChecks.size)
            }
        }

        val seenCandidateLabels = mutableSetOf<String>()
        val seenCandidateTaskPaths = mutableSetOf<String>()
        candidateChecks.forEach { candidateEntry ->
            val candidate = candidateEntry as? Map<*, *>
            if (candidate == null) {
                problems += "coverage report candidate check entry should be an object"
                return@forEach
            }
            if (trainingModuleFieldSet(candidate) != trainingModuleCoverageCandidateFieldSet) {
                problems += "coverage report candidate check entry fields drifted for ${candidate["label"]}"
            }
            val label = candidate["label"]?.toString().orEmpty()
            val projectPath = candidate["projectPath"]?.toString().orEmpty()
            val taskName = candidate["taskName"]?.toString().orEmpty()
            val taskPath = candidate["taskPath"]?.toString().orEmpty()
            if (label.isBlank() || taskName.isBlank()) {
                problems += "coverage candidate check label/taskName should be non-blank"
            } else if (!seenCandidateLabels.add(label)) {
                problems += "coverage candidate check label should be unique: $label"
            }
            if (!projectPath.startsWith(":")) {
                problems += "coverage candidate check '$label' projectPath should be absolute: $projectPath"
            }
            if (taskPath != "$projectPath:$taskName") {
                problems += "coverage candidate check '$label' taskPath should equal projectPath:taskName"
            } else if (!seenCandidateTaskPaths.add(taskPath)) {
                problems += "coverage candidate check taskPath should be unique: $taskPath"
            }
            normalizedCandidateChecks += mapOf(
                "label" to label,
                "projectPath" to projectPath,
                "taskName" to taskName,
                "taskPath" to taskPath,
            )
        }
    }

    if (projects == null) {
        problems += "coverage report projects should be a list"
    } else {
        if (projectCount != null && projectCount != projects.size) {
            problems += "coverage report projectCount=$projectCount but projects.size=${projects.size}"
        }

        val seenProjectPaths = mutableSetOf<String>()
        val computedUnregisteredTestProjects = mutableListOf<String>()
        val computedCandidateChecks = mutableListOf<Map<String, String>>()
        var computedRegisteredProjectCount = 0
        var computedTestProjectCount = 0

        projects.forEach { projectEntry ->
            val trainingProject = projectEntry as? Map<*, *>
            if (trainingProject == null) {
                problems += "coverage report project entry should be an object"
                return@forEach
            }
            if (trainingModuleFieldSet(trainingProject) != trainingModuleCoverageEntryFieldSet) {
                problems += "coverage report project entry fields drifted for ${trainingProject["projectPath"]}"
            }

            val projectPath = trainingProject["projectPath"]?.toString().orEmpty()
            val projectDirectory = trainingProject["projectDirectory"]?.toString().orEmpty()
            val hasTestSources = trainingProject["hasTestSources"] as? Boolean
            val testTaskExists = trainingProject["testTaskExists"] as? Boolean
            val registered = trainingProject["registered"] as? Boolean
            val labels = trainingModuleStringList(trainingProject["labels"])
            val taskPaths = trainingModuleStringList(trainingProject["taskPaths"])

            if (!projectPath.startsWith(":")) {
                problems += "coverage projectPath should be absolute: $projectPath"
            } else if (!seenProjectPaths.add(projectPath)) {
                problems += "coverage projectPath should be unique: $projectPath"
            }
            if (!projectDirectory.startsWith("training/")) {
                problems += "coverage project '$projectPath' directory should be under training/: $projectDirectory"
            }
            if (hasTestSources == null || testTaskExists == null || registered == null) {
                problems += "coverage project '$projectPath' should expose boolean hasTestSources/testTaskExists/registered"
            }
            if (labels == null || labels.any { it.isBlank() }) {
                problems += "coverage project '$projectPath' labels should be a list of non-blank strings"
            }
            if (taskPaths == null || taskPaths.any { !it.startsWith(":") }) {
                problems += "coverage project '$projectPath' taskPaths should be a list of absolute task paths"
            }
            if (registered == true) {
                computedRegisteredProjectCount += 1
                if (labels.isNullOrEmpty() || taskPaths.isNullOrEmpty()) {
                    problems += "registered coverage project '$projectPath' should include labels and taskPaths"
                }
            } else if (!labels.isNullOrEmpty() || !taskPaths.isNullOrEmpty()) {
                problems += "unregistered coverage project '$projectPath' should not include labels or taskPaths"
            }
            if (hasTestSources == true && testTaskExists == true) {
                computedTestProjectCount += 1
                if (registered != true) {
                    computedUnregisteredTestProjects += projectPath
                    computedCandidateChecks += mapOf(
                        "label" to trainingModuleSuggestedCheckLabel(projectPath),
                        "projectPath" to projectPath,
                        "taskName" to "test",
                        "taskPath" to "$projectPath:test",
                    )
                }
            }
        }

        if (registeredProjectCount != null && registeredProjectCount != computedRegisteredProjectCount) {
            problems += "coverage report registeredProjectCount=$registeredProjectCount but computed=$computedRegisteredProjectCount"
        }
        if (testProjectCount != null && testProjectCount != computedTestProjectCount) {
            problems += "coverage report testProjectCount=$testProjectCount but computed=$computedTestProjectCount"
        }
        if (unregisteredTestProjectCount != null
            && unregisteredTestProjectCount != computedUnregisteredTestProjects.size
        ) {
            problems += buildString {
                append("coverage report unregisteredTestProjectCount=")
                append(unregisteredTestProjectCount)
                append(" but computed=")
                append(computedUnregisteredTestProjects.size)
            }
        }
        if (unregisteredTestProjects != null && unregisteredTestProjects != computedUnregisteredTestProjects.sorted()) {
            problems += "coverage report unregisteredTestProjects should match computed sorted test projects"
        }
        val sortedComputedCandidateChecks = computedCandidateChecks.sortedBy { it["label"].orEmpty() }
        val sortedCandidateChecks = normalizedCandidateChecks.sortedBy { it["label"].orEmpty() }
        if (candidateChecks != null && sortedCandidateChecks != sortedComputedCandidateChecks) {
            problems += "coverage report candidateChecks should match computed sorted unregistered test projects"
        }
    }

    if (problems.isNotEmpty()) {
        throw GradleException("Training module coverage report contract problems:\n"
            + problems.joinToString(separator = "\n") { "- $it" })
    }
}

fun requireTrainingModuleCandidateSelectionReportContract(
    report: Map<*, *>,
    availableCandidates: List<Map<String, Any?>>? = null,
) {
    val problems = mutableListOf<String>()
    if (trainingModuleFieldSet(report) != trainingModuleCandidateSelectionRootFieldSet) {
        problems += "candidate selection report root fields drifted"
    }
    if (report["format"] != trainingModuleCandidateSelectionReportFormat) {
        problems += "candidate selection report format should be $trainingModuleCandidateSelectionReportFormat"
    }
    if ((report["schemaVersion"] as? Number)?.toInt() != trainingModuleCandidateSelectionReportSchemaVersion) {
        problems += "candidate selection report schemaVersion should be $trainingModuleCandidateSelectionReportSchemaVersion"
    }
    if (report["selectionProperty"] != trainingModuleCandidateSelectionProperty) {
        problems += "candidate selection report selectionProperty should be $trainingModuleCandidateSelectionProperty"
    }

    val selectionValue = report["selectionValue"]?.toString().orEmpty()
    val selectionFingerprint = report["selectionFingerprint"]?.toString().orEmpty()
    val selectedAll = report["selectedAll"] as? Boolean
    val selectedLabelCount = (report["selectedLabelCount"] as? Number)?.toInt()
    val selectedLabels = trainingModuleStringList(report["selectedLabels"])
    val candidateCount = (report["candidateCount"] as? Number)?.toInt()
    val taskPaths = trainingModuleStringList(report["taskPaths"])
    val candidates = report["candidates"] as? List<*>
    val promotionSnippets = trainingModuleStringList(report["promotionSnippets"])
    val nextCommands = trainingModuleStringList(report["nextCommands"])

    val computedSelectedLabels = trainingModuleCandidateSelectionLabels(selectionValue).sorted()
    if (report["selectionFingerprintAlgorithm"] != "SHA-256") {
        problems += "candidate selection report selectionFingerprintAlgorithm should be SHA-256"
    }
    if (!selectionFingerprint.matches(Regex("^[0-9a-f]{64}$"))) {
        problems += "candidate selection report selectionFingerprint should be a lowercase SHA-256 hex digest"
    }
    if (selectedAll == null) {
        problems += "candidate selection report selectedAll should be boolean"
    } else if (selectedAll != computedSelectedLabels.isEmpty()) {
        problems += "candidate selection report selectedAll should match selected labels"
    }
    if (selectedLabelCount == null || selectedLabelCount != computedSelectedLabels.size) {
        problems += "candidate selection report selectedLabelCount should match selectedLabels"
    }
    if (selectedLabels == null || selectedLabels != computedSelectedLabels) {
        problems += "candidate selection report selectedLabels should match selectionValue"
    }
    if (candidateCount == null || candidateCount < 0) {
        problems += "candidate selection report candidateCount should be a non-negative integer"
    }
    if (taskPaths == null || taskPaths.any { !it.startsWith(":") }) {
        problems += "candidate selection report taskPaths should be absolute task paths"
    }
    if (promotionSnippets == null || promotionSnippets.any { it.isBlank() }) {
        problems += "candidate selection report promotionSnippets should be non-blank strings"
    }
    if (nextCommands == null || nextCommands.any { !it.startsWith("./gradlew ") }) {
        problems += "candidate selection report nextCommands should contain ./gradlew commands"
    } else if (nextCommands != trainingModuleCandidateSelectionNextCommands(selectionValue)) {
        problems += "candidate selection report nextCommands should match selectionValue"
    }

    val normalizedCandidates = mutableListOf<Map<String, String>>()
    if (candidates == null) {
        problems += "candidate selection report candidates should be a list"
    } else {
        if (candidateCount != null && candidateCount != candidates.size) {
            problems += "candidate selection report candidateCount=$candidateCount but candidates.size=${candidates.size}"
        }
        candidates.forEach { candidateEntry ->
            val candidate = candidateEntry as? Map<*, *>
            if (candidate == null) {
                problems += "candidate selection report candidate entry should be an object"
                return@forEach
            }
            if (trainingModuleFieldSet(candidate) != trainingModuleCoverageCandidateFieldSet) {
                problems += "candidate selection report candidate fields drifted for ${candidate["label"]}"
            }
            val label = candidate["label"]?.toString().orEmpty()
            val projectPath = candidate["projectPath"]?.toString().orEmpty()
            val taskName = candidate["taskName"]?.toString().orEmpty()
            val taskPath = candidate["taskPath"]?.toString().orEmpty()
            if (label.isBlank() || taskName.isBlank()) {
                problems += "candidate selection report candidate label/taskName should be non-blank"
            }
            if (!projectPath.startsWith(":")) {
                problems += "candidate selection report candidate '$label' projectPath should be absolute"
            }
            if (taskPath != "$projectPath:$taskName") {
                problems += "candidate selection report candidate '$label' taskPath should equal projectPath:taskName"
            }
            normalizedCandidates += mapOf(
                "label" to label,
                "projectPath" to projectPath,
                "taskName" to taskName,
                "taskPath" to taskPath,
            )
        }
    }

    if (candidates != null) {
        val expectedCandidates = trainingModuleCoverageCandidateRowsForSelection(
            selectionValue,
            availableCandidates ?: trainingModuleCoverageCandidateRows(trainingModuleCoverageRows()),
        )
            .map { candidate ->
                mapOf(
                    "label" to candidate["label"]?.toString().orEmpty(),
                    "projectPath" to candidate["projectPath"]?.toString().orEmpty(),
                    "taskName" to candidate["taskName"]?.toString().orEmpty(),
                    "taskPath" to candidate["taskPath"]?.toString().orEmpty(),
                )
            }
        if (normalizedCandidates != expectedCandidates) {
            problems += "candidate selection report candidates should match computed selection"
        }
        val expectedSelectionFingerprint = trainingModuleChecksFingerprintFromRows(expectedCandidates)
        if (selectionFingerprint != expectedSelectionFingerprint) {
            problems += "candidate selection report selectionFingerprint should be $expectedSelectionFingerprint"
        }
        if (taskPaths != null && taskPaths != expectedCandidates.map { it["taskPath"].orEmpty() }) {
            problems += "candidate selection report taskPaths should match candidates"
        }
        if (promotionSnippets != null && promotionSnippets != expectedCandidates.map(::trainingModuleCandidatePromotionSnippet)) {
            problems += "candidate selection report promotionSnippets should match candidates"
        }
    }

    if (problems.isNotEmpty()) {
        throw GradleException("Training module candidate selection report contract problems:\n"
            + problems.joinToString(separator = "\n") { "- $it" })
    }
}

fun requireTrainingModuleCandidateLockContract(lock: Map<*, *>) {
    val problems = mutableListOf<String>()
    if (trainingModuleFieldSet(lock) != trainingModuleCandidateLockRootFieldSet) {
        problems += "candidate lock root fields drifted"
    }
    if (lock["format"] != trainingModuleCandidateLockFormat) {
        problems += "candidate lock format should be $trainingModuleCandidateLockFormat"
    }
    if ((lock["schemaVersion"] as? Number)?.toInt() != trainingModuleCandidateLockSchemaVersion) {
        problems += "candidate lock schemaVersion should be $trainingModuleCandidateLockSchemaVersion"
    }
    if (lock["coverageFormat"] != trainingModuleCoverageReportFormat) {
        problems += "candidate lock coverageFormat should be $trainingModuleCoverageReportFormat"
    }
    if (lock["candidateFingerprintAlgorithm"] != "SHA-256") {
        problems += "candidate lock candidateFingerprintAlgorithm should be SHA-256"
    }
    val candidateFingerprint = lock["candidateFingerprint"]?.toString().orEmpty()
    if (!candidateFingerprint.matches(Regex("^[0-9a-f]{64}$"))) {
        problems += "candidate lock candidateFingerprint should be a lowercase SHA-256 hex digest"
    }

    val candidateCount = (lock["candidateCount"] as? Number)?.toInt()
    val candidates = lock["candidates"] as? List<*>
    if (candidateCount == null || candidateCount < 0) {
        problems += "candidate lock candidateCount should be a non-negative integer"
    }
    if (candidates == null) {
        problems += "candidate lock candidates should be a list"
    } else {
        if (candidateCount != candidates.size) {
            problems += "candidate lock candidateCount=$candidateCount but candidates.size=${candidates.size}"
        }
        val rows = mutableListOf<Map<String, Any?>>()
        val labels = mutableSetOf<String>()
        candidates.forEach { candidateEntry ->
            val candidate = candidateEntry as? Map<*, *>
            if (candidate == null) {
                problems += "candidate lock candidate entry should be an object"
                return@forEach
            }
            if (trainingModuleFieldSet(candidate) != trainingModuleCoverageCandidateFieldSet) {
                problems += "candidate lock candidate fields drifted for ${candidate["label"]}"
            }
            val label = candidate["label"]?.toString().orEmpty()
            val projectPath = candidate["projectPath"]?.toString().orEmpty()
            val taskName = candidate["taskName"]?.toString().orEmpty()
            val taskPath = candidate["taskPath"]?.toString().orEmpty()
            if (label.isBlank() || taskName.isBlank()) {
                problems += "candidate lock candidate label/taskName should be non-blank"
            } else if (!labels.add(label)) {
                problems += "candidate lock candidate label should be unique: $label"
            }
            if (!projectPath.startsWith(":")) {
                problems += "candidate lock candidate '$label' projectPath should be absolute"
            }
            if (taskPath != "$projectPath:$taskName") {
                problems += "candidate lock candidate '$label' taskPath should equal projectPath:taskName"
            }
            rows += mapOf(
                "label" to label,
                "projectPath" to projectPath,
                "taskName" to taskName,
                "taskPath" to taskPath,
            )
        }
        val expectedFingerprint = trainingModuleChecksFingerprintFromRows(rows)
        if (candidateFingerprint != expectedFingerprint) {
            problems += "candidate lock candidateFingerprint should be $expectedFingerprint"
        }
    }

    if (problems.isNotEmpty()) {
        throw GradleException("Training module candidate lock contract problems:\n"
            + problems.joinToString(separator = "\n") { "- $it" })
    }
}

fun requireTrainingModuleCandidateLockDriftReportContract(report: Map<*, *>) {
    val problems = mutableListOf<String>()
    if (trainingModuleFieldSet(report) != trainingModuleCandidateLockDriftRootFieldSet) {
        problems += "candidate lock drift report root fields drifted"
    }
    if (report["format"] != "aqli.training.module-candidates.lock-drift.v1") {
        problems += "candidate lock drift report format should be aqli.training.module-candidates.lock-drift.v1"
    }
    if ((report["schemaVersion"] as? Number)?.toInt() != 1) {
        problems += "candidate lock drift report schemaVersion should be 1"
    }

    val passed = report["passed"] as? Boolean
    val missingLock = report["missingLock"] as? Boolean
    val fingerprintDrift = report["fingerprintDrift"] as? Boolean
    val expectedFingerprint = report["expectedFingerprint"]?.toString()
    val actualFingerprint = report["actualFingerprint"]?.toString().orEmpty()
    val addedLabels = trainingModuleStringList(report["addedLabels"])
    val removedLabels = trainingModuleStringList(report["removedLabels"])
    val changedCandidates = report["changedCandidates"] as? List<*>

    if (passed == null || missingLock == null || fingerprintDrift == null) {
        problems += "candidate lock drift report passed/missingLock/fingerprintDrift should be booleans"
    }
    if (report["lockFile"]?.toString().orEmpty().isBlank()) {
        problems += "candidate lock drift report lockFile should be non-blank"
    }
    if (missingLock == false && !expectedFingerprint.orEmpty().matches(Regex("^[0-9a-f]{64}$"))) {
        problems += "candidate lock drift report expectedFingerprint should be a lowercase SHA-256 hex digest"
    }
    if (!actualFingerprint.matches(Regex("^[0-9a-f]{64}$"))) {
        problems += "candidate lock drift report actualFingerprint should be a lowercase SHA-256 hex digest"
    }
    if (missingLock == true && expectedFingerprint != null) {
        problems += "candidate lock drift report expectedFingerprint should be null when missingLock=true"
    }
    if (missingLock == false && fingerprintDrift != (expectedFingerprint != actualFingerprint)) {
        problems += "candidate lock drift report fingerprintDrift should match expected/actual fingerprint comparison"
    }
    if (addedLabels == null || addedLabels.any { it.isBlank() }) {
        problems += "candidate lock drift report addedLabels should be a list of non-blank strings"
    }
    if (removedLabels == null || removedLabels.any { it.isBlank() }) {
        problems += "candidate lock drift report removedLabels should be a list of non-blank strings"
    }
    if (changedCandidates == null) {
        problems += "candidate lock drift report changedCandidates should be a list"
    } else {
        changedCandidates.forEach { changedEntry ->
            val changed = changedEntry as? Map<*, *>
            if (changed == null) {
                problems += "candidate lock drift report changedCandidates entries should be objects"
                return@forEach
            }
            if (trainingModuleFieldSet(changed) != trainingModuleCandidateLockDriftChangedCandidateFieldSet) {
                problems += "candidate lock drift report changed candidate fields drifted for ${changed["label"]}"
            }
            val expected = changed["expected"] as? Map<*, *>
            val actual = changed["actual"] as? Map<*, *>
            if (changed["label"]?.toString().orEmpty().isBlank()) {
                problems += "candidate lock drift report changed candidate label should be non-blank"
            }
            if (expected == null || trainingModuleFieldSet(expected) != trainingModuleCoverageCandidateFieldSet) {
                problems += "candidate lock drift report changed candidate expected fields drifted"
            }
            if (actual == null || trainingModuleFieldSet(actual) != trainingModuleCoverageCandidateFieldSet) {
                problems += "candidate lock drift report changed candidate actual fields drifted"
            }
        }
    }
    if (passed == true) {
        if (missingLock != false || fingerprintDrift != false) {
            problems += "passing candidate lock drift report should have missingLock=false and fingerprintDrift=false"
        }
        if (!addedLabels.isNullOrEmpty() || !removedLabels.isNullOrEmpty() || !changedCandidates.isNullOrEmpty()) {
            problems += "passing candidate lock drift report should not contain added, removed, or changed candidates"
        }
    }

    if (problems.isNotEmpty()) {
        throw GradleException("Training module candidate lock drift report contract problems:\n"
            + problems.joinToString(separator = "\n") { "- $it" })
    }
}

val validateTrainingModuleChecks = tasks.register("validateTrainingModuleChecks") {
    group = "verification"
    description = "Validates the registered Aqli training module verification checks."

    doLast {
        val problems = mutableListOf<String>()
        val labels = mutableSetOf<String>()
        val taskPaths = mutableSetOf<String>()

        trainingModuleChecks.forEach { check ->
            if (check.label.isBlank()) {
                problems += "Training module check label must not be blank"
            } else if (!labels.add(check.label)) {
                problems += "Duplicate training module check label: ${check.label}"
            }

            if (!check.projectPath.startsWith(":")) {
                problems += "Training module check '${check.label}' projectPath must be absolute: ${check.projectPath}"
            }

            if (check.taskName.isBlank()) {
                problems += "Training module check '${check.label}' taskName must not be blank"
            }

            if (!taskPaths.add(check.taskPath)) {
                problems += "Duplicate training module task path: ${check.taskPath}"
            }

            val registeredProject = findProject(check.projectPath)
            if (registeredProject == null) {
                problems += "Training module check '${check.label}' project does not exist: ${check.projectPath}"
            } else if (check.taskName !in registeredProject.tasks.names) {
                problems += "Training module check '${check.label}' task does not exist: ${check.taskPath}"
            }
        }

        if (problems.isNotEmpty()) {
            throw GradleException("Training module check registry problems:\n"
                + problems.joinToString(separator = "\n") { "- $it" })
        }

        println("Validated ${trainingModuleChecks.size} Aqli training module check(s).")
    }
}

val writeTrainingModuleChecksReport = tasks.register("writeTrainingModuleChecksReport") {
    group = "verification"
    description = "Writes JSON report/schema files for registered Aqli training module verification checks."
    dependsOn(validateTrainingModuleChecks)
    inputs.property("reportFormat", trainingModuleChecksReportFormat)
    inputs.property("reportSchemaVersion", trainingModuleChecksReportSchemaVersion)
    inputs.property("checks", trainingModuleChecks.map { "${it.label}|${it.projectPath}|${it.taskName}" })
    outputs.file(trainingModuleChecksReportFile)
    outputs.file(trainingModuleChecksSchemaFile)

    doLast {
        val outputFile = trainingModuleChecksReportFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(trainingModuleCheckReportPayload())) + "\n")

        val schemaFile = trainingModuleChecksSchemaFile.get().asFile
        schemaFile.parentFile.mkdirs()
        schemaFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(trainingModuleCheckReportSchemaPayload())) + "\n")

        println("Wrote training module checks report: ${outputFile.absolutePath}")
        println("Wrote training module checks schema: ${schemaFile.absolutePath}")
    }
}

val validateTrainingModuleChecksReportContract = tasks.register("validateTrainingModuleChecksReportContract") {
    group = "verification"
    description = "Validates the generated Aqli training module checks report contract."
    dependsOn(writeTrainingModuleChecksReport)
    inputs.property("reportFormat", trainingModuleChecksReportFormat)
    inputs.property("reportSchemaVersion", trainingModuleChecksReportSchemaVersion)
    inputs.file(trainingModuleChecksReportFile)
    inputs.file(trainingModuleChecksSchemaFile)

    doLast {
        val reportPayload = JsonSlurper().parse(trainingModuleChecksReportFile.get().asFile) as Map<*, *>
        val schemaPayload = JsonSlurper().parse(trainingModuleChecksSchemaFile.get().asFile) as Map<*, *>
        requireTrainingModuleChecksReportContract(reportPayload, schemaPayload)
        println("Validated training module checks report contract.")
    }
}

val writeTrainingModuleCoverageReport = tasks.register("writeTrainingModuleCoverageReport") {
    group = "verification"
    description = "Writes an advisory JSON coverage report for Gradle-included Aqli training modules."
    dependsOn(validateTrainingModuleChecks)
    inputs.property("coverageReportFormat", trainingModuleCoverageReportFormat)
    inputs.property("coverageReportSchemaVersion", trainingModuleCoverageReportSchemaVersion)
    inputs.property("checks", trainingModuleChecks.map { "${it.label}|${it.projectPath}|${it.taskName}" })
    outputs.file(trainingModuleCoverageReportFile)

    doLast {
        val coveragePayload = trainingModuleCoverageReportPayload()
        requireTrainingModuleCoverageReportContract(coveragePayload)

        val outputFile = trainingModuleCoverageReportFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(coveragePayload)) + "\n")
        println("Wrote training module coverage report: ${outputFile.absolutePath}")
    }
}

val validateTrainingModuleCoverageReportContract =
    tasks.register("validateTrainingModuleCoverageReportContract") {
        group = "verification"
        description = "Validates the generated Aqli training module coverage report contract."
        dependsOn(writeTrainingModuleCoverageReport)
        inputs.property("coverageReportFormat", trainingModuleCoverageReportFormat)
        inputs.file(trainingModuleCoverageReportFile)

        doLast {
            val coveragePayload = JsonSlurper().parse(trainingModuleCoverageReportFile.get().asFile) as Map<*, *>
            requireTrainingModuleCoverageReportContract(coveragePayload)
            println("Validated training module coverage report contract.")
        }
    }

tasks.register("printTrainingModuleCoverageJson") {
    group = "verification"
    description = "Prints advisory JSON coverage for Gradle-included Aqli training modules."
    dependsOn(validateTrainingModuleCoverageReportContract)

    doLast {
        println(trainingModuleCoverageReportFile.get().asFile.readText())
    }
}

tasks.register("printTrainingModuleCoverageSummary") {
    group = "verification"
    description = "Prints a human-readable summary of Aqli training module coverage."
    dependsOn(validateTrainingModuleCoverageReportContract)

    doLast {
        val coveragePayload = JsonSlurper().parse(trainingModuleCoverageReportFile.get().asFile) as Map<*, *>
        val candidateChecks = (coveragePayload["candidateChecks"] as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            ?: emptyList()

        println("Aqli training module coverage:")
        println("- Projects: ${coveragePayload["projectCount"]}")
        println("- Projects with tests: ${coveragePayload["testProjectCount"]}")
        println("- Registered hard-gate projects: ${coveragePayload["registeredProjectCount"]}")
        println("- Advisory candidate checks: ${coveragePayload["candidateCheckCount"]}")

        if (candidateChecks.isEmpty()) {
            println("Candidate checks to promote: none")
        } else {
            println("Candidate checks to promote:")
            candidateChecks.forEach { candidate ->
                println(buildString {
                    append("- ${trainingModuleCandidateSummaryLine(candidate)} -> ")
                    append("TrainingModuleCheck(label = \"${candidate["label"]}\", ")
                    append("projectPath = \"${candidate["projectPath"]}\")")
                })
            }
        }
    }
}

val writeTrainingModuleCandidateSelectionReport =
    tasks.register("writeTrainingModuleCandidateSelectionReport") {
        group = "verification"
        description = "Writes JSON report for the selected advisory Aqli training module candidates."
        dependsOn(validateTrainingModuleCoverageReportContract)
        inputs.property(trainingModuleCandidateSelectionProperty, trainingModuleCandidateSelection)
        inputs.file(trainingModuleCoverageReportFile)
        outputs.file(trainingModuleCandidateSelectionReportFile)

        doLast {
            val selectionValue = trainingModuleCandidateSelection.get()
            val availableCandidates = trainingModuleCoverageCandidateRowsFromReport()
            val selectionPayload = trainingModuleCandidateSelectionReportPayload(selectionValue, availableCandidates)
            requireTrainingModuleCandidateSelectionReportContract(selectionPayload, availableCandidates)

            val outputFile = trainingModuleCandidateSelectionReportFile.get().asFile
            outputFile.parentFile.mkdirs()
            outputFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(selectionPayload)) + "\n")
            println("Wrote training module candidate selection report: ${outputFile.absolutePath}")
        }
    }

val validateTrainingModuleCandidateSelectionReportContract =
    tasks.register("validateTrainingModuleCandidateSelectionReportContract") {
        group = "verification"
        description = "Validates the generated Aqli training module candidate selection report contract."
        dependsOn(writeTrainingModuleCandidateSelectionReport)
        inputs.property("candidateSelectionReportFormat", trainingModuleCandidateSelectionReportFormat)
        inputs.property(trainingModuleCandidateSelectionProperty, trainingModuleCandidateSelection)
        inputs.file(trainingModuleCoverageReportFile)
        inputs.file(trainingModuleCandidateSelectionReportFile)

        doLast {
            val availableCandidates = trainingModuleCoverageCandidateRowsFromReport()
            val selectionPayload =
                JsonSlurper().parse(trainingModuleCandidateSelectionReportFile.get().asFile) as Map<*, *>
            requireTrainingModuleCandidateSelectionReportContract(selectionPayload, availableCandidates)
            println("Validated training module candidate selection report contract.")
        }
    }

tasks.register("printTrainingModuleCandidateSelectionJson") {
    group = "verification"
    description = "Prints JSON report for the selected advisory Aqli training module candidates."
    dependsOn(validateTrainingModuleCandidateSelectionReportContract)

    doLast {
        println(trainingModuleCandidateSelectionReportFile.get().asFile.readText())
    }
}

tasks.register("validateTrainingModuleCandidateSelection") {
    group = "verification"
    description = "Validates the advisory training module candidate selection without resolving test tasks."
    dependsOn(validateTrainingModuleCandidateSelectionReportContract)
    inputs.property(trainingModuleCandidateSelectionProperty, trainingModuleCandidateSelection)

    doLast {
        val selectionValue = trainingModuleCandidateSelection.get()
        val selectedLabels = trainingModuleCandidateSelectionLabels(selectionValue)
        val selectionPayload =
            JsonSlurper().parse(trainingModuleCandidateSelectionReportFile.get().asFile) as Map<*, *>
        val candidates = trainingModuleCandidateRowsFromValue(selectionPayload["candidates"])
        if (candidates.isEmpty()) {
            println("No advisory training module candidates are available.")
        } else if (selectedLabels.isEmpty()) {
            println("Validated all ${candidates.size} advisory training module candidate selection(s):")
            candidates.forEach { candidate -> println("- ${trainingModuleCandidateSummaryLine(candidate)}") }
        } else {
            println("Validated ${candidates.size} selected advisory training module candidate selection(s):")
            candidates.forEach { candidate -> println("- ${trainingModuleCandidateSummaryLine(candidate)}") }
        }
    }
}

tasks.register("checkTrainingModuleCandidateTests") {
    group = "verification"
    description = "Runs advisory tests for training modules that have tests but are not yet in the hard gate."
    dependsOn("validateTrainingModuleCandidateSelection")
    dependsOn(provider { trainingModuleCoverageCandidateTaskPaths(trainingModuleCandidateSelection.get()) })
    inputs.property(trainingModuleCandidateSelectionProperty, trainingModuleCandidateSelection)

    doFirst {
        val selectionValue = trainingModuleCandidateSelection.get()
        val selectedLabels = trainingModuleCandidateSelectionLabels(selectionValue)
        val taskPaths = trainingModuleCoverageCandidateTaskPaths(selectionValue)
        if (taskPaths.isEmpty()) {
            println("No advisory training module candidate tests to run.")
        } else {
            if (selectedLabels.isEmpty()) {
                println("Running all ${taskPaths.size} advisory training module candidate test task(s):")
            } else {
                println("Running ${taskPaths.size} selected advisory training module candidate test task(s):")
            }
            taskPaths.forEach { taskPath -> println("- $taskPath") }
        }
    }
}

tasks.register("printTrainingModuleCandidatePromotionSnippets") {
    group = "help"
    description = "Prints copy-pasteable TrainingModuleCheck snippets for advisory training candidates."
    dependsOn(validateTrainingModuleCoverageReportContract)
    inputs.file(trainingModuleCoverageReportFile)
    inputs.property(trainingModuleCandidateSelectionProperty, trainingModuleCandidateSelection)

    doLast {
        val selectionValue = trainingModuleCandidateSelection.get()
        val candidates =
            trainingModuleCoverageCandidateRowsForSelection(selectionValue, trainingModuleCoverageCandidateRowsFromReport())
        if (candidates.isEmpty()) {
            println("No advisory training module promotion snippets to print.")
        } else {
            println("TrainingModuleCheck snippets:")
            candidates.forEach { candidate ->
                println()
                println(trainingModuleCandidatePromotionSnippet(candidate))
            }
            println()
            println("After adding snippets, run:")
            println("./gradlew --no-daemon writeTrainingModuleChecksLock")
            println("./gradlew --no-daemon verifyTrainingGates")
        }
    }
}

val writeTrainingModuleCandidateLock = tasks.register("writeTrainingModuleCandidateLock") {
    group = "help"
    description = "Writes a checked-in lock for advisory Aqli training module candidates."
    dependsOn(validateTrainingModuleCoverageReportContract)
    inputs.property("candidateLockFormat", trainingModuleCandidateLockFormat)
    inputs.property("coverageReportFormat", trainingModuleCoverageReportFormat)
    inputs.file(trainingModuleCoverageReportFile)
    outputs.file(trainingModuleCandidateLockFile)

    doLast {
        val lockPayload = trainingModuleCandidateLockPayload(trainingModuleCoverageCandidateRowsFromReport())
        requireTrainingModuleCandidateLockContract(lockPayload)

        val outputFile = trainingModuleCandidateLockFile.asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(lockPayload)) + "\n")
        println("Wrote training module candidate lock: ${outputFile.relativeTo(projectDir)}")
    }
}

val writeTrainingModuleCandidateLockDriftReport = tasks.register("writeTrainingModuleCandidateLockDriftReport") {
    group = "verification"
    description = "Writes a JSON drift report for the advisory Aqli training module candidate lock."
    mustRunAfter(writeTrainingModuleCandidateLock)
    dependsOn(validateTrainingModuleCoverageReportContract)
    inputs.property("candidateLockFormat", trainingModuleCandidateLockFormat)
    inputs.property("coverageReportFormat", trainingModuleCoverageReportFormat)
    inputs.file(trainingModuleCoverageReportFile)
    inputs.file(trainingModuleCandidateLockFile).optional()
    outputs.file(trainingModuleCandidateLockDriftReportFile)

    doLast {
        val lockFile = trainingModuleCandidateLockFile.asFile
        val lockPayload = if (lockFile.isFile) {
            (JsonSlurper().parse(lockFile) as Map<*, *>).also(::requireTrainingModuleCandidateLockContract)
        } else {
            null
        }
        val currentCandidates = trainingModuleCoverageCandidateRowsFromReport()
        val driftPayload =
            trainingModuleCandidateLockDriftPayload(
                lockFile,
                lockPayload,
                trainingModuleCandidateLockPayload(currentCandidates),
            )

        val outputFile = trainingModuleCandidateLockDriftReportFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(driftPayload)) + "\n")
        println("Wrote training module candidate lock drift report: ${outputFile.absolutePath}")
    }
}

val validateTrainingModuleCandidateLockDriftReportContract =
    tasks.register("validateTrainingModuleCandidateLockDriftReportContract") {
        group = "verification"
        description = "Validates the advisory Aqli training module candidate lock drift report contract."
        dependsOn(writeTrainingModuleCandidateLockDriftReport)
        inputs.property("candidateLockDriftReportFormat", "aqli.training.module-candidates.lock-drift.v1")
        inputs.file(trainingModuleCandidateLockDriftReportFile)

        doLast {
            val driftPayload =
                JsonSlurper().parse(trainingModuleCandidateLockDriftReportFile.get().asFile) as Map<*, *>
            requireTrainingModuleCandidateLockDriftReportContract(driftPayload)
            println("Validated training module candidate lock drift report contract.")
        }
    }

tasks.register("printTrainingModuleCandidateLockDriftJson") {
    group = "verification"
    description = "Prints the advisory Aqli training module candidate lock drift report JSON."
    dependsOn(validateTrainingModuleCandidateLockDriftReportContract)

    doLast {
        println(trainingModuleCandidateLockDriftReportFile.get().asFile.readText())
    }
}

val validateTrainingModuleCandidateLock = tasks.register("validateTrainingModuleCandidateLock") {
    group = "verification"
    description = "Validates the advisory Aqli training module candidate set against the checked-in lock."
    mustRunAfter(writeTrainingModuleCandidateLock)
    dependsOn(validateTrainingModuleCandidateLockDriftReportContract)
    inputs.file(trainingModuleCandidateLockFile).optional()
    inputs.file(trainingModuleCandidateLockDriftReportFile)
    inputs.property("candidateLockFormat", trainingModuleCandidateLockFormat)
    inputs.property("coverageReportFormat", trainingModuleCoverageReportFormat)

    doLast {
        val driftPayload =
            JsonSlurper().parse(trainingModuleCandidateLockDriftReportFile.get().asFile) as Map<*, *>
        if (driftPayload["passed"] != true) {
            val lockFile = trainingModuleCandidateLockFile.asFile
            throw GradleException(
                buildString {
                    append("Training module candidate lock validation failed for ")
                    append(lockFile.relativeTo(projectDir))
                    append(". Run ./gradlew --no-daemon writeTrainingModuleCandidateLock ")
                    append("when the advisory candidate change is intentional. Drift report: ")
                    append(trainingModuleCandidateLockDriftReportFile.get().asFile.relativeTo(projectDir))
                }
            )
        }
        println("Validated training module candidate lock.")
    }
}

val writeTrainingModuleChecksLock = tasks.register("writeTrainingModuleChecksLock") {
    group = "help"
    description = "Writes a checked-in lock for Aqli training module verification checks."
    dependsOn(validateTrainingModuleChecksReportContract)
    inputs.property("lockFormat", trainingModuleChecksLockFormat)
    inputs.property("reportFormat", trainingModuleChecksReportFormat)
    inputs.property("checks", trainingModuleChecks.map { "${it.label}|${it.projectPath}|${it.taskName}" })
    outputs.file(trainingModuleChecksLockFile)

    doLast {
        val lockPayload = trainingModuleCheckLockPayload()
        requireTrainingModuleChecksLockContract(lockPayload)

        val outputFile = trainingModuleChecksLockFile.asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(lockPayload)) + "\n")
        println("Wrote training module checks lock: ${outputFile.relativeTo(projectDir)}")
    }
}

val writeTrainingModuleChecksLockDriftReport = tasks.register("writeTrainingModuleChecksLockDriftReport") {
    group = "verification"
    description = "Writes a JSON drift report for the Aqli training module checks lock."
    mustRunAfter(writeTrainingModuleChecksLock)
    dependsOn(validateTrainingModuleChecksReportContract)
    inputs.property("lockFormat", trainingModuleChecksLockFormat)
    inputs.property("reportFormat", trainingModuleChecksReportFormat)
    inputs.property("checks", trainingModuleChecks.map { "${it.label}|${it.projectPath}|${it.taskName}" })
    inputs.file(trainingModuleChecksLockFile).optional()
    outputs.file(trainingModuleChecksLockDriftReportFile)

    doLast {
        val lockFile = trainingModuleChecksLockFile.asFile
        val lockPayload = if (lockFile.isFile) {
            (JsonSlurper().parse(lockFile) as Map<*, *>).also(::requireTrainingModuleChecksLockContract)
        } else {
            null
        }
        val driftPayload = trainingModuleCheckLockDriftPayload(lockFile, lockPayload, trainingModuleCheckLockPayload())

        val outputFile = trainingModuleChecksLockDriftReportFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(driftPayload)) + "\n")
        println("Wrote training module checks lock drift report: ${outputFile.absolutePath}")
    }
}

val validateTrainingModuleChecksLockDriftReportContract =
    tasks.register("validateTrainingModuleChecksLockDriftReportContract") {
        group = "verification"
        description = "Validates the Aqli training module checks lock drift report contract."
        dependsOn(writeTrainingModuleChecksLockDriftReport)
        inputs.property("lockDriftReportFormat", "aqli.training.module-checks.lock-drift.v1")
        inputs.file(trainingModuleChecksLockDriftReportFile)

        doLast {
            val driftPayload = JsonSlurper().parse(trainingModuleChecksLockDriftReportFile.get().asFile) as Map<*, *>
            requireTrainingModuleChecksLockDriftReportContract(driftPayload)
            println("Validated training module checks lock drift report contract.")
        }
    }

tasks.register("printTrainingModuleChecksLockDriftJson") {
    group = "verification"
    description = "Prints the Aqli training module checks lock drift report JSON."
    dependsOn(validateTrainingModuleChecksLockDriftReportContract)

    doLast {
        println(trainingModuleChecksLockDriftReportFile.get().asFile.readText())
    }
}

val validateTrainingModuleChecksLock = tasks.register("validateTrainingModuleChecksLock") {
    group = "verification"
    description = "Validates the current training module checks registry against the checked-in lock."
    mustRunAfter(writeTrainingModuleChecksLock)
    dependsOn(validateTrainingModuleChecksLockDriftReportContract)
    inputs.file(trainingModuleChecksLockFile).optional()
    inputs.file(trainingModuleChecksLockDriftReportFile)
    inputs.property("lockFormat", trainingModuleChecksLockFormat)
    inputs.property("reportFormat", trainingModuleChecksReportFormat)

    doLast {
        val driftPayload = JsonSlurper().parse(trainingModuleChecksLockDriftReportFile.get().asFile) as Map<*, *>
        if (driftPayload["passed"] != true) {
            val lockFile = trainingModuleChecksLockFile.asFile
            throw GradleException(buildString {
                append("Training module checks lock validation failed for ${lockFile.relativeTo(projectDir)}. ")
                append("Run ./gradlew --no-daemon writeTrainingModuleChecksLock when the registry change is intentional. ")
                append("Drift report: ${trainingModuleChecksLockDriftReportFile.get().asFile.relativeTo(projectDir)}")
            })
        }
        println("Validated training module checks lock.")
    }
}

tasks.register("checkRecursiveReasoningTraining") {
    group = "verification"
    description = "Runs the recursive reasoning training module test suite."
    dependsOn(validateTrainingModuleChecks)
    dependsOn(trainingModuleCheck("recursive-reasoning").taskPath)
}

tasks.register("checkTrainingModules") {
    group = "verification"
    description = "Runs registered verification checks for Aqli training modules wired into Gradle."
    dependsOn(validateTrainingModuleChecks)
    dependsOn(validateTrainingModuleChecksReportContract)
    dependsOn(validateTrainingModuleChecksLock)
    dependsOn(trainingModuleChecks.map { it.taskPath })
}

tasks.register("verifyTrainingGates") {
    group = "verification"
    description = "Runs all Aqli training gate validations and registered training module checks."
    dependsOn("checkTrainingModules")
    dependsOn(validateTrainingModuleCoverageReportContract)
}

tasks.register("verifyTrainingAdvisoryGates") {
    group = "verification"
    description = "Validates advisory Aqli training module coverage, candidate reports, and candidate lock drift."
    dependsOn(validateTrainingModuleCoverageReportContract)
    dependsOn(validateTrainingModuleCandidateSelectionReportContract)
    dependsOn(validateTrainingModuleCandidateLock)
}

tasks.register("printTrainingModuleChecks") {
    group = "help"
    description = "Prints registered Aqli training module verification checks."

    doLast {
        println("Registered Aqli training module checks:")
        trainingModuleChecks.forEach { check ->
            println("- ${check.label}: ${check.taskPath}")
        }
    }
}
