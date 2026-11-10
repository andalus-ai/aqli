package tech.kayys.aqli.cli.chat;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import tech.kayys.aqli.cli.commands.*;
import tech.kayys.aqli.sdk.core.AqliSdk;
import tech.kayys.aljabr.spi.model.ModelInfo;

import java.util.List;

/**
 * Handles slash commands within the chat session.
 */
@Dependent
public class ChatCommandHandler {

    @Inject
    ListCommand listCommand;
    @Inject
    InfoCommand infoCommand;
    @Inject
    ExtensionsCommand extensionsCommand;
    @Inject
    AqliSdk sdk;
    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "quarkus.log.file.path")
    String logFilePath;

    public boolean handleCommand(String input, ChatSessionManager session, ChatUIRenderer ui) {
        String cmd = input.toLowerCase().trim();

        if (cmd.equals("/reset")) {
            session.reset();
            System.out.println(ChatUIRenderer.YELLOW + "[Conversation reset]" + ChatUIRenderer.RESET);
            return true;
        }

        if (cmd.equals("/retry") || cmd.equals("/regen")) {
            session.retryLastRequest();
            return true;
        }

        if (cmd.equals("/help")) {
            printHelp();
            return true;
        }

        if (cmd.equals("/list")) {
            listCommand.run();
            return true;
        }

        if (cmd.equals("/providers")) {
            
            return true;
        }

        if (cmd.startsWith("/provider ")) {
            handleProviderSwitch(cmd.substring(10).trim(), session, ui);
            return true;
        }

        if (cmd.equals("/info")) {
            infoCommand.run();
            return true;
        }

        if (cmd.equals("/modules") || cmd.equals("/extensions")) {
            extensionsCommand.run();
            return true;
        }

        if (cmd.equals("/log")) {
            printLogs();
            return true;
        }

        if (cmd.equals("/models")) {
            handleListModels();
            return true;
        }

        if (cmd.startsWith("/model ")) {
            handleModelSwitch(cmd.substring(7).trim(), session, ui);
            return true;
        }

        if (cmd.equals("/model")) {
            System.out.println(ChatUIRenderer.YELLOW + "Usage: /model <model-id>" + ChatUIRenderer.RESET);
            System.out.println(ChatUIRenderer.DIM + "Use /models to see available models" + ChatUIRenderer.RESET);
            return true;
        }

        if (cmd.equals("/stats") || cmd.equals("/audit") || cmd.equals("/statistic") || cmd.equals("/statistics")) {
            handleStats(session);
            return true;
        }

        return false;
    }

    private void printHelp() {
        System.out.println(ChatUIRenderer.DIM + "Available commands:" + ChatUIRenderer.RESET);
        System.out.println(ChatUIRenderer.DIM + "  /reset        - Clear conversation history" + ChatUIRenderer.RESET);
        System.out.println(ChatUIRenderer.DIM + "  /retry        - Re-run the last prepared local request" + ChatUIRenderer.RESET);
        System.out.println(ChatUIRenderer.DIM + "  /quit         - Exit the chat session" + ChatUIRenderer.RESET);
        System.out.println(ChatUIRenderer.DIM + "  /log          - Show last 100 lines of log" + ChatUIRenderer.RESET);
        System.out.println(ChatUIRenderer.DIM + "  /list         - List available models" + ChatUIRenderer.RESET);
        System.out.println(ChatUIRenderer.DIM + "  /models       - List models for the current provider" + ChatUIRenderer.RESET);
        System.out.println(ChatUIRenderer.DIM + "  /model <id>   - Switch to a different model" + ChatUIRenderer.RESET);
        System.out.println(ChatUIRenderer.DIM + "  /providers    - List available LLM providers" + ChatUIRenderer.RESET);
        System.out.println(ChatUIRenderer.DIM + "  /provider <id>- Switch to a different provider" + ChatUIRenderer.RESET);
        System.out.println(ChatUIRenderer.DIM + "  /info         - Display system info" + ChatUIRenderer.RESET);
        System.out.println(ChatUIRenderer.DIM + "  /stats        - Show session usage statistics" + ChatUIRenderer.RESET);
        System.out.println(ChatUIRenderer.DIM + "  /modules      - Show packaged runtime modules" + ChatUIRenderer.RESET);
        System.out.println(ChatUIRenderer.DIM + "  /help         - Show this help message" + ChatUIRenderer.RESET);
    }

    private void handleProviderSwitch(String newProviderId, ChatSessionManager session, ChatUIRenderer ui) {
        if (newProviderId.isEmpty()) {
            System.out.println(ChatUIRenderer.YELLOW + "Usage: /provider <provider-id>" + ChatUIRenderer.RESET);
            return;
        }
        try {
            session.switchProvider(newProviderId);
            System.out.println(ChatUIRenderer.GREEN + "Switched to provider: " + ChatUIRenderer.RESET + ChatUIRenderer.CYAN + newProviderId + ChatUIRenderer.RESET);
        } catch (Exception e) {
            ui.printError("Failed to switch provider: " + e.getMessage(), false);
        }
    }

    private void handleListModels() {
        try {
            List<ModelInfo> models = sdk.listModels(0, 50);
            if (models.isEmpty()) {
                System.out.println(ChatUIRenderer.YELLOW + "No models found." + ChatUIRenderer.RESET);
                return;
            }
            System.out.println(ChatUIRenderer.DIM + "Available models:" + ChatUIRenderer.RESET);
            System.out.printf(ChatUIRenderer.DIM + "  %-30s %-12s %-10s" + ChatUIRenderer.RESET + "%n", "MODEL", "SIZE", "FORMAT");
            System.out.println(ChatUIRenderer.DIM + "  " + "-".repeat(55) + ChatUIRenderer.RESET);
            for (ModelInfo m : models) {
                System.out.printf("  " + ChatUIRenderer.CYAN + "%-30s" + ChatUIRenderer.RESET + " %-12s %-10s%n",
                        truncate(m.getModelId(), 30),
                        m.getSizeFormatted(),
                        m.getFormat() != null ? m.getFormat() : "N/A");
            }
            System.out.printf(ChatUIRenderer.DIM + "  %d model(s) found" + ChatUIRenderer.RESET + "%n", models.size());
        } catch (Exception e) {
            System.err.println(ChatUIRenderer.YELLOW + "Failed to list models: " + e.getMessage() + ChatUIRenderer.RESET);
        }
    }

    private void handleModelSwitch(String newModelId, ChatSessionManager session, ChatUIRenderer ui) {
        if (newModelId.isEmpty()) {
            System.out.println(ChatUIRenderer.YELLOW + "Usage: /model <model-id>" + ChatUIRenderer.RESET);
            return;
        }
        try {
            session.switchModel(newModelId);
            System.out.println(ChatUIRenderer.GREEN + "Switched to model: " + ChatUIRenderer.RESET + ChatUIRenderer.CYAN + newModelId + ChatUIRenderer.RESET);
        } catch (Exception e) {
            ui.printError("Failed to switch model: " + e.getMessage(), false);
        }
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen - 3) + "..." : str;
    }

    private void printLogs() {
        java.nio.file.Path path = java.nio.file.Path.of(logFilePath);
        try {
            if (java.nio.file.Files.isRegularFile(path)) {
                System.out.println(ChatUIRenderer.DIM + "--- Recent logs from: " + logFilePath + " ---" + ChatUIRenderer.RESET);

                // Keep only the last 50 lines without loading a potentially large log file into memory.
                java.util.ArrayDeque<String> recent = new java.util.ArrayDeque<>(50);
                try (java.util.stream.Stream<String> lines = java.nio.file.Files.lines(path)) {
                    lines.forEach(line -> {
                        if (recent.size() == 50) {
                            recent.removeFirst();
                        }
                        recent.addLast(line);
                    });
                }

                if (recent.isEmpty()) {
                    System.out.println(ChatUIRenderer.YELLOW + "No log entries found yet. Perform some actions first." + ChatUIRenderer.RESET);
                } else {
                    recent.forEach(System.out::println);
                    System.out.println(ChatUIRenderer.DIM + "--------------------------" + ChatUIRenderer.RESET);
                }
                return;
            }

            System.out.println(ChatUIRenderer.YELLOW + "Log file not found at: " + logFilePath + ChatUIRenderer.RESET);
        } catch (Exception e) {
            System.err.println(ChatUIRenderer.YELLOW + "Failed to read log file: " + e.getMessage() + ChatUIRenderer.RESET);
        }

        // The SDK fallback is useful when file logging is disabled or the file is unavailable.
        try {
            java.util.List<String> rawLines = sdk.getRecentLogs(50);
            if (rawLines != null && !rawLines.isEmpty()) {
                System.out.println(ChatUIRenderer.DIM + "--- Recent SDK logs ---" + ChatUIRenderer.RESET);
                rawLines.forEach(System.out::println);
            }
        } catch (Exception e) {
            System.err.println(ChatUIRenderer.YELLOW + "Failed to retrieve SDK logs: " + e.getMessage() + ChatUIRenderer.RESET);
        }
    }

    private void handleStats(ChatSessionManager session) {
        var stats = session.getSessionStats();
        var diagnostics = session.getExecutionDiagnostics();

        System.out.println();
        System.out.println(ChatUIRenderer.BOLD + "┌──────────────────── Session Statistics ────────────────────┐" + ChatUIRenderer.RESET);
        System.out.printf("│ %-22s │ %-33s │%n", "Session id", truncate(session.getSessionId(), 33));
        System.out.printf("│ %-22s │ %-33s │%n", "Persistent session", session.isSessionEnabled() ? "enabled" : "disabled");
        System.out.printf("│ %-22s │ %-33s │%n", "Session started", stats.sessionStart().toString().substring(0, 19));
        System.out.printf("│ %-22s │ %-33s │%n", "Duration", formatDuration(stats.sessionDurationSeconds()));
        System.out.printf("│ %-22s │ %-33d │%n", "Total requests", stats.totalRequests());
        System.out.printf("│ %-22s │ %-33d │%n", "Total tokens", stats.totalTokens());
        System.out.printf("│ %-22s │ %-33d │%n", "Total errors", stats.totalErrors());
        System.out.printf("│ %-22s │ %-33.1f │%n", "Avg tokens/request", stats.avgTokensPerRequest());
        System.out.printf("│ %-22s │ %-30.2f t/s │%n", "Avg speed", stats.avgTokensPerSecond());
        System.out.printf("│ %-22s │ %-30.2f s   │%n", "Total inference time", stats.totalDurationMs() / 1000.0);
        System.out.printf("│ %-22s │ %-33s │%n", "Avg TTFT", stats.avgTtftMs() + " ms");
        System.out.printf("│ %-22s │ %-33s │%n", "Avg TPOT", stats.avgTpotMs() + " ms");
        System.out.printf("│ %-22s │ %-33s │%n", "Avg ITL", stats.avgItlMs() + " ms");

        if (!stats.perModelStats().isEmpty()) {
            System.out.println("├──────────────────── Per Model ─────────────────────────────┤");
            System.out.printf("│ %-22s │ %6s %8s %6s          │%n", "Model", "Reqs", "Tokens", "Errs");
            System.out.println("│──────────────────────────────────────────────────────────── │");
            stats.perModelStats().forEach((model, s) ->
                    System.out.printf("│ %-22s │ %6d %8d %6d          │%n",
                            truncate(model, 22), s[0], s[1], s[2]));
        }

        if (!stats.perProviderStats().isEmpty()) {
            System.out.println("├──────────────────── Per Provider ──────────────────────────┤");
            System.out.printf("│ %-22s │ %6s %8s %6s          │%n", "Provider", "Reqs", "Tokens", "Errs");
            System.out.println("│──────────────────────────────────────────────────────────── │");
            stats.perProviderStats().forEach((provider, s) ->
                    System.out.printf("│ %-22s │ %6d %8d %6d          │%n",
                            truncate(provider, 22), s[0], s[1], s[2]));
        }

        System.out.println("├──────────────────── Engine Execution ──────────────────────┤");
        System.out.printf("│ %-22s │ %-33s │%n", "Last route", truncate(diagnostics.route(), 33));
        System.out.printf("│ %-22s │ %-33s │%n", "Provider impl", truncate(diagnostics.providerDescriptor(), 33));
        System.out.printf("│ %-22s │ %-33s │%n", "Registry available", diagnostics.providerRegistryAvailable() ? "yes" : "no");
        System.out.printf("│ %-22s │ %-33s │%n", "Provider registered", diagnostics.providerRegistered() ? "yes" : "no");
        printMetadataRow("Backend", diagnostics.metadata(), "execution_backend");
        printMetadataRow("Session scope", diagnostics.metadata(), "session_scope");
        printMetadataRow("Acquire mode", diagnostics.metadata(), "session_acquisition_mode");
        printMetadataRow("Reuse decision", diagnostics.metadata(), "session_reuse_decision");
        printMetadataRow("Conversation turn", diagnostics.metadata(), "conversation_turn_mode");
        printMetadataRow("Conversation state", diagnostics.metadata(), "conversation_session_status");
        printMetadataRow("Fast path", diagnostics.metadata(), "conversation_fast_path_mode");
        printMetadataRow("Fast-path why", diagnostics.metadata(), "conversation_fast_path_rationale");
        printMetadataRow("Delta prefill", diagnostics.metadata(), "conversation_delta_prefill_used");
        printMetadataRow("Cached prefix", diagnostics.metadata(), "conversation_turn_cached_tokens");
        printMetadataRow("Delta prompt", diagnostics.metadata(), "conversation_turn_delta_tokens");
        printMetadataRow("Prep profile", diagnostics.metadata(), "session_preparation_profile");
        printMetadataRow("Artifact kind", diagnostics.metadata(), "session_artifact_kind");
        printMetadataRow("KV retained", diagnostics.metadata(), "conversation_session_kv_retained");
        if (diagnostics.lastError() != null && !diagnostics.lastError().isBlank()) {
            System.out.printf("│ %-22s │ %-33s │%n", "Last error", truncate(diagnostics.lastError(), 33));
        }

        System.out.println("└────────────────────────────────────────────────────────────┘");
        if (diagnostics.lastError() != null && !diagnostics.lastError().isBlank()) {
            System.out.println(ChatUIRenderer.DIM + "Last error detail:" + ChatUIRenderer.RESET);
            System.out.println(diagnostics.lastError());
            System.out.println();
        } else {
            System.out.println();
        }
    }

    private void printMetadataRow(String label, java.util.Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        Object value = metadata.get(key);
        if (value == null) {
            return;
        }
        System.out.printf("│ %-22s │ %-33s │%n", label, truncate(String.valueOf(value), 33));
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }
}
