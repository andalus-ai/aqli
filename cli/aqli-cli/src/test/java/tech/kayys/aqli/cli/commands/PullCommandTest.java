package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.sdk.route.RunnerRouteBenchmarkCache;
import tech.kayys.aqli.sdk.route.RunnerRoutePolicy;
import tech.kayys.aqli.sdk.route.RunnerRouteReportContract;
import tech.kayys.aqli.safetensor.engine.route.*;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import jakarta.inject.Inject;

import tech.kayys.aqli.sdk.core.AqliSdk;
import tech.kayys.aqli.sdk.model.ModelPullRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@QuarkusTest
public class PullCommandTest {

    @Inject
    PullCommand pullCommand;

    @InjectMock
    AqliSdk sdk;

    @Test
    public void testPullCommandWithSimpleName() throws Exception {
        pullCommand.modelSpec = "llama3.2";
        pullCommand.insecure = false;

        pullCommand.run();

        Mockito.verify(sdk).pullModel(any(ModelPullRequest.class), any());
    }

    @Test
    public void testPullCommandCustomRegistry() throws Exception {
        pullCommand.modelSpec = "registry:llama3.2";
        pullCommand.insecure = false;

        pullCommand.run();

        Mockito.verify(sdk).pullModel(any(ModelPullRequest.class), any());
    }
}
