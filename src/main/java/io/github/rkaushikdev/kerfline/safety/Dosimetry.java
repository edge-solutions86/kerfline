package io.github.rkaushikdev.kerfline.safety;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Dose records for the crew.
 *
 * <p>The annual limit is five thousand millirem to the whole body. The shop does not run anybody
 * up to it: there are two investigation levels well below, and crossing either one starts a
 * conversation rather than stopping work. That is the entire point of setting them where they
 * are.
 *
 * <p>Two things here are deliberately awkward. A quarterly badge that has not come back from the
 * reader is not a zero, so an unreturned badge makes the total unknown rather than making it
 * smaller. And the pocket dosimeter is not the record of dose; it is the thing that tells a
 * technician during the shift that something is wrong. When the two disagree the badge is the
 * record and the disagreement is the finding.
 */
public final class Dosimetry {

    private static final double ANNUAL_LIMIT_MREM = 5000;
    private static final double LEVEL_ONE = 0.1;
    private static final double LEVEL_TWO = 0.3;
    private static final double DEFAULT_TOLERANCE = 0.3;

    private Dosimetry() {}

    /** The annual whole body limit, five thousand millirem. */
    public static double annualLimitMrem() {
        return ANNUAL_LIMIT_MREM;
    }

    public static InvestigationLevels investigationLevels() {
        return new InvestigationLevels(ANNUAL_LIMIT_MREM * LEVEL_ONE, ANNUAL_LIMIT_MREM * LEVEL_TWO);
    }

    /** A returned badge with its reading. */
    public static BadgeRecord makeBadgeRecord(String person, String from, String to, double mrem) {
        return makeBadgeRecord(person, from, to, mrem, true);
    }

    /**
     * A badge period. A returned badge needs a reading of at least nought; a badge that has not
     * come back must carry none, so pass null for its reading.
     */
    public static BadgeRecord makeBadgeRecord(String person, String from, String to, Double mrem, boolean returned) {
        String name = Guard.requireLabel(person, "a dose record names the person it belongs to");
        Dates.requireDay(from, "period start");
        Dates.requireDay(to, "period end");
        if (to.compareTo(from) < 0) {
            throw new BadInputException(
                    "a badge period cannot end before it starts", Details.of("from", from, "to", to));
        }
        if (returned) {
            double reading = Guard.requireNumber(mrem, "recorded dose");
            Guard.requireAtLeast(reading, 0, "recorded dose");
            return new BadgeRecord(name, from, to, OptionalDouble.of(Num.round(reading, 1)), true);
        }
        if (mrem != null) {
            throw new BadInputException(
                    "a badge that has not come back cannot carry a reading", Details.of("mrem", mrem));
        }
        return new BadgeRecord(name, from, to, OptionalDouble.empty(), false);
    }

    /**
     * The dose in a window, from every badge period that starts or ends inside it. An unreturned
     * badge makes the total unknown, which is reported rather than quietly dropped.
     */
    public static DosePeriod doseInPeriod(List<BadgeRecord> records, String from, String to) {
        Guard.requireArray(records, "dose records");
        Dates.requireDay(from, "period start");
        Dates.requireDay(to, "period end");
        List<BadgeRecord> inWindow = records.stream()
                .filter(record -> Dates.withinSpan(record.from(), from, to) || Dates.withinSpan(record.to(), from, to))
                .toList();
        int outstanding =
                (int) inWindow.stream().filter(record -> !record.returned()).count();
        List<Double> known = inWindow.stream()
                .filter(BadgeRecord::returned)
                .map(record -> record.mrem().orElseThrow())
                .toList();
        return new DosePeriod(from, to, inWindow.size(), Num.round(Num.sum(known), 1), outstanding, outstanding == 0);
    }

    /** The rolling twelve month dose ending on a day. */
    public static DosePeriod annualDose(List<BadgeRecord> records, String endingOn) {
        Dates.requireDay(endingOn, "date");
        return doseInPeriod(records, Dates.addYears(endingOn, -1), endingOn);
    }

    /**
     * Judge a person's standing. An incomplete record never reports "within limits", because the
     * missing badge is exactly where the dose would be.
     */
    public static AnnualStanding judgeAnnual(List<BadgeRecord> records, String endingOn) {
        DosePeriod period = annualDose(records, endingOn);
        InvestigationLevels levels = investigationLevels();
        DoseStanding standing = DoseStanding.WITHIN_LIMITS;
        if (period.mrem() > ANNUAL_LIMIT_MREM) {
            standing = DoseStanding.OVER_THE_ANNUAL_LIMIT;
        } else if (period.mrem() > levels.levelTwoMrem()) {
            standing = DoseStanding.LEVEL_TWO_INVESTIGATION;
        } else if (period.mrem() > levels.levelOneMrem()) {
            standing = DoseStanding.LEVEL_ONE_INVESTIGATION;
        }
        return new AnnualStanding(
                period,
                ANNUAL_LIMIT_MREM,
                levels,
                period.complete() ? standing : DoseStanding.INCOMPLETE_RECORD,
                Num.round(Math.max(0, ANNUAL_LIMIT_MREM - period.mrem()), 1),
                Num.round(period.mrem() / ANNUAL_LIMIT_MREM, 4));
    }

    public static PocketComparison comparePocket(double badgeMrem, double pocketMrem) {
        return comparePocket(badgeMrem, pocketMrem, DEFAULT_TOLERANCE);
    }

    /**
     * Compare a pocket dosimeter against the badge for the same period. A large disagreement is a
     * finding on the equipment or the record keeping, and the badge is what stands either way.
     * The tolerance is a fraction of the larger of the two readings.
     */
    public static PocketComparison comparePocket(double badgeMrem, double pocketMrem, double tolerance) {
        Guard.requireAtLeast(badgeMrem, 0, "badge dose");
        Guard.requireAtLeast(pocketMrem, 0, "pocket dose");
        Guard.requireAtLeast(tolerance, 0, "tolerance");
        double larger = Math.max(badgeMrem, pocketMrem);
        if (larger == 0) {
            return new PocketComparison(true, 0, 0, "both read nothing");
        }
        double difference = Math.abs(badgeMrem - pocketMrem);
        return new PocketComparison(
                difference / larger <= tolerance,
                Num.round(difference, 1),
                badgeMrem,
                "the badge is the record of dose whatever the pocket meter said");
    }
}
