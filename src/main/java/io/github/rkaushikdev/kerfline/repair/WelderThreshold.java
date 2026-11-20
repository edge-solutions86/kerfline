package io.github.rkaushikdev.kerfline.repair;

/** One welder's repair rate judged against a threshold, as {@link ThresholdCheck} describes. */
public record WelderThreshold(
        String welder,
        int shot,
        int repaired,
        double rate,
        double threshold,
        boolean exceeds,
        boolean enoughToSay,
        int shortBy) {

    static WelderThreshold of(String welder, ThresholdCheck check) {
        return new WelderThreshold(
                welder,
                check.shot(),
                check.repaired(),
                check.rate(),
                check.threshold(),
                check.exceeds(),
                check.enoughToSay(),
                check.shortBy());
    }
}
