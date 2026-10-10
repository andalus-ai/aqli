import os

build_path = './training/aqli-serializer/build.gradle.kts'
with open(build_path, 'r') as f:
    content = f.read()

deps = """
    implementation(project(":core:aqli-core"))
    implementation(project(":ml:aqli-ml-core"))
    implementation(project(":ml:aqli-ml-persistence"))
    implementation(project(":ml:aqli-ml-estimator"))
    implementation("com.google.code.gson:gson:2.10.1")
"""
content = content.replace('    implementation(project(":core:aqli-core"))', deps)
with open(build_path, 'w') as f:
    f.write(content)

pb_path = './training/aqli-serializer/src/main/java/tech/kayys/aqli/serializer/PythonBridge.java'
with open(pb_path, 'r') as f:
    pb_content = f.read()

pb_content = pb_content.replace('import tech.kayys.aqli.ml.ensemble.*;', 'import tech.kayys.aqli.train.estimator.*;')
with open(pb_path, 'w') as f:
    f.write(pb_content)

