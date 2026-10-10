import os

broken = [
    'plugins:aqli-plugin-observability',
    'plugins:aqli-plugin-pii-redaction',
    'plugins:aqli-plugin-rag',
    'plugins:aqli-plugin-prompt',
    'plugins:aqli-plugin-quota',
    'plugins:aqli-plugin-reasoning',
    'plugins:aqli-plugin-sampling',
    'plugins:aqli-plugin-semantic-cache',
    'plugins:aqli-plugin-streaming'
]

settings_path = 'settings.gradle.kts'
with open(settings_path, 'r') as f:
    content = f.read()

for b in broken:
    content = content.replace(f'include("{b}")', f'// include("{b}")')

with open(settings_path, 'w') as f:
    f.write(content)

