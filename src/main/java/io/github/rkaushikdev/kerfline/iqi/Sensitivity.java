package io.github.rkaushikdev.kerfline.iqi;

import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;

/**
 * Sensitivity: how small a change in thickness the radiograph can show.
 *
 * <p>A wire indicator answers this directly, because a wire that shows is a thickness change of
 * its own diameter that showed. A hole type plaque does not, because what showed was a hole of one
 * diameter drilled in a plaque of another thickness, and those are two different numbers. The
 * equivalent penetrameter sensitivity puts the two on the same scale:
 *
 * <pre>    EPS = 100 * sqrt(T h / 2) / t</pre>
 *
 * <p>with T the plaque thickness, h the hole diameter and t the thickness the shot went through.
 * Note it is the drilled hole diameter and not the nominal multiple, which is exactly where the
 * ten thousandth floor on the thin plaques comes back to bite.
 *
 * <p>Where a job does not say what it calls for, the shop figure of two percent applies.
 */
public final class Sensitivity {

    private static final double CALLED_FOR_PERCENT = 2;

    private Sensitivity() {}

    public static double calledForSensitivityPercent() {
        return CALLED_FOR_PERCENT;
    }

    /** Sensitivity from a plaque and hole that were actually seen on the film. */
    public static double holeSensitivityPercent(double iqiThicknessIn, double holeDiameterIn, double thicknessIn) {
        Guard.requireAbove(iqiThicknessIn, 0, "indicator thickness");
        Guard.requireAbove(holeDiameterIn, 0, "hole diameter");
        Guard.requireAbove(thicknessIn, 0, "penetrated thickness");
        return (100 * Math.sqrt((iqiThicknessIn * holeDiameterIn) / 2)) / thicknessIn;
    }

    /** The same, looked up from the stocked plaque rather than measured. */
    public static double holeSensitivityFor(int identity, String hole, double thicknessIn) {
        return holeSensitivityPercent(
                HoleType.holeIqiThicknessIn(identity), HoleType.holeDiameterIn(identity, hole), thicknessIn);
    }

    /** A wire that showed is a thickness change of its own diameter that showed. */
    public static double wireSensitivityPercent(double wireDiameterIn, double thicknessIn) {
        Guard.requireAbove(wireDiameterIn, 0, "wire diameter");
        Guard.requireAbove(thicknessIn, 0, "penetrated thickness");
        return (100 * wireDiameterIn) / thicknessIn;
    }

    public static double wireSensitivityFor(int wireNumber, double thicknessIn) {
        return wireSensitivityPercent(Wire.wireDiameterIn(wireNumber), thicknessIn);
    }

    public static SensitivityVerdict judgeHoleSensitivity(int identity, String hole, double thicknessIn) {
        return judgeHoleSensitivity(identity, hole, thicknessIn, CALLED_FOR_PERCENT);
    }

    /**
     * Judge a film. The verdict is on the sensitivity that was achieved against the one the job
     * called for, and it reports both.
     */
    public static SensitivityVerdict judgeHoleSensitivity(
            int identity, String hole, double thicknessIn, double calledFor) {
        Guard.requireAbove(calledFor, 0, "called for sensitivity");
        return verdict(holeSensitivityFor(identity, hole, thicknessIn), calledFor);
    }

    public static SensitivityVerdict judgeWireSensitivity(int wireNumber, double thicknessIn) {
        return judgeWireSensitivity(wireNumber, thicknessIn, CALLED_FOR_PERCENT);
    }

    public static SensitivityVerdict judgeWireSensitivity(int wireNumber, double thicknessIn, double calledFor) {
        Guard.requireAbove(calledFor, 0, "called for sensitivity");
        return verdict(wireSensitivityFor(wireNumber, thicknessIn), calledFor);
    }

    private static SensitivityVerdict verdict(double achieved, double wanted) {
        return new SensitivityVerdict(
                Num.round(achieved, 3), wanted, achieved <= wanted, Num.round(wanted - achieved, 3));
    }

    /**
     * The shorthand a reader writes on the sheet. A 25 plaque with its 2T hole showing is "2-2T"
     * when the plaque is two percent of the thickness, and the first number is the plaque as a
     * percentage rather than a fixed 2.
     */
    public static String sensitivityLabel(int identity, String hole, double thicknessIn) {
        Guard.requireAbove(thicknessIn, 0, "penetrated thickness");
        double plaquePercent = (100 * HoleType.holeIqiThicknessIn(identity)) / thicknessIn;
        return Num.text(Num.round(plaquePercent, 1)) + "-" + hole;
    }

    public static double thicknessHeadroomIn(int identity, String hole, double thicknessIn) {
        return thicknessHeadroomIn(identity, hole, thicknessIn, CALLED_FOR_PERCENT);
    }

    /**
     * How much thinner a wall would have to get before the shot stopped meeting the called for
     * sensitivity. Useful when a crew is deciding whether one technique can cover a run of joints
     * of slightly differing wall. Negative once the shot has stopped passing.
     */
    public static double thicknessHeadroomIn(int identity, String hole, double thicknessIn, double calledFor) {
        Guard.requireAtLeast(thicknessIn, 0, "penetrated thickness");
        double numerator =
                100 * Math.sqrt((HoleType.holeIqiThicknessIn(identity) * HoleType.holeDiameterIn(identity, hole)) / 2);
        double limitIn = numerator / calledFor;
        return Num.round(thicknessIn - limitIn, 4);
    }
}
