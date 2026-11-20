package io.github.rkaushikdev.kerfline.util;

/**
 * Rates, and what an empty set means.
 *
 * <p>Seven modules were dividing one count by another with a guard on the denominator, and they
 * did not agree about what to return when there was nothing to divide. A repair rate over no
 * joints is nought, because nothing has been repaired. An agreement rate over no reviews is one,
 * because nothing has been disagreed with. Both are right and neither is the default, which is
 * why the answer is an argument here rather than a convention.
 *
 * <p>Getting that wrong is not a crash. It is a report that says a crew with no work yet has a
 * zero percent agreement rate, and somebody acting on it.
 *
 * <p>Counts are taken as numbers and refused when they are not whole, because they are often the
 * result of arithmetic on a register rather than a literal.
 */
public final class Rate {

    private static final double EMPTY_AS_NOUGHT = 0;
    private static final int RATE_PLACES = 4;
    private static final int PERCENT_PLACES = 1;

    private Rate() {}

    public static double rateOf(double part, double whole) {
        return rateOf(part, whole, EMPTY_AS_NOUGHT, RATE_PLACES);
    }

    public static double rateOf(double part, double whole, double whenEmpty) {
        return rateOf(part, whole, whenEmpty, RATE_PLACES);
    }

    /**
     * The part over the whole, rounded, or {@code whenEmpty} when the whole is nought.
     *
     * @throws IllegalArgumentException when the part is more than the whole
     */
    public static double rateOf(double part, double whole, double whenEmpty, int places) {
        Guard.requireWhole(part, "part");
        Guard.requireWhole(whole, "whole");
        Guard.requireAtLeast(part, 0, "part");
        Guard.requireAtLeast(whole, 0, "whole");
        if (part > whole) {
            throw new IllegalArgumentException(Num.text(part) + " of " + Num.text(whole) + " is more than all of them");
        }
        return whole == 0 ? whenEmpty : Num.round(part / whole, places);
    }

    public static CountedRate asRate(double part, double whole) {
        return asRate(part, whole, EMPTY_AS_NOUGHT, RATE_PLACES);
    }

    public static CountedRate asRate(double part, double whole, double whenEmpty) {
        return asRate(part, whole, whenEmpty, RATE_PLACES);
    }

    /**
     * The rate with the two counts it came from. Every report in this package prints all three,
     * because a rate on its own invites the reader to treat one repair in three joints and thirty
     * in ninety as the same finding.
     */
    public static CountedRate asRate(double part, double whole, double whenEmpty, int places) {
        double rate = rateOf(part, whole, whenEmpty, places);
        return new CountedRate((int) part, (int) whole, rate);
    }

    public static double percentOf(double part, double whole) {
        return percentOf(part, whole, EMPTY_AS_NOUGHT, PERCENT_PLACES);
    }

    public static double percentOf(double part, double whole, double whenEmpty) {
        return percentOf(part, whole, whenEmpty, PERCENT_PLACES);
    }

    /** The rate as a percentage, rounded for a report line. */
    public static double percentOf(double part, double whole, double whenEmpty, int places) {
        double rate = rateOf(part, whole, whenEmpty, 6);
        return Num.round(rate * 100, places);
    }

    /**
     * How many more clean items it takes to bring a rate to or under a target. Returns nought when
     * it is already there, and refuses a target of nought, which no number of clean items can
     * reach once anything has gone wrong.
     *
     * @throws IllegalArgumentException when the target is not above zero and at most one
     */
    public static int itemsToReach(double part, double whole, double target) {
        Guard.requireWhole(part, "part");
        Guard.requireWhole(whole, "whole");
        if (!(target > 0) || target > 1) {
            throw new IllegalArgumentException("a target rate is above zero and no more than one");
        }
        if (whole == 0 || part / whole <= target) {
            return 0;
        }
        return (int) Math.max(0, Math.ceil(part / target - whole));
    }
}
