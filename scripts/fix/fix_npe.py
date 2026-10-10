import re
import os

file = "spi/aqli-spi-model/src/main/java/tech/kayys/aqli/spi/model/ModelConfig.java"
with open(file, 'r') as f:
    content = f.read()

content = content.replace("getMoeIntermediateSize() > 0", "(getMoeIntermediateSize() != null && getMoeIntermediateSize() > 0)")
content = content.replace("getNumLocalExperts() > 1", "(getNumLocalExperts() != null && getNumLocalExperts() > 1)")
content = content.replace("getNumExpertsPerTok() > 0", "(getNumExpertsPerTok() != null && getNumExpertsPerTok() > 0)")

with open(file, 'w') as f:
    f.write(content)

