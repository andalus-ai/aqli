package tech.kayys.aqli.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapabilityRegistry;

class RunCommandCapabilityOptionsReaderTest {

    @Test
    void returnsTheRegisteredTypedSnapshotForTheCurrentParse() {
        RunCapabilityRegistry registry = mock(RunCapabilityRegistry.class);
        ParseResult parseResult = mock(ParseResult.class);
        Object expected = new Object();
        when(registry.snapshot("text", parseResult, Object.class)).thenReturn(Optional.of(expected));

        RunCommandCapabilityOptionsReader reader =
                new RunCommandCapabilityOptionsReader(registry, parseResult);

        assertSame(expected, reader.require("text", Object.class, "missing text capability"));
        verify(registry).snapshot("text", parseResult, Object.class);
    }

    @Test
    void reportsTheExistingRequiredCapabilityMessageWhenNoSnapshotExists() {
        RunCapabilityRegistry registry = mock(RunCapabilityRegistry.class);
        ParseResult parseResult = mock(ParseResult.class);
        when(registry.snapshot("text", parseResult, String.class)).thenReturn(Optional.empty());

        RunCommandCapabilityOptionsReader reader =
                new RunCommandCapabilityOptionsReader(registry, parseResult);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> reader.require("text", String.class, "The 'text' run capability is missing"));

        assertEquals("The 'text' run capability is missing", failure.getMessage());
    }
}
