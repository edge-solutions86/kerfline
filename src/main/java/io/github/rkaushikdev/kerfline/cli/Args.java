package io.github.rkaushikdev.kerfline.cli;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Argument parsing for the command line.
 *
 * <p>Written out rather than handed to a parsing library because the behaviour that matters here
 * is the refusing, and every parser worth using has its own opinion about that. An unknown option
 * is a mistake and gets rejected; it is not passed through for somebody downstream to ignore. A
 * required option that is missing names itself. A value that should be a number and is not says
 * which option it was.
 *
 * <p>The one convenience is that both {@code --key value} and {@code --key=value} work, because both
 * turn up in the shell history of anybody who uses more than one tool.
 */
public final class Args {

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Pattern DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final String OPTION_PREFIX = "--";

    private Args() {}

    /** The kinds of value an option can take, by the label the usage screen prints. */
    public static List<String> optionKinds() {
        return OptionKind.labels();
    }

    /**
     * Check an option as written and fix it into an {@link OptionDef}.
     *
     * @throws BadInputException for a name that is not a flag name, or a required option that also
     *     has a fallback
     */
    public static OptionDef defineOption(OptionSpec spec) {
        if (spec.name() == null || !NAME.matcher(spec.name()).matches()) {
            throw new BadInputException(
                    "an option name is lower case letters, digits and dashes", Details.of("name", spec.name()));
        }
        if (spec.kind() == null) {
            throw new BadInputException(
                    "an option is one of " + String.join(", ", optionKinds()),
                    Details.of("kind", null, "known", optionKinds()));
        }
        if (spec.required() && spec.fallback() != null) {
            throw new BadInputException(
                    "a required option cannot also have a fallback", Details.of("name", spec.name()));
        }
        return new OptionDef(
                spec.name(),
                spec.kind(),
                spec.required(),
                Optional.ofNullable(spec.fallback()),
                Optional.ofNullable(spec.describe()));
    }

    private static Object coerce(OptionDef option, String raw) {
        return switch (option.kind()) {
            case BOOLEAN -> Boolean.TRUE;
            case NUMBER -> {
                double value = Num.parse(raw);
                if (!Double.isFinite(value)) {
                    throw new BadInputException(
                            "--" + option.name() + " wants a number and got " + raw,
                            Details.of("option", option.name(), "got", raw));
                }
                yield value;
            }
            case DAY -> {
                if (!DAY.matcher(raw).matches()) {
                    throw new BadInputException(
                            "--" + option.name() + " wants a date as YYYY-MM-DD and got " + raw,
                            Details.of("option", option.name(), "got", raw));
                }
                yield raw;
            }
            case STRING -> raw;
        };
    }

    /**
     * Parse a list of arguments against a set of options. Anything that is not an option lands in
     * the positionals, in the order it was given; everything after a bare {@code --} is a
     * positional. Missing required options are named all at once, and fallbacks are filled in
     * afterwards; an unset flag reads false.
     *
     * @throws BadInputException for an unknown option, a value on a flag, an option with no value,
     *     a value of the wrong kind, or a missing required option
     */
    public static ParsedArgs parseArgs(List<String> argv, List<OptionDef> options) {
        Guard.requireArray(argv, "arguments");
        Guard.requireArray(options, "options");
        Map<String, OptionDef> byName = new LinkedHashMap<>();
        options.forEach(option -> byName.put(option.name(), option));
        Map<String, Object> values = new LinkedHashMap<>();
        List<String> positionals = new ArrayList<>();

        for (int i = 0; i < argv.size(); i++) {
            String token = argv.get(i);
            if (token == null) {
                throw new BadInputException("every argument must be a string", Details.of("got", null));
            }
            if (token.equals(OPTION_PREFIX)) {
                positionals.addAll(argv.subList(i + 1, argv.size()));
                break;
            }
            if (!token.startsWith(OPTION_PREFIX)) {
                positionals.add(token);
                continue;
            }

            String body = token.substring(2);
            int equals = body.indexOf('=');
            String name = equals == -1 ? body : body.substring(0, equals);
            OptionDef option = byName.get(name);
            if (option == null) {
                throw new BadInputException(
                        "no such option --" + name,
                        Details.of(
                                "option",
                                name,
                                "known",
                                byName.keySet().stream().sorted().toList()));
            }

            if (equals != -1) {
                if (option.kind() == OptionKind.BOOLEAN) {
                    throw new BadInputException(
                            "--" + name + " is a flag and takes no value", Details.of("option", name));
                }
                values.put(name, coerce(option, body.substring(equals + 1)));
                continue;
            }

            if (option.kind() == OptionKind.BOOLEAN) {
                values.put(name, Boolean.TRUE);
                continue;
            }

            String next = i + 1 < argv.size() ? argv.get(i + 1) : null;
            if (next == null || next.startsWith(OPTION_PREFIX)) {
                throw new BadInputException("--" + name + " wants a value", Details.of("option", name));
            }
            values.put(name, coerce(option, next));
            i += 1;
        }

        List<String> missing = options.stream()
                .filter(option -> option.required() && !values.containsKey(option.name()))
                .map(OptionDef::name)
                .toList();
        if (!missing.isEmpty()) {
            List<String> flags =
                    missing.stream().map(name -> OPTION_PREFIX + name).toList();
            throw new BadInputException(
                    "missing required option" + (missing.size() > 1 ? "s" : "") + ": " + String.join(", ", flags),
                    Details.of("missing", missing));
        }

        for (OptionDef option : options) {
            if (!values.containsKey(option.name()) && option.fallback().isPresent()) {
                values.put(option.name(), option.fallback().get());
            }
            if (!values.containsKey(option.name()) && option.kind() == OptionKind.BOOLEAN) {
                values.put(option.name(), Boolean.FALSE);
            }
        }

        return new ParsedArgs(values, positionals);
    }

    /** The usage line for one option, for a help screen. */
    public static String describeOption(OptionDef option) {
        String value =
                option.kind() == OptionKind.BOOLEAN ? "" : " <" + option.kind().label() + ">";
        String tail = option.required()
                ? " (required)"
                : option.fallback()
                        .map(fallback -> " (default " + Text.str(fallback) + ")")
                        .orElse("");
        String describe = option.describe()
                .filter(text -> !text.isEmpty())
                .map(text -> "  " + text)
                .orElse("");
        return OPTION_PREFIX + option.name() + value + tail + describe;
    }

    /** The usage screen for one command: its usage line, then a line per option. */
    public static String usage(String name, List<OptionDef> options) {
        Guard.requireArray(options, "options");
        List<String> lines = new ArrayList<>(List.of("usage: kerf " + name + " [options]", ""));
        for (OptionDef option : options) {
            lines.add("  " + describeOption(option));
        }
        return String.join("\n", lines);
    }
}
