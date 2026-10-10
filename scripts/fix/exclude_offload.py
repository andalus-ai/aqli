import os

broken = [
    'optimization:aqli-plugin-weight-offload'
]

settings_path = 'settings.gradle.kts'
with open(settings_path, 'r') as f:
    content = f.read()

for b in broken:
    content = content.replace(f'include("{b}")', f'// include("{b}")')

with open(settings_path, 'w') as f:
    f.write(content)

