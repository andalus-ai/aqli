import re

with open("spi/aqli-spi-model/src/main/java/tech/kayys/aqli/spi/model/ModelConfig.java", "r") as f:
    content = f.read()

# The duplicates are at the end of the file.
# We have a whole block of duplicate getters.
# Let's just remove the ones causing compiler errors.

content = re.sub(r'public int getNumLocalExperts\(\) \{.*?\}', '', content, flags=re.DOTALL, count=1)
content = re.sub(r'public int getNumExpertsPerTok\(\) \{.*?\}', '', content, flags=re.DOTALL, count=1)
content = re.sub(r'public int getMoeIntermediateSize\(\) \{.*?\}', '', content, flags=re.DOTALL, count=1)
content = re.sub(r'public double getQueryPreAttnScalar\(\) \{.*?\}', '', content, flags=re.DOTALL, count=1)
content = re.sub(r'public int getVisionSoftTokensPerImage\(\) \{.*?\}', '', content, flags=re.DOTALL, count=1)


with open("spi/aqli-spi-model/src/main/java/tech/kayys/aqli/spi/model/ModelConfig.java", "w") as f:
    f.write(content)
