package io.github.rkaushikdev.kerfline.density;

import java.util.List;

/**
 * A whole area of interest judged against the reference. A single reading outside the band
 * condemns the film, but every reading is kept, because the pattern of where it went wrong is
 * what tells the crew whether to reshoot the joint or add an exposure at the ends.
 */
public record AreaJudgement(
        double reference,
        List<JudgedReading> readings,
        boolean pass,
        List<JudgedReading> failures,
        JudgedReading lightest,
        JudgedReading darkest) {

    public AreaJudgement {
        readings = List.copyOf(readings);
        failures = List.copyOf(failures);
    }
}
