package io.github.rkaushikdev.kerfline.exposure;

import io.github.rkaushikdev.kerfline.util.Guard;

/**
 * The two laws every exposure calculation goes through.
 *
 * <p>Intensity falls with the square of the distance, so the time needed to lay down the same
 * exposure rises with the square of the distance. Those are the same statement and people still
 * get the direction wrong under a truck at two in the morning, which is why they are two named
 * methods here rather than one exponent used in both directions.
 *
 * <p>Exposure itself is held in curie minutes for a gamma source and in milliampere minutes for an
 * X-ray head. Both are a rate multiplied by a time, and both assume reciprocity holds, which it
 * does over the range this shop works in.
 */
public final class Law {

    private static final double DEFAULT_TOLERANCE = 0.02;

    private Law() {}

    public static double intensityAtDistance(double intensity, double fromIn, double toIn) {
        Guard.requireAtLeast(intensity, 0, "intensity");
        Guard.requireAbove(fromIn, 0, "reference distance");
        Guard.requireAbove(toIn, 0, "new distance");
        return intensity * StrictMath.pow(fromIn / toIn, 2);
    }

    /** Time needed at a new distance to lay down the same exposure. */
    public static double timeAtDistance(double minutes, double fromIn, double toIn) {
        Guard.requireAtLeast(minutes, 0, "exposure time");
        Guard.requireAbove(fromIn, 0, "reference distance");
        Guard.requireAbove(toIn, 0, "new distance");
        return minutes * StrictMath.pow(toIn / fromIn, 2);
    }

    /** The distance at which a stated time gives the same exposure. */
    public static double distanceForTime(double minutes, double fromIn, double newMinutes) {
        Guard.requireAbove(minutes, 0, "reference exposure time");
        Guard.requireAbove(fromIn, 0, "reference distance");
        Guard.requireAtLeast(newMinutes, 0, "new exposure time");
        return fromIn * Math.sqrt(newMinutes / minutes);
    }

    public static double curieMinutes(double activityCi, double minutes) {
        Guard.requireAbove(activityCi, 0, "source activity");
        Guard.requireAtLeast(minutes, 0, "exposure time");
        return activityCi * minutes;
    }

    public static double minutesForCurieMinutes(double curieMinutesWanted, double activityCi) {
        Guard.requireAtLeast(curieMinutesWanted, 0, "curie minutes");
        Guard.requireAbove(activityCi, 0, "source activity");
        return curieMinutesWanted / activityCi;
    }

    public static double milliampereMinutes(double milliamps, double minutes) {
        Guard.requireAbove(milliamps, 0, "tube current");
        Guard.requireAtLeast(minutes, 0, "exposure time");
        return milliamps * minutes;
    }

    public static double minutesForMilliampereMinutes(double wanted, double milliamps) {
        Guard.requireAtLeast(wanted, 0, "milliampere minutes");
        Guard.requireAbove(milliamps, 0, "tube current");
        return wanted / milliamps;
    }

    /**
     * Move an exposure from the distance a chart was drawn at to the distance the crew can actually
     * set up at. This is the single most used calculation in the package and the one worth having
     * exactly one copy of.
     */
    public static double scaleExposure(double curieMinutesAt, double chartDistanceIn, double actualDistanceIn) {
        Guard.requireAtLeast(curieMinutesAt, 0, "chart exposure");
        Guard.requireAbove(chartDistanceIn, 0, "chart distance");
        Guard.requireAbove(actualDistanceIn, 0, "actual distance");
        return curieMinutesAt * StrictMath.pow(actualDistanceIn / chartDistanceIn, 2);
    }

    public static boolean equivalentExposure(ShotExposure a, ShotExposure b) {
        return equivalentExposure(a, b, DEFAULT_TOLERANCE);
    }

    /**
     * Two setups that give the same exposure, within a relative tolerance. Useful as a check on a
     * technique sheet somebody has already filled in by hand. Two setups that lay down nothing at
     * all are equivalent rather than a division by zero.
     */
    public static boolean equivalentExposure(ShotExposure a, ShotExposure b, double tolerance) {
        double left = curieMinutes(a.activityCi(), a.minutes()) / StrictMath.pow(a.distanceIn(), 2);
        double right = curieMinutes(b.activityCi(), b.minutes()) / StrictMath.pow(b.distanceIn(), 2);
        Guard.requireAtLeast(tolerance, 0, "tolerance");
        double larger = Math.max(left, right);
        if (larger == 0) {
            return true;
        }
        return Math.abs(left - right) / larger <= tolerance;
    }
}
