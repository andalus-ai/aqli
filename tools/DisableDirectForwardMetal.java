import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class DisableDirectForwardMetal {
    private static final String DIRECT_FORWARD_CLASS_ENTRY =
            "/tech/kayys/aqli/safetensor/engine/forward/DirectForwardPass.class";
    private static final String NATIVE_SESSION_CLASS_ENTRY =
            "/tech/kayys/aqli/inference/nativeimpl/NativeInferenceSession.class";
    private static final String NATIVE_PROVIDER_CLASS_ENTRY =
            "/tech/kayys/aqli/inference/nativeimpl/NativeLLMProvider.class";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: DisableDirectForwardMetal <aqli.jar>");
        }

        Path jarPath = Path.of(args[0]).toAbsolutePath();
        Map<String, String> env = new HashMap<>();
        env.put("create", "false");

        try (FileSystem zipFs = FileSystems.newFileSystem(jarPath, env)) {
            patchDirectForwardPass(zipFs);
            patchNativeInferenceSession(zipFs);
            patchNativeLlmProvider(zipFs);
        }
    }

    private static void patchDirectForwardPass(FileSystem zipFs) throws IOException {
        Path classPath = zipFs.getPath(DIRECT_FORWARD_CLASS_ENTRY);
        byte[] original = Files.readAllBytes(classPath);

        ClassNode classNode = new ClassNode();
        new ClassReader(original).accept(classNode, 0);

        boolean patched = false;
        for (MethodNode method : classNode.methods) {
            if ("init".equals(method.name) && "()V".equals(method.desc)) {
                method.instructions = new InsnList();
                method.instructions.add(new InsnNode(Opcodes.RETURN));
                method.tryCatchBlocks.clear();
                method.maxStack = 0;
                method.maxLocals = Math.max(1, method.maxLocals);
                patched = true;
            }
        }

        if (!patched) {
            throw new IllegalStateException("init() method not found in DirectForwardPass");
        }

        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        Files.write(classPath, writer.toByteArray());
    }

    private static void patchNativeInferenceSession(FileSystem zipFs) throws IOException {
        patchArenaFactory(zipFs, NATIVE_SESSION_CLASS_ENTRY);
        patchArenaCloseCalls(zipFs, NATIVE_SESSION_CLASS_ENTRY);
    }

    private static void patchNativeLlmProvider(FileSystem zipFs) throws IOException {
        patchArenaFactory(zipFs, NATIVE_PROVIDER_CLASS_ENTRY);
        patchArenaCloseCalls(zipFs, NATIVE_PROVIDER_CLASS_ENTRY);
        patchGemmaArchitectureRewrite(zipFs, NATIVE_PROVIDER_CLASS_ENTRY);
    }

    private static void patchArenaFactory(FileSystem zipFs, String classEntry) throws IOException {
        Path classPath = zipFs.getPath(classEntry);
        if (!Files.exists(classPath)) {
            return;
        }
        byte[] original = Files.readAllBytes(classPath);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        ClassReader reader = new ClassReader(original);
        reader.accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && "java/lang/foreign/Arena".equals(owner)
                                && "ofAuto".equals(methodName)
                                && "()Ljava/lang/foreign/Arena;".equals(methodDescriptor)) {
                            super.visitMethodInsn(opcode, owner, "ofConfined", methodDescriptor, isInterface);
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);

        Files.write(classPath, writer.toByteArray());
    }

    private static void patchArenaCloseCalls(FileSystem zipFs, String classEntry) throws IOException {
        Path classPath = zipFs.getPath(classEntry);
        if (!Files.exists(classPath)) {
            return;
        }
        byte[] original = Files.readAllBytes(classPath);

        ClassNode classNode = new ClassNode();
        new ClassReader(original).accept(classNode, ClassReader.EXPAND_FRAMES);

        boolean patched = false;
        for (MethodNode method : classNode.methods) {
            for (var insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKEINTERFACE
                        && "java/lang/foreign/Arena".equals(call.owner)
                        && "close".equals(call.name)
                        && "()V".equals(call.desc)) {
                    method.instructions.set(call, new InsnNode(Opcodes.POP));
                    patched = true;
                }
            }
        }

        if (!patched) {
            throw new IllegalStateException("Arena.close() call not found in " + classEntry);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        Files.write(classPath, writer.toByteArray());
    }

    private static void patchGemmaArchitectureRewrite(FileSystem zipFs, String classEntry) throws IOException {
        Path classPath = zipFs.getPath(classEntry);
        if (!Files.exists(classPath)) {
            return;
        }
        byte[] original = Files.readAllBytes(classPath);

        ClassNode classNode = new ClassNode();
        new ClassReader(original).accept(classNode, ClassReader.EXPAND_FRAMES);

        boolean patched = false;
        for (MethodNode method : classNode.methods) {
            if (!"getOrLoadEngine".equals(method.name) || !"(Ljava/lang/String;)Ltech/kayys/aqli/inference/nativeimpl/NativeInferenceEngine;".equals(method.desc)) {
                continue;
            }

            for (var insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof LdcInsnNode ldc) || !"gemma".equals(ldc.cst)) {
                    continue;
                }
                if (!(insn.getNext() instanceof MethodInsnNode equalsCall)
                        || !"java/lang/String".equals(equalsCall.owner)
                        || !"equals".equals(equalsCall.name)
                        || !"(Ljava/lang/Object;)Z".equals(equalsCall.desc)) {
                    continue;
                }

                InsnList guard = new InsnList();
                LabelNode skip = new LabelNode();

                // if (!"gemma".equals(arch)) skip;
                guard.add(new VarInsnNode(Opcodes.ALOAD, 3));
                guard.add(new LdcInsnNode("gemma"));
                guard.add(new MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        "java/lang/String",
                        "equals",
                        "(Ljava/lang/Object;)Z",
                        false));
                guard.add(new JumpInsnNode(Opcodes.IFEQ, skip));

                // Object generalName = model.metadata().get("general.name");
                guard.add(new VarInsnNode(Opcodes.ALOAD, 2));
                guard.add(new MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        "tech/kayys/aqli/gguf/loader/GGUFModel",
                        "metadata",
                        "()Ljava/util/Map;",
                        false));
                guard.add(new LdcInsnNode("general.name"));
                guard.add(new MethodInsnNode(
                        Opcodes.INVOKEINTERFACE,
                        "java/util/Map",
                        "get",
                        "(Ljava/lang/Object;)Ljava/lang/Object;",
                        true));
                guard.add(new VarInsnNode(Opcodes.ASTORE, method.maxLocals));

                // if (generalName instanceof String && ((String) generalName).toLowerCase().contains("gemma4")) arch = "gemma4";
                guard.add(new VarInsnNode(Opcodes.ALOAD, method.maxLocals));
                guard.add(new TypeInsnNode(Opcodes.INSTANCEOF, "java/lang/String"));
                guard.add(new JumpInsnNode(Opcodes.IFEQ, skip));
                guard.add(new VarInsnNode(Opcodes.ALOAD, method.maxLocals));
                guard.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/String"));
                guard.add(new MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        "java/lang/String",
                        "toLowerCase",
                        "()Ljava/lang/String;",
                        false));
                guard.add(new LdcInsnNode("gemma4"));
                guard.add(new MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        "java/lang/String",
                        "contains",
                        "(Ljava/lang/CharSequence;)Z",
                        false));
                guard.add(new JumpInsnNode(Opcodes.IFEQ, skip));
                guard.add(new LdcInsnNode("gemma4"));
                guard.add(new VarInsnNode(Opcodes.ASTORE, 3));
                guard.add(skip);

                method.instructions.insertBefore(insn, guard);
                method.maxLocals += 1;
                patched = true;
                break;
            }
        }

        if (!patched) {
            throw new IllegalStateException("Gemma architecture rewrite hook not found in " + classEntry);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        Files.write(classPath, writer.toByteArray());
    }
}
