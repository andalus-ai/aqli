package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tech.kayys.aqli.sdk.core.AqliSdk;
import tech.kayys.aqli.spi.inference.AsyncJobStatus;
import tech.kayys.aqli.spi.inference.InferenceRequest;
import tech.kayys.aqli.spi.Message;

import java.time.Duration;

@Dependent
@Unremovable
@Command(name = "jobs", description = "Manage asynchronous inference jobs", subcommands = {
    JobsCommand.SubmitCommand.class,
    JobsCommand.StatusCommand.class,
    JobsCommand.WaitCommand.class
})
public class JobsCommand implements Runnable {

    @Override
    public void run() {
        // If no subcommand is specified, picocli defaults to showing help or doing nothing.
        // We'll let picocli handle the help output if needed via standard mixin options.
        System.out.println("Use a subcommand: submit, status, or wait. Run 'aqli jobs --help' for details.");
    }

    @Command(name = "submit", description = "Submit a new async background job")
    public static class SubmitCommand implements Runnable {
        @Inject AqliSdk sdk;
        @Option(names = {"-m", "--model"}, required = true) String modelId;
        @Option(names = {"-p", "--prompt"}, required = true) String prompt;

        @Override
        public void run() {
            try {
                InferenceRequest req = InferenceRequest.builder()
                        .requestId(java.util.UUID.randomUUID().toString())
                        .model(modelId)
                        .message(Message.user(prompt))
                        .build();
                String jobId = sdk.submitAsyncJob(req);
                System.out.println("Job submitted successfully.");
                System.out.println("Job ID: " + jobId);
            } catch (Exception e) {
                System.err.println("Job submission failed: " + e.getMessage());
            }
        }
    }

    @Command(name = "status", description = "Check the status of an existing async job")
    public static class StatusCommand implements Runnable {
        @Inject AqliSdk sdk;
        @Parameters(index = "0", description = "Job ID to check") String jobId;

        @Override
        public void run() {
            try {
                AsyncJobStatus status = sdk.getJobStatus(jobId);
                printStatus(status);
            } catch (Exception e) {
                System.err.println("Failed to fetch job status: " + e.getMessage());
            }
        }
    }

    @Command(name = "wait", description = "Wait synchronously for a job to complete")
    public static class WaitCommand implements Runnable {
        @Inject AqliSdk sdk;
        @Parameters(index = "0", description = "Job ID") String jobId;
        @Option(names = {"--timeout"}, description = "Max wait time in seconds", defaultValue = "300") int timeout;

        @Override
        public void run() {
            try {
                System.out.println("Waiting up to " + timeout + "s for block completion...");
                AsyncJobStatus status = sdk.waitForJob(jobId, Duration.ofSeconds(timeout), Duration.ofSeconds(2));
                System.out.println("\nFinal Job State:");
                printStatus(status);
            } catch (Exception e) {
                System.err.println("Wait interrupted/failed: " + e.getMessage());
            }
        }
    }
    
    private static void printStatus(AsyncJobStatus status) {
        System.out.println("Status:   " + status.status());
        if (status.result() != null) {
            System.out.println("Output length: " + status.result().getContent().length() + " chars");
        }
        if (status.error() != null) {
            System.out.println("Error: " + status.error());
        }
    }
}
