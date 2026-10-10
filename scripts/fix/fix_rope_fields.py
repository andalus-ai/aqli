import re

file = "spi/aqli-spi-model/src/main/java/tech/kayys/aqli/spi/model/ModelConfig.java"
with open(file, 'r') as f:
    content = f.read()

content = re.sub(r'private\s+String\s+type;', 'public String type;', content)
content = re.sub(r'private\s+Double\s+factor;', 'public Double factor;', content)
content = re.sub(r'private\s+Double\s+lowFreqFactor;', 'public Double lowFreqFactor;', content)
content = re.sub(r'private\s+Double\s+highFreqFactor;', 'public Double highFreqFactor;', content)
content = re.sub(r'private\s+Integer\s+originalMaxPositionEmbeddings;', 'public Integer originalMaxPositionEmbeddings;', content)

with open(file, 'w') as f:
    f.write(content)

