package io.github.rkaushikdev.kerfline.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.joint.Heat;
import io.github.rkaushikdev.kerfline.joint.HeatInput;
import io.github.rkaushikdev.kerfline.joint.JointInput;
import io.github.rkaushikdev.kerfline.joint.JointRegister;
import io.github.rkaushikdev.kerfline.joint.PipeHeat;
import io.github.rkaushikdev.kerfline.joint.Register;
import io.github.rkaushikdev.kerfline.report.Ncr;
import io.github.rkaushikdev.kerfline.report.NewNcr;
import io.github.rkaushikdev.kerfline.report.Nonconformance;
import io.github.rkaushikdev.kerfline.store.Entry;
import io.github.rkaushikdev.kerfline.store.EntryDraft;
import io.github.rkaushikdev.kerfline.store.Journal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AuditTest {

    private static EntryDraft entry(String kind, String day, String subject) {
        return EntryDraft.of(kind, day, subject);
    }

    private static EntryDraft entry(String kind, String day, String subject, Map<String, ?> payload) {
        return EntryDraft.of(kind, day, subject, payload);
    }

    private static JointInput onRegister(String id) {
        return JointInput.builder()
                .id(id)
                .welder("A17")
                .spool("SP-1")
                .nominalSize(6)
                .schedule(40)
                .build();
    }

    private static final JointRegister REGISTER =
            Register.addJoints(Register.makeRegister("C-4471"), List.of(onRegister("J-1"), onRegister("J-2")));

    private static final Journal CLEAN = Journal.appendAll(
            Journal.makeJournal("C-4471"),
            List.of(
                    entry("shot", "2025-03-10", "J-1"),
                    entry("shot", "2025-03-10", "J-2"),
                    entry("verdict", "2025-03-11", "J-1", Map.of("disposition", "accept")),
                    entry("verdict", "2025-03-11", "J-2", Map.of("disposition", "accept"))));

    private static AuditRequest.Builder audit(Journal journal) {
        return AuditRequest.builder().job("C-4471").register(REGISTER).journal(journal);
    }

    /** The clean journal with the second entry's subject changed after it was written. */
    private static Journal tampered() {
        List<Entry> entries = CLEAN.entries().stream()
                .map(e -> e.seq() == 2
                        ? new Entry(e.seq(), e.kind(), e.day(), "J-9", e.payload(), e.id(), e.checksum(), e.version())
                        : e)
                .toList();
        return new Journal(CLEAN.name(), entries);
    }

    private static Optional<AuditFinding> finding(AuditResult audit, String check) {
        return audit.findings().stream().filter(f -> f.check().equals(check)).findFirst();
    }

    private static boolean hasFinding(AuditResult audit, String check) {
        return finding(audit, check).isPresent();
    }

    private static Journal withStray() {
        return Journal.appendAll(CLEAN, List.of(entry("shot", "2025-03-12", "J-99")));
    }

    @Nested
    class RunAuditOnACleanJob {

        private final AuditResult result =
                Audit.runAudit(audit(CLEAN).day("2025-03-20").build());

        @Test
        void findsNothing() {
            assertThat(result.clean()).isTrue();
            assertThat(result.findings()).isEmpty();
        }

        @Test
        void stillSaysWhatItChecked() {
            assertThat(result.checked().joints()).isEqualTo(2);
            assertThat(result.checked().entries()).isEqualTo(4);
        }

        @Test
        void namesTheSeveritiesItGradesBy() {
            assertThat(Audit.severities())
                    .containsExactly(AuditSeverity.MINOR, AuditSeverity.MAJOR, AuditSeverity.SEVERE);
            assertThat(Audit.severities().stream().map(AuditSeverity::label))
                    .containsExactly("minor", "major", "severe");
        }

        @Test
        void readsARequestBackThroughACopy() {
            AuditRequest request = audit(CLEAN).day("2025-03-20").level("ten").build();
            assertThat(request.toBuilder().build()).isEqualTo(request);
        }

        @Test
        void refusesAnAuditWithNoJob() {
            assertThatThrownBy(() -> Audit.runAudit(audit(CLEAN).job(" ").build()))
                    .hasMessageContaining("names the job");
        }
    }

    @Nested
    class TheChecks {

        @Test
        void catchesABrokenChainAsSevere() {
            AuditResult result = Audit.runAudit(audit(tampered()).build());
            assertThat(finding(result, "record integrity").orElseThrow().severity())
                    .isEqualTo(AuditSeverity.SEVERE);
        }

        @Test
        void catchesAJointTheJournalKnowsAndTheRegisterDoesNot() {
            AuditResult result = Audit.runAudit(audit(withStray()).build());
            assertThat(finding(result, "joints not on the register")
                            .orElseThrow()
                            .items())
                    .contains("J-99");
        }

        @Test
        void catchesFilmsThatWereShotAndNeverRead() {
            Journal unread =
                    Journal.appendAll(Journal.makeJournal("C-4471"), List.of(entry("shot", "2025-03-10", "J-1")));
            assertThat(hasFinding(Audit.runAudit(audit(unread).build()), "films not read"))
                    .isTrue();
        }

        @Test
        void catchesAJointReopenedAfterItWasAccepted() {
            Journal reopened = Journal.appendAll(
                    CLEAN, List.of(entry("verdict", "2025-03-20", "J-1", Map.of("disposition", "repair"))));
            AuditResult result = Audit.runAudit(audit(reopened).build());
            assertThat(finding(result, "joints reopened after acceptance")
                            .orElseThrow()
                            .items()
                            .get(0))
                    .contains("J-1");
        }

        @Test
        void catchesAJointWithNoMillReportBehindIt() {
            List<PipeHeat> heats = List.of(Heat.makeHeat(HeatInput.builder()
                    .number("H-1")
                    .materialId("CARBON_STEEL")
                    .receivedOn("2025-02-01")
                    .build()));
            JointRegister traced = new JointRegister(
                    REGISTER.job(),
                    REGISTER.joints().stream()
                            .map(joint -> Heat.assignHeats(joint, "H-1"))
                            .toList());
            AuditResult result =
                    Audit.runAudit(audit(CLEAN).register(traced).heats(heats).build());
            assertThat(hasFinding(result, "material traceability")).isTrue();
        }

        @Test
        void catchesCoverageShortOfWhatWasCalledFor() {
            Journal barely = Journal.appendAll(
                    Journal.makeJournal("C-4471"),
                    List.of(
                            entry("shot", "2025-03-10", "J-1"),
                            entry("verdict", "2025-03-11", "J-1", Map.of("disposition", "accept"))));
            AuditResult result = Audit.runAudit(audit(barely).level("full").build());
            assertThat(hasFinding(result, "coverage")).isTrue();
        }

        @Test
        void catchesANonconformanceOpenPastItsDueDate() {
            Ncr ncr = Nonconformance.raiseNcr(NewNcr.builder()
                    .number(1)
                    .job("C-4471")
                    .kind("procedure")
                    .detail("wrong code applied")
                    .raisedOn("2025-03-01")
                    .raisedBy("K. Osei")
                    .build());
            AuditResult result = Audit.runAudit(
                    audit(CLEAN).nonconformances(List.of(ncr)).day("2025-05-01").build());
            AuditFinding late = finding(result, "nonconformances past due").orElseThrow();
            assertThat(late.items()).hasSize(1);
            assertThat(late.items().get(0)).startsWith(ncr.id() + " by ").endsWith(" days");
        }

        @Test
        void saysNothingAboutNonconformancesWhenNoDateWasGiven() {
            Ncr ncr = Nonconformance.raiseNcr(NewNcr.builder()
                    .number(1)
                    .kind("procedure")
                    .detail("x")
                    .raisedOn("2025-03-01")
                    .raisedBy("K. Osei")
                    .build());
            AuditResult result =
                    Audit.runAudit(audit(CLEAN).nonconformances(List.of(ncr)).build());
            assertThat(hasFinding(result, "nonconformances past due")).isFalse();
        }
    }

    @Nested
    class Grading {

        private final AuditResult result =
                Audit.runAudit(audit(tampered()).level("full").build());

        @Test
        void putsTheSevereFindingFirstWhateverItsItemCount() {
            assertThat(result.findings().get(0).severity()).isEqualTo(AuditSeverity.SEVERE);
        }

        @Test
        void doesNotGradeByHowMuchWorkEachOneIs() {
            Optional<AuditFinding> severe = result.findings().stream()
                    .filter(f -> f.severity() == AuditSeverity.SEVERE)
                    .findFirst();
            Optional<AuditFinding> minor = result.findings().stream()
                    .filter(f -> f.severity() == AuditSeverity.MINOR)
                    .findFirst();
            minor.ifPresent(found -> assertThat(result.findings().indexOf(severe.orElseThrow()))
                    .isLessThan(result.findings().indexOf(found)));
            assertThat(severe).isPresent();
        }

        @Test
        void filtersToWhatIsAtLeastAGivenSeverity() {
            assertThat(Audit.atLeast(result, AuditSeverity.SEVERE)).allMatch(f -> f.severity() == AuditSeverity.SEVERE);
            assertThat(Audit.atLeast(result, AuditSeverity.MINOR)).hasSameSizeAs(result.findings());
        }
    }

    @Nested
    class RenderAudit {

        @Test
        void saysNothingFoundOnACleanJob() {
            String text = Audit.renderAudit(Audit.runAudit(audit(CLEAN).build()));
            assertThat(text).contains("AUDIT READINESS");
            assertThat(text).contains("Nothing found.");
        }

        @Test
        void printsASummaryTableAndThenEachFinding() {
            String text = Audit.renderAudit(Audit.runAudit(audit(withStray()).build()));
            assertThat(text).contains("severity");
            assertThat(text).contains("JOINTS NOT ON THE REGISTER");
            assertThat(text).contains("J-99");
        }

        @Test
        void keepsEveryLineInsideThePaperWidth() {
            String text = Audit.renderAudit(Audit.runAudit(audit(withStray()).build()));
            for (String line : text.split("\n")) {
                assertThat(line.length()).isLessThanOrEqualTo(78);
            }
        }
    }
}
