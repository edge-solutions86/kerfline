package io.github.rkaushikdev.kerfline.density;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.List;

/**
 * Densitometer calibration against a step wedge comparison film.
 *
 * <p>The comparison film carries a strip of steps of certified density. The densitometer reads
 * each one and every reading has to land within five hundredths of the certificate. That
 * tolerance is on each step and not on the average, which matters: an instrument reading high at
 * the light end and low at the dark end averages to nothing and is still unusable.
 *
 * <p>The calibration lapses after ninety days. It also lapses the moment the instrument is dropped
 * or repaired, which is not something this class can know about, so the record carries a reason
 * and a caller can retire a calibration early.
 */
public final class Stepwedge {

    private static final double TOLERANCE = 0.05;
    private static final int VALID_DAYS = 90;
    private static final double SLACK = 1e-9;

    private Stepwedge() {}

    public static double calibrationTolerance() {
        return TOLERANCE;
    }

    public static int calibrationValidDays() {
        return VALID_DAYS;
    }

    public static StepComparison compareSteps(List<Double> readings, List<Double> certified) {
        return compareSteps(readings, certified, TOLERANCE);
    }

    /**
     * Compare a set of readings against the certified step densities. Both lists are in step order
     * and have to be the same length, because a reading with no step to compare it against is not
     * a calibration point.
     */
    public static StepComparison compareSteps(List<Double> readings, List<Double> certified, double tolerance) {
        Guard.requireArray(readings, "readings");
        Guard.requireArray(certified, "certified densities");
        Guard.requirePositive(tolerance, "tolerance");
        if (readings.isEmpty()) {
            throw new BadInputException("a calibration needs at least one step", Details.of("readings", readings));
        }
        if (readings.size() != certified.size()) {
            throw new BadInputException(
                    "there is a reading without a certified step to match it",
                    Details.of("readings", readings.size(), "certified", certified.size()));
        }

        List<StepCheck> steps = new ArrayList<>();
        for (int index = 0; index < readings.size(); index++) {
            Double reading = readings.get(index);
            Double expected = certified.get(index);
            if (reading == null || expected == null) {
                throw new BadInputException(
                        "step " + (index + 1) + " is not a pair of numbers",
                        Details.of("reading", reading, "expected", expected));
            }
            double error = Num.round(reading - expected, 4);
            steps.add(new StepCheck(index + 1, expected, reading, error, Math.abs(error) <= tolerance + SLACK));
        }

        StepCheck worst = steps.get(0);
        List<Double> errors = new ArrayList<>();
        for (StepCheck step : steps) {
            if (Math.abs(step.error()) > Math.abs(worst.error())) {
                worst = step;
            }
            errors.add(Math.abs(step.error()));
        }
        return new StepComparison(
                steps,
                tolerance,
                steps.stream().allMatch(StepCheck::withinTolerance),
                worst.step(),
                worst.error(),
                Num.round(Num.maxOf(errors), 4));
    }

    public static CalibrationRecord calibrationRecord(
            String instrument, String day, List<Double> readings, List<Double> certified) {
        return calibrationRecord(instrument, day, readings, certified, TOLERANCE);
    }

    /** A calibration record, with its expiry worked out once on the day and stored. */
    public static CalibrationRecord calibrationRecord(
            String instrument, String day, List<Double> readings, List<Double> certified, double tolerance) {
        Dates.requireDay(day, "calibration date");
        if (instrument == null || Text.trim(instrument).isEmpty()) {
            throw new BadInputException(
                    "a calibration names the instrument it was done on", Details.of("instrument", instrument));
        }
        StepComparison comparison = compareSteps(readings, certified, tolerance);
        return new CalibrationRecord(Text.trim(instrument), day, Dates.addDays(day, VALID_DAYS), comparison);
    }

    /** Whether a calibration is still good on a given day; a failed calibration never is. */
    public static boolean calibrationCurrent(CalibrationRecord record, String onDay) {
        Dates.requireDay(onDay, "date");
        if (!record.pass()) {
            return false;
        }
        return Dates.daysBetween(record.day(), onDay) >= 0 && onDay.compareTo(record.expires()) <= 0;
    }

    /** Days left before the calibration lapses, negative once it has. */
    public static int daysUntilExpiry(CalibrationRecord record, String onDay) {
        Dates.requireDay(onDay, "date");
        return Dates.daysBetween(onDay, record.expires());
    }

    /**
     * Read a density off the wedge between two steps, as a step number with a fraction. The steps
     * are close enough together that a straight line between them is good to well inside the
     * tolerance, so this does not pretend to a curve it cannot justify.
     */
    public static double interpolateStep(List<Double> certified, double value) {
        Guard.requireArray(certified, "certified densities");
        if (certified.size() < 2) {
            throw new BadInputException(
                    "interpolating a wedge needs at least two steps", Details.of("certified", certified));
        }
        double first = certified.get(0);
        double last = certified.get(certified.size() - 1);
        if (value < Math.min(first, last) || value > Math.max(first, last)) {
            throw new BadInputException(
                    "that density is off the end of the wedge",
                    Details.of("value", value, "first", first, "last", last));
        }
        for (int i = 0; i < certified.size() - 1; i++) {
            double low = certified.get(i);
            double high = certified.get(i + 1);
            if (value >= Math.min(low, high) && value <= Math.max(low, high)) {
                double span = high - low;
                double into = span == 0 ? 0 : (value - low) / span;
                return Num.round(i + 1 + into, 3);
            }
        }
        throw new BadInputException("that density falls between no two steps", Details.of("value", value));
    }
}
