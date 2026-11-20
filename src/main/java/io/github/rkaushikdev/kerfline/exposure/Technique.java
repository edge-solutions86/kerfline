package io.github.rkaushikdev.kerfline.exposure;

import io.github.rkaushikdev.kerfline.isotope.Catalog;
import io.github.rkaushikdev.kerfline.isotope.Decay;
import io.github.rkaushikdev.kerfline.isotope.Isotope;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;

/**
 * The whole exposure calculation, in the order the shop does it on paper.
 *
 * <p>The chart is the only measurement in the chain. Everything after it is a correction for the
 * ways the real setup differs from the conditions the chart was shot at, and each correction is a
 * multiplication, so the order they are applied in does not change the answer. It does change how
 * easy the sheet is to check, which is why they come out as separate named factors rather than one
 * number.
 */
public final class Technique {

    private static final double DEFAULT_MARGIN = 0.1;

    private Technique() {}

    /** The chart reading and every correction from the chart conditions to this setup. */
    public static ExposureFactors exposureFactors(TechniqueSetup setup) {
        return exposureFactors(
                setup.thicknessIn(),
                setup.distanceIn(),
                setup.filmId(),
                setup.screens(),
                setup.targetDensity(),
                setup.isotopeId());
    }

    private static ExposureFactors exposureFactors(
            double thicknessIn,
            double distanceIn,
            String filmId,
            String screens,
            double targetDensity,
            String isotopeId) {
        ChartReference reference = Chart.chartReference();
        double base = Chart.chartExposure(isotopeId, thicknessIn);

        double forDistance = Law.scaleExposure(base, reference.distanceIn(), distanceIn);
        double forFilm = FilmClass.convertExposure(forDistance, reference.film(), filmId);
        double screenRatio =
                Screens.screenFactor(screens, isotopeId) / Screens.screenFactor(reference.screens(), isotopeId);
        double forScreens = forFilm * screenRatio;
        double forDensity = Aim.aimAtDensity(forScreens, filmId, reference.density(), targetDensity);

        return new ExposureFactors(
                Num.round(base, 3),
                Num.round(StrictMath.pow(distanceIn / reference.distanceIn(), 2), 4),
                Num.round(forFilm / forDistance, 4),
                Num.round(screenRatio, 4),
                Num.round(forDensity / forScreens, 4),
                Num.round(forDensity, 3));
    }

    /**
     * The exposure in curie minutes and the time it takes on the source as it stands today.
     * Everything the crew needs to set the timer. The source may be named by its label.
     */
    public static ExposurePlan planExposure(TechniqueSetup setup) {
        Guard.requireAbove(setup.assayCi(), 0, "assay activity");
        Guard.requireNumber(setup.daysSinceAssay(), "days since assay");

        Isotope source = Catalog.isotope(setup.isotopeId());
        double activityCi = Decay.activityAfter(setup.assayCi(), source.halfLifeDays(), setup.daysSinceAssay());
        ExposureFactors factors = exposureFactors(
                setup.thicknessIn(),
                setup.distanceIn(),
                setup.filmId(),
                setup.screens(),
                setup.targetDensity(),
                source.id());
        double minutes = Law.minutesForCurieMinutes(factors.curieMinutes(), activityCi);

        return new ExposurePlan(
                source.id(),
                Num.round(activityCi, 2),
                setup.filmId(),
                setup.screens(),
                setup.targetDensity(),
                setup.thicknessIn(),
                setup.distanceIn(),
                factors.chartCurieMinutes(),
                factors.distanceFactor(),
                factors.filmFactor(),
                factors.screenFactor(),
                factors.densityFactor(),
                factors.curieMinutes(),
                Num.round(minutes, 2),
                (int) Math.round(minutes * 60));
    }

    public static int timerSetting(double minutes) {
        return timerSetting(minutes, DEFAULT_MARGIN);
    }

    /**
     * The time a crew can actually dial, in seconds: rounded up to the nearest whole second with a
     * working margin on it, ten percent unless stated. Rounding down is how a light film happens.
     */
    public static int timerSetting(double minutes, double marginFraction) {
        Guard.requireAbove(minutes, 0, "exposure time");
        Guard.requireAtLeast(marginFraction, 0, "margin");
        return (int) Math.ceil(minutes * (1 + marginFraction) * 60);
    }
}
