package io.github.rkaushikdev.kerfline.isotope;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.List;

/**
 * Source decay. A camera loaded in March is not the camera it was in June, and every exposure
 * time in this package is computed from the activity on the day of the shot rather than from the
 * number stamped on the certificate.
 */
public final class Decay {

    private static final double LN2 = StrictMath.log(2);
    private static final int DEFAULT_PLACES = 2;

    private Decay() {}

    public static double decayConstant(double halfLifeDays) {
        Guard.requirePositive(halfLifeDays, "half life");
        return LN2 / halfLifeDays;
    }

    /** The fraction of the original activity left after the elapsed days. */
    public static double decayFactor(double halfLifeDays, double elapsedDays) {
        Guard.requirePositive(halfLifeDays, "half life");
        Guard.requireNumber(elapsedDays, "elapsed days");
        return StrictMath.exp(-decayConstant(halfLifeDays) * elapsedDays);
    }

    public static double halfLivesElapsed(double halfLifeDays, double elapsedDays) {
        Guard.requirePositive(halfLifeDays, "half life");
        Guard.requireNumber(elapsedDays, "elapsed days");
        return elapsedDays / halfLifeDays;
    }

    /**
     * Activity after a span. A negative span back decays to a date before the assay, which is
     * what a technician does when a certificate arrives late.
     */
    public static double activityAfter(double startCi, double halfLifeDays, double elapsedDays) {
        Guard.requirePositive(startCi, "assay activity");
        return startCi * decayFactor(halfLifeDays, elapsedDays);
    }

    /** The same, taking the isotope by name so the half life comes from one place. */
    public static double activityOf(String idOrLabel, double startCi, double elapsedDays) {
        Isotope source = Catalog.isotope(idOrLabel);
        return activityAfter(startCi, source.halfLifeDays(), elapsedDays);
    }

    /**
     * Days from the assay until the activity has fallen to the target. Negative when the target is
     * above the assay, which means the date has passed.
     */
    public static double daysToActivity(double startCi, double halfLifeDays, double targetCi) {
        Guard.requirePositive(startCi, "assay activity");
        Guard.requirePositive(targetCi, "target activity");
        Guard.requirePositive(halfLifeDays, "half life");
        return (halfLifeDays * StrictMath.log(startCi / targetCi)) / LN2;
    }

    /**
     * The last day the source is worth loading, given the smallest activity the shop will shoot
     * with. Rounded down, because a partial day of usefulness is not one a scheduler can book.
     */
    public static int usableDays(String idOrLabel, double startCi, double minimumCi) {
        Isotope source = Catalog.isotope(idOrLabel);
        double days = daysToActivity(startCi, source.halfLifeDays(), minimumCi);
        return (int) Math.max(0, Math.floor(days));
    }

    public static List<DecayRow> decayTable(String idOrLabel, double startCi, List<Double> dayOffsets) {
        return decayTable(idOrLabel, startCi, dayOffsets, DEFAULT_PLACES);
    }

    /**
     * A decay strip for the camera log: activity on each of the given days, at the stated number
     * of decimal places. Field crews keep one of these taped inside the transport case.
     */
    public static List<DecayRow> decayTable(String idOrLabel, double startCi, List<Double> dayOffsets, int decimals) {
        Isotope source = Catalog.isotope(idOrLabel);
        if (dayOffsets == null || dayOffsets.isEmpty()) {
            throw new BadInputException("a decay table needs at least one day offset", Details.of("got", dayOffsets));
        }
        List<DecayRow> rows = new ArrayList<>();
        for (Double offset : dayOffsets) {
            double days = Guard.requireNumber(offset, "day offset");
            double curies = activityAfter(startCi, source.halfLifeDays(), days);
            rows.add(new DecayRow(
                    days, Num.round(curies, decimals), Num.round(decayFactor(source.halfLifeDays(), days), 4)));
        }
        return List.copyOf(rows);
    }

    /**
     * The multiplier to put on an exposure time worked out at the assay activity, so a chart read
     * once stays usable as the source ages.
     */
    public static double exposureMultiplier(double halfLifeDays, double elapsedDays) {
        return 1 / decayFactor(halfLifeDays, elapsedDays);
    }
}
