import os
import re

directory = "runner/gguf/aqli-gguf-converter/src/main/java/tech/kayys/aqli/converter/gguf/"

for filename in os.listdir(directory):
    if filename.endswith(".java"):
        filepath = os.path.join(directory, filename)
        with open(filepath, 'r') as f:
            content = f.read()

        content = re.sub(r'cfg\.get([A-Z])([a-zA-Z0-9_]*)', lambda m: 'cfg.' + m.group(1).lower() + m.group(2), content)
        content = re.sub(r'cfg\.is([A-Z])([a-zA-Z0-9_]*)', lambda m: 'cfg.' + m.group(1).lower() + m.group(2), content)

        with open(filepath, 'w') as f:
            f.write(content)
