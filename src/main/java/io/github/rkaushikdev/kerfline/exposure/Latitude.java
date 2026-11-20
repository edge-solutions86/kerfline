package io.github.rkaushikdev.kerfline.exposure;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Film latitude.
 *
 * <p>A single shot covers a band of thickness, not a single thickness. The thickest part of the
 * section lands at the light end of the readable density range and the thinnest at the dark end,
 * and how wide that band is falls out of two things already in this package: how fast the exposure
 * climbs with thickness, which is the chart, and how fast density climbs with exposure, which is
 * the film gradient.
 *
 * <pre>    dD = gradient * log10(2) * dt / HVL</pre>
 *
 * <p>so the thickness a density band covers is
 *
 * <pre>    dt = dD * HVL / (gradient * log10(2))</pre>
 *
 * <p>A fine grained film has a steeper gradient, so it has LESS latitude, not more. That is the
 * trade nobody remembers: the film that resolves the most covers the least section in one shot.
 *
 * <p>Every method takes an optional density span; without one it uses the whole readable range.
 */
public final class Latitude {

    private static final double LOG10_2 = StrictMath.log10(2);
    private static final List<String> FINEST_FIRST = List.of("D2", "D3", "D4", "D5", "D7", "D8");

    private Latitude() {}

    public static double latitudeIn(String isotopeId, String filmId) {
        return latitude(isotopeId, filmId, OptionalDouble.empty());
    }

    /** The thickness band one shot covers on a film, at a source, over a stated density span. */
    public static double latitudeIn(String isotopeId, String filmId, double densitySpan) {
        return latitude(isotopeId, filmId, OptionalDouble.of(densitySpan));
    }

    private static double latitude(String isotopeId, String filmId, OptionalDouble densitySpan) {
        double hvl = Chart.impliedHalfValueLayerIn(isotopeId);
        double gradient = Aim.filmGradient(filmId);
        double span = densitySpan.orElseGet(Latitude::readableSpan);
        Guard.requireAbove(span, 0, "density span");
        return Num.round((span * hvl) / (gradient * LOG10_2), 4);
    }

    private static double readableSpan() {
        DensityRange range = Aim.readableDensityRange();
        return range.maximum() - range.minimum();
    }

    public static CoveredBand coveredBand(String isotopeId, String filmId, double thicknessIn) {
        return band(isotopeId, filmId, thicknessIn, OptionalDouble.empty());
    }

    /** The band of thickness a shot aimed at one thickness actually covers; never below nothing. */
    public static CoveredBand coveredBand(String isotopeId, String filmId, double thicknessIn, double densitySpan) {
        return band(isotopeId, filmId, thicknessIn, OptionalDouble.of(densitySpan));
    }

    private static CoveredBand band(String isotopeId, String filmId, double thicknessIn, OptionalDouble densitySpan) {
        Guard.requireAbove(thicknessIn, 0, "thickness");
        double width = latitude(isotopeId, filmId, densitySpan);
        return new CoveredBand(
                thicknessIn,
                width,
                Num.round(Math.max(0, thicknessIn - width / 2), 4),
                Num.round(thicknessIn + width / 2, 4));
    }

    public static RangeCover coversRange(String isotopeId, String filmId, double minimumIn, double maximumIn) {
        return cover(isotopeId, filmId, minimumIn, maximumIn, OptionalDouble.empty());
    }

    /** Whether one shot can cover a section that varies in thickness. */
    public static RangeCover coversRange(
            String isotopeId, String filmId, double minimumIn, double maximumIn, double densitySpan) {
        return cover(isotopeId, filmId, minimumIn, maximumIn, OptionalDouble.of(densitySpan));
    }

    private static RangeCover cover(
            String isotopeId, String filmId, double minimumIn, double maximumIn, OptionalDouble densitySpan) {
        Guard.requireAbove(minimumIn, 0, "thinnest section");
        Guard.requireAbove(maximumIn, 0, "thickest section");
        if (maximumIn < minimumIn) {
            throw new BadInputException(
                    "the thickest section is thinner than the thinnest",
                    Details.of("minimumIn", minimumIn, "maximumIn", maximumIn));
        }
        double needed = Num.round(maximumIn - minimumIn, 4);
        double available = latitude(isotopeId, filmId, densitySpan);
        boolean covers = needed <= available;
        return new RangeCover(needed, available, covers, covers ? 0 : Num.round(needed - available, 4));
    }

    public static DoubleLoad doubleLoadBand(String isotopeId, String slowFilmId, String fastFilmId) {
        return doubleLoad(isotopeId, slowFilmId, fastFilmId, OptionalDouble.empty());
    }

    /**
     * Two films in one cassette, a slow one and a fast one. The pair covers a wider band than
     * either alone, because the thin end is read on the slow film and the thick end on the fast
     * one, and the widening is the ratio of their speeds expressed as density.
     */
    public static DoubleLoad doubleLoadBand(
            String isotopeId, String slowFilmId, String fastFilmId, double densitySpan) {
        return doubleLoad(isotopeId, slowFilmId, fastFilmId, OptionalDouble.of(densitySpan));
    }

    private static DoubleLoad doubleLoad(
            String isotopeId, String slowFilmId, String fastFilmId, OptionalDouble densitySpan) {
        Film slow = FilmClass.film(slowFilmId);
        Film fast = FilmClass.film(fastFilmId);
        if (FilmClass.filmSpeed(fast.id()) <= FilmClass.filmSpeed(slow.id())) {
            throw new BadInputException(
                    "the second film has to be faster than the first",
                    Details.of("slow", slow.id(), "fast", fast.id()));
        }
        double hvl = Chart.impliedHalfValueLayerIn(isotopeId);
        double slowBand = latitude(isotopeId, slow.id(), densitySpan);
        double fastBand = latitude(isotopeId, fast.id(), densitySpan);
        double offset = Num.round(
                (StrictMath.log10(FilmClass.filmSpeed(fast.id()) / FilmClass.filmSpeed(slow.id())) * hvl) / LOG10_2, 4);
        return new DoubleLoad(
                slow.id(), fast.id(), slowBand, fastBand, offset, Num.round(Math.max(slowBand, fastBand) + offset, 4));
    }

    public static List<FilmLatitude> filmsCovering(String isotopeId, double minimumIn, double maximumIn) {
        return covering(isotopeId, minimumIn, maximumIn, OptionalDouble.empty());
    }

    /**
     * The films that will cover a section in one shot, finest first. Finest is the right order
     * because the finest film that covers the range is the one that shows the most, and latitude
     * is a constraint to satisfy rather than a quantity to maximise.
     */
    public static List<FilmLatitude> filmsCovering(
            String isotopeId, double minimumIn, double maximumIn, double densitySpan) {
        return covering(isotopeId, minimumIn, maximumIn, OptionalDouble.of(densitySpan));
    }

    private static List<FilmLatitude> covering(
            String isotopeId, double minimumIn, double maximumIn, OptionalDouble densitySpan) {
        double needed = maximumIn - minimumIn;
        return FINEST_FIRST.stream()
                .map(filmId -> new FilmLatitude(filmId, latitude(isotopeId, filmId, densitySpan)))
                .filter(entry -> entry.latitudeIn() >= needed)
                .toList();
    }
}
