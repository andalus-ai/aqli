import re
import os

replacements = {
    "resources.getVocabSize()": "resources.vocabSize()",
    "tokenizer.getVocabSize()": "tokenizer.vocabSize()",
    "tokenizer.getEosTokenId()": "tokenizer.eosTokenId()"
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

