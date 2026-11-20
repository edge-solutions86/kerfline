package io.github.rkaushikdev.kerfline.cli;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IParameterConsumer;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * The {@code kerf} command: the whole of the process wiring.
 *
 * <p>Everything that can be decided is decided in {@link Run}, which returns text and a status
 * rather than printing. What is left here is the three things that cannot be tested without a
 * process: writing to the two streams and setting the exit status. Picocli hands every argument
 * through untouched, in order and with no help, version or argument file handling of its own, so
 * {@link Run} sees exactly what was typed.
 */
@Command(name = "kerf", description = "Radiographic technique planning and film interpretation for weld inspection.")
public final class Kerf implements Callable<Integer> {

    @Parameters(
            arity = "0..*",
            paramLabel = "<argument>",
            description = "a command and its options; run kerf --help for the list",
            parameterConsumer = RawArguments.class)
    private List<String> arguments = new ArrayList<>();

    private CommandSpec spec;

    /**
     * Picocli hands over the command's model as it is built; the parser is told to leave option
     * lookalikes and {@code @} arguments to {@link Run} rather than interpreting them itself.
     */
    @Spec
    void setSpec(CommandSpec commandSpec) {
        commandSpec.parser().unmatchedOptionsArePositionalParams(true).expandAtFiles(false);
        spec = commandSpec;
    }

    @Override
    public Integer call() {
        CliResult result = Run.run(arguments);
        CommandLine commandLine = spec.commandLine();
        write(commandLine.getOut(), result.out());
        write(commandLine.getErr(), result.err());
        return result.code();
    }

    private static void write(PrintWriter stream, String text) {
        if (!text.isEmpty()) {
            stream.print(text + "\n");
            stream.flush();
        }
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Kerf()).execute(args));
    }

    /** Takes every remaining argument as it stands, a bare {@code --} included. */
    static final class RawArguments implements IParameterConsumer {

        @Override
        public void consumeParameters(Stack<String> args, ArgSpec argSpec, CommandSpec commandSpec) {
            List<String> taken = argSpec.getValue();
            while (!args.isEmpty()) {
                taken.add(args.pop());
            }
        }
    }
}
