package io.github.rkaushikdev.kerfline.util;

import java.util.Comparator;

/**
 * The order a person reads a list of labels in, as the root locale collates them, which is how
 * the reports have always sorted welder stencils and size labels: whitespace before punctuation, punctuation before digits, digits before letters, letters compared without
 * regard to case first and with lower case ahead of upper case only to break a tie. Unlike {@link
 * java.text.Collator}, spaces and punctuation are not ignored, so {@code "6 in sch 40"} sorts ahead
 * of {@code "6.625 in OD"}.
 */
public final class RootCollation {

    public static final Comparator<String> ORDER = RootCollation::compare;

    private RootCollation() {}

    /** Negative, zero or positive as {@code a} reads before, level with or after {@code b}. */
    public static int compare(String a, String b) {
        int shared = Math.min(a.length(), b.length());
        for (int i = 0; i < shared; i++) {
            int order = Long.compare(primary(a.charAt(i)), primary(b.charAt(i)));
            if (order != 0) {
                return order;
            }
        }
        if (a.length() != b.length()) {
            return Integer.compare(a.length(), b.length());
        }
        for (int i = 0; i < shared; i++) {
            char x = a.charAt(i);
            char y = b.charAt(i);
            if (x != y) {
                return Character.isLowerCase(x) && !Character.isLowerCase(y) ? -1 : 1;
            }
        }
        return 0;
    }

    private static long primary(char c) {
        if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
            return c;
        }
        if (Character.isDigit(c)) {
            return 3L << 32 | Character.digit(c, 10);
        }
        if (Character.isLetter(c)) {
            return 4L << 32 | Character.toLowerCase(c);
        }
        return 2L << 32 | c;
    }
}
