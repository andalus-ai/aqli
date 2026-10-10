import re
import os

# Fix ModelConfig
file = "spi/aqli-spi-model/src/main/java/tech/kayys/aqli/spi/model/ModelConfig.java"
with open(file, 'r') as f:
    content = f.read()

content = content.replace("private double factor", "public double factor")
content = content.replace("private double lowFreqFactor", "public double lowFreqFactor")
content = content.replace("private double highFreqFactor", "public double highFreqFactor")
content = content.replace("private int originalMaxPositionEmbeddings", "public int originalMaxPositionEmbeddings")

with open(file, 'w') as f:
    f.write(content)

# Fix ONNX provider
replacements = {
    "tokenizer.getPadTokenId()": "tokenizer.padTokenId()",
    "diagnostics.getModelType()": "diagnostics.modelType()",
    "diagnostics.getArchitectures()": "diagnostics.architectures()"
}

for root, _, files in os.walk('runner/onnx'):
    for file in files:
        if file.endswith('.java'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r') as f:
                content = f.read()
            
            new_content = content
            for old, new in replacements.items():
                new_content = new_content.replace(old, new)
            
            if new_content != content:
                with open(filepath, 'w') as f:
                    f.write(new_content)

