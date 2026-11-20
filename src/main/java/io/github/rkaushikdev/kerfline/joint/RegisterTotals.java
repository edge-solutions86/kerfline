package io.github.rkaushikdev.kerfline.joint;

/**
 * What a job amounts to: how many joints, welders and spools, the total weld length the
 * radiography is priced on, and how many joints are small bore or welded in a fixed position.
 */
public record RegisterTotals(
        String job, int joints, int welders, int spools, double weldLengthIn, int smallBore, int fixedPosition) {}
