package io.github.rkaushikdev.kerfline.integration;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.kerfline.job.Audit;
import io.github.rkaushikdev.kerfline.job.AuditFinding;
import io.github.rkaushikdev.kerfline.job.AuditRequest;
import io.github.rkaushikdev.kerfline.job.AuditResult;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.joint.JointInput;
import io.github.rkaushikdev.kerfline.joint.JointRegister;
import io.github.rkaushikdev.kerfline.pipeline.Close;
import io.github.rkaushikdev.kerfline.pipeline.JointState;
import io.github.rkaushikdev.kerfline.repair.ChainStatus;
import io.github.rkaushikdev.kerfline.report.Coverage;
import io.github.rkaushikdev.kerfline.report.CoverageReport;
import io.github.rkaushikdev.kerfline.report.CoveredJoint;
import io.github.rkaushikdev.kerfline.report.CsvColumn;
import io.github.rkaushikdev.kerfline.report.Daily;
import io.github.rkaushikdev.kerfline.report.DailyReport;
import io.github.rkaushikdev.kerfline.report.DailyRequest;
import io.github.rkaushikdev.kerfline.report.Export;
import io.github.rkaushikdev.kerfline.report.GroupCoverage;
import io.github.rkaushikdev.kerfline.report.JobSummary;
import io.github.rkaushikdev.kerfline.report.Register;
import io.github.rkaushikdev.kerfline.report.RegisterReport;
import io.github.rkaushikdev.kerfline.report.RegisterReportRow;
import io.github.rkaushikdev.kerfline.report.Summary;
import io.github.rkaushikdev.kerfline.report.SummaryJoint;
import io.github.rkaushikdev.kerfline.store.Entry;
import io.github.rkaushikdev.kerfline.store.EntryDraft;
import io.github.rkaushikdev.kerfline.store.Journal;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Every report on one job, cross checked against every other.
 *
 * <p>Each report has its own tests and each of them passes. What none of them can see is the two
 * reports disagreeing: the summary saying five joints were shot while the register report shows
 * four, because one of them counted entries and the other counted subjects. That disagreement is
 * invisible to a unit test and obvious to a client reading both pages, which is the worst possible
 * place to find it.
 *
 * <p>So this asserts the reports agree, and it deliberately derives the same number two different
 * ways to do it.
 */
class ReportsTest {

    private static final String JOB = "C-4471";

    private static final JointRegister REGISTER = io.github.rkaushikdev.kerfline.joint.Register.addJoints(
            io.github.rkaushikdev.kerfline.joint.Register.makeRegister(JOB),
            List.of(
                    joint("J-1", "A17", "SP-1", 6),
                    joint("J-2", "A17", "SP-1", 6),
                    joint("J-3", "B04", "SP-2", 6),
                    joint("J-10", "B04", "SP-2", 2)));

    // J-1 accepted, J-2 repaired then accepted, J-3 shot but unread, J-10 not shot.
    private static final Journal JOURNAL = Journal.appendAll(
            Journal.makeJournal(JOB),
            List.of(
                    EntryDraft.of("shot", "2025-03-10", "J-1", Map.of("films", 3)),
                    EntryDraft.of("shot", "2025-03-10", "J-2", Map.of("films", 3)),
                    EntryDraft.of("shot", "2025-03-10", "J-3", Map.of("films", 3)),
                    EntryDraft.of("verdict", "2025-03-11", "J-1", Map.of("disposition", "accept", "clause", "9.3")),
                    EntryDraft.of("verdict", "2025-03-11", "J-2", Map.of("disposition", "repair", "clause", "9.3.6")),
                    EntryDraft.of("repair", "2025-03-13", "J-2", Map.of("at", "3:00")),
                    EntryDraft.of("shot", "2025-03-14", "J-2", Map.of("films", 3)),
                    EntryDraft.of("verdict", "2025-03-14", "J-2", Map.of("disposition", "accept", "clause", "9.3"))));

    private static final RegisterReport REGISTER_REPORT = Register.buildRegisterReport(REGISTER, JOURNAL);

    private static final JobSummary SUMMARY = Summary.buildSummary(
            JOB,
            REGISTER.joints().stream()
                    .map(joint -> SummaryJoint.of(
                            joint.id(),
                            joint.welder().orElse(null),
                            state(joint).everShot(),
                            disposition(joint)))
                    .toList(),
            "ten");

    private static final CoverageReport COVERAGE = Coverage.buildCoverage(
            JOB,
            REGISTER.joints().stream()
                    .map(joint -> CoveredJoint.of(
                            joint.id(),
                            joint.welder().orElse(null),
                            joint.spool().orElse(null),
                            state(joint).everShot()))
                    .toList(),
            "ten");

    private static final AuditResult AUDIT = Audit.runAudit(AuditRequest.builder()
            .job(JOB)
            .register(REGISTER)
            .journal(JOURNAL)
            .level("ten")
            .day("2025-03-20")
            .build());

    private static JointInput joint(String id, String welder, String spool, double nominalSize) {
        return JointInput.builder()
                .id(id)
                .welder(welder)
                .spool(spool)
                .nominalSize(nominalSize)
                .schedule(40)
                .build();
    }

    private static JointState state(Joint joint) {
        return Close.jointState(JOURNAL, joint.id());
    }

    /** The disposition the register's repair chain implies, as the original built it. */
    private static String disposition(Joint joint) {
        ChainStatus status = state(joint).status();
        return status == ChainStatus.ACCEPTED ? "accept" : status == ChainStatus.CUT_OUT ? "reject" : null;
    }

    private static long entriesOf(String kind) {
        return JOURNAL.entries().stream().filter(e -> e.kind().equals(kind)).count();
    }

    private static Map<String, Object> asMap(RegisterReportRow row) {
        Map<String, Object> cells = new LinkedHashMap<>();
        cells.put("id", row.id());
        cells.put("spool", row.spool());
        cells.put("welder", row.welder());
        cells.put("size", row.size());
        cells.put("wallIn", row.wallIn());
        cells.put("shotOn", row.shotOn());
        cells.put("disposition", row.disposition());
        cells.put("clause", row.clause());
        cells.put("state", row.state());
        return cells;
    }

    /** The row as the export takes it: the register report row with the size and schedule added. */
    private static Map<String, Object> forExport(RegisterReportRow row) {
        Map<String, Object> cells = asMap(row);
        cells.put("nominalSize", row.size());
        cells.put("schedule", "");
        return cells;
    }

    private static RegisterReportRow rowFor(String id) {
        return REGISTER_REPORT.rows().stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    class TheReportsAgreeOnHowManyJointsThereAre {

        @Test
        void theRegisterReportHasARowPerRegisteredJoint() {
            assertThat(REGISTER_REPORT.rows()).hasSize(REGISTER.joints().size());
        }

        @Test
        void theSummaryCountsTheSameJoints() {
            assertThat(SUMMARY.counts().joints()).isEqualTo(REGISTER.joints().size());
        }

        @Test
        void theCoverageReportCountsTheSameJoints() {
            assertThat(COVERAGE.overall().totalJoints())
                    .isEqualTo(REGISTER.joints().size());
        }

        @Test
        void theAuditCheckedTheSameJoints() {
            assertThat(AUDIT.checked().joints()).isEqualTo(REGISTER.joints().size());
        }
    }

    @Nested
    class TheReportsAgreeOnWhatHasBeenShot {

        private final long shotFromRegisterReport = REGISTER_REPORT.rows().stream()
                .filter(row -> !row.shotOn().isEmpty())
                .count();

        @Test
        void theRegisterReportAndTheSummaryAgree() {
            assertThat((long) SUMMARY.counts().shot()).isEqualTo(shotFromRegisterReport);
        }

        @Test
        void theCoverageReportAgreesWithBoth() {
            assertThat((long) COVERAGE.overall().shotJoints()).isEqualTo(shotFromRegisterReport);
        }

        @Test
        void countsAJointShotTwiceAsOneJointNotTwo() {
            // J-2 has two shot entries. Counting entries rather than subjects would
            // give four shot joints out of four registered, which is wrong twice.
            assertThat(shotFromRegisterReport).isEqualTo(3);
            assertThat(entriesOf("shot")).isEqualTo(4);
        }
    }

    @Nested
    class TheReportsAgreeOnTheDispositions {

        @Test
        void theRegisterStatesAndTheSummaryCountsLineUp() {
            Map<String, Integer> states = Register.stateCounts(REGISTER_REPORT);
            assertThat(states.get("accept")).isEqualTo(SUMMARY.counts().accepted());
        }

        @Test
        void aJointRepairedAndThenAcceptedCountsOnceAsAccepted() {
            assertThat(rowFor("J-2").state()).isEqualTo("accept");
            assertThat(JOURNAL.entries().stream()
                            .filter(e ->
                                    e.kind().equals("verdict") && e.subject().equals("J-2")))
                    .hasSize(2);
            assertThat(SUMMARY.counts().repaired()).isZero();
        }

        @Test
        void aJointShotButUnreadIsNotCountedAsJudgedAnywhere() {
            assertThat(rowFor("J-3").state()).isEqualTo("awaiting reading");
            assertThat(SUMMARY.outstanding()).isEqualTo(1);
        }

        @Test
        void theAuditFindsExactlyTheUnreadJoint() {
            AuditFinding found = AUDIT.findings().stream()
                    .filter(f -> f.check().equals("films not read"))
                    .findFirst()
                    .orElseThrow();
            assertThat(found.items()).containsExactly("J-3");
        }
    }

    @Nested
    class TheReportsAgreeOnCoverage {

        @Test
        void theSummaryAndTheCoverageReportReachTheSameFraction() {
            assertThat(SUMMARY.coverage().achievedFraction())
                    .isCloseTo(COVERAGE.overall().achievedFraction(), digits(6));
        }

        @Test
        void bothAgreeOnWhetherTheJobIsCompliant() {
            assertThat(SUMMARY.coverage().compliant())
                    .isEqualTo(COVERAGE.overall().compliant());
        }

        @Test
        void thePerWelderBreakdownAddsBackToTheWhole() {
            int total =
                    COVERAGE.byWelder().stream().mapToInt(GroupCoverage::total).sum();
            int shot =
                    COVERAGE.byWelder().stream().mapToInt(GroupCoverage::shot).sum();
            assertThat(total).isEqualTo(COVERAGE.overall().totalJoints());
            assertThat(shot).isEqualTo(COVERAGE.overall().shotJoints());
        }

        @Test
        void thePerSpoolBreakdownAddsBackToTheSameWhole() {
            int total =
                    COVERAGE.bySpool().stream().mapToInt(GroupCoverage::total).sum();
            assertThat(total).isEqualTo(COVERAGE.overall().totalJoints());
        }
    }

    @Nested
    class TheDailyReportAddsUpToTheJob {

        // Every day the journal touched, not a guess at which ones matter. Missing
        // one is exactly how a set of daily reports stops summing to the job.
        private final List<String> days =
                JOURNAL.entries().stream().map(Entry::day).distinct().sorted().toList();
        private final List<DailyReport> dailies = days.stream()
                .map(day -> Daily.buildDaily(DailyRequest.builder()
                        .job(JOB)
                        .day(day)
                        .journal(JOURNAL)
                        .source(null)
                        .build()))
                .toList();

        @Test
        void coversEveryDayTheJournalTouched() {
            assertThat(days).containsExactly("2025-03-10", "2025-03-11", "2025-03-13", "2025-03-14");
        }

        @Test
        void theFilmsExposedAcrossTheDaysMatchTheJournal() {
            double fromDays = dailies.stream()
                    .mapToDouble(daily -> Daily.dailyTotals(daily).films())
                    .sum();
            double fromJournal = JOURNAL.entries().stream()
                    .filter(e -> e.kind().equals("shot"))
                    .mapToDouble(e -> ((Number) e.payload().getOrDefault("films", 0)).doubleValue())
                    .sum();
            assertThat(fromDays).isEqualTo(fromJournal);
        }

        @Test
        void theReadingsAcrossTheDaysMatchTheVerdicts() {
            int read = dailies.stream()
                    .mapToInt(daily -> Daily.dailyTotals(daily).read())
                    .sum();
            assertThat((long) read).isEqualTo(entriesOf("verdict"));
        }

        @Test
        void theJointsShotAcrossTheDaysMatchTheShotEntries() {
            int shot = dailies.stream()
                    .mapToInt(daily -> daily.jointsShot().size())
                    .sum();
            assertThat((long) shot).isEqualTo(entriesOf("shot"));
        }
    }

    @Nested
    class TheExportCarriesWhatTheRegisterReportShows {

        private final String csv = Export.exportRegister(
                REGISTER_REPORT.rows().stream().map(ReportsTest::forExport).toList());

        @Test
        void checksumsClean() {
            assertThat(Export.verifyChecksum(csv).intact()).isTrue();
        }

        @Test
        void hasALinePerJointPlusAHeaderAndAChecksum() {
            assertThat(Text.split(csv, "\n")).hasSize(REGISTER_REPORT.rows().size() + 2);
        }

        @Test
        void namesEveryJointOnTheRegister() {
            for (Joint joint : REGISTER.joints()) {
                assertThat(csv).contains(joint.id());
            }
        }

        @Test
        void exportsTheSameRowsWhicheverWayTheyAreShaped() {
            String plain = Export.toCsv(
                    REGISTER_REPORT.rows().stream().map(ReportsTest::asMap).toList(), List.of(CsvColumn.of("id")));
            assertThat(Text.split(plain, "\n")).hasSize(REGISTER_REPORT.rows().size() + 1);
        }
    }

    @Nested
    class EveryReportRendersInsideItsPage {

        private record Page(String text, int width) {}

        private final List<Page> pages = List.of(
                new Page(Register.renderRegisterReport(REGISTER_REPORT), 96),
                new Page(Summary.renderSummary(SUMMARY), 78),
                new Page(Coverage.renderCoverage(COVERAGE), 78),
                new Page(
                        Daily.renderDaily(Daily.buildDaily(DailyRequest.builder()
                                .job(JOB)
                                .day("2025-03-10")
                                .journal(JOURNAL)
                                .source(null)
                                .build())),
                        78),
                new Page(Audit.renderAudit(AUDIT), 78));

        @Test
        void printsSomethingOnEveryPage() {
            for (Page page : pages) {
                assertThat(page.text().length()).isGreaterThan(50);
            }
        }

        @Test
        void keepsEveryLineInsideTheWidthThatPageIsSetAt() {
            for (Page page : pages) {
                for (String line : Text.split(page.text(), "\n")) {
                    assertThat(line.length()).isLessThanOrEqualTo(page.width());
                }
            }
        }

        @Test
        void namesTheJobOnEveryPage() {
            for (Page page : pages) {
                assertThat(page.text()).contains(JOB);
            }
        }
    }
}
