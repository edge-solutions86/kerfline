package io.github.rkaushikdev.kerfline.util;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identifiers. Joints, films, reports and welder stencils all get written by hand on a lead tape
 * or a film jacket before they ever reach a keyboard, so the parser here has to accept what a
 * marker pen produces and the formatter has to produce something that survives being read back
 * off a light box.
 */
public final class Ids {

    /** Register order: J-2 before J-10, numbers before letters, case ignored. */
    public static final Comparator<String> REGISTER_ORDER = Ids::compareIds;

    private static final Pattern SEQUENCE = Pattern.compile("([A-Z]{1,4})(?:-|" + Text.SPACE_CLASS + ")?(\\d{1,6})");
    private static final Pattern PREFIX = Pattern.compile("[A-Za-z]{1,4}");
    private static final Pattern WRITTEN_PREFIX = Pattern.compile("^[A-Za-z]{1,4}(?:-|" + Text.SPACE_CLASS + ")?");
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d+");
    private static final Pattern STENCIL = Pattern.compile("[A-Z0-9]{1,6}");
    private static final Pattern NOT_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");

    private Ids() {}

    public static String sequence(String prefix, int number) {
        return sequence(prefix, number, 3);
    }

    /** Format a prefixed sequence number, zero padded to a fixed width: {@code J-012}. */
    public static String sequence(String prefix, int number, int width) {
        if (prefix == null || !PREFIX.matcher(prefix).matches()) {
            throw new BadInputException("prefix must be one to four letters", Details.of("prefix", prefix));
        }
        if (number < 0) {
            throw new BadInputException("sequence number must be a whole number", Details.of("number", number));
        }
        if (width < 1 || width > 6) {
            throw new BadInputException("width must be between one and six", Details.of("width", width));
        }
        return prefix.toUpperCase(Locale.ROOT) + "-" + Text.padLeft(String.valueOf(number), width, "0");
    }

    /** Read a sequence back, accepting a dash, a space or nothing between prefix and number. */
    public static SequenceId parseSequence(String text) {
        if (text == null) {
            throw new BadInputException("identifier must be a string", Details.of("got", null));
        }
        Matcher match = SEQUENCE.matcher(Text.trim(text).toUpperCase(Locale.ROOT));
        if (!match.matches()) {
            throw new BadInputException("cannot read " + text + " as a prefixed sequence", Details.of("text", text));
        }
        return new SequenceId(match.group(1), Integer.parseInt(match.group(2)));
    }

    public static boolean isSequence(String text) {
        try {
            parseSequence(text);
            return true;
        } catch (BadInputException unreadable) {
            return false;
        }
    }

    /** The next number in the same series, keeping the written width. */
    public static String nextInSequence(String text) {
        SequenceId parsed = parseSequence(text);
        int digits = WRITTEN_PREFIX.matcher(Text.trim(text)).replaceFirst("").length();
        return sequence(parsed.prefix(), parsed.number() + 1, Math.max(1, digits));
    }

    /**
     * Compare identifiers the way a register reads: J-2 before J-10, and digits before letters at
     * the same position. A plain string sort puts J-10 above J-2 and a technician looking for a
     * joint gives up on the list. A shorter id that is a prefix of a longer one sorts first.
     */
    public static int compareIds(String a, String b) {
        List<Object> left = chunks(String.valueOf(a));
        List<Object> right = chunks(String.valueOf(b));
        int shared = Math.min(left.size(), right.size());
        for (int i = 0; i < shared; i++) {
            Object x = left.get(i);
            Object y = right.get(i);
            if (x instanceof Double dx && y instanceof Double dy) {
                int order = Double.compare(dx, dy);
                if (order != 0) {
                    return order < 0 ? -1 : 1;
                }
            } else if (x instanceof String sx && y instanceof String sy) {
                int order = sx.compareTo(sy);
                if (order != 0) {
                    return order < 0 ? -1 : 1;
                }
            } else {
                return x instanceof Double ? -1 : 1;
            }
        }
        return left.size() - right.size();
    }

    /** Runs of digits as numbers and the text between them as upper case strings. */
    private static List<Object> chunks(String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        List<Object> parts = new ArrayList<>();
        Matcher digits = DIGIT_RUN.matcher(upper);
        int at = 0;
        while (digits.find()) {
            if (digits.start() > at) {
                parts.add(upper.substring(at, digits.start()));
            }
            parts.add(Double.parseDouble(digits.group()));
            at = digits.end();
        }
        if (at < upper.length()) {
            parts.add(upper.substring(at));
        }
        return parts;
    }

    /** A sorted copy of the ids in register order; the caller's list is left alone. */
    public static List<String> sortIds(List<String> ids) {
        if (ids == null) {
            throw new BadInputException("ids must be an array", Details.of("got", null));
        }
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(REGISTER_ORDER);
        return List.copyOf(sorted);
    }

    /**
     * A welder stencil. These are short, they are stamped into the pipe next to the weld, and the
     * shop allows letters and digits only so that a rubbing taken off the stamp is unambiguous.
     *
     * @return the stencil trimmed and in upper case
     */
    public static String requireStencil(String text) {
        if (text == null
                || !STENCIL.matcher(Text.trim(text).toUpperCase(Locale.ROOT)).matches()) {
            throw new BadInputException("a welder stencil is one to six letters or digits", Details.of("got", text));
        }
        return Text.trim(text).toUpperCase(Locale.ROOT);
    }

    public static boolean isStencil(String text) {
        try {
            requireStencil(text);
            return true;
        } catch (BadInputException unstampable) {
            return false;
        }
    }

    /** A filesystem safe key for a record, derived from whatever it is called. */
    public static String slug(String text) {
        String lower = (text == null ? "" : text).toLowerCase(Locale.ROOT);
        String body =
                EDGE_DASHES.matcher(NOT_SLUG.matcher(lower).replaceAll("-")).replaceAll("");
        if (body.isEmpty()) {
            throw new BadInputException("cannot slug an empty name", Details.of("got", text));
        }
        return body;
    }
}
