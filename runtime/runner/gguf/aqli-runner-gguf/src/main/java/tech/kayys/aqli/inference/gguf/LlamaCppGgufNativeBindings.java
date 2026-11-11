package tech.kayys.aqli.inference.gguf;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FFM downcall bindings for the optional llama.cpp GGUF bridge.
 *
 * <p>This class owns the bridge ABI and symbol lookup only. Session ownership
 * is handled by {@link LlamaCppGgufSession}; runtime selection and daemon policy
 * remain with the application.</p>
 */
final class LlamaCppGgufNativeBindings {
    private static final Map<Path, LlamaCppGgufNativeBindings> INSTANCES = new ConcurrentHashMap<>();

    final MethodHandle open;
    final MethodHandle generate;
    final MethodHandle backendName;
    final MethodHandle metrics;
    final MethodHandle hardExit;
    final MethodHandle close;

    static LlamaCppGgufNativeBindings load(Path bridgeLibrary) {
        Path bridge = bridgeLibrary.toAbsolutePath().normalize();
        try {
            return INSTANCES.computeIfAbsent(bridge, LlamaCppGgufNativeBindings::create);
        } catch (BindingLoadFailure failure) {
            throw new IllegalStateException("Unable to load llama.cpp GGUF bridge at " + bridge,
                    failure.getCause());
        }
    }

    private static LlamaCppGgufNativeBindings create(Path bridge) {
        try {
            return new LlamaCppGgufNativeBindings(bridge);
        } catch (Throwable throwable) {
            throw new BindingLoadFailure(throwable);
        }
    }

    private LlamaCppGgufNativeBindings(Path bridge) throws Throwable {
        if (!Files.isRegularFile(bridge)) {
            throw new IOException("GGUF native bridge is not installed at " + bridge);
        }
        System.load(bridge.toString());
        Linker linker = Linker.nativeLinker();
        SymbolLookup lookup = SymbolLookup.libraryLookup(bridge, Arena.global());
        this.open = linker.downcallHandle(
                lookup.find("aqli_gguf_open").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG));
        this.generate = linker.downcallHandle(
                lookup.find("aqli_gguf_generate").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_FLOAT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_FLOAT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG));
        this.backendName = linker.downcallHandle(
                lookup.find("aqli_gguf_backend_name").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        Optional<MemorySegment> metricsSymbol = lookup.find("aqli_gguf_last_metrics");
        this.metrics = metricsSymbol.isPresent()
                ? linker.downcallHandle(
                        metricsSymbol.get(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_LONG))
                : null;
        Optional<MemorySegment> hardExitSymbol = lookup.find("aqli_gguf_hard_exit");
        this.hardExit = hardExitSymbol.isPresent()
                ? linker.downcallHandle(
                        hardExitSymbol.get(),
                        FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT))
                : null;
        this.close = linker.downcallHandle(
                lookup.find("aqli_gguf_close").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    }

    private static final class BindingLoadFailure extends RuntimeException {
        private BindingLoadFailure(Throwable cause) {
            super(cause);
        }
    }
}
