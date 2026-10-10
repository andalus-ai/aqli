#!/usr/bin/env python3

from __future__ import annotations

import re
import textwrap
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
DEFAULT_GROUP = "tech.kayys.aqli"
DEFAULT_VERSION = "0.1.0-SNAPSHOT"
DEFAULT_JAVA = 25


@dataclass
class Dependency:
    group_id: str
    artifact_id: str
    version: str | None
    scope: str | None
    exclusions: list[tuple[str | None, str | None]] = field(default_factory=list)


@dataclass
class Module:
    path: Path
    artifact_id: str
    group_id: str
    version: str
    packaging: str
    java_release: int
    modules: list[str]
    dependencies: list[Dependency]
    manifest_entries: dict[str, str]
    copy_to_plugins_dir: bool
    has_sources: bool
    has_tests: bool
    properties: dict[str, str]

    @property
    def rel(self) -> Path:
        return self.path.relative_to(ROOT)

    @property
    def gradle_path(self) -> str:
        return ":" + ":".join(self.rel.parts)

    @property
    def is_leaf(self) -> bool:
        return not self.modules

    @property
    def should_generate(self) -> bool:
        if self.packaging == "pom" and not self.has_sources:
            return False
        return self.is_leaf or self.has_sources


ROOT = Path(__file__).resolve().parents[1]


def xml_text(parent: ET.Element, query: str) -> str | None:
    node = parent.find(query, NS)
    if node is None or node.text is None:
        return None
    text = node.text.strip()
    return text or None


def parse_pom(pom_path: Path) -> Module:
    tree = ET.parse(pom_path)
    root = tree.getroot()

    artifact_id = xml_text(root, "m:artifactId") or pom_path.parent.name
    group_id = (
        xml_text(root, "m:groupId")
        or xml_text(root, "m:parent/m:groupId")
        or DEFAULT_GROUP
    )
    version = (
        xml_text(root, "m:version")
        or xml_text(root, "m:parent/m:version")
        or DEFAULT_VERSION
    )
    packaging = xml_text(root, "m:packaging") or "jar"
    modules = [node.text.strip() for node in root.findall("m:modules/m:module", NS) if node.text]

    properties_node = root.find("m:properties", NS)
    properties: dict[str, str] = {
        "project.version": version,
        "project.groupId": group_id,
        "project.artifactId": artifact_id,
    }
    if properties_node is not None:
        for child in properties_node:
            name = child.tag.split("}", 1)[-1]
            if child.text and child.text.strip():
                properties[name] = " ".join(child.text.split())
    java_release = DEFAULT_JAVA
    if properties_node is not None:
        for key in ("maven.compiler.release", "maven.compiler.source", "maven.compiler.target"):
            value = xml_text(properties_node, f"m:{key}")
            if value and value.isdigit():
                java_release = int(value)
                break

    compiler_plugin = root.find(
        "m:build/m:plugins/m:plugin[m:artifactId='maven-compiler-plugin']",
        NS,
    )
    if compiler_plugin is not None:
        for key in ("m:configuration/m:release", "m:configuration/m:source", "m:configuration/m:target"):
            value = xml_text(compiler_plugin, key)
            if value and value.isdigit():
                java_release = int(value)
                break

    dependencies: list[Dependency] = []
    for dep in root.findall("m:dependencies/m:dependency", NS):
        group = xml_text(dep, "m:groupId")
        artifact = xml_text(dep, "m:artifactId")
        if not group or not artifact:
            continue
        exclusions = []
        for exclusion in dep.findall("m:exclusions/m:exclusion", NS):
            exclusions.append((xml_text(exclusion, "m:groupId"), xml_text(exclusion, "m:artifactId")))
        dependencies.append(
            Dependency(
                group_id=group,
                artifact_id=artifact,
                version=resolve_props(xml_text(dep, "m:version"), properties),
                scope=xml_text(dep, "m:scope"),
                exclusions=exclusions,
            )
        )

    manifest_entries: dict[str, str] = {}
    jar_plugin = root.find(
        "m:build/m:plugins/m:plugin[m:artifactId='maven-jar-plugin']",
        NS,
    )
    if jar_plugin is not None:
        manifest = jar_plugin.find("m:configuration/m:archive/m:manifestEntries", NS)
        if manifest is not None:
            for child in manifest:
                name = child.tag.split("}", 1)[-1]
                if child.text and child.text.strip():
                    manifest_entries[name] = resolve_props(" ".join(child.text.split()), properties) or child.text.strip()

    copy_to_plugins_dir = ".aqli/plugins" in pom_path.read_text(encoding="utf-8", errors="ignore")
    has_sources = any((pom_path.parent / rel).exists() for rel in ("src/main/java", "src/main/kotlin", "src/main/resources"))
    has_tests = any((pom_path.parent / rel).exists() for rel in ("src/test/java", "src/test/kotlin", "src/test/resources"))

    return Module(
        path=pom_path.parent,
        artifact_id=artifact_id,
        group_id=group_id,
        version=version,
        packaging=packaging,
        java_release=java_release,
        modules=modules,
        dependencies=dependencies,
        manifest_entries=manifest_entries,
        copy_to_plugins_dir=copy_to_plugins_dir,
        has_sources=has_sources,
        has_tests=has_tests,
        properties=properties,
    )


def resolve_props(value: str | None, properties: dict[str, str]) -> str | None:
    if value is None:
        return None

    def repl(match: re.Match[str]) -> str:
        key = match.group(1)
        return properties.get(key, match.group(0))

    return re.sub(r"\$\{([^}]+)\}", repl, value)


def dep_configuration(scope: str | None) -> str:
    return {
        "provided": "compileOnly",
        "runtime": "runtimeOnly",
        "test": "testImplementation",
        "system": "compileOnly",
    }.get(scope or "", "implementation")


def render_external_dependency(dep: Dependency, configuration: str) -> str:
    entries = [f'group = "{dep.group_id}"', f'name = "{dep.artifact_id}"']
    if dep.version and dep.version != "${project.version}":
        entries.append(f'version = "{dep.version}"')
    call = f"{configuration}({', '.join(entries)})"
    if not dep.exclusions:
        return f"    {call}"

    lines = [f"    {call} {{"] 
    for group, artifact in dep.exclusions:
        args = []
        if group and group != "*":
            args.append(f'group = "{group}"')
        if artifact and artifact != "*":
            args.append(f'module = "{artifact}"')
        if args:
            lines.append(f"        exclude({', '.join(args)})")
    lines.append("    }")
    return "\n".join(lines)


def render_build_file(module: Module, artifact_to_project: dict[str, str]) -> str:
    dep_lines: list[str] = []
    uses_junit = module.has_tests

    for dep in module.dependencies:
        configuration = dep_configuration(dep.scope)
        uses_junit = uses_junit or dep.artifact_id.startswith("junit") or dep.group_id.startswith("org.junit")

        internal_project = artifact_to_project.get(dep.artifact_id) if dep.group_id.startswith(DEFAULT_GROUP) else None
        if internal_project:
            dep_lines.append(f'    {configuration}(project("{internal_project}"))')
            continue
        dep_lines.append(render_external_dependency(dep, configuration))

    if not dep_lines:
        dep_lines.append("    // Dependencies migrated from pom.xml can be added here as needed.")

    manifest_block = ""
    if module.manifest_entries:
        attrs = ",\n".join(
            f'                "{key}" to "{value.replace("${project.version}", DEFAULT_VERSION)}"'
            for key, value in sorted(module.manifest_entries.items())
        )
        manifest_block = f"""
tasks.jar {{
    manifest {{
        attributes(
            mapOf(
{attrs}
            )
        )
    }}
}}
"""

    plugin_copy_block = ""
    if module.copy_to_plugins_dir:
        plugin_copy_block = """
val installPluginJar by tasks.registering(Copy::class) {
    dependsOn(tasks.jar)
    from(tasks.jar)
    into(file("${System.getProperty("user.home")}/.aqli/plugins"))
}
"""

    junit_block = ""
    if uses_junit:
        junit_block = """
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
"""

    body = f"""plugins {{
    `java-library`
    `maven-publish`
}}

group = "{module.group_id}"
version = "{module.version}"

java {{
    toolchain {{
        languageVersion.set(JavaLanguageVersion.of({module.java_release}))
    }}
}}

repositories {{
    mavenCentral()
    mavenLocal()
}}

dependencies {{
{chr(10).join(dep_lines)}
}}

publishing {{
    publications {{
        create<MavenPublication>("mavenJava") {{
            from(components["java"])
        }}
    }}
    repositories {{
        mavenLocal()
    }}
}}
{manifest_block}{plugin_copy_block}{junit_block}"""
    return textwrap.dedent(body).strip() + "\n"


def write_if_missing(module: Module, artifact_to_project: dict[str, str]) -> bool:
    build_file = module.path / "build.gradle.kts"
    if build_file.exists():
        return False
    build_file.write_text(render_build_file(module, artifact_to_project), encoding="utf-8")
    return True


def render_settings(module_paths: list[str]) -> str:
    include_lines = "\n".join(f'include("{path}")' for path in module_paths)
    return f'rootProject.name = "aqli-engine"\n\n{include_lines}\n'


def normalize_module_paths(paths: set[str]) -> list[str]:
    return sorted(paths, key=lambda value: (value.count(":"), value))


def main() -> None:
    poms = sorted(path for path in ROOT.rglob("pom.xml") if path.stat().st_size > 0)
    modules = [parse_pom(pom) for pom in poms]

    artifact_to_project: dict[str, str] = {}
    for module in modules:
        artifact_to_project[module.artifact_id] = module.gradle_path

    for build in ROOT.rglob("build.gradle.kts"):
        if build.parent == ROOT:
            continue
        artifact_to_project.setdefault(build.parent.name, ":" + ":".join(build.parent.relative_to(ROOT).parts))

    created = []
    gradle_projects: set[str] = set()
    for build in ROOT.rglob("build.gradle.kts"):
        if build.parent == ROOT:
            continue
        gradle_projects.add(":".join(build.parent.relative_to(ROOT).parts))

    for module in modules:
        if not module.should_generate:
            continue
        gradle_projects.add(":".join(module.rel.parts))
        if write_if_missing(module, artifact_to_project):
            created.append(str(module.rel / "build.gradle.kts"))

    module_paths = normalize_module_paths({path.replace("/", ":") for path in gradle_projects})
    settings_file = ROOT / "settings.gradle.kts"
    settings_file.write_text(render_settings(module_paths), encoding="utf-8")

    print(f"Generated {len(created)} missing build.gradle.kts files.")
    for path in created:
        print(f"  - {path}")
    print("Updated settings.gradle.kts")


if __name__ == "__main__":
    main()
