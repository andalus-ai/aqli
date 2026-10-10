import re
import os

replacements = {
    "tokenizer.getVocabSize()": "tokenizer.vocabSize()",
    "tokenizer.getEosTokenId()": "tokenizer.eosTokenId()",
    "tokenizer.getBosTokenId()": "tokenizer.bosTokenId()",
    "tokenizer.getPadTokenId()": "tokenizer.padTokenId()",
    "traits.getModelType()": "traits.modelType()",
    "request.getNumKeyValueHeads()": "request.numKeyValueHeads()",
    "layout.getNumKeyValueHeads()": "layout.numKeyValueHeads()",
    "headLayout.getNumKeyValueHeads()": "headLayout.numKeyValueHeads()",
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

