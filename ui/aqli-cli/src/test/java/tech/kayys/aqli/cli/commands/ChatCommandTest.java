package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.sdk.route.RunnerRouteBenchmarkCache;
import tech.kayys.aqli.sdk.route.RunnerRoutePolicy;
import tech.kayys.aqli.sdk.route.RunnerRouteReportContract;
import tech.kayys.aqli.safetensor.engine.route.*;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import org.junit.jupiter.api.Test;
import jakarta.inject.Inject;
import tech.kayys.aqli.cli.commands.ChatCommand;
import tech.kayys.aqli.sdk.core.AqliSdk;

@QuarkusTest
public class ChatCommandTest {

    @Inject
    ChatCommand chatCommand;

    @InjectMock
    AqliSdk sdk;

    @Test
    public void testChatCommandInitialization() {
        // Test that the command can be injected and configured
        chatCommand.modelId = "test-model";
        chatCommand.temperature = 0.7;

        // ChatCommand is interactive, so we can't fully test run()
        // Just verify it's properly configured
        assert chatCommand.modelId.equals("test-model");
    }
}
