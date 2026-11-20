package io.github.rkaushikdev.kerfline.cli;

import io.github.rkaushikdev.kerfline.errors.KerflineException;
import io.github.rkaushikdev.kerfline.util.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turning arguments into text and an exit code.
 *
 * <p>This class does not print and does not exit. It returns what should go to each stream and what
 * the process status should be, and {@link Kerf} does the wiring. Splitting it that way is what
 * lets the whole command line be tested without spawning anything.
 *
 * <p>Exit codes: 0 for a clean answer, 1 for a weld or a technique that did not pass, and 2 for
 * being asked something that could not be done.
 */
public final class Run {

    private static final int OK = 0;
    private static final int FINDING = 1;
    private static final int MISUSE = 2;

    private Run() {}

    public static ExitCodes exitCodes() {
        return new ExitCodes(OK, FINDING, MISUSE);
    }

    public static String version() {
        return Version.VERSION;
    }

    /**
     * Format an error for the terminal, without a stack trace: {@code kerf: } and the message, then
     * one line per detail that has a value.
     */
    public static String renderError(Throwable err) {
        if (err instanceof KerflineException ours) {
            List<String> lines = new ArrayList<>(List.of("kerf: " + ours.getMessage()));
            for (Map.Entry<String, Object> detail : ours.details().entrySet()) {
                Object value = detail.getValue();
                if (value != null && !(value instanceof Optional<?> optional && optional.isEmpty())) {
                    lines.add("  " + detail.getKey() + ": " + DetailText.format(value));
                }
            }
            return String.join("\n", lines);
        }
        return "kerf: " + err.getMessage();
    }

    /**
     * Run the tool. Help and version are handled before anything else, so that asking for help
     * never depends on the rest of the arguments being valid, which is the state somebody asking
     * for help is usually in. A null argument list is treated as an empty one.
     */
    public static CliResult run(List<String> argv) {
        List<String> args = argv == null ? List.of() : argv;

        if (args.isEmpty() || isHelp(args.get(0)) || "help".equals(args.get(0))) {
            return new CliResult(Commands.overallUsage(), "", args.isEmpty() ? MISUSE : OK);
        }

        if ("--version".equals(args.get(0)) || "-v".equals(args.get(0))) {
            return new CliResult(version(), "", OK);
        }

        String name = args.get(0);
        List<String> rest = args.subList(1, args.size());

        if (name == null || !Commands.commandNames().contains(name)) {
            return new CliResult("", "kerf: no command " + name + "\n\n" + Commands.overallUsage(), MISUSE);
        }

        if (rest.stream().anyMatch(Run::isHelp)) {
            return new CliResult(Commands.commandUsage(name), "", OK);
        }

        try {
            CommandResult result = Commands.runCommand(name, rest);
            return new CliResult(result.text(), "", result.code() == 0 ? OK : FINDING);
        } catch (RuntimeException refused) {
            return new CliResult("", renderError(refused) + "\n\n" + Commands.commandUsage(name), MISUSE);
        }
    }

    private static boolean isHelp(String arg) {
        return "--help".equals(arg) || "-h".equals(arg);
    }
}
