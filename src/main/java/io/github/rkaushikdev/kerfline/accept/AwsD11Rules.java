package io.github.rkaushikdev.kerfline.accept;

/**
 * The AWS D1.1 rules for one kind of loading: the clause, the size cap as a fraction of the weld
 * thickness with its floor and ceiling, what counts as small, the cap on the sum of small ones in a
 * window of weld, and the clearance two neighbours need as a multiple of the larger.
 */
public record AwsD11Rules(
        String clause,
        double fraction,
        double floorIn,
        double ceilingIn,
        double smallIn,
        double sumIn,
        double windowIn,
        double clearanceMultiple) {}
