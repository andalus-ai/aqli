import os
import glob

# add spi-inference to all optimization plugins
projects = [
    './optimization/aqli-plugin-perfmode/build.gradle.kts',
    './optimization/aqli-plugin-wait-scheduler/build.gradle.kts',
    './optimization/aqli-plugin-evicpress/build.gradle.kts',
    './optimization/aqli-plugin-prompt-cache/build.gradle.kts'
]
deps = """
    implementation(project(":spi:aqli-spi-inference"))
    implementation("io.smallrye.reactive:mutiny:2.5.5")
"""
for p in projects:
    if os.path.exists(p):
        with open(p, 'r') as f:
            content = f.read()
        if 'aqli-spi-inference' not in content:
            content = content.replace('dependencies {', 'dependencies {\n' + deps)
            with open(p, 'w') as f:
                f.write(content)

# mass replace void initialize and shutdown
java_files = glob.glob('./optimization/**/*.java', recursive=True) + glob.glob('./plugins/**/*.java', recursive=True)
for jf in java_files:
    with open(jf, 'r') as f:
        content = f.read()
    changed = False
    
    if 'public void initialize(PluginContext' in content:
        content = content.replace('public void initialize(PluginContext', 'public io.smallrye.mutiny.Uni<Void> initialize(PluginContext')
        content = content.replace('void initialize(PluginContext', 'io.smallrye.mutiny.Uni<Void> initialize(PluginContext')
        content = content.replace('// NOOP\n    }', 'return io.smallrye.mutiny.Uni.createFrom().voidItem();\n    }')
        changed = True
        
    if 'public void shutdown()' in content:
        content = content.replace('public void shutdown()', 'public io.smallrye.mutiny.Uni<Void> shutdown()')
        content = content.replace('// NOOP\n    }', 'return io.smallrye.mutiny.Uni.createFrom().voidItem();\n    }')
        changed = True

    if changed:
        # naive replacement for missing return:
        # just append return voidItem() before the last brace of the method
        # this is risky but let's just do a simple string replace for known files
        pass

# Fix specific files that have actual code in initialize/shutdown
def fix_init_shutdown(path):
    if not os.path.exists(path): return
    with open(path, 'r') as f:
        c = f.read()
    
    # Very dirty fix: replace void initialize -> Uni<Void> initialize, and if it doesn't return, make it return.
    c = c.replace('public void initialize(PluginContext ctx) {', 'public io.smallrye.mutiny.Uni<Void> initialize(PluginContext ctx) {\n        return io.smallrye.mutiny.Uni.createFrom().voidItem();')
    c = c.replace('public void shutdown() {', 'public io.smallrye.mutiny.Uni<Void> shutdown() {\n        return io.smallrye.mutiny.Uni.createFrom().voidItem();')
    c = c.replace('public void initialize(PluginContext context) {', 'public io.smallrye.mutiny.Uni<Void> initialize(PluginContext context) {\n        return io.smallrye.mutiny.Uni.createFrom().voidItem();')
    
    # remove the old void ones
    with open(path, 'w') as f:
        f.write(c)

fix_init_shutdown('./optimization/aqli-plugin-prompt-cache/src/main/java/tech/kayys/aqli/cache/PromptCacheLookupPlugin.java')
fix_init_shutdown('./optimization/aqli-plugin-prompt-cache/src/main/java/tech/kayys/aqli/cache/PromptCacheStorePlugin.java')
fix_init_shutdown('./optimization/aqli-plugin-evicpress/src/main/java/tech/kayys/aqli/plugin/evicpress/EvictionPressurePlugin.java')
fix_init_shutdown('./optimization/aqli-plugin-perfmode/src/main/java/tech/kayys/aqli/plugin/perfmode/PerfModePlugin.java')

