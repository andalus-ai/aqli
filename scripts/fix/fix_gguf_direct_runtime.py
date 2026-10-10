import re

filepath = "sdk/aqli-sdk-local/src/main/java/tech/kayys/aqli/sdk/local/gguf/GgufDirectRuntime.java"
with open(filepath, 'r') as f:
    content = f.read()

content = content.replace("model.config().eosTokenIds()", "model.config().getEosTokenIds()")
content = content.replace("config.getBosTokenId().orElse(1)", "config.getBosTokenId() != null ? config.getBosTokenId() : 1")
content = content.replace("config.getEosTokenId().orElse(2)", "config.getEosTokenId() != null ? config.getEosTokenId() : 2")
content = content.replace("config.getPadTokenId().orElse(-1)", "config.getPadTokenId() != null ? config.getPadTokenId() : -1")
content = content.replace("config.resolvedNumKvHeads()", "config.getResolvedNumKvHeads()")

with open(filepath, 'w') as f:
    f.write(content)
