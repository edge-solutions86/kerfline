package io.github.rkaushikdev.kerfline.safety;

import io.github.rkaushikdev.kerfline.errors.ConflictException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.isotope.Catalog;
import io.github.rkaushikdev.kerfline.isotope.Decay;
import io.github.rkaushikdev.kerfline.isotope.Isotope;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The source inventory, the utilisation log and the leak test.
 *
 * <p>Every sealed source in the shop is on this list from the day it arrives to the day it is
 * shipped back, and the log has to be able to say where it was on any given day in between. That
 * is not paperwork for its own sake: it is the only way to answer the question that gets asked
 * after an incident, which is who had it and what were they doing.
 *
 * <p>The leak test is six monthly and the threshold is five nanocuries of removable contamination.
 * A source over that comes out of service the same day, and there is no margin and no supervisor
 * override on that number.
 */
public final class SourceLog {

    private static final double LEAK_LIMIT_UCI = 0.005;
    private static final int LEAK_INTERVAL_MONTHS = 6;

    private SourceLog() {}

    /** The leak test limit, five nanocuries, in microcuries. A reading at the limit fails. */
    public static double leakLimitMicroCuries() {
        return LEAK_LIMIT_UCI;
    }

    public static int leakIntervalMonths() {
        return LEAK_INTERVAL_MONTHS;
    }

    public static SealedSource makeSource(String serial, String isotopeId, double assayCi, String assayDay) {
        return makeSource(serial, isotopeId, assayCi, assayDay, null);
    }

    /** A source off its certificate. The isotope may be given by label; the camera may be null. */
    public static SealedSource makeSource(
            String serial, String isotopeId, double assayCi, String assayDay, String camera) {
        String id = Guard.requireLabel(serial, "a source is identified by its serial number");
        Isotope source = Catalog.isotope(isotopeId);
        Guard.requireAbove(assayCi, 0, "assay activity");
        Dates.requireDay(assayDay, "assay date");
        return new SealedSource(id, source.id(), Num.round(assayCi, 2), assayDay, Optional.ofNullable(camera));
    }

    /** What the source reads on a given day, in curies, from its certificate. Before the assay it back decays. */
    public static double activityOn(SealedSource source, String day) {
        Dates.requireDay(day, "date");
        Isotope record = Catalog.isotope(source.isotope());
        int days = Dates.daysBetween(source.assayDay(), day);
        return Num.round(Decay.activityAfter(source.assayCi(), record.halfLifeDays(), days), 2);
    }

    /** One entry in the utilisation log; the location may be null. */
    public static SourceUse makeUse(
            String serial, String day, String user, String location, double minutes, int exposures) {
        Dates.requireDay(day, "date");
        String who = Guard.requireLabel(user, "every use of a source names who had it");
        Guard.requireAtLeast(minutes, 0, "time out of shield");
        Guard.requireAtLeast(exposures, 0, "exposure count");
        return new SourceUse(serial, day, who, Optional.ofNullable(location), Num.round(minutes, 1), exposures);
    }

    /** What a source did over a period, both days included. */
    public static Utilisation utilisation(List<SourceUse> uses, String serial, String from, String to) {
        Guard.requireArray(uses, "log entries");
        Dates.requireDay(from, "period start");
        Dates.requireDay(to, "period end");
        List<SourceUse> mine = uses.stream()
                .filter(use -> Objects.equals(use.serial(), serial) && Dates.withinSpan(use.day(), from, to))
                .toList();
        return new Utilisation(
                serial,
                from,
                to,
                mine.size(),
                Num.round(Num.sum(mine.stream().map(SourceUse::minutes).toList()), 1),
                mine.stream().mapToInt(SourceUse::exposures).sum(),
                mine.stream().map(SourceUse::user).distinct().sorted().toList(),
                mine.stream().map(SourceUse::day).distinct().sorted().toList());
    }

    /** Who had a source on a day, which is the question asked after an incident. */
    public static List<Holder> whoHadIt(List<SourceUse> uses, String serial, String day) {
        Guard.requireArray(uses, "log entries");
        Dates.requireDay(day, "date");
        return uses.stream()
                .filter(use -> Objects.equals(use.serial(), serial) && Objects.equals(use.day(), day))
                .map(use -> new Holder(use.user(), use.location(), use.minutes()))
                .toList();
    }

    public static LeakTest makeLeakTest(String serial, String day, double microCuries) {
        return makeLeakTest(serial, day, microCuries, null);
    }

    /**
     * A leak test result. It passes only under the limit, and the next one falls due six months
     * on, clamped onto the end of a short month. The laboratory may be null.
     */
    public static LeakTest makeLeakTest(String serial, String day, double microCuries, String laboratory) {
        Dates.requireDay(day, "test date");
        Guard.requireAtLeast(microCuries, 0, "removable activity");
        boolean pass = microCuries < LEAK_LIMIT_UCI;
        return new LeakTest(
                serial,
                day,
                Num.round(microCuries, 5),
                Optional.ofNullable(laboratory),
                pass,
                Dates.addMonths(day, LEAK_INTERVAL_MONTHS),
                pass ? Optional.empty() : Optional.of("remove the source from service today"));
    }

    /**
     * Whether a source may be used on a day. Only tests taken on or before that day count, and the
     * latest of them decides; when two share a day the one listed last is taken. A failed test
     * never becomes current again by waiting, and a test that has run out is the same answer as
     * never having had one.
     */
    public static LeakTestStatus leakTestCurrent(List<LeakTest> tests, String serial, String day) {
        Guard.requireArray(tests, "leak tests");
        Dates.requireDay(day, "date");
        LeakTest latest = null;
        for (LeakTest test : tests) {
            if (Objects.equals(test.serial(), serial)
                    && Dates.daysBetween(test.day(), day) >= 0
                    && (latest == null || test.day().compareTo(latest.day()) >= 0)) {
                latest = test;
            }
        }
        if (latest == null) {
            return notCurrent("no leak test on file", Optional.empty());
        }
        if (!latest.pass()) {
            return notCurrent("the last leak test failed", Optional.of(latest));
        }
        if (day.compareTo(latest.dueBy()) > 0) {
            return notCurrent("the leak test has run out", Optional.of(latest));
        }
        return new LeakTestStatus(
                true, Optional.empty(), Optional.of(latest), OptionalInt.of(Dates.daysBetween(day, latest.dueBy())));
    }

    private static LeakTestStatus notCurrent(String reason, Optional<LeakTest> latest) {
        return new LeakTestStatus(false, Optional.of(reason), latest, OptionalInt.empty());
    }

    /**
     * Book a source out of the store with its activity for the day.
     *
     * @throws ConflictException when its leak testing is not current, which no override changes
     */
    public static Booking checkOut(SealedSource source, List<LeakTest> tests, String day) {
        LeakTestStatus leak = leakTestCurrent(tests, source.serial(), day);
        if (!leak.current()) {
            String reason = leak.reason().orElseThrow();
            throw new ConflictException(
                    "source " + source.serial() + " cannot go out: " + reason,
                    Details.of("serial", source.serial(), "reason", reason));
        }
        return new Booking(source.serial(), day, activityOn(source, day));
    }

    /** Sources whose leak test is missing, failed, run out or due inside a window, so it can be booked. */
    public static List<LeakTestDue> leakTestsDueBy(
            List<SealedSource> sources, List<LeakTest> tests, String day, double withinDays) {
        Dates.requireDay(day, "date");
        Guard.requireAtLeast(withinDays, 0, "window");
        List<LeakTestDue> due = new ArrayList<>();
        for (SealedSource source : sources) {
            LeakTestStatus leak = leakTestCurrent(tests, source.serial(), day);
            if (!leak.current() || leak.daysLeft().getAsInt() <= withinDays) {
                due.add(new LeakTestDue(
                        source.serial(),
                        leak.latest().map(LeakTest::dueBy),
                        leak.reason().orElse("due for renewal")));
            }
        }
        return List.copyOf(due);
    }
}
