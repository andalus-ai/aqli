import re
import os

files = [
    "spi/aqli-spi-model/src/main/java/tech/kayys/aqli/spi/model/ModelFamilyRuntimeCompatibility.java",
    "spi/aqli-spi-model/src/main/java/tech/kayys/aqli/spi/model/ModelFamilyPluginRegistry.java",
    "spi/aqli-spi-model/src/main/java/tech/kayys/aqli/spi/model/ModelFamilyContractValidator.java"
]

for file in files:
    with open(file, 'r') as f:
        content = f.read()

    # We only want to revert `resolved.getModelType()` and `requirement.getModelType()`
    content = content.replace("resolved.getModelType()", "resolved.modelType()")
    content = content.replace("requirement.getModelType()", "requirement.modelType()")

    with open(file, 'w') as f:
        f.write(content)
