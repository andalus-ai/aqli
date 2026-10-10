import re
import os

replacements = {
    "input.getHiddenSize()": "input.hiddenSize()",
    "plan.getNumKeyValueHeads()": "plan.numKeyValueHeads()",
    "config.ropeThetaForLayer(layerIdx)": "config.getRopeThetaForLayer(layerIdx)",
    "config.partialRotaryFactorForLayer(layerIdx)": "config.getPartialRotaryFactorForLayer(layerIdx)",
    "config.resolvedNumKvHeadsForLayer(layerIdx)": "config.getResolvedNumKvHeadsForLayer(layerIdx)",
    "admission.getResolvedHeadDim()": "admission.resolvedHeadDim()"
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

