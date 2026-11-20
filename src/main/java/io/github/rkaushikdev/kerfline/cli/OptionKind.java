package io.github.rkaushikdev.kerfline.cli;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** What kind of value a command line option takes: text, a number, a bare flag, or a day. */
public enum OptionKind {
    STRING,
    NUMBER,
    BOOLEAN,
    DAY;

    /** The lower case name the usage screen prints: {@code <number>}, {@code <day>}. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Every kind by its label, in the order the usage screen documents them. */
    public static List<String> labels() {
        return Arrays.stream(values()).map(OptionKind::label).toList();
    }

    /**
     * The kind with this label.
     *
     * @throws BadInputException naming the kinds there are, when there is no such kind
     */
    public static OptionKind of(String label) {
        for (OptionKind kind : values()) {
            if (kind.label().equals(label)) {
                return kind;
            }
        }
        throw new BadInputException(
                "an option is one of " + String.join(", ", labels()), Details.of("kind", label, "known", labels()));
    }
}
