package io.github.rkaushikdev.kerfline.store;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Ids;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What the record said on a day.
 *
 * <p>An append only journal can answer this and a mutable store cannot, which is most of the
 * reason the journal is the shape it is. A client asking why a joint was signed off in March is
 * not asking what the register says now.
 *
 * <p>A snapshot is the journal with the later entries dropped, so it verifies against the same
 * chain: the checksums of the entries that remain are exactly what they were, and a snapshot that
 * fails verification is a snapshot of a journal that was already broken.
 *
 * <p>A state is the disposition of every judged subject, keyed by subject in register order. A
 * verdict entry that carried no disposition maps its subject to null, as it did on the record.
 */
public record Snapshot(String name, String asOf, List<Entry> entries, int dropped) {

    private static final String ACCEPT = "accept";

    public Snapshot {
        entries = List.copyOf(entries);
    }

    /** The journal as it stood at the end of a day: every entry dated on or before it. */
    public static Snapshot snapshotAt(Journal journal, String day) {
        Journal.assertJournal(journal);
        Dates.requireDay(day, "date");
        List<Entry> kept = journal.entries().stream()
                .filter(entry -> Dates.daysBetween(entry.day(), day) >= 0)
                .toList();
        return new Snapshot(journal.name(), day, kept, journal.entries().size() - kept.size());
    }

    /** A snapshot verifies exactly as the journal it came from did. */
    public static ChainCheck verifySnapshot(Snapshot snapshot) {
        if (snapshot == null) {
            throw new BadInputException("not a journal", Details.of("got", null));
        }
        return Journal.verify(new Journal(snapshot.name(), snapshot.entries()));
    }

    /** The disposition of every subject as the record stood on a day, in register order. */
    public static Map<String, String> stateAt(Journal journal, String day) {
        Snapshot snapshot = snapshotAt(journal, day);
        Map<String, String> latest = new LinkedHashMap<>();
        for (Entry entry : snapshot.entries()) {
            if (!"verdict".equals(entry.kind())) {
                continue;
            }
            latest.put(entry.subject(), entry.textField("disposition").orElse(null));
        }
        List<String> order = new ArrayList<>(latest.keySet());
        order.sort(Ids.REGISTER_ORDER);
        Map<String, String> state = new LinkedHashMap<>();
        order.forEach(subject -> state.put(subject, latest.get(subject)));
        return Collections.unmodifiableMap(state);
    }

    /**
     * What changed between two states. Three kinds of change matter and they are reported
     * separately, because a joint that went from repair to accept is ordinary progress and one
     * that went from accept to repair is a reopening somebody has to explain.
     */
    public static StateDiff diffStates(Map<String, String> before, Map<String, String> after) {
        if (before == null || after == null) {
            throw new BadInputException(
                    "two states are needed to compare", Details.of("before", before, "after", after));
        }
        List<SubjectDisposition> added = new ArrayList<>();
        List<DispositionChange> changed = new ArrayList<>();
        List<SubjectDisposition> removed = new ArrayList<>();

        for (Map.Entry<String, String> row : after.entrySet()) {
            String subject = row.getKey();
            if (!before.containsKey(subject)) {
                added.add(new SubjectDisposition(subject, row.getValue()));
            } else if (!Objects.equals(before.get(subject), row.getValue())) {
                changed.add(new DispositionChange(subject, before.get(subject), row.getValue()));
            }
        }
        for (Map.Entry<String, String> row : before.entrySet()) {
            if (!after.containsKey(row.getKey())) {
                removed.add(new SubjectDisposition(row.getKey(), row.getValue()));
            }
        }

        List<DispositionChange> reopened = changed.stream()
                .filter(row -> ACCEPT.equals(row.from()) && !ACCEPT.equals(row.to()))
                .toList();
        return new StateDiff(added, changed, removed, reopened, added.size() + changed.size() == 0);
    }

    /** What moved between two days. */
    public static StateDiff changedBetween(Journal journal, String from, String to) {
        Dates.requireDay(from, "period start");
        Dates.requireDay(to, "period end");
        if (Dates.daysBetween(from, to) < 0) {
            throw new BadInputException("the period runs backwards", Details.of("from", from, "to", to));
        }
        return diffStates(stateAt(journal, from), stateAt(journal, to));
    }

    /**
     * Every day the record changed, in order, which is where an audit starts. Reading only the
     * days something happened is far quicker than walking a year of them.
     */
    public static List<String> activeDays(Journal journal) {
        Journal.assertJournal(journal);
        Set<String> days = new LinkedHashSet<>();
        journal.entries().forEach(entry -> days.add(entry.day()));
        return days.stream().sorted().toList();
    }

    /** The record as it stood at the end of each active day. */
    public static List<DayState> history(Journal journal) {
        return activeDays(journal).stream()
                .map(day -> new DayState(day, stateAt(journal, day)))
                .toList();
    }

    /** Anything that was accepted and later stopped being accepted, with the day it happened. */
    public static List<Reopening> reopenings(Journal journal) {
        List<String> days = activeDays(journal);
        List<Reopening> found = new ArrayList<>();
        for (int i = 1; i < days.size(); i++) {
            StateDiff diff = diffStates(stateAt(journal, days.get(i - 1)), stateAt(journal, days.get(i)));
            for (DispositionChange row : diff.reopened()) {
                found.add(new Reopening(days.get(i), row.subject(), row.from(), row.to()));
            }
        }
        return List.copyOf(found);
    }
}
