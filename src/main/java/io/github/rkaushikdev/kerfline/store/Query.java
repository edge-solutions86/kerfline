package io.github.rkaushikdev.kerfline.store;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Ids;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Reading the journal back.
 *
 * <p>Nothing here caches. Every query walks the entries, because the journal is a few thousand
 * lines on the largest job this shop has run and an index that can go stale is a worse problem
 * than a walk that takes a millisecond.
 *
 * <p>The one method worth reading carefully is {@link #latestPerSubject}. A joint that was
 * accepted, then reopened, then accepted again has three verdict entries and only the last one
 * is its disposition. Anything that counts joints by filtering on kind and tallying will count
 * that joint three times, and the number it produces looks entirely reasonable.
 */
public final class Query {

    private Query() {}

    private static List<Entry> entriesOf(Journal journal) {
        Journal.assertJournal(journal);
        return journal.entries();
    }

    public static List<Entry> where(Journal journal, Predicate<? super Entry> predicate) {
        if (predicate == null) {
            throw new BadInputException("a query needs a predicate", Details.of("got", null));
        }
        return entriesOf(journal).stream().filter(predicate).toList();
    }

    public static List<Entry> byKind(Journal journal, String kind) {
        return where(journal, entry -> Objects.equals(entry.kind(), kind));
    }

    public static List<Entry> bySubject(Journal journal, String subject) {
        return where(journal, entry -> Objects.equals(entry.subject(), subject));
    }

    /** The entries dated inside a span of days, both ends included. */
    public static List<Entry> between(Journal journal, String from, String to) {
        Dates.requireDay(from, "period start");
        Dates.requireDay(to, "period end");
        return where(journal, entry -> Dates.withinSpan(entry.day(), from, to));
    }

    /** Every subject the journal mentions, in register order. */
    public static List<String> subjects(Journal journal) {
        Set<String> seen = new LinkedHashSet<>();
        entriesOf(journal).forEach(entry -> seen.add(entry.subject()));
        return seen.stream().sorted(Ids.REGISTER_ORDER).toList();
    }

    /** Everything about one subject, oldest first, which is its history. */
    public static List<Entry> timeline(Journal journal, String subject) {
        return bySubject(journal, subject);
    }

    /**
     * The last entry of a kind for each subject, in register order. This is how current state is
     * read, and it is a different question from "every entry of that kind".
     */
    public static List<Entry> latestPerSubject(Journal journal, String kind) {
        Map<String, Entry> latest = new LinkedHashMap<>();
        for (Entry entry : byKind(journal, kind)) {
            latest.put(entry.subject(), entry);
        }
        return latest.values().stream()
                .sorted(Comparator.comparing(Entry::subject, Ids.REGISTER_ORDER))
                .toList();
    }

    /** The last entry of a kind for one subject, or empty when there is none. */
    public static Optional<Entry> latestFor(Journal journal, String subject, String kind) {
        List<Entry> mine =
                where(journal, entry -> Objects.equals(entry.subject(), subject) && Objects.equals(entry.kind(), kind));
        return mine.isEmpty() ? Optional.empty() : Optional.of(mine.get(mine.size() - 1));
    }

    /** Group entries by whatever key a caller wants, keeping journal order inside each group. */
    public static <K> Map<K, List<Entry>> groupBy(Journal journal, Function<? super Entry, ? extends K> keyOf) {
        if (keyOf == null) {
            throw new BadInputException("grouping needs a key function", Details.of("got", null));
        }
        Map<K, List<Entry>> groups = new LinkedHashMap<>();
        for (Entry entry : entriesOf(journal)) {
            groups.computeIfAbsent(keyOf.apply(entry), key -> new ArrayList<>()).add(entry);
        }
        Map<K, List<Entry>> frozen = new LinkedHashMap<>();
        groups.forEach((key, entries) -> frozen.put(key, List.copyOf(entries)));
        return Collections.unmodifiableMap(frozen);
    }

    /** How many entries of each kind, for the front page of a job report. */
    public static Map<String, Integer> countByKind(Journal journal) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Entry entry : entriesOf(journal)) {
            counts.merge(entry.kind(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(counts);
    }

    /**
     * The dispositions as they stand, one per joint. Built from the last verdict for each subject
     * rather than from every verdict, which is the whole reason this method exists instead of a
     * filter at the call site.
     */
    public static List<CurrentDisposition> currentDispositions(Journal journal) {
        return latestPerSubject(journal, "verdict").stream()
                .map(entry -> new CurrentDisposition(
                        entry.subject(), entry.textField("disposition"), entry.day(), entry.seq()))
                .toList();
    }

    /** Subjects that have been shot but never had a verdict recorded, in register order. */
    public static List<String> awaitingVerdict(Journal journal) {
        Set<String> judged = new HashSet<>();
        byKind(journal, "verdict").forEach(entry -> judged.add(entry.subject()));
        Set<String> shot = new LinkedHashSet<>();
        byKind(journal, "shot").forEach(entry -> shot.add(entry.subject()));
        return shot.stream()
                .filter(subject -> !judged.contains(subject))
                .sorted(Ids.REGISTER_ORDER)
                .toList();
    }

    /** The span of days the journal covers, or empty when it has no entries. */
    public static Optional<Span> coveredSpan(Journal journal) {
        List<Entry> entries = entriesOf(journal);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                new Span(entries.get(0).day(), entries.get(entries.size() - 1).day()));
    }

    /** Merge several journals' entries into one list in day order, each tagged with its job. */
    public static List<MergedEntry> mergeEntries(List<Journal> journals) {
        Guard.requireArray(journals, "journals");
        List<MergedEntry> merged = new ArrayList<>();
        for (Journal journal : journals) {
            for (Entry entry : entriesOf(journal)) {
                merged.add(new MergedEntry(journal.name(), entry));
            }
        }
        merged.sort(Comparator.comparing((MergedEntry row) -> row.entry().day())
                .thenComparingInt(row -> row.entry().seq()));
        return List.copyOf(merged);
    }
}
