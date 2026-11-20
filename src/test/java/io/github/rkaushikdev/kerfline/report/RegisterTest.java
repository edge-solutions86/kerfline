package io.github.rkaushikdev.kerfline.report;

import static io.github.rkaushikdev.kerfline.joint.Register.addJoints;
import static io.github.rkaushikdev.kerfline.joint.Register.makeRegister;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.joint.JointInput;
import io.github.rkaushikdev.kerfline.joint.JointRegister;
import io.github.rkaushikdev.kerfline.store.CurrentDisposition;
import io.github.rkaushikdev.kerfline.store.EntryDraft;
import io.github.rkaushikdev.kerfline.store.Journal;
import io.github.rkaushikdev.kerfline.util.Column;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RegisterTest {

    private static JointInput onRegister(String id, String spool, String welder, double size) {
        return JointInput.builder()
                .id(id)
                .spool(spool)
                .welder(welder)
                .nominalSize(size)
                .schedule(40)
                .build();
    }

    private static final JointRegister REGISTER = addJoints(
            makeRegister("C-4471"),
            List.of(
                    onRegister("J-2", "SP-1", "A17", 6),
                    onRegister("J-10", "SP-1", "A17", 6),
                    onRegister("J-3", "SP-2", "B04", 2)));

    private static EntryDraft entry(String kind, String day, String subject) {
        return EntryDraft.of(kind, day, subject);
    }

    private static EntryDraft entry(String kind, String day, String subject, Map<String, ?> payload) {
        return EntryDraft.of(kind, day, subject, payload);
    }

    // The journal only takes entries in date order, so the fixture is written
    // the way the day actually ran rather than one joint at a time.
    private static final Journal JOURNAL = Journal.appendAll(
            Journal.makeJournal("C-4471"),
            List.of(
                    entry("shot", "2025-03-10", "J-2"),
                    entry("shot", "2025-03-10", "J-3"),
                    entry("verdict", "2025-03-11", "J-2", Map.of("disposition", "repair", "clause", "9.3.6")),
                    entry("verdict", "2025-03-14", "J-2", Map.of("disposition", "accept", "clause", "9.3"))));

    private static final RegisterReport REPORT = Register.buildRegisterReport(REGISTER, JOURNAL);

    private static RegisterReportRow row(String id) {
        return REPORT.rows().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    @Nested
    class BuildRegisterReport {

        @Test
        void makesOneRowPerJointOnTheRegister() {
            assertThat(REPORT.rows()).hasSize(3);
        }

        @Test
        void readsInRegisterOrder() {
            assertThat(REPORT.rows().stream().map(RegisterReportRow::id)).containsExactly("J-2", "J-3", "J-10");
        }

        @Test
        void takesWhatAJointIsFromTheRegister() {
            RegisterReportRow row = row("J-2");
            assertThat(row.welder()).isEqualTo("A17");
            assertThat(row.size()).isEqualTo("6/40");
            assertThat(row.wallIn()).isEqualTo("0.280");
        }

        @Test
        void takesWhatHappenedFromTheJournal() {
            RegisterReportRow row = row("J-2");
            assertThat(row.shotOn()).isEqualTo("2025-03-10");
            assertThat(row.disposition()).isEqualTo("accept");
            assertThat(row.clause()).isEqualTo("9.3");
        }

        @Test
        void reportsTheLatestVerdictAndNotTheFirst() {
            RegisterReportRow row = row("J-2");
            assertThat(row.disposition()).isEqualTo("accept");
            assertThat(row.state()).isEqualTo("accept");
        }

        @Test
        void marksAJointShotButNotRead() {
            assertThat(row("J-3").state()).isEqualTo("awaiting reading");
        }

        @Test
        void marksAJointThatHasNotBeenShotAtAll() {
            assertThat(row("J-10").state()).isEqualTo("not shot");
        }

        @Test
        void findsNothingStrayWhenTheJournalStaysOnTheRegister() {
            assertThat(REPORT.strays()).isEmpty();
        }

        @Test
        void surfacesAJointTheJournalKnowsAndTheRegisterDoesNot() {
            Journal withStray = Journal.appendAll(JOURNAL, List.of(entry("shot", "2025-03-15", "J-99")));
            RegisterReport other = Register.buildRegisterReport(REGISTER, withStray);
            assertThat(other.strays()).containsExactly("J-99");
        }

        @Test
        void refusesAReportWithNoRegisterOrNoJournal() {
            assertThatThrownBy(() -> Register.buildRegisterReport(null, JOURNAL))
                    .hasMessageContaining("needs a register");
            assertThatThrownBy(() -> Register.buildRegisterReport(REGISTER, null))
                    .hasMessageContaining("needs a journal");
        }
    }

    @Nested
    class Filtering {

        @Test
        void listsOnlyWhatIsStillOwing() {
            assertThat(Register.outstandingRows(REPORT).stream().map(RegisterReportRow::id))
                    .containsExactly("J-3", "J-10");
        }

        @Test
        void listsOneWelderWork() {
            assertThat(Register.byWelderRows(REPORT, "A17").stream().map(RegisterReportRow::id))
                    .containsExactly("J-2", "J-10");
        }

        @Test
        void countsTheStates() {
            assertThat(Register.stateCounts(REPORT))
                    .isEqualTo(Map.of("accept", 1, "awaiting reading", 1, "not shot", 1));
        }
    }

    @Nested
    class RenderRegisterReport {

        @Test
        void printsTheHeadingAndTheJob() {
            String text = Register.renderRegisterReport(REPORT);
            assertThat(text).contains("JOINT REGISTER");
            assertThat(text).contains("C-4471");
        }

        @Test
        void printsARowPerJointUnderTheColumnHeadings() {
            String text = Register.renderRegisterReport(REPORT);
            assertThat(text).contains("joint");
            assertThat(text).contains("welder");
            assertThat(text).contains("J-10");
        }

        @Test
        void printsOnlyTheOutstandingOnesWhenAsked() {
            String text = Register.renderRegisterReport(REPORT, true);
            assertThat(text).contains("OUTSTANDING JOINTS");
            assertThat(text).doesNotContain("J-2 ");
        }

        @Test
        void saysSoPlainlyWhenThereIsNothingOutstanding() {
            RegisterReport clean = Register.buildRegisterReport(
                    addJoints(
                            makeRegister("C-1"),
                            List.of(JointInput.builder()
                                    .id("J-1")
                                    .nominalSize(6)
                                    .schedule(40)
                                    .build())),
                    Journal.appendAll(
                            Journal.makeJournal("C-1"),
                            List.of(
                                    entry("shot", "2025-03-10", "J-1"),
                                    entry("verdict", "2025-03-11", "J-1", Map.of("disposition", "accept")))));
            assertThat(Register.renderRegisterReport(clean, true)).contains("Nothing to report");
        }

        @Test
        void callsOutAStrayJointAtTheFoot() {
            Journal withStray = Journal.appendAll(JOURNAL, List.of(entry("shot", "2025-03-15", "J-99")));
            RegisterReport other = Register.buildRegisterReport(REGISTER, withStray);
            String text = Register.renderRegisterReport(other);
            assertThat(text).contains("NOT ON THE REGISTER");
            assertThat(text).contains("J-99");
        }

        @Test
        void keepsEveryLineInsideThePaperWidth() {
            for (String line : Register.renderRegisterReport(REPORT).split("\n")) {
                assertThat(line.length()).isLessThanOrEqualTo(96);
            }
        }
    }

    @Nested
    class ExportRowsAndTheColumns {

        @Test
        void namesTheColumnsForThePrintedTable() {
            assertThat(Register.registerColumns().stream().map(Column::key)).contains("welder");
        }

        @Test
        void handsBackCopiesOfTheColumns() {
            List<Column> columns = Register.registerColumns();
            assertThatThrownBy(() -> columns.set(0, columns.get(0).label("changed")))
                    .isInstanceOf(UnsupportedOperationException.class);
            Column changed = columns.get(0).label("changed");
            assertThat(changed.label()).isEqualTo("changed");
            assertThat(Register.registerColumns().get(0).label()).isEqualTo("joint");
        }

        @Test
        void shapesTheRowsForTheCsvExport() {
            List<Map<String, String>> rows = Register.exportRows(REPORT);
            assertThat(rows.get(0)).containsKey("disposition");
            assertThat(rows.get(0)).containsKey("day");
            assertThat(rows).hasSize(3);
        }
    }

    @Nested
    class JudgedJoints {

        @Test
        void readsTheCurrentDispositionOfEverythingJudged() {
            List<CurrentDisposition> judged = Register.judgedJoints(JOURNAL);
            assertThat(judged).hasSize(1);
            assertThat(judged.get(0).subject()).isEqualTo("J-2");
            assertThat(judged.get(0).disposition()).isEqualTo(Optional.of("accept"));
        }
    }
}
