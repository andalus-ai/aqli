import os

file = "spi/aqli-spi-model/src/main/java/tech/kayys/aqli/spi/model/loader/ModelConfigLoader.java"

with open(file, 'r') as f:
    content = f.read()

# I'll just change the bad formatting back to normal slf4j or just remove the args and print a simple string.
# Actually, the easiest is to just delete the log.info statements altogether for now or replace them with a simple string.
import re
content = re.sub(r'log\.info\(.*?Loaded model config:.*?\);', 'log.info("Loaded model config");', content, flags=re.DOTALL)
content = re.sub(r'log\.info\(.*?Inferred fields from fallback.*?\);', 'log.info("Inferred fields from fallback");', content, flags=re.DOTALL)

with open(file, 'w') as f:
    f.write(content)

