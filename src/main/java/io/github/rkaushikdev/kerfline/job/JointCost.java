package io.github.rkaushikdev.kerfline.job;

/**
 * What one joint costs on a given day: the shots that walk it, the source activity that day, and
 * the exposure, setup and total minutes.
 */
public record JointCost(
        String jointId,
        int shots,
        double activityCi,
        double exposureMinutes,
        double setupMinutes,
        double totalMinutes) {}
