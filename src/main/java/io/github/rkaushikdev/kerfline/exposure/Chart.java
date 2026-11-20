package io.github.rkaushikdev.kerfline.exposure;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.errors.OutOfRangeException;
import io.github.rkaushikdev.kerfline.isotope.Catalog;
import io.github.rkaushikdev.kerfline.isotope.Isotope;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposure charts.
 *
 * <p>A chart is a set of measured points tying steel thickness to the exposure that produced a
 * density of 2.0 on the reference film at the reference distance. Every chart here was shot by
 * this shop on a step wedge and they are the numbers the technique sheets are built from.
 *
 * <p>The interpolation is on a log scale, because a chart is a straight line only when exposure is
 * plotted logarithmically. Interpolating linearly between two chart points understates the
 * exposure in the middle of every interval, and the error is worst exactly where the wall
 * thicknesses are most common.
 */
public final class Chart {

    private static final ChartReference REFERENCE = new ChartReference(24, "D7", 2, "LEAD_STANDARD");

    private static final Map<String, List<ChartPoint>> CHARTS = charts();

    private Chart() {}

    private static Map<String, List<ChartPoint>> charts() {
        Map<String, List<ChartPoint>> charts = new LinkedHashMap<>();
        charts.put(
                "IR192",
                points(0.25, 5, 0.5, 8, 0.75, 13, 1, 20, 1.25, 32, 1.5, 50, 1.75, 80, 2, 125, 2.25, 200, 2.5, 320));
        charts.put("SE75", points(0.2, 5, 0.4, 8, 0.6, 13, 0.8, 21, 1, 34, 1.1, 43));
        charts.put("CO60", points(1.5, 30, 2, 45, 3, 100, 4, 220, 5, 480, 6, 1050, 7, 2300, 8, 5000, 9, 11000));
        return Collections.unmodifiableMap(charts);
    }

    private static List<ChartPoint> points(double... pairs) {
        ChartPoint[] points = new ChartPoint[pairs.length / 2];
        for (int i = 0; i < points.length; i++) {
            points[i] = new ChartPoint(pairs[2 * i], pairs[2 * i + 1]);
        }
        return List.of(points);
    }

    /** The conditions the charts were shot at. */
    public static ChartReference chartReference() {
        return REFERENCE;
    }

    public static List<String> chartedIsotopes() {
        return List.copyOf(CHARTS.keySet());
    }

    /**
     * The measured points of a source's chart, thinnest first.
     *
     * @throws NotFoundException when the shop has no chart for that source
     */
    public static List<ChartPoint> chartPoints(String isotopeId) {
        Isotope source = Catalog.isotope(isotopeId);
        List<ChartPoint> points = CHARTS.get(source.id());
        if (points == null) {
            throw new NotFoundException(
                    "no exposure chart for " + source.label(),
                    Details.of("isotope", source.id(), "known", chartedIsotopes()));
        }
        return points;
    }

    public static ThicknessSpan chartRange(String isotopeId) {
        List<ChartPoint> points = chartPoints(isotopeId);
        return new ThicknessSpan(
                points.get(0).thicknessIn(), points.get(points.size() - 1).thicknessIn());
    }

    /** True when the chart covers the thickness; a thickness that is not a number is simply not covered. */
    public static boolean chartCovers(String isotopeId, double thicknessIn) {
        if (!Double.isFinite(thicknessIn)) {
            return false;
        }
        ThicknessSpan range = chartRange(isotopeId);
        return thicknessIn >= range.minimumIn() && thicknessIn <= range.maximumIn();
    }

    /**
     * Read a chart at a thickness, in curie minutes. Off either end this refuses rather than
     * extrapolating: the curve bends at both ends and a technique built on an extrapolated chart
     * point is a reshoot waiting to happen.
     *
     * @throws OutOfRangeException when the thickness is off the chart
     */
    public static double chartExposure(String isotopeId, double thicknessIn) {
        if (!Double.isFinite(thicknessIn) || thicknessIn <= 0) {
            throw new BadInputException("thickness must be a positive number", Details.of("got", thicknessIn));
        }
        List<ChartPoint> points = chartPoints(isotopeId);
        ThicknessSpan range = chartRange(isotopeId);
        if (thicknessIn < range.minimumIn() || thicknessIn > range.maximumIn()) {
            throw new OutOfRangeException(
                    Num.text(thicknessIn) + " in is off the " + isotopeId + " chart",
                    Details.of(
                            "thicknessIn",
                            thicknessIn,
                            "minimumIn",
                            range.minimumIn(),
                            "maximumIn",
                            range.maximumIn()));
        }
        for (int i = 0; i < points.size() - 1; i++) {
            ChartPoint low = points.get(i);
            ChartPoint high = points.get(i + 1);
            if (thicknessIn >= low.thicknessIn() && thicknessIn <= high.thicknessIn()) {
                return Num.logInterpolate(
                        thicknessIn, low.thicknessIn(), low.curieMinutes(), high.thicknessIn(), high.curieMinutes());
            }
        }
        throw new OutOfRangeException(
                Num.text(thicknessIn) + " in falls between no two chart points",
                Details.of("thicknessIn", thicknessIn));
    }

    /**
     * The thickness a stated exposure would cover, which is the question asked when a crew has a
     * fixed window and wants to know what it can shoot in it.
     */
    public static double chartThickness(String isotopeId, double curieMinutes) {
        if (!Double.isFinite(curieMinutes) || curieMinutes <= 0) {
            throw new BadInputException("exposure must be a positive number", Details.of("got", curieMinutes));
        }
        List<ChartPoint> points = chartPoints(isotopeId);
        ChartPoint first = points.get(0);
        ChartPoint last = points.get(points.size() - 1);
        if (curieMinutes < first.curieMinutes() || curieMinutes > last.curieMinutes()) {
            throw new OutOfRangeException(
                    Num.text(curieMinutes) + " curie minutes is off the " + isotopeId + " chart",
                    Details.of(
                            "curieMinutes",
                            curieMinutes,
                            "minimum",
                            first.curieMinutes(),
                            "maximum",
                            last.curieMinutes()));
        }
        for (int i = 0; i < points.size() - 1; i++) {
            ChartPoint low = points.get(i);
            ChartPoint high = points.get(i + 1);
            if (curieMinutes >= low.curieMinutes() && curieMinutes <= high.curieMinutes()) {
                double span = StrictMath.log(high.curieMinutes()) - StrictMath.log(low.curieMinutes());
                double into = StrictMath.log(curieMinutes) - StrictMath.log(low.curieMinutes());
                double width = high.thicknessIn() - low.thicknessIn();
                return Num.round(low.thicknessIn() + (into / span) * width, 4);
            }
        }
        throw new OutOfRangeException(
                "that exposure falls between no two chart points", Details.of("curieMinutes", curieMinutes));
    }

    /**
     * The equivalent half value layer the chart implies over its own span. A chart whose implied
     * layer wanders far from the published narrow beam figure has either been mis-measured or is
     * carrying a lot of scatter, and this is the cheapest way to notice.
     */
    public static double impliedHalfValueLayerIn(String isotopeId) {
        List<ChartPoint> points = chartPoints(isotopeId);
        ChartPoint first = points.get(0);
        ChartPoint last = points.get(points.size() - 1);
        double doublings = Num.log2(last.curieMinutes() / first.curieMinutes());
        return Num.round((last.thicknessIn() - first.thicknessIn()) / doublings, 4);
    }
}
