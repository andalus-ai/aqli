import os
import re

replacements = {
    r'\.resolvedMaxKvHeads\(\)': '.getResolvedMaxKvHeads()',
    r'\.resolvedMaxHeadDim\(\)': '.getResolvedMaxHeadDim()',
}

for root, _, files in os.walk('.'):
    for file in files:
        if file.endswith('.java'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r') as f:
                content = f.read()

            new_content = content
            for old, new in replacements.items():
                new_content = re.sub(old, new, new_content)

            if new_content != content:
                with open(filepath, 'w') as f:
                    f.write(new_content)
                print(f"Replaced getters in {filepath}")
