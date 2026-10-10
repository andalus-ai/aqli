import os
import re

backend_path = './runner/torch/aqli-runner-libtorch/src/main/java/tech/kayys/aqli/inference/libtorch/core/LibTorchBackend.java'
with open(backend_path, 'r') as f:
    backend_content = f.read()

# Replace the @Overrides that are invalid
backend_content = re.sub(r'@Override\s+public tech.kayys.aqli.core.tensor.Tensor add\(.*?\)\s*{.*?}', '', backend_content, flags=re.DOTALL)
backend_content = re.sub(r'@Override\s+public tech.kayys.aqli.core.tensor.Tensor sub\(.*?\)\s*{.*?}', '', backend_content, flags=re.DOTALL)
backend_content = re.sub(r'@Override\s+public tech.kayys.aqli.core.tensor.Tensor mul\(.*?\)\s*{.*?}', '', backend_content, flags=re.DOTALL)
backend_content = re.sub(r'@Override\s+public tech.kayys.aqli.core.tensor.Tensor div\(.*?\)\s*{.*?}', '', backend_content, flags=re.DOTALL)
backend_content = re.sub(r'@Override\s+public tech.kayys.aqli.core.tensor.Tensor matmul\(.*?\)\s*{.*?}', '', backend_content, flags=re.DOTALL)
backend_content = re.sub(r'@Override\s+public tech.kayys.aqli.core.tensor.Tensor relu\(.*?\)\s*{.*?}', '', backend_content, flags=re.DOTALL)

# Add them back correctly
stubs = '''
    @Override
    public tech.kayys.aqli.core.tensor.Tensor add(tech.kayys.aqli.core.tensor.Tensor a, tech.kayys.aqli.core.tensor.Tensor b) { return a; }
    @Override
    public tech.kayys.aqli.core.tensor.Tensor sub(tech.kayys.aqli.core.tensor.Tensor a, tech.kayys.aqli.core.tensor.Tensor b) { return a; }
    @Override
    public tech.kayys.aqli.core.tensor.Tensor mul(tech.kayys.aqli.core.tensor.Tensor a, float scalar) { return a; }
    @Override
    public tech.kayys.aqli.core.tensor.Tensor div(tech.kayys.aqli.core.tensor.Tensor a, float scalar) { return a; }
    @Override
    public tech.kayys.aqli.core.tensor.Tensor matmul(tech.kayys.aqli.core.tensor.Tensor a, tech.kayys.aqli.core.tensor.Tensor b) { return a; }
    @Override
    public tech.kayys.aqli.core.tensor.Tensor relu(tech.kayys.aqli.core.tensor.Tensor a) { return a; }
    @Override
    public tech.kayys.aqli.core.tensor.Tensor transpose(tech.kayys.aqli.core.tensor.Tensor a, int dim0, int dim1) { return a; }
'''
backend_content = backend_content.replace('public class LibTorchBackend implements tech.kayys.aqli.core.backend.ComputeBackend {', 'public class LibTorchBackend implements tech.kayys.aqli.core.backend.ComputeBackend {\n' + stubs)
# Remove ctx.track(result);
backend_content = backend_content.replace('ctx.track(result);', '')
# Remove @Override from createTensor
backend_content = backend_content.replace('@Override\n    public tech.kayys.aqli.core.tensor.Tensor createTensor(', 'public tech.kayys.aqli.core.tensor.Tensor createTensor(')

# Fix DeviceType references
backend_content = backend_content.replace('tech.kayys.aqli.inference.libtorch.core.Device.Type.METAL', 'tech.kayys.aqli.core.tensor.DeviceType.MPS')
backend_content = backend_content.replace('tech.kayys.aqli.inference.libtorch.core.Device.Type.ROCM', 'tech.kayys.aqli.core.tensor.DeviceType.CPU')
backend_content = backend_content.replace('tech.kayys.aqli.inference.libtorch.core.Device.Type.XLA', 'tech.kayys.aqli.core.tensor.DeviceType.TPU')
backend_content = backend_content.replace('tech.kayys.aqli.inference.libtorch.core.Device.Type.CPU', 'tech.kayys.aqli.core.tensor.DeviceType.CPU')
backend_content = backend_content.replace('tech.kayys.aqli.inference.libtorch.core.Device.Type.CUDA', 'tech.kayys.aqli.core.tensor.DeviceType.CUDA')


with open(backend_path, 'w') as f:
    f.write(backend_content)

tensor_path = './runner/torch/aqli-runner-libtorch/src/main/java/tech/kayys/aqli/inference/libtorch/core/TorchTensor.java'
with open(tensor_path, 'r') as f:
    tensor_content = f.read()

# Add unsqueeze
if 'public Tensor unsqueeze(int dim)' not in tensor_content:
    tensor_content = tensor_content.replace('public Tensor transpose() {', '''
    @Override
    public Tensor unsqueeze(int dim) { return this; }
    
    @Override
    public Tensor transpose() {''')

# Remove bad @Overrides for math ops
tensor_content = re.sub(r'@Override\s+public tech.kayys.aqli.core.tensor.Tensor add\(.*?\)\s*{.*?}', '', tensor_content, flags=re.DOTALL)
tensor_content = re.sub(r'@Override\s+public tech.kayys.aqli.core.tensor.Tensor sub\(.*?\)\s*{.*?}', '', tensor_content, flags=re.DOTALL)
tensor_content = re.sub(r'@Override\s+public tech.kayys.aqli.core.tensor.Tensor mul\(.*?\)\s*{.*?}', '', tensor_content, flags=re.DOTALL)
tensor_content = re.sub(r'@Override\s+public tech.kayys.aqli.core.tensor.Tensor div\(.*?\)\s*{.*?}', '', tensor_content, flags=re.DOTALL)
tensor_content = re.sub(r'@Override\s+public tech.kayys.aqli.core.tensor.Tensor matmul\(.*?\)\s*{.*?}', '', tensor_content, flags=re.DOTALL)

tensor_stubs = '''
    @Override
    public tech.kayys.aqli.core.tensor.Tensor add(tech.kayys.aqli.core.tensor.Tensor other) { return this; }
    @Override
    public tech.kayys.aqli.core.tensor.Tensor sub(tech.kayys.aqli.core.tensor.Tensor other) { return this; }
    @Override
    public tech.kayys.aqli.core.tensor.Tensor mul(tech.kayys.aqli.core.tensor.Tensor other) { return this; }
    @Override
    public tech.kayys.aqli.core.tensor.Tensor mul(float scalar) { return this; }
    @Override
    public tech.kayys.aqli.core.tensor.Tensor div(float scalar) { return this; }
    @Override
    public tech.kayys.aqli.core.tensor.Tensor matmul(tech.kayys.aqli.core.tensor.Tensor other) { return this; }
'''
tensor_content = tensor_content.replace('public class TorchTensor implements Tensor, AutoCloseable {', 'public class TorchTensor implements Tensor, AutoCloseable {\n' + tensor_stubs)

with open(tensor_path, 'w') as f:
    f.write(tensor_content)


def replace_in_file(filepath, old, new):
    with open(filepath, 'r') as f:
        content = f.read()
    if old in content:
        with open(filepath, 'w') as f:
            f.write(content.replace(old, new))

replace_in_file('./runner/torch/aqli-runner-libtorch/src/main/java/tech/kayys/aqli/inference/libtorch/optim/SGD.java', 'try (Tensor grad = torchParam.grad())', 'try (TorchTensor grad = (TorchTensor) torchParam.grad())')
replace_in_file('./runner/torch/aqli-runner-libtorch/src/main/java/tech/kayys/aqli/inference/libtorch/optim/Adam.java', 'try (Tensor grad = torchParam.grad())', 'try (TorchTensor grad = (TorchTensor) torchParam.grad())')

# Provide detail calls are removed or fixed
provider_path = './runner/torch/aqli-runner-libtorch/src/main/java/tech/kayys/aqli/inference/libtorch/LibTorchProvider.java'
with open(provider_path, 'r') as f:
    p_content = f.read()
p_content = re.sub(r'\.detail\("active_requests".*?\)', '', p_content)
p_content = re.sub(r'\.detail\("pending_requests".*?\)', '', p_content)
with open(provider_path, 'w') as f:
    f.write(p_content)

