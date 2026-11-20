package io.github.rkaushikdev.kerfline.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NonconformanceTest {

    private static final NewNcr BASE = NewNcr.builder()
            .number(1)
            .job("C-4471")
            .kind("procedure")
            .detail("joint shot on the wrong code")
            .raisedOn("2025-03-14")
            .raisedBy("K. Osei")
            .build();

    private static final Ncr NCR = Nonconformance.raiseNcr(BASE);

    private static Ncr closed(Ncr ncr, String day) {
        return Nonconformance.closeNcr(ncr, day, "x", "done");
    }

    @Nested
    class TheVocabularies {

        @Test
        void namesTheKindsAndSeverities() {
            assertThat(Nonconformance.ncrKinds()).contains("procedure");
            assertThat(Nonconformance.ncrKinds()).contains("calibration");
            assertThat(Nonconformance.severities()).containsExactly("observation", "minor", "major");
        }

        @Test
        void givesAMajorReportTheShortestFuse() {
            assertThat(Nonconformance.workingDaysFor("major")).isLessThan(Nonconformance.workingDaysFor("minor"));
            assertThat(Nonconformance.workingDaysFor("minor")).isLessThan(Nonconformance.workingDaysFor("observation"));
        }

        @Test
        void refusesASeverityItDoesNotKnow() {
            assertThatThrownBy(() -> Nonconformance.workingDaysFor("critical"))
                    .hasMessageContaining("severity must be one of");
        }
    }

    @Nested
    class RaiseNcr {

        @Test
        void numbersTheReport() {
            assertThat(NCR.id()).isEqualTo("NCR-001");
        }

        @Test
        void worksTheDueDateOutInWorkingDays() {
            // 2025-03-14 is a Friday, so ten working days lands on the 28th.
            assertThat(NCR.dueBy()).isEqualTo("2025-03-28");
        }

        @Test
        void startsOpen() {
            assertThat(Nonconformance.isOpen(NCR)).isTrue();
            assertThat(NCR.closedOn()).isEmpty();
        }

        @Test
        void refusesAReportWithNoDetailOrNobodyRaisingIt() {
            assertThatThrownBy(() -> Nonconformance.raiseNcr(
                            BASE.toBuilder().detail("  ").build()))
                    .hasMessageContaining("says what was wrong");
            assertThatThrownBy(() -> Nonconformance.raiseNcr(
                            BASE.toBuilder().raisedBy("").build()))
                    .hasMessageContaining("names who raised it");
        }

        @Test
        void refusesAKindNobodyUses() {
            assertThatThrownBy(() -> Nonconformance.raiseNcr(
                            BASE.toBuilder().kind("attitude").build()))
                    .hasMessageContaining("nonconformance kind");
        }

        @Test
        void refusesANumberThatIsNotAWholeNumberFromOne() {
            assertThatThrownBy(() ->
                            Nonconformance.raiseNcr(BASE.toBuilder().number(0).build()))
                    .hasMessageContaining("numbered from one");
            assertThatThrownBy(() ->
                            Nonconformance.raiseNcr(BASE.toBuilder().number(-1).build()))
                    .hasMessageContaining("numbered from one");
        }

        @Test
        void givesAMajorReportANearerDueDateThanAMinorOne() {
            Ncr major =
                    Nonconformance.raiseNcr(BASE.toBuilder().severity("major").build());
            assertThat(major.dueBy().compareTo(NCR.dueBy())).isNegative();
        }
    }

    @Nested
    class CloseNcr {

        @Test
        void closesWithAnActionAndADate() {
            Ncr done = Nonconformance.closeNcr(NCR, "2025-03-20", "K. Osei", "rejudged under API 1104 and reissued");
            assertThat(Nonconformance.isOpen(done)).isFalse();
            assertThat(done.closedOn()).contains("2025-03-20");
            assertThat(done.action())
                    .hasValueSatisfying(action -> assertThat(action).contains("rejudged"));
        }

        @Test
        void leavesTheReportItWasGivenAlone() {
            closed(NCR, "2025-03-20");
            assertThat(NCR.closedOn()).isEmpty();
        }

        @Test
        void refusesToCloseOneTwice() {
            Ncr done = closed(NCR, "2025-03-20");
            assertThatThrownBy(() -> Nonconformance.closeNcr(done, "2025-03-21", "x", "again"))
                    .hasMessageContaining("was closed on 2025-03-20");
        }

        @Test
        void refusesToCloseBeforeItWasRaised() {
            assertThatThrownBy(() -> closed(NCR, "2025-03-01")).hasMessageContaining("before it was raised");
        }

        @Test
        void refusesToCloseWithoutSayingWhatWasDone() {
            assertThatThrownBy(() -> Nonconformance.closeNcr(NCR, "2025-03-20", "x", " "))
                    .hasMessageContaining("says what was done");
        }
    }

    @Nested
    class OpenNcrsAndOverdueNcrs {

        private final Ncr major = Nonconformance.raiseNcr(
                BASE.toBuilder().number(2).severity("major").build());
        private final Ncr done =
                closed(Nonconformance.raiseNcr(BASE.toBuilder().number(3).build()), "2025-03-18");
        private final List<Ncr> reports = List.of(NCR, major, done);

        @Test
        void listsOnlyTheOpenOnes() {
            assertThat(Nonconformance.openNcrs(reports).stream().map(Ncr::id)).containsExactly("NCR-001", "NCR-002");
        }

        @Test
        void findsNothingOverdueBeforeAnythingIsDue() {
            assertThat(Nonconformance.overdueNcrs(reports, "2025-03-15")).isEmpty();
        }

        @Test
        void findsTheMajorOneFirstOnceBothAreLate() {
            List<OverdueNcr> late = Nonconformance.overdueNcrs(reports, "2025-04-30");
            assertThat(late.get(0).severity()).isEqualTo("major");
            assertThat(late).hasSize(2);
        }

        @Test
        void countsHowManyDaysOver() {
            List<OverdueNcr> late = Nonconformance.overdueNcrs(reports, "2025-04-30");
            assertThat(late.get(0).daysOverdue()).isPositive();
        }

        @Test
        void neverCountsAClosedReportAsOverdue() {
            List<OverdueNcr> late = Nonconformance.overdueNcrs(reports, "2026-01-01");
            assertThat(late.stream().map(OverdueNcr::id)).doesNotContain("NCR-003");
        }
    }

    @Nested
    class Aging {

        @Test
        void bucketsTheOpenReportsByAge() {
            Map<String, Integer> counts = Nonconformance.aging(List.of(NCR), "2025-03-16");
            assertThat(counts.get("under 7")).isEqualTo(1);
            assertThat(counts.get("under 30")).isZero();
        }

        @Test
        void movesAReportAlongAsItAges() {
            assertThat(Nonconformance.aging(List.of(NCR), "2025-04-01").get("under 30"))
                    .isEqualTo(1);
            assertThat(Nonconformance.aging(List.of(NCR), "2025-09-01").get("over 90"))
                    .isEqualTo(1);
        }

        @Test
        void countsNothingWhenEverythingIsClosed() {
            Map<String, Integer> counts = Nonconformance.aging(List.of(closed(NCR, "2025-03-20")), "2025-09-01");
            assertThat(counts.values()).allMatch(count -> count == 0);
        }

        @Test
        void takesDifferentBuckets() {
            Map<String, Integer> counts = Nonconformance.aging(List.of(NCR), "2025-03-16", List.of(3, 14));
            assertThat(counts.keySet()).containsExactly("under 3", "under 14", "over 14");
        }
    }

    @Nested
    class FromDeviation {

        @Test
        void turnsAContractDeviationIntoAReportWithoutRetypingIt() {
            Ncr raised = Nonconformance.fromDeviation(
                    new Deviation("code", "API1104", "AWSD1.1"),
                    NewNcr.builder()
                            .job("C-4471")
                            .number(4)
                            .raisedOn("2025-03-14")
                            .raisedBy("K. Osei")
                            .build());
            assertThat(raised.detail()).isEqualTo("code was AWSD1.1 where API1104 was agreed");
            assertThat(raised.kind()).isEqualTo("procedure");
        }

        @Test
        void treatsAWrongCodeAsMajorAndEverythingElseAsMinor() {
            Ncr code = Nonconformance.fromDeviation(
                    new Deviation("code", "a", "b"),
                    NewNcr.builder()
                            .number(1)
                            .raisedOn("2025-03-14")
                            .raisedBy("x")
                            .build());
            Ncr density = Nonconformance.fromDeviation(
                    new Deviation("density", 2.0, 1.8),
                    NewNcr.builder()
                            .number(2)
                            .raisedOn("2025-03-14")
                            .raisedBy("x")
                            .build());
            assertThat(code.severity()).isEqualTo("major");
            assertThat(density.severity()).isEqualTo("minor");
            assertThat(density.detail()).isEqualTo("density was 1.8 where 2 was agreed");
        }
    }

    @Nested
    class RenderNcrs {

        @Test
        void printsTheOpenReports() {
            String text = Nonconformance.renderNcrs(List.of(NCR), "2025-03-16", "C-4471");
            assertThat(text).contains("OPEN NONCONFORMANCES");
            assertThat(text).contains("NCR-001");
            assertThat(text).contains("procedure");
        }

        @Test
        void saysSoPlainlyWhenNothingIsOpen() {
            assertThat(Nonconformance.renderNcrs(List.of(closed(NCR, "2025-03-20")), "2025-03-21", null))
                    .contains("Nothing open.");
        }

        @Test
        void keepsEveryLineInsideThePaperWidth() {
            for (String line :
                    Nonconformance.renderNcrs(List.of(NCR), "2025-03-16", null).split("\n")) {
                assertThat(line.length()).isLessThanOrEqualTo(78);
            }
        }
    }
}
