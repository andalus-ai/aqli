import os
import glob

directory = './runner/torch/aqli-runner-libtorch/src/main/java/tech/kayys/aqli/inference/libtorch'

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    new_content = content.replace('tech.kayys.aqli.runtime.tensor.Tensor', 'tech.kayys.aqli.core.tensor.Tensor')
    new_content = new_content.replace('tech.kayys.aqli.runtime.tensor.DType', 'tech.kayys.aqli.core.tensor.DType')
    new_content = new_content.replace('tech.kayys.aqli.runtime.tensor.Device', 'tech.kayys.aqli.core.tensor.DeviceType')
    new_content = new_content.replace('tech.kayys.aqli.runtime.tensor.Backend', 'tech.kayys.aqli.core.backend.ComputeBackend')
    new_content = new_content.replace('tech.kayys.aqli.runtime.tensor.DefaultTensor', 'tech.kayys.aqli.core.tensor.Tensor') # not sure, let's remove DefaultTensor
    new_content = new_content.replace('tech.kayys.aqli.provider.core.session.', 'tech.kayys.aqli.provider.core.session.') # no change needed but wait, it might be correct
    new_content = new_content.replace('tech.kayys.aqli.runtime.inference.batch.ContinuousBatchScheduler', 'tech.kayys.aqli.runtime.batch.ContinuousBatchScheduler')
    
    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Updated {filepath}")

for root, dirs, files in os.walk(directory):
    for file in files:
        if file.endswith('.java'):
            process_file(os.path.join(root, file))
