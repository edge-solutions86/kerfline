package io.github.rkaushikdev.kerfline.accept;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * What a code paragraph decides about a weld, ordered by severity. Accept means the weld stays as
 * it is. Repair means a defect was found that can be ground out and rewelded. Reject means the
 * joint comes out of the line: a weld with more repair in it than sound metal is not repaired
 * again, and some defects are never repaired at all.
 */
public enum Disposition {
    ACCEPT,
    REPAIR,
    REJECT;

    /** The word the journal, the sheets and the command line write: {@code accept}, {@code repair} or {@code reject}. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Zero for an acceptance, rising with how much of the weld has to come out. */
    public int severity() {
        return ordinal();
    }

    /** Every label, from accept to reject. */
    public static List<String> labels() {
        return Arrays.stream(values()).map(Disposition::label).toList();
    }

    /**
     * The disposition a label names, as written in a journal payload or on a form.
     *
     * @throws BadInputException when the label is none of the three
     */
    public static Disposition of(String label) {
        for (Disposition disposition : values()) {
            if (disposition.label().equals(label)) {
                return disposition;
            }
        }
        throw unknown(label);
    }

    /** The refusal for something that is not a disposition, worded as every guard in the package words it. */
    static BadInputException unknown(Object got) {
        return new BadInputException(
                "disposition must be one of " + String.join(" or ", labels()),
                Details.of("got", got, "known", labels()));
    }
}
