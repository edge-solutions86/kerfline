package io.github.rkaushikdev.kerfline.cli;

import java.util.List;
import java.util.function.Function;

/**
 * One command the tool offers: its name, the line the usage screen prints for it, the options it
 * takes, and the action from parsed options to text. An action prints nothing, reads no file and
 * looks at no clock, which is what makes a command testable without a filesystem and gives the
 * same answer twice.
 */
public record CliCommand(
        String name, String describe, List<OptionDef> options, Function<ParsedArgs, CommandResult> action) {

    public CliCommand {
        options = List.copyOf(options);
    }

    /** Run the action on arguments already parsed against {@link #options}. */
    public CommandResult run(ParsedArgs parsed) {
        return action.apply(parsed);
    }
}
