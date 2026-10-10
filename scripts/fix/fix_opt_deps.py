import os

projects = [
    './optimization/aqli-plugin-perfmode/build.gradle.kts',
    './optimization/aqli-plugin-wait-scheduler/build.gradle.kts',
    './optimization/aqli-plugin-evicpress/build.gradle.kts',
    './optimization/aqli-plugin-prompt-cache/build.gradle.kts'
]

deps = """
    implementation(project(":spi:aqli-spi"))
    implementation(project(":spi:aqli-spi-plugin"))
    implementation(project(":core:plugin:aqli-plugin-optimization-core"))
"""

for p in projects:
    if os.path.exists(p):
        with open(p, 'r') as f:
            content = f.read()
        content = content.replace('dependencies {', 'dependencies {\n' + deps)
        with open(p, 'w') as f:
            f.write(content)
