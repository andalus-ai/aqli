package tech.kayys.aqli.cli.config;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** Test-only provider proving that Quarkus command construction sees ServiceLoader extensions. */
public class RegistryProbeRunCapabilityProvider implements RunCapabilityProvider {

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
                // No additional validation is needed for the test probe.
            }
        };
    }
}
