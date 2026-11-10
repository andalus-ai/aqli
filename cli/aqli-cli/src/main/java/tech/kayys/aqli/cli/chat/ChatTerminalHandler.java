package tech.kayys.aqli.cli.chat;

import jakarta.enterprise.context.Dependent;
import org.jline.reader.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.widget.AutosuggestionWidgets;
import org.jline.widget.TailTipWidgets;
import org.jline.console.CmdDesc;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import tech.kayys.aqli.sdk.util.AqliHome;

/**
 * Manages JLine Terminal and LineReader for the chat session.
 */
@Dependent
public class ChatTerminalHandler implements AutoCloseable {

    public static final List<String> COMMANDS = List.of(
            "/help", "/reset", "/retry", "/regen", "/quit", "/log", "/list", "/models", "/model",
            "/providers", "/provider", "/info", "/stats", "/modules", "/extensions");

    private Terminal terminal;
    private LineReader reader;

    public void initialize(boolean quiet, Completer completer, Map<String, CmdDesc> commandHelp) {
        try {
            try {
                terminal = TerminalBuilder.builder().system(true).dumb(false).build();
            } catch (Exception e) {
                terminal = TerminalBuilder.builder().dumb(true).build();
            }

            reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(completer)
                    .variable(LineReader.HISTORY_FILE,
                            AqliHome.path("chat_history"))
                    .variable(LineReader.LIST_MAX, 50)
                    .option(LineReader.Option.AUTO_MENU, true)
                    .option(LineReader.Option.AUTO_LIST, true)
                    .option(LineReader.Option.COMPLETE_IN_WORD, true)
                    .build();

            // Bind Enter to smart-accept: expand to first matching command then accept
            reader.getKeyMaps().get(LineReader.MAIN).bind(
                    (Widget) () -> smartAccept(), "\r");

            new AutosuggestionWidgets(reader).enable();

            if (commandHelp != null && !commandHelp.isEmpty()) {
                new TailTipWidgets(reader, commandHelp, 0, TailTipWidgets.TipType.COMPLETER).enable();
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize terminal: " + e.getMessage(), e);
        }
    }

    /**
     * On Enter: if the buffer is an incomplete /command prefix with exactly one
     * match, expand it and accept. If multiple matches exist, expand to the longest
     * common prefix so the user can keep typing or press Tab. Otherwise accept as-is.
     */
    private boolean smartAccept() {
        String buf = reader.getBuffer().toString();
        if (buf.startsWith("/") && !buf.contains(" ")) {
            List<String> matches = COMMANDS.stream()
                    .filter(c -> c.startsWith(buf) && !c.equals(buf))
                    .toList();
            if (matches.size() == 1) {
                reader.getBuffer().clear();
                reader.getBuffer().write(matches.get(0));
                reader.callWidget(LineReader.ACCEPT_LINE);
                return true;
            }
            if (matches.size() > 1) {
                String lcp = longestCommonPrefix(matches);
                if (lcp.length() > buf.length()) {
                    reader.getBuffer().clear();
                    reader.getBuffer().write(lcp);
                    return true;
                }
            }
        }
        reader.callWidget(LineReader.ACCEPT_LINE);
        return true;
    }

    private static String longestCommonPrefix(List<String> list) {
        String first = list.get(0);
        int len = first.length();
        for (String s : list) len = Math.min(len, commonPrefixLength(first, s));
        return first.substring(0, len);
    }

    private static int commonPrefixLength(String a, String b) {
        int i = 0;
        while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    public String readInput(String prompt, String secondaryPrompt) {
        StringBuilder inputBuffer = new StringBuilder();
        String currentPrompt = prompt;
        while (true) {
            String lineInput;
            try {
                lineInput = reader.readLine(currentPrompt);
            } catch (UserInterruptException e) {
                return null;
            } catch (EndOfFileException e) {
                throw e;
            }
            if (lineInput.endsWith("\\")) {
                inputBuffer.append(lineInput, 0, lineInput.length() - 1).append("\n");
                currentPrompt = secondaryPrompt;
            } else {
                inputBuffer.append(lineInput);
                break;
            }
        }
        return inputBuffer.toString().trim();
    }

    public Terminal getTerminal() { return terminal; }
    public LineReader getReader()  { return reader; }

    @Override
    public void close() throws Exception {
        if (terminal != null) terminal.close();
    }
}
