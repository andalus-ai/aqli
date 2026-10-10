import re
import os

replacements = {
    "config.resolvedHeadDimForLayer(layer)": "config.getResolvedHeadDimForLayer(layer)",
    "config.resolvedNumKvHeadsForLayer(layer)": "config.getResolvedNumKvHeadsForLayer(layer)",
    "profile.getModelType()": "profile.modelType()",
    "config.eosTokenIds()": "config.getEosTokenIds()",
    "model.tokenizer().getBosTokenId()": "model.tokenizer().bosTokenId()",
    "model.tokenizer().getEosTokenId()": "model.tokenizer().eosTokenId()",
    "model.tokenizer().getPadTokenId()": "model.tokenizer().padTokenId()",
    "modelProfile.getModelType()": "modelProfile.modelType()",
    "prepared.preparationContext().getPrimaryArchitecture()": "prepared.preparationContext().primaryArchitecture()",
    "new ModelConfigLoader(objectMapper.get().loadFromDirectory(configDir))": "new ModelConfigLoader(objectMapper.get()).loadFromDirectory(configDir)",
    "new ModelConfigLoader(new ObjectMapper().load(configPath))": "new ModelConfigLoader(new ObjectMapper()).load(configPath)"
}

for root, _, files in os.walk('runner/safetensor'):
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

