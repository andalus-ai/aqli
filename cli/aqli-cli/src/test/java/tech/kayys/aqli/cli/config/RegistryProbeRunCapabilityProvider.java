package tech.kayys.aqli.cli.config;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import java.util.concurrent.atomic.AtomicInteger;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** Test-only provider proving that Quarkus command construction sees ServiceLoader extensions. */
public class RegistryProbeRunCapabilityProvider implements RunCapabilityProvider {

    static final AtomicInteger VALIDATION_COUNT = new AtomicInteger();

    @Override
    public String capabilityId() {
        return "registry-probe";
    }

    @Override
    public RunCapability create() {
        return new RunCapability() {
            @Override
            public String id() {
                return "registry-probe";
            }

            @Override
            public void registerOptions(CommandSpec commandSpec) {
                commandSpec.addOption(OptionSpec.builder("--registry-probe")
                        .type(boolean.class)
                        .arity("0")
                        .description("Test-only run capability registration probe")
                        .build());
            }

            @Override
            public void validate(ParseResult parseResult) {
                VALIDATION_COUNT.incrementAndGet();
            }
        };
    }
}
