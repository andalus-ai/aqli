package tech.kayys.aqli.cli.commands;

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
import java.util.Optional;

/**
 * FFM downcall bindings for the optional llama.cpp GGUF bridge.
 *
 * <p>This class owns the bridge ABI and symbol lookup only. Model/session
 * lifecycle and runtime policy remain with the GGUF runner and AQLI.</p>
 */
final class LlamaCppGgufNativeBindings {
    static final LlamaCppGgufNativeBindings INSTANCE = new LlamaCppGgufNativeBindings();

    final MethodHandle open;
    final MethodHandle generate;
    final MethodHandle backendName;
    final MethodHandle metrics;
    final MethodHandle hardExit;
    final MethodHandle close;

    private LlamaCppGgufNativeBindings() {
        try {
            Path bridge = GgufFastRun.bridgePath();
            if (!Files.isRegularFile(bridge)) {
                throw new IOException("GGUF fast bridge is not installed at " + bridge);
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
        } catch (Throwable throwable) {
            throw new ExceptionInInitializerError(throwable);
        }
    }
}
