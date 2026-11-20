package io.github.rkaushikdev.kerfline.store;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.ConflictException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.util.Checksum;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * The record store.
 *
 * <p>Everything this package writes down goes in a journal and nothing is ever edited. A joint
 * that was accepted and then found to have been read wrong gets a second entry saying so; the
 * first entry stays exactly as it was. That is not a storage preference, it is the requirement:
 * the question asked in an audit is not what the record says now, it is what it said in March and
 * who changed it since.
 *
 * <p>Each entry carries a checksum chained onto the one before it, so an entry altered in the
 * middle of the file invalidates every entry after it and cannot be quietly repaired by
 * recomputing one line.
 *
 * <p>A journal is a value: {@link #append} hands back a new journal and leaves the one it was
 * given alone.
 */
public record Journal(String name, List<Entry> entries) {

    private static final List<String> KINDS = List.of(
            "joint", "shot", "interpretation", "verdict", "repair", "survey", "source", "certification", "note");

    public Journal {
        entries = List.copyOf(entries);
    }

    /** The kinds of record a journal carries. */
    public static List<String> entryKinds() {
        return KINDS;
    }

    /** An empty journal, named after the job it belongs to. */
    public static Journal makeJournal(String name) {
        String trimmed = Guard.requireLabel(name, "a journal is named after the job it belongs to");
        return new Journal(trimmed, List.of());
    }

    /**
     * Append an entry. Days must not run backwards: a record dated before the one it follows is
     * either a mistake or a rewrite, and both are worth stopping.
     *
     * @return a new journal with the entry on the end
     * @throws ConflictException when the entry is dated before the one it follows
     */
    public static Journal append(Journal journal, EntryDraft draft) {
        assertJournal(journal);
        Objects.requireNonNull(draft, "draft");
        String kind = draft.kind();
        if (kind == null || !KINDS.contains(kind)) {
            throw new BadInputException(
                    "a journal entry is one of " + String.join(", ", KINDS), Details.of("got", kind, "known", KINDS));
        }
        String day = draft.day();
        Dates.requireDay(day, "entry date");
        Guard.requireLabel(draft.subject(), "every entry names what it is about");

        List<Entry> entries = journal.entries();
        Entry previous = entries.isEmpty() ? null : entries.get(entries.size() - 1);
        if (previous != null && Dates.daysBetween(previous.day(), day) < 0) {
            throw new ConflictException(
                    "an entry cannot be dated before the one it follows",
                    Details.of("previous", previous.day(), "entry", day));
        }

        int seq = entries.size() + 1;
        Map<String, Object> fields = recordFields(seq, kind, day, draft.subject(), draft.payload());
        Entry entry = new Entry(
                seq,
                kind,
                day,
                Text.trim(draft.subject()),
                draft.payload(),
                Checksum.digestOf(fields),
                Checksum.chain(previous == null ? null : previous.checksum(), Checksum.stableStringify(fields)),
                Entry.UNSTATED_VERSION);

        List<Entry> grown = new ArrayList<>(entries);
        grown.add(entry);
        return new Journal(journal.name(), grown);
    }

    /** Append several entries in order, stopping on the first one that is refused. */
    public static Journal appendAll(Journal journal, List<EntryDraft> drafts) {
        Guard.requireArray(drafts, "entries");
        Journal current = journal;
        for (EntryDraft draft : drafts) {
            current = append(current, draft);
        }
        return current;
    }

    /**
     * Recompute the chain and report the first entry that does not match. An entry altered in
     * place breaks at itself; one removed breaks at the entry that followed it.
     */
    public static ChainCheck verify(Journal journal) {
        assertJournal(journal);
        String previous = null;
        for (Entry entry : journal.entries()) {
            String body = Checksum.stableStringify(recordFields(entry));
            String expected = Checksum.chain(previous, body);
            if (!expected.equals(entry.checksum())) {
                return ChainCheck.brokenAt(
                        entry.seq(),
                        "entry " + entry.seq() + " does not match the chain",
                        journal.entries().size());
            }
            previous = entry.checksum();
        }
        return ChainCheck.holds(journal.entries().size());
    }

    public static int entryCount(Journal journal) {
        assertJournal(journal);
        return journal.entries().size();
    }

    /**
     * The entry with a sequence number.
     *
     * @throws NotFoundException when the journal has no entry with that number
     */
    public static Entry entryAt(Journal journal, int seq) {
        assertJournal(journal);
        return journal.entries().stream()
                .filter(entry -> entry.seq() == seq)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "no entry " + seq + " in " + journal.name(),
                        Details.of("seq", seq, "entries", journal.entries().size())));
    }

    /**
     * Fold the journal into whatever shape a caller wants. This is the only way current state is
     * derived, so there is never a stored total that can drift away from the entries it was
     * supposed to summarise.
     */
    public static <S> S replay(Journal journal, BiFunction<S, Entry, S> reducer, S initial) {
        assertJournal(journal);
        if (reducer == null) {
            throw new BadInputException("replay needs a reducer", Details.of("got", null));
        }
        S state = initial;
        for (Entry entry : journal.entries()) {
            state = reducer.apply(state, entry);
        }
        return state;
    }

    /** The journal as lines of canonical JSON, one entry each, for an export or a diff. */
    public static String serialise(Journal journal) {
        assertJournal(journal);
        return journal.entries().stream()
                .map(entry -> {
                    Map<String, Object> line = recordFields(entry);
                    line.put("checksum", entry.checksum());
                    return Checksum.stableStringify(line);
                })
                .collect(Collectors.joining("\n"));
    }

    /** The fields an entry's checksum and id are computed over. */
    static Map<String, Object> recordFields(Entry entry) {
        return recordFields(entry.seq(), entry.kind(), entry.day(), entry.subject(), entry.payload());
    }

    private static Map<String, Object> recordFields(
            int seq, String kind, String day, String subject, Map<String, Object> payload) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("seq", seq);
        fields.put("kind", kind);
        fields.put("day", day);
        fields.put("subject", subject);
        fields.put("payload", payload);
        return fields;
    }

    static void assertJournal(Journal journal) {
        if (journal == null) {
            throw new BadInputException("not a journal", Details.of("got", null));
        }
    }
}
