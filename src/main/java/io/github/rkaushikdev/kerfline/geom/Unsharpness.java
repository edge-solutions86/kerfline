package io.github.rkaushikdev.kerfline.geom;

import io.github.rkaushikdev.kerfline.util.Band;
import io.github.rkaushikdev.kerfline.util.BandTable;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.List;

/**
 * Geometric unsharpness. The source is not a point, so every edge in the weld is projected onto
 * the film as a band rather than as a line:
 *
 * <pre>
 *     Ug = F t / d
 * </pre>
 *
 * with F the source size, t the distance from the back of the object to the film, and d the
 * distance from the source to the front of the object. The limit is banded on material thickness
 * and the bands are wider than people expect, which is why the check is worth doing rather than
 * eyeballing.
 */
public final class Unsharpness {

    private static final BandTable<Double> LIMIT_TABLE = Band.bandTable(
            List.of(
                    Band.upTo(2, 0.02),
                    Band.upTo(3, 0.03),
                    Band.upTo(4, 0.04),
                    Band.upTo(Double.POSITIVE_INFINITY, 0.07)),
            "unsharpness limit");

    private Unsharpness() {}

    /**
     * The limit in inches for a material thickness in inches. The thickness meant here is the one
     * the image quality indicator is chosen on, not the sum of both walls on a double wall shot,
     * and getting that wrong moves the limit by a whole band on anything over an inch.
     */
    public static double unsharpnessLimit(double materialThicknessIn) {
        Guard.requireAbove(materialThicknessIn, 0, "material thickness");
        return Band.bandValue(LIMIT_TABLE, materialThicknessIn);
    }

    /** The limit bands, lowest first, for the technique sheet appendix. */
    public static List<UnsharpnessBand> unsharpnessBands() {
        return LIMIT_TABLE.rows().stream()
                .map(row -> new UnsharpnessBand(row.over(), row.upTo(), row.value()))
                .toList();
    }

    public static double geometricUnsharpness(double sourceSizeIn, double objectToFilmIn, double sourceToObjectIn) {
        Guard.requireAbove(sourceSizeIn, 0, "source size");
        Guard.requireAtLeast(objectToFilmIn, 0, "object to film distance");
        Guard.requireAbove(sourceToObjectIn, 0, "source to object distance");
        return (sourceSizeIn * objectToFilmIn) / sourceToObjectIn;
    }

    /**
     * The shortest source to object distance that still meets a limit. This is the number a crew
     * actually wants, because the source size and the standoff are fixed by the camera and the
     * pipe and the only free variable is how far back the crank goes.
     */
    public static double minimumSourceToObject(double sourceSizeIn, double objectToFilmIn, double limitIn) {
        Guard.requireAbove(sourceSizeIn, 0, "source size");
        Guard.requireAtLeast(objectToFilmIn, 0, "object to film distance");
        Guard.requireAbove(limitIn, 0, "unsharpness limit");
        return (sourceSizeIn * objectToFilmIn) / limitIn;
    }

    /**
     * Evaluate a setup. Returns the computed unsharpness, the limit it was judged against, and how
     * much room is left, so a technique sheet can print all three rather than a bare pass.
     */
    public static UnsharpnessCheck checkUnsharpness(UnsharpnessSetup setup) {
        double ug = geometricUnsharpness(setup.sourceSizeIn(), setup.objectToFilmIn(), setup.sourceToObjectIn());
        double limitIn = unsharpnessLimit(setup.materialThicknessIn());
        double minimum = minimumSourceToObject(setup.sourceSizeIn(), setup.objectToFilmIn(), limitIn);
        boolean pass = ug <= limitIn;
        return new UnsharpnessCheck(
                Num.round(ug, 4),
                limitIn,
                pass,
                Num.round(limitIn - ug, 4),
                Num.round(minimum, 2),
                pass ? 0 : Num.round(minimum - setup.sourceToObjectIn(), 2));
    }
}
