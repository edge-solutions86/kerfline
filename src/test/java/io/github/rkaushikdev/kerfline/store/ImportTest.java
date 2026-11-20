package io.github.rkaushikdev.kerfline.store;

import static io.github.rkaushikdev.kerfline.store.JournalTest.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ImportTest {

    private static final Journal JOURNAL = Journal.appendAll(
            Journal.makeJournal("C-4471"),
            List.of(
                    entry("joint", "2025-03-10", "J-118", Map.of("wallIn", 0.28)),
                    entry("shot", "2025-03-11", "J-118", Map.of("films", 3)),
                    entry("verdict", "2025-03-12", "J-118", Map.of("disposition", "accept"))));

    private static final String FILE = Journal.serialise(JOURNAL);

    /** Replace the first occurrence only, the way an edit to one line of the file would. */
    private static String once(String text, String from, String to) {
        int at = text.indexOf(from);
        assertThat(at).as("the file contains %s", from).isNotNegative();
        return text.substring(0, at) + to + text.substring(at + from.length());
    }

    @Nested
    class ParseLine {

        private final String good = FILE.split("\n")[0];

        @Test
        void readsALineTheSerialiserWrote() {
            Entry parsed = Import.parseLine(good, 1);
            assertThat(parsed.kind()).isEqualTo("joint");
            assertThat(parsed.subject()).isEqualTo("J-118");
            assertThat(parsed.payload().get("wallIn")).isEqualTo(0.28);
        }

        @Test
        void namesTheLineNumberOnAnEmptyLine() {
            assertThatThrownBy(() -> Import.parseLine("", 7)).hasMessageContaining("line 7 is empty");
        }

        @Test
        void namesTheLineNumberOnSomethingThatIsNotJson() {
            assertThatThrownBy(() -> Import.parseLine("not a record", 7))
                    .hasMessageContaining("line 7 is not readable");
        }

        @Test
        void refusesAnArrayOrABareValue() {
            assertThatThrownBy(() -> Import.parseLine("[1,2]", 3)).hasMessageContaining("line 3 is not a record");
            assertThatThrownBy(() -> Import.parseLine("42", 3)).hasMessageContaining("line 3 is not a record");
        }

        @Test
        void namesTheFieldThatIsMissing() {
            assertThatThrownBy(() -> Import.parseLine("{\"seq\":1,\"kind\":\"joint\",\"day\":\"2025-03-10\"}", 4))
                    .hasMessageContaining("line 4 has no subject");
        }

        @Test
        void refusesABadSequenceNumber() {
            String bad = "{\"seq\":0,\"kind\":\"joint\",\"day\":\"2025-03-10\",\"subject\":\"J-1\",\"checksum\":\"x\"}";
            assertThatThrownBy(() -> Import.parseLine(bad, 2)).hasMessageContaining("bad sequence number");
        }

        @Test
        void refusesAKindTheJournalDoesNotCarry() {
            String bad =
                    "{\"seq\":1,\"kind\":\"invoice\",\"day\":\"2025-03-10\",\"subject\":\"J-1\",\"checksum\":\"x\"}";
            assertThatThrownBy(() -> Import.parseLine(bad, 2)).hasMessageContaining("unknown kind invoice");
        }

        @Test
        void refusesADateThatIsNotARealDay() {
            String bad = "{\"seq\":1,\"kind\":\"joint\",\"day\":\"2025-02-30\",\"subject\":\"J-1\",\"checksum\":\"x\"}";
            assertThatThrownBy(() -> Import.parseLine(bad, 5))
                    .hasMessageContaining("line 5 has a date that is not a real day");
        }

        @Test
        void refusesTextThatIsAlmostButNotQuiteJson() {
            List<String> malformed = List.of(
                    "{\"seq\":1,}",
                    "{\"seq\" 1}",
                    "{seq:1}",
                    "{\"a\":\"\\x\"}",
                    "{\"a\":\"\\u12\"}",
                    "{\"a\":\"\\u12zz\"}",
                    "{\"a\":\"abc",
                    "{\"a\":\"ab\\",
                    "{\"a\":01}",
                    "{\"a\":1.}",
                    "{\"a\":tru}",
                    "[1,2",
                    "{\"a\":\"tab\there\"}",
                    "{\"a\":",
                    "{\"a\"",
                    "{",
                    "-",
                    "@");
            for (String line : malformed) {
                assertThatThrownBy(() -> Import.parseLine(line, 6))
                        .as(line)
                        .hasMessageContaining("line 6 is not readable as a record");
            }
        }

        @Test
        void readsEveryJsonEscapeAndLiteral() {
            String line = "{\"seq\":1,\"kind\":\"note\",\"day\":\"2025-03-10\",\"subject\":\"J-1\","
                    + "\"checksum\":\"x\",\"payload\":{\"text\":\"a\\/b\\b\\f\\r\\u00e9\","
                    + "\"flags\":[true,false,null,[],{}],\"n\":-1.5e2}}";
            Entry parsed = Import.parseLine(line, 1);
            assertThat(parsed.payload().get("text")).isEqualTo("a/b\b\f\r\u00e9");
            assertThat(parsed.payload().get("flags")).isEqualTo(Arrays.asList(true, false, null, List.of(), Map.of()));
            assertThat(parsed.payload().get("n")).isEqualTo(-150.0);
        }

        @Test
        void refusesAPayloadThatIsNotARecord() {
            String line = "{\"seq\":1,\"kind\":\"note\",\"day\":\"2025-03-10\",\"subject\":\"J-1\","
                    + "\"checksum\":\"x\",\"payload\":[1]}";
            assertThatThrownBy(() -> Import.parseLine(line, 2))
                    .hasMessageContaining("line 2 has a payload that is not a record");
        }

        @Test
        void defaultsAMissingPayloadToNothingRatherThanRefusing() {
            String bare = "{\"seq\":1,\"kind\":\"note\",\"day\":\"2025-03-10\",\"subject\":\"J-1\",\"checksum\":\"x\"}";
            assertThat(Import.parseLine(bare, 1).payload()).isEmpty();
        }
    }

    @Nested
    class ParseJournal {

        @Test
        void readsBackWhatTheSerialiserWrote() {
            Journal back = Import.parseJournal(FILE, "C-4471");
            assertThat(back.entries()).hasSize(3);
            assertThat(back.name()).isEqualTo("C-4471");
        }

        @Test
        void ignoresBlankLines() {
            assertThat(Import.parseJournal(FILE + "\n\n", "C-4471").entries()).hasSize(3);
        }

        @Test
        void readsAnEmptyFileAsAnEmptyJournal() {
            assertThat(Import.parseJournal("", "C-4471").entries()).isEmpty();
        }

        @Test
        void refusesAFileWhoseLinesAreOutOfSequence() {
            List<String> lines = new ArrayList<>(Arrays.asList(FILE.split("\n")));
            Collections.reverse(lines);
            String reordered = String.join("\n", lines);
            assertThatThrownBy(() -> Import.parseJournal(reordered, "C-4471"))
                    .hasMessageContaining("line 1 is numbered 3");
        }

        @Test
        void refusesAnImportWithNoJobName() {
            assertThatThrownBy(() -> Import.parseJournal(FILE, " ")).hasMessageContaining("needs a job name");
        }

        @Test
        void refusesSomethingThatIsNotText() {
            assertThatThrownBy(() -> Import.parseJournal(null, "C-4471")).hasMessageContaining("is text");
        }
    }

    @Nested
    class VerifyImported {

        @Test
        void findsAnUntouchedFileIntact() {
            assertThat(Import.verifyImported(Import.parseJournal(FILE, "C-4471"))
                            .intact())
                    .isTrue();
        }

        @Test
        void findsAnEmptyFileIntact() {
            assertThat(Import.verifyImported(Import.parseJournal("", "C-4471")).intact())
                    .isTrue();
        }

        @Test
        void catchesAPayloadEditedInTheFile() {
            String edited = once(FILE, "\"disposition\":\"accept\"", "\"disposition\":\"reject\"");
            ChainCheck check = Import.verifyImported(Import.parseJournal(edited, "C-4471"));
            assertThat(check.intact()).isFalse();
            assertThat(check.brokenAt()).hasValue(3);
        }

        @Test
        void catchesAnEditInTheMiddleAndPointsAtIt() {
            String edited = once(FILE, "\"films\":3", "\"films\":9");
            assertThat(Import.verifyImported(Import.parseJournal(edited, "C-4471"))
                            .brokenAt())
                    .hasValue(2);
        }

        @Test
        void catchesASubjectThatWasChanged() {
            String edited = once(FILE, "\"subject\":\"J-118\"", "\"subject\":\"J-119\"");
            assertThat(Import.verifyImported(Import.parseJournal(edited, "C-4471"))
                            .intact())
                    .isFalse();
        }
    }

    @Nested
    class ImportJournal {

        @Test
        void importsAGoodFile() {
            assertThat(Import.importJournal(FILE, "C-4471").entries()).hasSize(3);
        }

        @Test
        void refusesAnEditedFileRatherThanImportingIt() {
            String edited = once(FILE, "\"films\":3", "\"films\":9");
            assertThatThrownBy(() -> Import.importJournal(edited, "C-4471"))
                    .hasMessageContaining("the file has been edited");
        }

        @Test
        void roundTripsThroughTheSerialiser() {
            Journal back = Import.importJournal(FILE, "C-4471");
            assertThat(back.entries().stream().map(Entry::checksum))
                    .containsExactlyElementsOf(
                            JOURNAL.entries().stream().map(Entry::checksum).toList());
        }

        @Test
        void roundTripsNestedPayloadsAndEscapedTextIntact() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("note", "said \"reshoot\" \\ twice\nthen\tstopped é \u0001");
            payload.put("readings", List.of(2.1, -0.5, 1e-7, 3));
            payload.put("checked", true);
            payload.put("by", null);
            payload.put("nested", Map.of("films", List.of(Map.of("id", "F-1")), "empty", List.of()));
            Journal written =
                    Journal.appendAll(Journal.makeJournal("C-9"), List.of(entry("note", "2025-03-10", "J-1", payload)));
            Journal back = Import.importJournal(Journal.serialise(written), "C-9");
            assertThat(back.entries().get(0).checksum())
                    .isEqualTo(written.entries().get(0).checksum());
            assertThat(back.entries().get(0).id())
                    .isEqualTo(written.entries().get(0).id());
            assertThat(back.entries().get(0).payload().get("note")).isEqualTo(payload.get("note"));
            String line = Journal.serialise(written);
            assertThatThrownBy(() -> Import.parseLine(line + " x", 1)).hasMessageContaining("not readable");
        }
    }

    @Nested
    class SurveyFile {

        @Test
        void saysWhatAGoodFileHolds() {
            FileSurvey survey = Import.surveyFile(FILE);
            assertThat(survey.lines()).isEqualTo(3);
            assertThat(survey.readable()).isTrue();
            assertThat(survey.kinds()).isEqualTo(Map.of("joint", 1, "shot", 1, "verdict", 1));
        }

        @Test
        void readsEveryLineBeforeReportingRatherThanStoppingAtTheFirst() {
            FileSurvey survey = Import.surveyFile(String.join("\n", "nonsense", "also nonsense"));
            assertThat(survey.problems()).hasSize(2);
            assertThat(survey.readable()).isFalse();
        }

        @Test
        void namesTheLineNumbersOfTheProblems() {
            String mixed = String.join("\n", FILE.split("\n")[0], "nonsense");
            assertThat(Import.surveyFile(mixed).problems().get(0).line()).isEqualTo(2);
        }

        @Test
        void surveysAnEmptyFile() {
            assertThat(Import.surveyFile("")).isEqualTo(new FileSurvey(0, Map.of(), List.of(), true));
        }
    }
}
