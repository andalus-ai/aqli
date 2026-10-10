import re

file = "core/aqli-observability/src/main/java/tech/kayys/aqli/reliability/DefaultCircuitBreaker.java"
with open(file, 'r') as f:
    content = f.read()

content = content.replace("config.getSlidingWindowSize()", "config.slidingWindowSize()")

with open(file, 'w') as f:
    f.write(content)
