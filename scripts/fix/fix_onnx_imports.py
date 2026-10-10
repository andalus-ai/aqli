import os

dir_path = "/Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/aqli/runner/onnx/aqli-runner-onnx"

replacements = {
    "import tech.kayys.aqli.runner.RunnerConfiguration;": "import tech.kayys.alkhawarizm.runner.RunnerConfiguration;",
    "import tech.kayys.aqli.runner.RunnerCapabilities;": "import tech.kayys.alkhawarizm.runner.RunnerCapabilities;",
    "import tech.kayys.aqli.extension.AbstractAqliRunner;": "import tech.kayys.alkhawarizm.extension.AbstractAljabrRunner;",
    "extends AbstractAqliRunner": "extends AbstractAljabrRunner",
    "import tech.kayys.aqli.exception.RunnerInitializationException;": "import tech.kayys.alkhawarizm.exception.RunnerInitializationException;",
    "import tech.kayys.aqli.core.model.ModelFormat;": "import tech.kayys.alkhawarizm.core.model.ModelFormat;",
    "import tech.kayys.aqli.tokenizer.runtime.TokenizerFactory;": "import tech.kayys.alkhawarizm.tokenizer.runtime.TokenizerFactory;",
    "import tech.kayys.aqli.tokenizer.spi.DecodeOptions;": "import tech.kayys.alkhawarizm.tokenizer.spi.DecodeOptions;",
    "import tech.kayys.aqli.tokenizer.spi.EncodeOptions;": "import tech.kayys.alkhawarizm.tokenizer.spi.EncodeOptions;",
    "import tech.kayys.aqli.tokenizer.spi.Tokenizer;": "import tech.kayys.alkhawarizm.tokenizer.spi.Tokenizer;"
}

for root, dirs, files in os.walk(dir_path):
    for file in files:
        if file.endswith(".java"):
            file_path = os.path.join(root, file)
            with open(file_path, "r", encoding="utf-8") as f:
                content = f.read()
            
            modified = False
            for old, new in replacements.items():
                if old in content:
                    content = content.replace(old, new)
                    modified = True
            
            if modified:
                print(f"Fixing imports in {file_path}")
                with open(file_path, "w", encoding="utf-8") as f:
                    f.write(content)
