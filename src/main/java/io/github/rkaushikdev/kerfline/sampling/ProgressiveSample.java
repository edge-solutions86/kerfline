package io.github.rkaushikdev.kerfline.sampling;

import java.util.List;

/**
 * The joints a failure pulls into the shoot: up to two more by the same welder, and whether the
 * welder had fewer than two left to pull.
 */
public record ProgressiveSample(String welder, List<SampledJoint> additional, boolean exhausted) {

    public ProgressiveSample {
        additional = List.copyOf(additional);
    }
}
