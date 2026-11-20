package io.github.rkaushikdev.kerfline.report;

/**
 * How a set of second readings came out: the agreement rate, and the split of the disagreements
 * by direction, because an even split and a one sided one mean different things. Three or more
 * disagreements all one way is a reader who has drifted.
 */
public record Agreement(
        int reviewed,
        int agreed,
        int disagreements,
        double rate,
        int lenient,
        int strict,
        boolean oneSided,
        boolean drifting) {}
