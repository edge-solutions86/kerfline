package io.github.rkaushikdev.kerfline.safety;

/**
 * What a retraction survey says: the highest reading at the camera, the threshold and background it
 * was judged against, whether the source is home, whether the camera reads at background, and what
 * the crew may do next.
 */
public record RetractionVerdict(
        double highestMrPerHour,
        double thresholdMrPerHour,
        double backgroundMrPerHour,
        boolean sourceIsHome,
        boolean atBackground,
        String action) {}
