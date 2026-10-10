import re

file = "spi/aqli-spi-model/src/main/java/tech/kayys/aqli/spi/model/ModelConfig.java"

with open(file, 'r') as f:
    content = f.read()

content = content.replace("private String type;", "public String type;")
content = content.replace("private Double factor;", "public Double factor;")
content = content.replace("private Double lowFreqFactor;", "public Double lowFreqFactor;")
content = content.replace("private Double highFreqFactor;", "public Double highFreqFactor;")
content = content.replace("private Integer originalMaxPositionEmbeddings;", "public Integer originalMaxPositionEmbeddings;")

with open(file, 'w') as f:
    f.write(content)
