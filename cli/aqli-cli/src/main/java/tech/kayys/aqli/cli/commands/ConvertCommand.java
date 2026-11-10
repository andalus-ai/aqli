package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import picocli.CommandLine.Command;

@Dependent
@Unremovable
@Command(name = "convert", description = "Convert a model to GGUF (local) - CURRENTLY DISABLED")
public class ConvertCommand implements Runnable {
    @Override
    public void run() {
        System.err.println("Convert command is temporarily disabled due to build issues.");
    }
}
