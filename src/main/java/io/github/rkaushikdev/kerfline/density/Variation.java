package io.github.rkaushikdev.kerfline.density;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The density variation rule.
 *
 * <p>Density anywhere through the area of interest is judged against the density through the body
 * of the indicator, and it may fall no more than fifteen percent below it or rise more than thirty
 * percent above it. The asymmetry is not arbitrary: a light patch hides indications and a dark one
 * only makes the film harder to read, so the rule is tighter on the side that loses information.
 *
 * <p>The thing worth being careful about is that the reference is a measurement and not a
 * constant. Two films of the same joint shot at different densities have different allowed bands,
 * and a reading that passes on one fails on the other. Every method here takes the reference
 * explicitly for that reason.
 */
public final class Variation {

    private static final double BELOW = 0.15;
    private static final double ABOVE = 0.3;
    private static final int MOST_SPOTS_TO_PATCH = 2;

    private Variation() {}

    public static VariationFractions variationFractions() {
        return new VariationFractions(BELOW, ABOVE);
    }

    /** The band a reading has to land in, given the density at the indicator. */
    public static VariationLimits variationLimits(double referenceDensity) {
        Guard.requirePositive(referenceDensity, "reference density");
        return new VariationLimits(
                referenceDensity,
                Num.round(referenceDensity * (1 - BELOW), 4),
                Num.round(referenceDensity * (1 + ABOVE), 4));
    }

    public static boolean withinVariation(double value, double referenceDensity) {
        VariationLimits limits = variationLimits(referenceDensity);
        return value >= limits.minimum() && value <= limits.maximum();
    }

    /**
     * Judge one reading, saying which way it went and by how much. The percentage is signed
     * against the reference so a report can print it straight.
     */
    public static VariationJudgement judgeVariation(double value, double referenceDensity) {
        Guard.requirePositive(value, "density reading");
        VariationLimits limits = variationLimits(referenceDensity);
        double change = Num.percentChange(referenceDensity, value);
        VariationCall verdict = VariationCall.ACCEPT;
        if (value < limits.minimum()) {
            verdict = VariationCall.TOO_LIGHT;
        } else if (value > limits.maximum()) {
            verdict = VariationCall.TOO_DARK;
        }
        return new VariationJudgement(
                value,
                Num.round(change, 2),
                verdict,
                verdict == VariationCall.ACCEPT,
                limits.minimum(),
                limits.maximum());
    }

    /**
     * Judge a whole area of interest. A single reading outside the band condemns the film, but the
     * result still carries every reading, because the pattern of where it went wrong is what tells
     * the crew whether to reshoot the joint or add an exposure at the ends.
     */
    public static AreaJudgement judgeArea(List<AreaReading> readings, double referenceDensity) {
        Guard.requireArray(readings, "readings");
        if (readings.isEmpty()) {
            throw new BadInputException("an area of interest needs at least one reading", Details.of("got", readings));
        }
        List<JudgedReading> judged = new ArrayList<>();
        for (int index = 0; index < readings.size(); index++) {
            AreaReading reading = readings.get(index);
            if (reading == null || reading.density().isEmpty()) {
                throw new BadInputException(
                        "reading " + (index + 1) + " has no density on it", Details.of("got", reading));
            }
            judged.add(JudgedReading.of(
                    reading.at(), judgeVariation(reading.density().getAsDouble(), referenceDensity)));
        }
        List<JudgedReading> failures =
                judged.stream().filter(reading -> !reading.pass()).toList();
        JudgedReading lightest = judged.get(0);
        JudgedReading darkest = judged.get(0);
        for (JudgedReading reading : judged) {
            if (reading.density() < lightest.density()) {
                lightest = reading;
            }
            if (reading.density() > darkest.density()) {
                darkest = reading;
            }
        }
        return new AreaJudgement(referenceDensity, judged, failures.isEmpty(), failures, lightest, darkest);
    }

    /**
     * The extra exposures a failing area needs. A film that is light only at one end is fixed by
     * shooting that end again; one that is out in more places than that, or whose failing
     * readings carry no location, is a technique problem and the whole joint gets reshot.
     */
    public static Remedy remedyFor(AreaJudgement area) {
        if (area.pass()) {
            return new Remedy(RemedyAction.NONE, false, List.of());
        }
        Set<String> distinct = new LinkedHashSet<>();
        area.failures().forEach(reading -> reading.at().ifPresent(distinct::add));
        if (distinct.isEmpty() || distinct.size() > MOST_SPOTS_TO_PATCH) {
            return new Remedy(RemedyAction.RESHOOT_JOINT, true, List.of());
        }
        return new Remedy(RemedyAction.ADD_EXPOSURE, false, List.copyOf(distinct));
    }

    static Optional<AreaJudgement> judgeAreaIfAny(List<AreaReading> readings, double referenceDensity) {
        return readings.isEmpty() ? Optional.empty() : Optional.of(judgeArea(readings, referenceDensity));
    }
}
