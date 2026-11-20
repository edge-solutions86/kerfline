package io.github.rkaushikdev.kerfline.accept;

import io.github.rkaushikdev.kerfline.indication.Indication;

/**
 * A size to compare against a cap, for {@link Common#capVerdict}: the code and clause doing the
 * capping, what is being measured (it opens the reason), the size and the cap, the units the reason
 * prints them in, and the indication when the finding is about one. Start from {@link #of} and
 * adjust with the copying setters; a null indication means an aggregate finding.
 */
public record CapCheck(
        String code, String clause, String what, double sizeIn, double capIn, String units, Indication indication) {

    /** A check in inches with no size or cap yet; both must be set before it is judged. */
    public static CapCheck of(String code, String clause, String what) {
        return new CapCheck(code, clause, what, Double.NaN, Double.NaN, "in", null);
    }

    public CapCheck size(double value) {
        return new CapCheck(code, clause, what, value, capIn, units, indication);
    }

    public CapCheck cap(double value) {
        return new CapCheck(code, clause, what, sizeIn, value, units, indication);
    }

    public CapCheck units(String word) {
        return new CapCheck(code, clause, what, sizeIn, capIn, word, indication);
    }

    public CapCheck indication(Indication about) {
        return new CapCheck(code, clause, what, sizeIn, capIn, units, about);
    }
}
