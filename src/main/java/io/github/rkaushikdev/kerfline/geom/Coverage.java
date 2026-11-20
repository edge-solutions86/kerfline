package io.github.rkaushikdev.kerfline.geom;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * How much of a circumferential weld one shot actually covers.
 *
 * <p>Straight under the source the beam crosses the wall square on. Move round the pipe and the
 * same beam crosses at an angle, so it passes through more metal and the film there is lighter
 * than it should be. The technique procedure caps that increase, and the cap is what decides how
 * many shots go round the joint.
 *
 * <p>With the pipe centre at the origin, outer radius R and the source at distance L from the
 * centre, a point at angle phi off the source axis sees the beam arrive at an incidence angle b
 * where
 *
 * <pre>
 *     cos b = (L cos phi - R) / sqrt(R^2 + L^2 - 2 R L cos phi)
 * </pre>
 *
 * and the ray crosses about t / cos b of wall. The tangent point sits at phi = acos(R / L); past
 * there the beam misses the pipe altogether.
 */
public final class Coverage {

    private static final double DEG = 180 / Math.PI;
    private static final int BISECTION_STEPS = 80;
    private static final double SHORT_OF_TANGENT = 0.999999;

    private Coverage() {}

    /**
     * The angle the beam crosses the wall at, a given offset round the pipe from the point under
     * the source. The sign of the offset does not matter.
     *
     * @throws BadInputException when the offset is past the tangent point
     */
    public static double incidenceAngleDegrees(double odIn, double sourceToObjectIn, double offsetDegrees) {
        Geometry geometry = geometry(odIn, sourceToObjectIn);
        Guard.requireNumber(offsetDegrees, "offset in degrees");
        double phi = Math.abs(offsetDegrees) / DEG;
        double tangent = StrictMath.acos(geometry.radius() / geometry.axis());
        if (phi >= tangent) {
            throw new BadInputException(
                    "that offset is past the tangent point, the beam misses the pipe",
                    Details.of("offsetDegrees", offsetDegrees, "tangentDegrees", Num.round(tangent * DEG, 3)));
        }
        double radius = geometry.radius();
        double axis = geometry.axis();
        double numerator = axis * StrictMath.cos(phi) - radius;
        double denominator = Math.sqrt(radius * radius + axis * axis - 2 * radius * axis * StrictMath.cos(phi));
        return StrictMath.acos(numerator / denominator) * DEG;
    }

    /** How much longer the path through the wall is than the wall itself. */
    public static double thicknessRatio(double odIn, double sourceToObjectIn, double offsetDegrees) {
        double beta = incidenceAngleDegrees(odIn, sourceToObjectIn, offsetDegrees);
        return 1 / StrictMath.cos(beta / DEG);
    }

    /** The offset at which the beam grazes the pipe and stops entering it. */
    public static double tangentOffsetDegrees(double odIn, double sourceToObjectIn) {
        Geometry geometry = geometry(odIn, sourceToObjectIn);
        return StrictMath.acos(geometry.radius() / geometry.axis()) * DEG;
    }

    /**
     * The largest offset either side of the film centre that stays inside the allowed thickness
     * increase. Solved by bisection because the relation is not worth inverting by hand and the
     * tangent point gives a hard bracket.
     */
    public static double maxHalfAngleDegrees(double odIn, double sourceToObjectIn, double maxThicknessRatio) {
        Guard.requireAbove(maxThicknessRatio, 1, "maximum thickness ratio");
        double tangent = tangentOffsetDegrees(odIn, sourceToObjectIn);
        DoubleUnaryOperator at = offset -> thicknessRatio(odIn, sourceToObjectIn, offset);

        double low = 0;
        double high = tangent * SHORT_OF_TANGENT;
        if (at.applyAsDouble(high) <= maxThicknessRatio) {
            return Num.round(high, 4);
        }
        for (int i = 0; i < BISECTION_STEPS; i++) {
            double mid = (low + high) / 2;
            if (at.applyAsDouble(mid) <= maxThicknessRatio) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return Num.round(low, 4);
    }

    /**
     * Shots needed to walk the whole joint. Each shot covers twice the half angle, and the count is
     * rounded up because a fraction of a shot leaves a gap in the coverage that nobody signs for.
     */
    public static int exposuresForCircumference(double odIn, double sourceToObjectIn, double maxThicknessRatio) {
        double half = maxHalfAngleDegrees(odIn, sourceToObjectIn, maxThicknessRatio);
        if (half <= 0) {
            throw new BadInputException(
                    "no offset meets that thickness ratio", Details.of("maxThicknessRatio", maxThicknessRatio));
        }
        return (int) Math.max(1, Math.ceil(360 / (2 * half)));
    }

    public static List<PlannedShot> shotPlan(double odIn, double sourceToObjectIn, double maxThicknessRatio) {
        return shotPlan(odIn, sourceToObjectIn, maxThicknessRatio, 0);
    }

    /**
     * The shot plan: where each film centre sits, evenly spread from the start position, given as
     * both degrees and the clock position that goes on the lead tape.
     */
    public static List<PlannedShot> shotPlan(
            double odIn, double sourceToObjectIn, double maxThicknessRatio, double startDegrees) {
        Guard.requireNumber(startDegrees, "start position in degrees");
        int count = exposuresForCircumference(odIn, sourceToObjectIn, maxThicknessRatio);
        double step = 360.0 / count;
        double half = maxHalfAngleDegrees(odIn, sourceToObjectIn, maxThicknessRatio);
        List<PlannedShot> plan = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            double centre = startDegrees + index * step;
            plan.add(new PlannedShot(
                    index + 1,
                    Num.round(((centre % 360) + 360) % 360, 3),
                    Pipe.clockFromDegrees(centre),
                    Num.round(Math.min(step, 2 * half), 3),
                    Num.round(Math.max(0, 2 * half - step), 3)));
        }
        return List.copyOf(plan);
    }

    /**
     * The source to object distance at which the joint is covered in a given number of shots. A
     * source cranked out far enough behaves almost like a parallel beam, so some counts are
     * unreachable on a real pipe and this refuses them, which is more useful than returning a
     * number nobody can set up.
     *
     * @throws BadInputException when the count is below one or cannot be met at any distance
     */
    public static double requiredDistanceForShots(double odIn, int shots, double maxThicknessRatio) {
        if (shots < 1) {
            throw new BadInputException("shot count must be a whole number of shots", Details.of("shots", shots));
        }
        double wanted = 360.0 / (2 * shots);
        double low = 0.01;
        double high = odIn * 1000;
        DoubleUnaryOperator covers = sod -> maxHalfAngleDegrees(odIn, sod, maxThicknessRatio);
        if (covers.applyAsDouble(high) < wanted) {
            throw new BadInputException(
                    "that many shots cannot cover the joint at any distance",
                    Details.of("shots", shots, "maxThicknessRatio", maxThicknessRatio));
        }
        for (int i = 0; i < BISECTION_STEPS; i++) {
            double mid = (low + high) / 2;
            if (covers.applyAsDouble(mid) >= wanted) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return Num.round(high, 3);
    }

    private record Geometry(double radius, double axis) {}

    private static Geometry geometry(double odIn, double sourceToObjectIn) {
        double radius = Pipe.outsideRadius(odIn);
        Guard.requireAbove(sourceToObjectIn, 0, "source to object distance");
        return new Geometry(radius, radius + sourceToObjectIn);
    }
}
