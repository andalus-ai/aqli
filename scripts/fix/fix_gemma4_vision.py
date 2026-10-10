import re
import os

file = "models/aqli-model-gemma4/src/main/java/tech/kayys/aqli/models/gemma4/Gemma4VisionTower.java"
with open(file, 'r') as f:
    content = f.read()

content = content.replace("vConfig.numHiddenLayers", "vConfig.getNumHiddenLayers()")

with open(file, 'w') as f:
    f.write(content)

