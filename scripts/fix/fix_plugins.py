import os

# fix build.gradle.kts for paged-attention
paged_build = './optimization/aqli-plugin-paged-attention/build.gradle.kts'
with open(paged_build, 'r') as f:
    content = f.read()
if 'io.smallrye.reactive:mutiny' not in content:
    content = content.replace('dependencies {', 'dependencies {\n    implementation("io.smallrye.reactive:mutiny:2.5.5")\n    implementation("org.jboss.logging:jboss-logging:3.6.1.Final")')
    with open(paged_build, 'w') as f:
        f.write(content)

# fix PagedAttentionPlugin.java
paged_plugin = './optimization/aqli-plugin-paged-attention/src/main/java/tech/kayys/aqli/kernel/paged/PagedAttentionPlugin.java'
with open(paged_plugin, 'r') as f:
    content = f.read()
content = content.replace('public Uni<Void> initialize', 'public io.smallrye.mutiny.Uni<Void> initialize')
content = content.replace('public Uni<Void> shutdown', 'public io.smallrye.mutiny.Uni<Void> shutdown')
with open(paged_plugin, 'w') as f:
    f.write(content)

# fix build.gradle.kts for content-safety
safety_build = './plugins/aqli-plugin-content-safety/build.gradle.kts'
with open(safety_build, 'r') as f:
    content = f.read()
if 'io.smallrye.reactive:mutiny' not in content:
    content = content.replace('dependencies {', 'dependencies {\n    implementation("io.smallrye.reactive:mutiny:2.5.5")\n    implementation("org.jboss.logging:jboss-logging:3.6.1.Final")')
    with open(safety_build, 'w') as f:
        f.write(content)

# fix ContentSafetyPlugin.java
safety_plugin = './plugins/aqli-plugin-content-safety/src/main/java/tech/kayys/aqli/plugin/ContentSafetyPlugin.java'
with open(safety_plugin, 'r') as f:
    content = f.read()
content = content.replace('public void initialize', 'public io.smallrye.mutiny.Uni<Void> initialize')
content = content.replace('public void shutdown', 'public io.smallrye.mutiny.Uni<Void> shutdown')
# return Uni.createFrom().voidItem()
content = content.replace('moderator.initialize(context);\n    }', 'moderator.initialize(context);\n        return io.smallrye.mutiny.Uni.createFrom().voidItem();\n    }')
content = content.replace('moderator.shutdown();\n    }', 'moderator.shutdown();\n        return io.smallrye.mutiny.Uni.createFrom().voidItem();\n    }')
with open(safety_plugin, 'w') as f:
    f.write(content)

# fix DefaultContentModerator.java
mod_plugin = './plugins/aqli-plugin-content-safety/src/main/java/tech/kayys/aqli/plugin/DefaultContentModerator.java'
with open(mod_plugin, 'r') as f:
    content = f.read()
content = content.replace('public void initialize', 'public io.smallrye.mutiny.Uni<Void> initialize')
content = content.replace('public void shutdown', 'public io.smallrye.mutiny.Uni<Void> shutdown')
content = content.replace('loadPatterns();\n    }', 'loadPatterns();\n        return io.smallrye.mutiny.Uni.createFrom().voidItem();\n    }')
content = content.replace('patterns.clear();\n    }', 'patterns.clear();\n        return io.smallrye.mutiny.Uni.createFrom().voidItem();\n    }')
with open(mod_plugin, 'w') as f:
    f.write(content)


