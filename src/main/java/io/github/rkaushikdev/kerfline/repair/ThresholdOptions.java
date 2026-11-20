package io.github.rkaushikdev.kerfline.repair;

/**
 * How a repair rate is judged: the threshold it must not exceed, the fewest joints before an
 * exceedance is reported, and the rolling window to measure over, where zero means the whole
 * history. A job specification can tighten any of them.
 */
public record ThresholdOptions(double threshold, int minimumSample, int windowSize) {

    /** Ten percent over at least twenty joints, measured over the whole history. */
    public static final ThresholdOptions DEFAULTS =
            new ThresholdOptions(Rate.defaultThreshold(), Rate.minimumSample(), 0);

    public ThresholdOptions threshold(double rate) {
        return new ThresholdOptions(rate, minimumSample, windowSize);
    }

    public ThresholdOptions minimumSample(int joints) {
        return new ThresholdOptions(threshold, joints, windowSize);
    }

    public ThresholdOptions windowSize(int joints) {
        return new ThresholdOptions(threshold, minimumSample, joints);
    }
}
