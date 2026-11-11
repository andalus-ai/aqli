package tech.kayys.aqli.cli.commands.replay;

import java.util.List;
import java.util.stream.Collectors;

/** Renders replay arguments as a POSIX-shell-safe command string. */
public final class ReplayShellCommandFormatter {
    private ReplayShellCommandFormatter() {
    }

    public static String format(List<String> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }
        return arguments.stream()
                .map(ReplayShellCommandFormatter::quote)
                .collect(Collectors.joining(" "));
    }

    private static String quote(String value) {
        if (value == null || value.isEmpty()) {
            return "''";
        }
        if (value.matches("[A-Za-z0-9_@%+=:,./-]+")) {
            return value;
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
