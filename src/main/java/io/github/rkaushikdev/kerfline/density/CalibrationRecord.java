package io.github.rkaushikdev.kerfline.density;

import java.util.List;

/**
 * A densitometer calibration as filed: the instrument, the day it was done, the day it lapses and
 * the step comparison behind it. The expiry is stored rather than computed on read, so a record
 * printed a year later still says what it said on the day.
 */
public record CalibrationRecord(String instrument, String day, String expires, StepComparison comparison) {

    public boolean pass() {
        return comparison.pass();
    }

    public List<StepCheck> steps() {
        return comparison.steps();
    }

    public double tolerance() {
        return comparison.tolerance();
    }

    public int worstStep() {
        return comparison.worstStep();
    }

    public double worstError() {
        return comparison.worstError();
    }

    public double largestError() {
        return comparison.largestError();
    }
}
