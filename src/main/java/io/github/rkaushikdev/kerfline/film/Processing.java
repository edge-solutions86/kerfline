package io.github.rkaushikdev.kerfline.film;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.OutOfRangeException;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.List;

/**
 * The darkroom.
 *
 * <p>Development is a time and a temperature together and neither means anything on its own. Five
 * minutes is right at sixty eight degrees and badly wrong at seventy eight, and the film that
 * comes out of the wrong one is not merely light or dark: it has the wrong contrast, which is
 * worse, because a light film is obviously light and a low contrast film just quietly hides
 * things.
 *
 * <p>The temperature band this shop will process in is sixty to eighty. Outside it the developer
 * either will not work or works too fast to control by hand, and the answer is to fix the darkroom
 * rather than to adjust the clock.
 */
public final class Processing {

    private static final List<CurvePoint> CURVE = List.of(
            new CurvePoint(60, 8),
            new CurvePoint(65, 6),
            new CurvePoint(68, 5),
            new CurvePoint(70, 4.5),
            new CurvePoint(72, 4),
            new CurvePoint(75, 3.5),
            new CurvePoint(78, 3),
            new CurvePoint(80, 2.75));

    private static final double REFERENCE_F = 68;
    private static final double TOLERANCE_MINUTES = 0.25;
    private static final double ML_PER_HUNDRED = 75;

    private Processing() {}

    /** The temperature the shop's five minute development is quoted at. */
    public static double referenceTemperatureF() {
        return REFERENCE_F;
    }

    public static TemperatureBand temperatureBandF() {
        return new TemperatureBand(
                CURVE.get(0).temperatureF(), CURVE.get(CURVE.size() - 1).temperatureF());
    }

    /** The curve the developer is run to, coldest bath first. */
    public static List<CurvePoint> timeTemperatureCurve() {
        return CURVE;
    }

    /**
     * The development time for a bath at a temperature, read off the curve and interpolated
     * between its points.
     *
     * @throws OutOfRangeException for a bath outside the band this shop develops in
     */
    public static double developmentMinutes(double temperatureF) {
        Guard.requireAbove(temperatureF, 0, "developer temperature");
        TemperatureBand band = temperatureBandF();
        if (temperatureF < band.minimumF() || temperatureF > band.maximumF()) {
            throw new OutOfRangeException(
                    Num.text(temperatureF) + " F is outside the band this shop develops in",
                    Details.of("temperatureF", temperatureF, "minimumF", band.minimumF(), "maximumF", band.maximumF()));
        }
        for (int i = 0; i < CURVE.size() - 1; i++) {
            CurvePoint low = CURVE.get(i);
            CurvePoint high = CURVE.get(i + 1);
            if (temperatureF >= low.temperatureF() && temperatureF <= high.temperatureF()) {
                return Num.round(
                        Num.interpolate(
                                temperatureF, low.temperatureF(), low.minutes(), high.temperatureF(), high.minutes()),
                        2);
            }
        }
        throw new OutOfRangeException(
                "that temperature falls between no two points", Details.of("temperatureF", temperatureF));
    }

    /**
     * The temperature at which a wanted time is the right time.
     *
     * @throws OutOfRangeException for a time off the curve
     */
    public static double temperatureForMinutes(double minutes) {
        Guard.requireAbove(minutes, 0, "development time");
        double fastest = CURVE.get(CURVE.size() - 1).minutes();
        double slowest = CURVE.get(0).minutes();
        if (minutes < fastest || minutes > slowest) {
            throw new OutOfRangeException(
                    Num.text(minutes) + " minutes is off the time temperature curve",
                    Details.of("minutes", minutes, "fastest", fastest, "slowest", slowest));
        }
        for (int i = 0; i < CURVE.size() - 1; i++) {
            CurvePoint low = CURVE.get(i);
            CurvePoint high = CURVE.get(i + 1);
            if (minutes <= low.minutes() && minutes >= high.minutes()) {
                return Num.round(
                        Num.interpolate(
                                minutes, low.minutes(), low.temperatureF(), high.minutes(), high.temperatureF()),
                        2);
            }
        }
        throw new OutOfRangeException("that time falls between no two points", Details.of("minutes", minutes));
    }

    public static CycleJudgement judgeCycle(double temperatureF, double minutes) {
        return judgeCycle(temperatureF, minutes, TOLERANCE_MINUTES);
    }

    /**
     * Judge a processing cycle against the curve, allowing the tolerance either side. The verdict
     * names which way it went, because an overdeveloped film and an underdeveloped one look
     * nothing alike on the light box and get confused with exposure faults in opposite directions.
     */
    public static CycleJudgement judgeCycle(double temperatureF, double minutes, double tolerance) {
        Guard.requireAtLeast(minutes, 0, "development time");
        Guard.requireAtLeast(tolerance, 0, "tolerance");
        double wanted = developmentMinutes(temperatureF);
        double difference = Num.round(minutes - wanted, 2);
        DevelopmentVerdict verdict = DevelopmentVerdict.CORRECT;
        if (difference > tolerance) {
            verdict = DevelopmentVerdict.OVERDEVELOPED;
        } else if (difference < -tolerance) {
            verdict = DevelopmentVerdict.UNDERDEVELOPED;
        }
        return new CycleJudgement(
                temperatureF,
                minutes,
                wanted,
                difference,
                verdict,
                verdict == DevelopmentVerdict.CORRECT,
                verdict.effect());
    }

    public static double replenisherMl(double areaSquareInches) {
        return replenisherMl(areaSquareInches, ML_PER_HUNDRED);
    }

    /**
     * Replenisher to add for a run of film. Quoted per hundred square inches because that is how
     * the tank is dosed, and it does not scale with the number of films, only with their area.
     */
    public static double replenisherMl(double areaSquareInches, double mlPerHundred) {
        Guard.requireAtLeast(areaSquareInches, 0, "film area");
        Guard.requireAbove(mlPerHundred, 0, "replenishment rate");
        return Num.round((areaSquareInches / 100) * mlPerHundred, 1);
    }

    /**
     * The area a batch of films comes to.
     *
     * @throws BadInputException for a film without both a width and a length above zero
     */
    public static double batchAreaSquareInches(List<FilmSheet> films) {
        Guard.requireArray(films, "films");
        double total = 0;
        for (FilmSheet film : films) {
            if (film == null || !(film.widthIn() > 0) || !(film.lengthIn() > 0)) {
                throw new BadInputException("every film needs a width and a length", Details.of("got", film));
            }
            total = total + film.widthIn() * film.lengthIn();
        }
        return Num.round(total, 2);
    }

    /** The whole darkroom cycle, for the log sheet. */
    public static CycleRecord cycleRecord(CycleInput input) {
        CycleJudgement development = judgeCycle(input.temperatureF(), input.minutes());
        double area = batchAreaSquareInches(input.films());
        return new CycleRecord(
                development,
                input.films().size(),
                area,
                replenisherMl(area),
                input.fixMinutes(),
                input.washMinutes(),
                Num.round(input.minutes() + input.fixMinutes() + input.washMinutes(), 2));
    }
}
