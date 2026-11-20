package io.github.rkaushikdev.kerfline.pipeline;

import io.github.rkaushikdev.kerfline.density.AreaJudgement;
import io.github.rkaushikdev.kerfline.density.DensityCall;
import io.github.rkaushikdev.kerfline.density.DensityJudgement;
import io.github.rkaushikdev.kerfline.density.JudgedReading;
import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.exposure.Aim;
import io.github.rkaushikdev.kerfline.store.Entry;
import io.github.rkaushikdev.kerfline.store.EntryDraft;
import io.github.rkaushikdev.kerfline.store.Journal;
import io.github.rkaushikdev.kerfline.store.Query;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Rate;
import io.github.rkaushikdev.kerfline.util.RootCollation;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * What to change before shooting it again.
 *
 * <p>A reshoot is only useful if something is different about it, and which something depends on
 * what went wrong. A light film wants more time. A film with no indicator on it wants the
 * indicator put on, and exactly the same exposure. A film that failed on backscatter wants lead
 * behind the cassette and nothing else touched.
 *
 * <p>The failure worth being careful about is density variation. A film out at one end is fixed by
 * an extra exposure at that end, and a film out at both ends is a technique that cannot cover the
 * section, which means a different film or a different source and not another go at the same
 * setup. Shooting that one again unchanged is the commonest wasted film there is.
 */
public final class Reshoot {

    private static final Map<String, Correction> CORRECTIONS = corrections();
    private static final int DEFAULT_REPEAT_LIMIT = 2;

    private Reshoot() {}

    private static Map<String, Correction> corrections() {
        Map<String, Correction> table = new LinkedHashMap<>();
        put(table, "identification", List.of("lay the missing identification in lead before the shot"), 1);
        table.put("density", new Correction("density", List.of(), OptionalDouble.empty(), false));
        put(table, "variation", List.of("add an exposure at the end that ran outside the band"), 1);
        put(table, "sensitivity", List.of("fit the indicator the table calls for and check it is in the beam"), 1);
        put(table, "backscatter", List.of("put lead behind the cassette and reshoot with the B in place"), 1);
        return Collections.unmodifiableMap(table);
    }

    private static void put(Map<String, Correction> table, String check, List<String> changes, double factor) {
        table.put(check, new Correction(check, changes, OptionalDouble.of(factor), false));
    }

    /**
     * The standing correction for one failed check.
     *
     * @throws BadInputException when no correction is known for that check
     */
    public static Correction correctionFor(String check) {
        Correction found = CORRECTIONS.get(check);
        if (found == null) {
            throw new BadInputException(
                    "no correction is known for a " + check + " failure",
                    Details.of("check", check, "known", List.copyOf(CORRECTIONS.keySet())));
        }
        return found;
    }

    /**
     * Plan the reshoot from a graded film. Returns the changes and the exposure to use, or says
     * plainly that the technique itself has to change. A film that passed needs nothing.
     */
    public static ReshootPlan planReshoot(ReshootRequest request) {
        Objects.requireNonNull(request, "request");
        FilmGrade grade = request.grade();
        if (grade == null) {
            throw new BadInputException("a reshoot is planned from a graded film", Details.of("got", null));
        }
        if (grade.acceptable()) {
            return new ReshootPlan(false, List.of(), OptionalDouble.empty(), request.minutes(), false);
        }

        List<String> changes = new ArrayList<>();
        double factor = 1;
        boolean techniqueChange = false;

        for (QualityFailure failure : grade.failures()) {
            Correction correction = correctionFor(failure.check());
            changes.addAll(correction.changes());

            if ("density".equals(failure.check()) && grade.density().isPresent()) {
                DensityJudgement density = grade.density().get();
                factor *= Aim.reshootFactor(request.filmId(), density.density(), request.wantedDensity());
                changes.add(
                        density.verdict() == DensityCall.LIGHT
                                ? "lengthen the exposure, the film came out light"
                                : "shorten the exposure, the film came out dark");
            }

            if ("variation".equals(failure.check()) && grade.area().isPresent()) {
                AreaJudgement area = grade.area().get();
                Set<Optional<String>> ends = new LinkedHashSet<>();
                for (JudgedReading reading : area.failures()) {
                    ends.add(reading.at());
                }
                if (ends.size() > 2 || ends.contains(Optional.<String>empty())) {
                    techniqueChange = true;
                    changes.add("the section is wider than this film covers, change film or source");
                }
            }
        }

        OptionalDouble minutes = request.minutes().isPresent()
                ? OptionalDouble.of(Num.round(request.minutes().getAsDouble() * factor, 3))
                : OptionalDouble.empty();
        return new ReshootPlan(
                true,
                List.copyOf(new LinkedHashSet<>(changes)),
                OptionalDouble.of(Num.round(factor, 4)),
                minutes,
                techniqueChange);
    }

    public static Journal recordReshoot(Journal journal, String jointId, String day, ReshootPlan plan) {
        return recordReshoot(journal, jointId, day, plan, null);
    }

    /** Write the reshoot into the journal as a shot against the joint it belongs to; the film may be null. */
    public static Journal recordReshoot(Journal journal, String jointId, String day, ReshootPlan plan, String film) {
        Dates.requireDay(day, "reshoot date");
        String subject = Guard.requireLabel(jointId, "a reshoot names the joint it is on");
        Objects.requireNonNull(plan, "plan");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reshoot", true);
        payload.put("film", film);
        payload.put("changes", plan.changes());
        payload.put("exposureFactor", plan.exposureFactor().orElse(1));
        payload.put("techniqueChange", plan.techniqueChange());
        return Journal.append(journal, EntryDraft.of("shot", day, subject, payload));
    }

    /**
     * How much of the shooting is being done twice. A rate that climbs is a technique or a
     * darkroom problem rather than bad luck, and it is one of the few numbers that says so early.
     */
    public static ReshootRate reshootRate(Journal journal) {
        List<Entry> shots = Query.byKind(journal, "shot");
        int reshoots = (int) shots.stream().filter(Reshoot::isReshoot).count();
        return new ReshootRate(shots.size(), reshoots, Rate.rateOf(reshoots, shots.size()));
    }

    /** The changes reshoots are being called for, commonest first and alphabetical within a count. */
    public static List<ReshootReason> reshootReasons(Journal journal) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Entry entry : Query.byKind(journal, "shot")) {
            if (!isReshoot(entry)) {
                continue;
            }
            if (entry.payload().get("changes") instanceof List<?> changes) {
                for (Object change : changes) {
                    counts.merge(Text.str(change), 1, Integer::sum);
                }
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new ReshootReason(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(ReshootReason::count)
                        .reversed()
                        .thenComparing(ReshootReason::change, RootCollation.ORDER))
                .toList();
    }

    public static List<RepeatedShot> repeatedlyShot(Journal journal) {
        return repeatedlyShot(journal, DEFAULT_REPEAT_LIMIT);
    }

    /** Joints shot more times than the limit, most often shot first. */
    public static List<RepeatedShot> repeatedlyShot(Journal journal, int limit) {
        Guard.requireArray(journal == null ? null : journal.entries(), "entries");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Entry entry : Query.byKind(journal, "shot")) {
            counts.merge(entry.subject(), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() > limit)
                .map(entry -> new RepeatedShot(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(RepeatedShot::shots).reversed())
                .toList();
    }

    private static boolean isReshoot(Entry entry) {
        return Boolean.TRUE.equals(entry.payload().get("reshoot"));
    }
}
