package io.github.rkaushikdev.kerfline.density;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.ConflictException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A set of density readings off one film.
 *
 * <p>The readings are only worth what the instrument that took them is worth, so a set carries
 * the calibration it was taken under and refuses to be built on an instrument that was out of
 * calibration on the day. That is a stricter rule than it sounds: a densitometer whose ninety days
 * ran out yesterday produces readings that look exactly like good ones, and the only thing that
 * separates them is this check.
 *
 * <p>The reading at the indicator is kept apart from the readings through the area of interest,
 * because the first is the reference the second is judged against and mixing them into one list
 * loses that.
 */
public final class Reading {

    private static final double DEFAULT_TOLERANCE = 0.05;

    private Reading() {}

    /**
     * Build a reading set.
     *
     * @throws ConflictException when the calibration on file belongs to another instrument or was
     *     not current on the day
     */
    public static ReadingSet makeReadingSet(ReadingSetSpec spec) {
        Dates.requireDay(spec.day(), "reading date");
        String film = Guard.requireLabel(spec.film(), "a reading set names the film it came off");
        String instrument = Guard.requireLabel(spec.instrument(), "a reading set names the instrument");
        List<AreaReading> area = Guard.requireArray(spec.area(), "area readings");
        Guard.requireAtLeast(spec.indicatorDensity(), 0, "indicator density");

        if (spec.calibration().isPresent()) {
            CalibrationRecord calibration = spec.calibration().get();
            if (!calibration.instrument().equals(instrument)) {
                throw new ConflictException(
                        "the calibration on file is for a different instrument",
                        Details.of("instrument", instrument, "calibration", calibration.instrument()));
            }
            if (!Stepwedge.calibrationCurrent(calibration, spec.day())) {
                throw new ConflictException(
                        instrument + " was not in calibration on " + spec.day(),
                        Details.of("instrument", instrument, "expires", calibration.expires(), "day", spec.day()));
            }
        }

        List<AreaReading> readings = new ArrayList<>();
        for (int index = 0; index < area.size(); index++) {
            AreaReading reading = area.get(index);
            OptionalDouble density = reading == null ? OptionalDouble.empty() : reading.density();
            if (density.isEmpty() || !(density.getAsDouble() > 0)) {
                throw new BadInputException(
                        "area reading " + (index + 1) + " has no usable density", Details.of("got", reading));
            }
            readings.add(new AreaReading(reading.at(), OptionalDouble.of(Num.round(density.getAsDouble(), 2))));
        }

        return new ReadingSet(
                film,
                spec.day(),
                instrument,
                spec.calibration().isPresent(),
                spec.sourceKind(),
                Num.round(spec.indicatorDensity(), 2),
                readings);
    }

    /** The shape of the film, as numbers a report can print; the reference stands in for an unread area. */
    public static DensityProfile profile(ReadingSet set) {
        if (set.area().isEmpty()) {
            return new DensityProfile(0, set.indicatorDensity(), set.indicatorDensity(), set.indicatorDensity(), 0);
        }
        List<Double> values = set.area().stream()
                .map(reading -> reading.density().getAsDouble())
                .toList();
        double lightest = Num.minOf(values);
        double darkest = Num.maxOf(values);
        return new DensityProfile(
                values.size(), lightest, darkest, Num.round(Num.mean(values), 2), Num.round(darkest - lightest, 2));
    }

    /** Judge the set: the reference against the code, then the area against it. */
    public static SetJudgement judgeSet(ReadingSet set) {
        DensityJudgement reference = Limits.judgeDensity(set.indicatorDensity(), set.sourceKind());
        Optional<AreaJudgement> area = Variation.judgeAreaIfAny(set.area(), set.indicatorDensity());
        boolean pass = reference.pass() && area.map(AreaJudgement::pass).orElse(true);
        return new SetJudgement(set.film(), reference, area, pass, set.calibrated());
    }

    /** The readings that put the film outside the variation band. */
    public static List<JudgedReading> failingReadings(ReadingSet set) {
        return judgeSet(set).area().map(AreaJudgement::failures).orElse(List.of());
    }

    public static SetComparison compareSets(ReadingSet a, ReadingSet b) {
        return compareSets(a, b, DEFAULT_TOLERANCE);
    }

    /**
     * Compare two sets of the same film taken on different instruments. Used when a client
     * disputes a reading, which is the only time anybody does it.
     */
    public static SetComparison compareSets(ReadingSet a, ReadingSet b, double tolerance) {
        Guard.requireAtLeast(tolerance, 0, "tolerance");
        if (!a.film().equals(b.film())) {
            throw new BadInputException(
                    "those two sets are off different films", Details.of("a", a.film(), "b", b.film()));
        }
        double difference = Num.round(Math.abs(a.indicatorDensity() - b.indicatorDensity()), 3);
        return new SetComparison(
                a.film(), List.of(a.instrument(), b.instrument()), difference, difference <= tolerance);
    }
}
