package io.github.rkaushikdev.kerfline.store;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.ConflictException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Guard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Bringing old records forward.
 *
 * <p>A journal written two years ago has payloads in the shape the tool used then. Those files
 * are the client's copy of the record and they are not going to be rewritten, so the reading side
 * has to know how to bring them forward. The alternative is a growing pile of "if this field is
 * missing then" scattered through every consumer, which is where this started.
 *
 * <p>Two rules make this safe to rely on. A migration only ever moves forward by one version, so
 * the chain is checkable one step at a time. And migrating changes the payload but never the
 * checksum on the stored entry, because the checksum attests to what was written, not to what it
 * means today.
 */
public final class Migrate {

    private static final Map<String, Integer> CURRENT = currentVersions();
    private static final Map<String, Map<Integer, UnaryOperator<Map<String, Object>>>> MIGRATIONS = migrations();

    private Migrate() {}

    private static Map<String, Integer> currentVersions() {
        Map<String, Integer> current = new LinkedHashMap<>();
        current.put("joint", 2);
        current.put("shot", 2);
        current.put("interpretation", 1);
        current.put("verdict", 2);
        current.put("repair", 1);
        current.put("survey", 1);
        current.put("source", 1);
        current.put("certification", 1);
        current.put("note", 1);
        return Collections.unmodifiableMap(current);
    }

    private static Map<String, Map<Integer, UnaryOperator<Map<String, Object>>>> migrations() {
        return Map.of(
                // Version 1 recorded the wall as "thickness". It was renamed once the difference
                // between wall and penetrated thickness started to matter.
                "joint", Map.of(1, Migrate::renameThickness),
                // Version 1 recorded a single film count. Version 2 splits the count from whether
                // the shot was ready to fire.
                "shot", Map.of(1, payload -> withDefaults(payload, "ready", true)),
                // Version 1 had no clause on a verdict, which made a finding impossible to argue
                // about later. Anything without one is marked as such rather than being given a
                // clause it never had.
                "verdict", Map.of(1, payload -> withDefaults(payload, "clause", "not recorded", "reasons", List.of())));
    }

    private static Map<String, Object> renameThickness(Map<String, Object> payload) {
        Map<String, Object> rest = new LinkedHashMap<>(payload);
        boolean hadThickness = rest.containsKey("thickness");
        Object thickness = rest.remove("thickness");
        if (hadThickness) {
            rest.put("wallIn", thickness);
        }
        return rest;
    }

    /** The defaults first, then the payload over them, so a field the record had always wins. */
    private static Map<String, Object> withDefaults(Map<String, Object> payload, Object... keysAndValues) {
        Map<String, Object> filled = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            filled.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        filled.putAll(payload);
        return filled;
    }

    /** Every record kind the version table knows, sorted. */
    public static List<String> knownKinds() {
        return CURRENT.keySet().stream().sorted().toList();
    }

    /** The payload version this build writes for a kind of record. */
    public static int currentVersion(String kind) {
        Integer version = kind == null ? null : CURRENT.get(kind);
        if (version == null) {
            throw new BadInputException("no record kind " + kind, Details.of("kind", kind, "known", knownKinds()));
        }
        return version;
    }

    /** True when an entry is in an older shape than this build writes. */
    public static boolean needsMigration(Entry entry) {
        return entry.version() < currentVersion(entry.kind());
    }

    /**
     * Bring one entry forward to the current version, one step at a time. An entry already at the
     * current version comes back untouched, and one from a newer version than this build knows
     * about is refused rather than guessed.
     *
     * @throws ConflictException for a version past what this build knows, or a step nobody wrote
     */
    public static MigratedEntry migrateEntry(Entry entry) {
        if (entry == null) {
            throw new BadInputException("a record must be an object", Details.of("got", null));
        }
        int target = currentVersion(entry.kind());
        int version = entry.version();
        if (version > target) {
            throw new ConflictException(
                    "this record is version " + version + " and the tool knows " + target,
                    Details.of("kind", entry.kind(), "version", version, "known", target));
        }

        Map<String, Object> payload = new LinkedHashMap<>(entry.payload());
        List<Integer> steps = new ArrayList<>();
        while (version < target) {
            UnaryOperator<Map<String, Object>> step =
                    MIGRATIONS.getOrDefault(entry.kind(), Map.of()).get(version);
            if (step == null) {
                throw new ConflictException(
                        "no migration from version " + version + " of a " + entry.kind(),
                        Details.of("kind", entry.kind(), "version", version));
            }
            payload = step.apply(payload);
            steps.add(version);
            version += 1;
        }

        Entry moved = new Entry(
                entry.seq(),
                entry.kind(),
                entry.day(),
                entry.subject(),
                payload,
                entry.id(),
                entry.checksum(),
                version);
        return new MigratedEntry(moved, steps);
    }

    /** Bring a whole journal forward, leaving the checksums as they were. */
    public static MigratedJournal migrateJournal(Journal journal) {
        Journal.assertJournal(journal);
        List<MigratedEntry> entries =
                journal.entries().stream().map(Migrate::migrateEntry).toList();
        int migrated = (int) entries.stream().filter(MigratedEntry::migrated).count();
        return new MigratedJournal(journal.name(), entries, migrated);
    }

    /** What a journal would need, without changing anything. */
    public static JournalSurvey surveyJournal(Journal journal) {
        Guard.requireArray(journal == null ? null : journal.entries(), "entries");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Entry entry : journal.entries()) {
            counts.merge(entry.kind() + " v" + entry.version(), 1, Integer::sum);
        }
        int needing =
                (int) journal.entries().stream().filter(Migrate::needsMigration).count();
        return new JournalSurvey(journal.entries().size(), counts, needing);
    }
}
