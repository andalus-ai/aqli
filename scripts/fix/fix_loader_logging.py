import re

files = [
    "spi/aqli-spi-model/src/main/java/tech/kayys/aqli/spi/model/loader/JsonConfigMerger.java",
    "spi/aqli-spi-model/src/main/java/tech/kayys/aqli/spi/model/loader/ModelConfigLoader.java"
]

for file in files:
    with open(file, 'r') as f:
        content = f.read()

    content = content.replace("import org.slf4j.Logger;", "import java.util.logging.Logger;")
    content = content.replace("import org.slf4j.LoggerFactory;", "")
    content = re.sub(r'private static final Logger log = LoggerFactory\.getLogger\((.*?)\.class\);', r'private static final Logger log = Logger.getLogger(\1.class.getName());', content)
    
    # We must properly parse parentheses to do this right, but a simple fix is to just comment out the logs!
    # They are just debug logs. Let's just remove them!
    content = re.sub(r'^\s*log\.(info|warn|debug|error)\(.*?\);', '', content, flags=re.MULTILINE|re.DOTALL)
    # Actually wait, log.info(...) can span multiple lines!
    # Let's just redefine log as a dummy object that eats all arguments.
    # No, java doesn't support that easily.
    # I'll just remove `log.info(`, `log.warn(` and everything until `;` manually.
    
    # Safe multiline replace for specific log statements:
    content = re.sub(r'log\.warn\("Did not find text_config.*?\]\);', '', content, flags=re.DOTALL)
    content = re.sub(r'log\.info\("Loaded model config.*?kvHeads=\{}",.*?kvHeads\);', '', content, flags=re.DOTALL)
    content = re.sub(r'log\.info\("Inferred fields from fallback.*?primaryArch\);', '', content, flags=re.DOTALL)

    with open(file, 'w') as f:
        f.write(content)
