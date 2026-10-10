import os
import glob

# Remove aqli-engine from all plugin build files
build_files = glob.glob('./plugins/*/build.gradle.kts')
for bf in build_files:
    with open(bf, 'r') as f:
        content = f.read()
    if 'aqli-engine' in content:
        content = content.replace('implementation(group = "tech.kayys.aqli", name = "aqli-engine")', '')
        with open(bf, 'w') as f:
            f.write(content)

# Fix RequestValidationPlugin.java missing return statement
req_path = './plugins/aqli-plugin-content-safety/src/main/java/tech/kayys/aqli/plugin/RequestValidationPlugin.java'
with open(req_path, 'r') as f:
    content = f.read()

content = content.replace('''
    @Override
    public io.smallrye.mutiny.Uni<Void> shutdown() {
        LOG.info("RequestValidationPlugin shutdown");
        return io.smallrye.mutiny.Uni.createFrom().voidItem();
    }
}''', '''
    @Override
    public io.smallrye.mutiny.Uni<Void> shutdown() {
        LOG.info("RequestValidationPlugin shutdown");
        return io.smallrye.mutiny.Uni.createFrom().voidItem();
    }
}''')
# Wait, let me just add it manually with a sed or just simple python replace.
content = content.replace('// NOOP\n    }', 'return io.smallrye.mutiny.Uni.createFrom().voidItem();\n    }')
content = content.replace('public io.smallrye.mutiny.Uni<Void> shutdown() {\n        LOG.info("RequestValidationPlugin shutdown");\n    }', 'public io.smallrye.mutiny.Uni<Void> shutdown() {\n        LOG.info("RequestValidationPlugin shutdown");\n        return io.smallrye.mutiny.Uni.createFrom().voidItem();\n    }')
with open(req_path, 'w') as f:
    f.write(content)
