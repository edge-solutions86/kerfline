package io.github.rkaushikdev.kerfline.personnel;

import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Training records, and the hours they add up to.
 *
 * <p>Two things make this less trivial than a running total. Training is per method, so hours on
 * ultrasonics do not count towards a radiography level however many of them there are. And some
 * courses expire: radiation safety has to be refreshed, and hours from a lapsed safety course
 * still count towards experience but do not keep somebody current to work.
 *
 * <p>So there are two questions with different answers, and this class answers both separately
 * rather than producing one number that means neither.
 */
public final class Training {

    private static final List<String> METHODS = List.of("RT", "UT", "MT", "PT", "VT", "SAFETY");
    private static final Map<String, Integer> REFRESH_YEARS = Map.of("SAFETY", 1);
    private static final String DEFAULT_METHOD = "RT";

    private Training() {}

    public static List<String> methods() {
        return METHODS;
    }

    /** The years after which a method's training has to be refreshed; empty when it never does. */
    public static OptionalInt refreshYearsFor(String method) {
        Guard.requireOneOf(method, METHODS, "method");
        Integer years = REFRESH_YEARS.get(method);
        return years == null ? OptionalInt.empty() : OptionalInt.of(years);
    }

    public static TrainingRecord makeTraining(TrainingInput input) {
        String person = Guard.requireLabel(input.person(), "a training record names who attended");
        String course = Guard.requireLabel(input.course(), "a training record names the course");
        Guard.requireOneOf(input.method(), METHODS, "method");
        Dates.requireDay(input.day(), "training date");
        Guard.requireAtLeast(input.hours(), 0, "hours");
        OptionalInt refresh = refreshYearsFor(input.method());
        return new TrainingRecord(
                person,
                input.method(),
                course,
                Num.round(input.hours(), 1),
                input.day(),
                Optional.ofNullable(input.provider()),
                refresh.isPresent() ? Optional.of(Dates.addYears(input.day(), refresh.getAsInt())) : Optional.empty());
    }

    /** Hours in one method, which is the only total that counts for a level. */
    public static double hoursToward(List<TrainingRecord> records, String method) {
        Guard.requireArray(records, "training records");
        Guard.requireOneOf(method, METHODS, "method");
        return Num.round(
                Num.sum(records.stream()
                        .filter(record -> record.method().equals(method))
                        .map(TrainingRecord::hours)
                        .toList()),
                1);
    }

    /** Hours split by method, in method order, so a gap in one shows next to a surplus in another. */
    public static Map<String, Double> hoursByMethod(List<TrainingRecord> records) {
        Guard.requireArray(records, "training records");
        Map<String, Double> split = new LinkedHashMap<>();
        for (String method : METHODS) {
            split.put(method, hoursToward(records, method));
        }
        return Collections.unmodifiableMap(split);
    }

    /** Readiness in radiography with no experience hours counted yet. */
    public static Readiness readyFor(List<TrainingRecord> records, String level) {
        return readyFor(records, DEFAULT_METHOD, level, 0);
    }

    /** Readiness in radiography. */
    public static Readiness readyFor(List<TrainingRecord> records, String level, double experienceHours) {
        return readyFor(records, DEFAULT_METHOD, level, experienceHours);
    }

    /**
     * Whether the training hours in a method reach a level. Experience hours are counted
     * separately, on the job, and are passed in rather than derived here.
     */
    public static Readiness readyFor(
            List<TrainingRecord> records, String method, String level, double experienceHours) {
        Guard.requireOneOf(level, Cert.levels(), "certification level");
        LevelRequirements wanted = Cert.requirementsFor(level);
        double training = hoursToward(records, method);
        Guard.requireAtLeast(experienceHours, 0, "experience hours");
        return new Readiness(
                level,
                method,
                training,
                experienceHours,
                Num.round(Math.max(0, wanted.trainingHours() - training), 1),
                Num.round(Math.max(0, wanted.experienceHours() - experienceHours), 1),
                training >= wanted.trainingHours() && experienceHours >= wanted.experienceHours());
    }

    /** Records of a kind that has to be refreshed and has not been, on a day. */
    public static List<TrainingRecord> lapsed(List<TrainingRecord> records, String day) {
        Guard.requireArray(records, "training records");
        Dates.requireDay(day, "date");
        return records.stream()
                .filter(record -> record.expiresOn().isPresent()
                        && Dates.daysBetween(record.expiresOn().get(), day) > 0)
                .toList();
    }

    /**
     * Whether somebody is current to work on a day. Safety training is the one that gates that,
     * and having taken it once a long time ago is not the same as being current. The latest course
     * taken on or before the day is the one that counts; of two on the same day, the one recorded
     * later.
     */
    public static SafetyStatus currentToWork(List<TrainingRecord> records, String day) {
        Dates.requireDay(day, "date");
        Guard.requireArray(records, "training records");
        TrainingRecord latest = null;
        for (TrainingRecord record : records) {
            if (record.method().equals("SAFETY")
                    && Dates.daysBetween(record.day(), day) >= 0
                    && (latest == null || record.day().compareTo(latest.day()) >= 0)) {
                latest = record;
            }
        }
        if (latest == null) {
            return new SafetyStatus(
                    false, Optional.of("no radiation safety training on file"), Optional.empty(), OptionalInt.empty());
        }
        String expiresOn = latest.expiresOn().orElseThrow();
        if (Dates.daysBetween(expiresOn, day) > 0) {
            return new SafetyStatus(
                    false, Optional.of("the safety training has lapsed"), Optional.of(latest), OptionalInt.empty());
        }
        return new SafetyStatus(
                true, Optional.empty(), Optional.of(latest), OptionalInt.of(Dates.daysBetween(day, expiresOn)));
    }

    /** Training taken inside a window, both ends included, for an annual return. */
    public static List<TrainingRecord> takenBetween(List<TrainingRecord> records, String from, String to) {
        Guard.requireArray(records, "training records");
        Dates.requireDay(from, "period start");
        Dates.requireDay(to, "period end");
        return records.stream()
                .filter(record -> Dates.withinSpan(record.day(), from, to))
                .toList();
    }
}
