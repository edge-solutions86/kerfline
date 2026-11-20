package io.github.rkaushikdev.kerfline.safety;

import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the rope goes.
 *
 * <p>Two boundaries matter on a radiography job. The high radiation boundary is where the rate
 * falls to 100 mR/h, and inside it nobody stands. The restricted area boundary is where it falls
 * to 2 mR/h, and that is the rope the public is kept behind. The second one is much further out
 * than people expect: a forty curie iridium camera reads 2 mR/h at about three hundred feet, which
 * on a plant job is most of a unit.
 *
 * <p>A calculated distance is a starting point and not the boundary. The boundary is where the
 * survey meter says it is, and the calculation exists to tell the crew roughly where to start
 * walking with the meter and to catch the case where the job cannot be done at all in the space
 * available.
 */
public final class Boundary {

    /** The shop margin on a calculated distance; it covers scatter, which nothing here models. */
    public static final double DEFAULT_MARGIN = 1.2;

    private static final double TRIAL_CI = 10;
    private static final int BISECTION_STEPS = 80;

    private Boundary() {}

    /** The high radiation rate, 100 mR/h. */
    public static double highRadiationRate() {
        return BoundaryLine.HIGH_RADIATION.limitMrPerHour();
    }

    /** The restricted area rate, 2 mR/h. */
    public static double restrictedAreaRate() {
        return BoundaryLine.RESTRICTED.limitMrPerHour();
    }

    /** The two boundary distances for a source, before any margin is added. */
    public static BoundaryDistances boundaryDistancesFt(Camera camera) {
        return new BoundaryDistances(
                DoseRate.distanceForRateFt(camera, highRadiationRate()),
                DoseRate.distanceForRateFt(camera, restrictedAreaRate()));
    }

    public static double barricadeDistanceFt(Camera camera) {
        return barricadeDistanceFt(camera, DEFAULT_MARGIN);
    }

    /**
     * Where to put the rope, with the shop margin on the calculated distance. The margin is not a
     * safety factor on the physics, which is exact; it covers scatter off surrounding steel, which
     * this calculation does not model at all.
     */
    public static double barricadeDistanceFt(Camera camera, double margin) {
        Guard.requireAtLeast(margin, 1, "margin");
        double restrictedFt = boundaryDistancesFt(camera).restrictedFt();
        return Num.round(restrictedFt * margin, 1);
    }

    /** Whether a spot is inside the restricted area boundary. */
    public static boolean isInsideBoundary(Camera camera, double distanceFt) {
        return isInsideBoundary(camera, distanceFt, BoundaryLine.RESTRICTED);
    }

    /** Whether a spot is inside the given boundary: the rate there is over the line's limit. */
    public static boolean isInsideBoundary(Camera camera, double distanceFt, BoundaryLine boundary) {
        Guard.requireAbove(distanceFt, 0, "distance");
        double rate = DoseRate.doseRateAt(camera, distanceFt);
        return rate > boundary.limitMrPerHour();
    }

    public static SpaceCheck fitsInSpace(Camera camera, double availableFt) {
        return fitsInSpace(camera, availableFt, DEFAULT_MARGIN);
    }

    /**
     * Whether the job fits in the space there is. A shot that cannot get its restricted boundary
     * inside the available space needs shielding, a smaller source, or the area cleared, and
     * knowing which before the crew mobilises is worth a lot.
     */
    public static SpaceCheck fitsInSpace(Camera camera, double availableFt, double margin) {
        Guard.requireAbove(availableFt, 0, "available space");
        double needed = barricadeDistanceFt(camera, margin);
        boolean fits = needed <= availableFt;
        return new SpaceCheck(needed, availableFt, fits, fits ? 0 : Num.round(needed - availableFt, 1));
    }

    public static double largestActivityFor(String isotopeId, double availableFt) {
        return largestActivityFor(isotopeId, availableFt, DEFAULT_MARGIN);
    }

    /**
     * The largest activity, in curies, that would fit a boundary into the space available. Since
     * rate goes as activity over distance squared, the activity scales with the square of the
     * distance the space allows.
     */
    public static double largestActivityFor(String isotopeId, double availableFt, double margin) {
        Guard.requireAbove(availableFt, 0, "available space");
        Guard.requireAtLeast(margin, 1, "margin");
        double trialFt = barricadeDistanceFt(new Camera(isotopeId, TRIAL_CI), margin);
        return Num.round(TRIAL_CI * Math.pow(availableFt / trialFt, 2), 2);
    }

    public static double combinedBoundaryFt(List<Camera> sources) {
        return combinedBoundaryFt(sources, DEFAULT_MARGIN);
    }

    /**
     * The boundary for several sources being used at once. Rates add, so the boundary for two
     * cameras is not the further of the two boundaries, and taking it as such is the mistake this
     * method exists to prevent.
     *
     * @throws IllegalArgumentException when there are no sources at all
     */
    public static double combinedBoundaryFt(List<Camera> sources, double margin) {
        Guard.requireArray(sources, "sources");
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("a combined boundary needs at least one source");
        }
        List<Double> singles = new ArrayList<>();
        for (Camera source : sources) {
            singles.add(barricadeDistanceFt(source, 1));
        }
        double low = Num.maxOf(singles);
        double high = low * 10;
        for (int i = 0; i < BISECTION_STEPS; i++) {
            double mid = (low + high) / 2;
            if (totalRateAt(sources, mid) > restrictedAreaRate()) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return Num.round(high * margin, 1);
    }

    private static double totalRateAt(List<Camera> sources, double distanceFt) {
        double total = 0;
        for (Camera source : sources) {
            total += DoseRate.doseRateAt(source, distanceFt);
        }
        return total;
    }
}
