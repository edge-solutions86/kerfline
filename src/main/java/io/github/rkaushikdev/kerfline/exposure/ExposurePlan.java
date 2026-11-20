package io.github.rkaushikdev.kerfline.exposure;

/**
 * Everything a crew needs to set the timer for a gamma shot: the source and its activity on the
 * day, the setup carried through, every exposure factor, and the time in minutes and in whole
 * seconds.
 */
public record ExposurePlan(
        String isotope,
        double activityCi,
        String film,
        String screens,
        double targetDensity,
        double thicknessIn,
        double distanceIn,
        double chartCurieMinutes,
        double distanceFactor,
        double filmFactor,
        double screenFactor,
        double densityFactor,
        double curieMinutes,
        double minutes,
        int seconds) {

    /** The exposure factors on their own, as {@link Technique#exposureFactors} reports them. */
    public ExposureFactors factors() {
        return new ExposureFactors(
                chartCurieMinutes, distanceFactor, filmFactor, screenFactor, densityFactor, curieMinutes);
    }
}
