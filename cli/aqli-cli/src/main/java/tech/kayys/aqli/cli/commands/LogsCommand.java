package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tech.kayys.aqli.sdk.core.AqliSdk;

import java.util.List;

@Dependent
@Unremovable
@Command(name = "logs", description = "View recent system logs from the inference engine runtime")
public class LogsCommand implements Runnable {

    @Inject
    AqliSdk sdk;

    @Option(names = { "-n", "--lines" }, description = "Number of lines to retrieve", defaultValue = "50")
    public int lines;

    @Override
    public void run() {
        try {
            System.out.printf("Fetching last %d log lines...%n", lines);
            List<String> logs = sdk.getRecentLogs(lines);
            
            if (logs == null || logs.isEmpty()) {
                System.out.println("No recent logs available.");
                return;
            }
            
            System.out.println("--- System Logs ---");
            for (String line : logs) {
                System.out.println(line);
            }
            System.out.println("-------------------");
            
        } catch (Exception e) {
            System.err.println("Failed to fetch logs: " + e.getMessage());
        }
    }
}
