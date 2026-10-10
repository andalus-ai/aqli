import re
import os

replacements = {
    "requirement.getModelType()": "requirement.modelType()",
    "compatibility.getModelType()": "compatibility.modelType()",
    "resolution.getModelType()": "resolution.modelType()",
    "profile.modelConfig().resolvedNumKvHeads()": "profile.modelConfig().getResolvedNumKvHeads()",
    "report.getModelType()": "report.modelType()",
    "report.getArchitectures()": "report.architectures()",
    "info.getVocabSize()": "info.vocabSize()",
    "result.getHiddenSize()": "result.hiddenSize()",
    "summary.getVocabSize()": "summary.vocabSize()",
    "new ModelConfigLoader(new ObjectMapper().load(configPath))": "new ModelConfigLoader(new ObjectMapper()).load(configPath)"
}

for root, _, files in os.walk('ui/aqli-cli'):
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

