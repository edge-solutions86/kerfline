package io.github.rkaushikdev.kerfline.repair;

/**
 * A repair rate judged against a threshold. {@code enoughToSay} is false while the sample is
 * smaller than the minimum, and {@code shortBy} is how many more joints it needs; {@code exceeds}
 * is only ever true once there is enough to say.
 */
public record ThresholdCheck(
        int shot, int repaired, double rate, double threshold, boolean exceeds, boolean enoughToSay, int shortBy) {}
