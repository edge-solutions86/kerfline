package io.github.rkaushikdev.kerfline.store;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.ConflictException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Checksum;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reading a journal back off a file.
 *
 * <p>The file is what a client was sent and it may have been through anything on the way: an
 * email client, a spreadsheet, a copy and paste. So this parses strictly and names the line
 * number on every refusal, because "the import failed" is not a message anybody can act on and
 * "line 412 has no subject" is.
 *
 * <p>The chain is verified against what the file claims rather than recomputed and stored. A file
 * whose checksums do not chain is a file that was edited, and importing it silently as though the
 * numbers were fine would defeat the only thing the checksums are there for.
 *
 * <p>Numbers in an imported payload come back as {@link Double}, as JSON numbers do. An imported
 * entry's id is derived from its contents exactly as {@link Journal#append} derives it, so a
 * journal read back from its own file carries the ids it was written with.
 */
public final class Import {

    private static final List<String> REQUIRED = List.of("seq", "kind", "day", "subject", "checksum");

    private Import() {}

    /**
     * Read one line of a journal file into an entry.
     *
     * @param lineNumber the line's number among the non blank lines, for the refusal message
     */
    public static Entry parseLine(String text, int lineNumber) {
        if (text == null || Text.trim(text).isEmpty()) {
            throw new BadInputException("line " + lineNumber + " is empty", Details.of("line", lineNumber));
        }
        Object parsed;
        try {
            parsed = JsonLine.parse(text);
        } catch (JsonLine.Unreadable notJson) {
            throw new BadInputException(
                    "line " + lineNumber + " is not readable as a record", Details.of("line", lineNumber));
        }
        if (!(parsed instanceof Map<?, ?> record)) {
            throw new BadInputException("line " + lineNumber + " is not a record", Details.of("line", lineNumber));
        }

        for (String field : REQUIRED) {
            if (record.get(field) == null) {
                throw new BadInputException(
                        "line " + lineNumber + " has no " + field, Details.of("line", lineNumber, "field", field));
            }
        }
        Object seq = record.get("seq");
        if (!(seq instanceof Double written) || !Guard.isWhole(written) || written < 1) {
            throw new BadInputException(
                    "line " + lineNumber + " has a bad sequence number", Details.of("line", lineNumber, "seq", seq));
        }
        Object kind = record.get("kind");
        if (!Journal.entryKinds().contains(kind)) {
            throw new BadInputException(
                    "line " + lineNumber + " carries an unknown kind " + Text.str(kind),
                    Details.of("line", lineNumber, "kind", kind, "known", Journal.entryKinds()));
        }
        Object day = record.get("day");
        if (!(day instanceof String dayText) || !Dates.isDay(dayText)) {
            throw new BadInputException(
                    "line " + lineNumber + " has a date that is not a real day",
                    Details.of("line", lineNumber, "day", day));
        }
        Map<String, Object> payload = payloadOf(record.get("payload"), lineNumber);

        int number = (int) written.doubleValue();
        String subject = Text.str(record.get("subject"));
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("seq", number);
        fields.put("kind", kind);
        fields.put("day", day);
        fields.put("subject", subject);
        fields.put("payload", payload);
        return new Entry(
                number,
                (String) kind,
                dayText,
                subject,
                payload,
                Checksum.digestOf(fields),
                Text.str(record.get("checksum")),
                Entry.UNSTATED_VERSION);
    }

    private static Map<String, Object> payloadOf(Object payload, int lineNumber) {
        if (payload == null) {
            return Map.of();
        }
        if (!(payload instanceof Map<?, ?> map)) {
            throw new BadInputException(
                    "line " + lineNumber + " has a payload that is not a record", Details.of("line", lineNumber));
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put((String) key, value));
        return copy;
    }

    /** Parse the whole file. Every line is read before any refusal about numbering is raised. */
    public static Journal parseJournal(String text, String name) {
        if (text == null) {
            throw new BadInputException("a journal file is text", Details.of("got", null));
        }
        String trimmed = Guard.requireLabel(name, "an imported journal needs a job name");
        List<String> lines = nonBlankLines(text);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            entries.add(parseLine(lines.get(i), i + 1));
        }
        for (int i = 0; i < entries.size(); i++) {
            int seq = entries.get(i).seq();
            if (seq != i + 1) {
                throw new ConflictException(
                        "line " + (i + 1) + " is numbered " + seq, Details.of("line", i + 1, "seq", seq));
            }
        }
        return new Journal(trimmed, entries);
    }

    /**
     * Check the chain the file claims. Reports the first line that does not follow from the one
     * before it, which is where the edit was.
     */
    public static ChainCheck verifyImported(Journal journal) {
        Guard.requireArray(journal == null ? null : journal.entries(), "entries");
        String previous = null;
        for (Entry entry : journal.entries()) {
            String expected = Checksum.chain(previous, Checksum.stableStringify(Journal.recordFields(entry)));
            if (!expected.equals(entry.checksum())) {
                return ChainCheck.brokenAt(
                        entry.seq(),
                        "entry " + entry.seq() + " does not follow from the one before it",
                        journal.entries().size());
            }
            previous = entry.checksum();
        }
        return ChainCheck.holds(journal.entries().size());
    }

    /**
     * Parse and verify in one step. Refuses a file whose chain is broken, because importing it
     * would put a record nobody can stand behind into the system.
     *
     * @throws ConflictException when the file's chain does not hold
     */
    public static Journal importJournal(String text, String name) {
        Journal journal = parseJournal(text, name);
        ChainCheck check = verifyImported(journal);
        if (!check.intact()) {
            throw new ConflictException(
                    "the file has been edited: " + check.reason().orElseThrow(),
                    Details.of("name", name, "brokenAt", check.brokenAt().orElseThrow()));
        }
        return journal;
    }

    /** What a file holds, without importing it. Every line is read before anything is reported. */
    public static FileSurvey surveyFile(String text) {
        List<String> lines = nonBlankLines(String.valueOf(text));
        List<LineProblem> problems = new ArrayList<>();
        Map<String, Integer> kinds = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            try {
                Entry entry = parseLine(lines.get(i), i + 1);
                kinds.merge(entry.kind(), 1, Integer::sum);
            } catch (BadInputException unreadable) {
                problems.add(new LineProblem(i + 1, unreadable.getMessage()));
            }
        }
        return new FileSurvey(lines.size(), kinds, problems, problems.isEmpty());
    }

    private static List<String> nonBlankLines(String text) {
        return Text.split(text, "\n").stream()
                .filter(line -> !Text.trim(line).isEmpty())
                .toList();
    }
}
