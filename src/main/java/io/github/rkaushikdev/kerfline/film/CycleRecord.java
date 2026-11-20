package io.github.rkaushikdev.kerfline.film;

/**
 * The darkroom log entry for one processing run: the development verdict, how many films went
 * through and their area, the replenisher dosed for them, and the fix, wash and total times.
 */
public record CycleRecord(
        CycleJudgement development,
        int films,
        double areaSquareInches,
        double replenisherMl,
        double fixMinutes,
        double washMinutes,
        double totalMinutes) {

    /** Whether the development itself was right. */
    public boolean pass() {
        return development.pass();
    }

    public DevelopmentVerdict verdict() {
        return development.verdict();
    }
}
