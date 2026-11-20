package io.github.rkaushikdev.kerfline.exposure;

import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.OutOfRangeException;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;

/**
 * The X-ray head.
 *
 * <p>An isotope has one energy and the only variables are time and distance. A tube has a third,
 * the kilovoltage, and it does something neither of the others does: it changes the contrast of
 * the radiograph as well as the density. Turning the kV up gets through the wall faster and
 * produces a flatter image, so the technique is always the lowest kV that will penetrate in a
 * workable time rather than the fastest one available.
 *
 * <p>That is why {@link #kilovoltsFor} returns the shop minimum for a thickness and not a
 * midpoint. Going above it is a decision somebody makes and writes down.
 */
public final class Xray {

    private static final double[][] KV_CURVE = {
        {0.125, 100}, {0.25, 120}, {0.5, 150}, {0.75, 180}, {1, 200}, {1.5, 250}, {2, 300}, {3, 400}
    };

    /** Milliampere minutes at the reference distance and density on D7 film. */
    private static final double[][] MAMIN_CURVE = {
        {0.125, 4}, {0.25, 6}, {0.5, 9}, {0.75, 12}, {1, 15}, {1.5, 22}, {2, 30}, {3, 48}
    };

    private static final XrayReference REFERENCE = new XrayReference(40, "D7", 2);
    private static final double MAX_KV = 400;
    private static final double DEFAULT_STEP = 5;

    @FunctionalInterface
    private interface CurveReading {
        double read(double x, double x0, double y0, double x1, double y1);
    }

    private Xray() {}

    public static XrayReference xrayReference() {
        return REFERENCE;
    }

    public static double maximumKilovolts() {
        return MAX_KV;
    }

    /** The walls this head covers. */
    public static ThicknessSpan thicknessRange() {
        return new ThicknessSpan(KV_CURVE[0][0], KV_CURVE[KV_CURVE.length - 1][0]);
    }

    private static double readCurve(double[][] curve, double thicknessIn, CurveReading how) {
        Guard.requireAbove(thicknessIn, 0, "thickness");
        ThicknessSpan range = thicknessRange();
        if (thicknessIn < range.minimumIn() || thicknessIn > range.maximumIn()) {
            throw new OutOfRangeException(
                    Num.text(thicknessIn) + " in is outside the range this head covers",
                    Details.of(
                            "thicknessIn",
                            thicknessIn,
                            "minimumIn",
                            range.minimumIn(),
                            "maximumIn",
                            range.maximumIn()));
        }
        for (int i = 0; i < curve.length - 1; i++) {
            double lowT = curve[i][0];
            double lowV = curve[i][1];
            double highT = curve[i + 1][0];
            double highV = curve[i + 1][1];
            if (thicknessIn >= lowT && thicknessIn <= highT) {
                return how.read(thicknessIn, lowT, lowV, highT, highV);
            }
        }
        throw new OutOfRangeException(
                "that thickness falls between no two points", Details.of("thicknessIn", thicknessIn));
    }

    public static double kilovoltsFor(double thicknessIn) {
        return kilovoltsFor(thicknessIn, DEFAULT_STEP);
    }

    /** The lowest kilovoltage that will penetrate, rounded up to a settable step and capped at the head rating. */
    public static double kilovoltsFor(double thicknessIn, double step) {
        double exact = readCurve(KV_CURVE, thicknessIn, Num::interpolate);
        return Math.min(MAX_KV, Math.ceil(exact / step) * step);
    }

    /** The thickest section a given kilovoltage will reach. */
    public static double thicknessAtKilovolts(double kilovolts) {
        Guard.requireAbove(kilovolts, 0, "kilovoltage");
        double lowest = KV_CURVE[0][1];
        double highest = KV_CURVE[KV_CURVE.length - 1][1];
        if (kilovolts < lowest || kilovolts > highest) {
            throw new OutOfRangeException(
                    Num.text(kilovolts) + " kV is outside the range of this head",
                    Details.of("kilovolts", kilovolts, "lowest", lowest, "highest", highest));
        }
        for (int i = 0; i < KV_CURVE.length - 1; i++) {
            double lowT = KV_CURVE[i][0];
            double lowV = KV_CURVE[i][1];
            double highT = KV_CURVE[i + 1][0];
            double highV = KV_CURVE[i + 1][1];
            if (kilovolts >= lowV && kilovolts <= highV) {
                return Num.round(Num.interpolate(kilovolts, lowV, lowT, highV, highT), 4);
            }
        }
        throw new OutOfRangeException(
                "that kilovoltage falls between no two points", Details.of("kilovolts", kilovolts));
    }

    /** Milliampere minutes at the reference distance. */
    public static double milliampereMinutesFor(double thicknessIn) {
        return Num.round(readCurve(MAMIN_CURVE, thicknessIn, Num::logInterpolate), 3);
    }

    /**
     * A whole tube technique. The kilovoltage is the shop minimum unless one is given, and giving
     * one is recorded so that a flatter than usual radiograph can be traced back to the decision
     * that produced it.
     */
    public static XrayPlan planXray(XrayRequest request) {
        Guard.requireAbove(request.distanceIn(), 0, "distance");
        Guard.requireAbove(request.milliamps(), 0, "tube current");
        double minimum = kilovoltsFor(request.thicknessIn(), request.step());
        double chosen = request.kilovolts().orElse(minimum);
        Guard.requireAtLeast(chosen, minimum, "kilovoltage");

        double base = milliampereMinutesFor(request.thicknessIn());
        double forDistance = Law.scaleExposure(base, REFERENCE.distanceIn(), request.distanceIn());
        double minutes = Law.minutesForMilliampereMinutes(forDistance, request.milliamps());

        return new XrayPlan(
                request.thicknessIn(),
                chosen,
                minimum,
                chosen > minimum,
                request.distanceIn(),
                request.milliamps(),
                Num.round(forDistance, 3),
                Num.round(minutes, 3),
                (int) Math.round(minutes * 60));
    }

    /**
     * Whether a tube can do a job at all. A head rated below what the wall needs is not a slow
     * option, it is the wrong tool, and saying so before the crew mobilises is the point.
     */
    public static HeadCheck headCanReach(double ratedKilovolts, double thicknessIn) {
        Guard.requireAbove(ratedKilovolts, 0, "rated kilovoltage");
        double needed = kilovoltsFor(thicknessIn);
        boolean canReach = ratedKilovolts >= needed;
        return new HeadCheck(needed, ratedKilovolts, canReach, canReach ? 0 : needed - ratedKilovolts);
    }
}
