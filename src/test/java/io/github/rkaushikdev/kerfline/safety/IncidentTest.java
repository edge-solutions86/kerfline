package io.github.rkaushikdev.kerfline.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IncidentTest {

    private static final NewIncident BASE = NewIncident.builder()
            .reference("INC-1")
            .kind("survey_failure")
            .day("2025-03-14")
            .detail("boundary reading over two at the rope")
            .build();

    @Nested
    class TheVocabulary {

        @Test
        void namesTheKindsAndTheWindows() {
            assertThat(Incident.incidentKinds()).contains("disconnect");
            assertThat(Incident.incidentKinds()).contains("overexposure");
            assertThat(Incident.reportingWindows()).isEqualTo(Map.of("immediate", 0, "oneDay", 1, "thirtyDay", 30));
        }

        @Test
        void refusesAKindNobodyRecords() {
            assertThatThrownBy(() -> Incident.classify("nuisance")).hasMessageContaining("incident kind");
        }
    }

    @Nested
    class Classify {

        @Test
        void reportsADisconnectInsideADayWhateverTheDoseWas() {
            assertThat(Incident.classify("disconnect", 0).window()).isEqualTo(ReportingWindow.ONE_DAY);
            assertThat(Incident.classify("lost_source", 0).window()).isEqualTo(ReportingWindow.ONE_DAY);
        }

        @Test
        void saysTheEquipmentFailingIsTheReasonNotTheDose() {
            assertThat(Incident.classify("disconnect").because()).contains("equipment failed");
        }

        @Test
        void reportsAVeryLargeDoseImmediately() {
            assertThat(Incident.classify("overexposure", 30000).window()).isEqualTo(ReportingWindow.IMMEDIATE);
        }

        @Test
        void reportsALargeDoseInsideADay() {
            assertThat(Incident.classify("overexposure", 6000).window()).isEqualTo(ReportingWindow.ONE_DAY);
        }

        @Test
        void treatsReachingTheAnnualLimitAsTheSameOneDayReport() {
            // The one day threshold and the annual whole body limit are both five
            // thousand millirem, so there is no gap between them to fall into.
            Classification atLimit = Incident.classify("overexposure", 5100);
            assertThat(atLimit.window()).isEqualTo(ReportingWindow.ONE_DAY);
            assertThat(atLimit.because()).contains("annual whole body limit");
        }

        @Test
        void reportsASmallDoseInsideThirtyDays() {
            assertThat(Incident.classify("overexposure", 400).window()).isEqualTo(ReportingWindow.THIRTY_DAY);
        }

        @Test
        void letsAVeryLargeDoseOvertakeTheEquipmentClock() {
            assertThat(Incident.classify("disconnect", 30000).window()).isEqualTo(ReportingWindow.IMMEDIATE);
        }

        @Test
        void reportsASurveyFailureWithNoDoseInsideThirtyDays() {
            assertThat(Incident.classify("survey_failure", 0).window()).isEqualTo(ReportingWindow.THIRTY_DAY);
        }

        @Test
        void neverSetsTheClockByHowBadTheDayFelt() {
            // A disconnect where nobody was exposed reports faster than a dose that
            // somebody actually picked up on a job where the equipment behaved.
            Classification quiet = Incident.classify("disconnect", 0);
            Classification noisy = Incident.classify("overexposure", 400);
            assertThat(quiet.days()).isLessThan(noisy.days());
        }

        @Test
        void refusesANegativeDose() {
            assertThatThrownBy(() -> Incident.classify("damage", -1)).hasMessageContaining("not be below 0");
        }
    }

    @Nested
    class MakeIncident {

        @Test
        void setsTheReportingDateFromTheClassification() {
            IncidentRecord incident = Incident.makeIncident(BASE);
            assertThat(incident.clock().window()).isEqualTo(ReportingWindow.THIRTY_DAY);
            assertThat(incident.reportBy()).isEqualTo("2025-04-13");
        }

        @Test
        void setsItTheSameDayForAnImmediateReport() {
            IncidentRecord incident = Incident.makeIncident(
                    BASE.toBuilder().kind("overexposure").doseMrem(30000).build());
            assertThat(incident.reportBy()).isEqualTo("2025-03-14");
        }

        @Test
        void startsUnreported() {
            assertThat(Incident.makeIncident(BASE).reportedOn()).isEmpty();
        }

        @Test
        void refusesAnIncidentWithNoReferenceOrNoDetail() {
            assertThatThrownBy(() -> Incident.makeIncident(
                            BASE.toBuilder().reference(" ").build()))
                    .hasMessageContaining("carries a reference");
            assertThatThrownBy(() ->
                            Incident.makeIncident(BASE.toBuilder().detail("").build()))
                    .hasMessageContaining("says what happened");
        }

        @Test
        void refusesADateThatIsNotARealDay() {
            assertThatThrownBy(() -> Incident.makeIncident(
                            BASE.toBuilder().day("2025-02-30").build()))
                    .hasMessageContaining("not a real calendar day");
        }
    }

    @Nested
    class MarkReported {

        private final IncidentRecord incident = Incident.makeIncident(BASE);

        @Test
        void recordsTheDateAndThatItWasOnTime() {
            IncidentRecord reported = Incident.markReported(incident, "2025-03-20");
            Filing filing = reported.filing().orElseThrow();
            assertThat(filing.reportedOn()).isEqualTo("2025-03-20");
            assertThat(filing.onTime()).isTrue();
            assertThat(filing.daysLate()).isZero();
        }

        @Test
        void countsTheDaysWhenItWasLate() {
            Filing filing =
                    Incident.markReported(incident, "2025-04-20").filing().orElseThrow();
            assertThat(filing.onTime()).isFalse();
            assertThat(filing.daysLate()).isEqualTo(7);
        }

        @Test
        void isOnTimeOnTheLastDayOfTheWindow() {
            assertThat(Incident.markReported(incident, "2025-04-13")
                            .filing()
                            .orElseThrow()
                            .onTime())
                    .isTrue();
        }

        @Test
        void refusesAReportDatedBeforeTheIncident() {
            assertThatThrownBy(() -> Incident.markReported(incident, "2025-03-01"))
                    .hasMessageContaining("before it happened");
        }

        @Test
        void leavesTheIncidentItWasGivenAlone() {
            Incident.markReported(incident, "2025-03-20");
            assertThat(incident.reportedOn()).isEmpty();
        }
    }

    @Nested
    class UnreportedAndOverdueReports {

        private final IncidentRecord slow = Incident.makeIncident(BASE);
        private final IncidentRecord fast = Incident.makeIncident(
                BASE.toBuilder().reference("INC-2").kind("disconnect").build());
        private final IncidentRecord done = Incident.markReported(
                Incident.makeIncident(BASE.toBuilder().reference("INC-3").build()), "2025-03-16");
        private final List<IncidentRecord> all = List.of(slow, fast, done);

        @Test
        void listsOnlyTheOnesNotYetReported() {
            assertThat(Incident.unreported(all).stream().map(IncidentRecord::reference))
                    .containsExactly("INC-1", "INC-2");
        }

        @Test
        void findsNothingOverdueOnTheDay() {
            assertThat(Incident.overdueReports(all, "2025-03-14")).isEmpty();
        }

        @Test
        void findsTheFastOneOverdueFirst() {
            List<OverdueReport> late = Incident.overdueReports(all, "2025-03-20");
            assertThat(late).hasSize(1);
            assertThat(late.get(0).incident().reference()).isEqualTo("INC-2");
        }

        @Test
        void putsTheMostUrgentWindowFirstOnceBothAreLate() {
            List<OverdueReport> late = Incident.overdueReports(all, "2025-05-01");
            assertThat(late.get(0).incident().clock().window()).isEqualTo(ReportingWindow.ONE_DAY);
            assertThat(late).hasSize(2);
        }

        @Test
        void neverListsAReportedIncidentAsOverdue() {
            assertThat(Incident.overdueReports(all, "2026-01-01").stream()
                            .map(late -> late.incident().reference()))
                    .doesNotContain("INC-3");
        }
    }

    @Nested
    class DueWithin {

        private final IncidentRecord slow = Incident.makeIncident(BASE);

        @Test
        void findsNothingDueFarAheadOfTheWindow() {
            assertThat(Incident.dueWithin(List.of(slow), "2025-03-14", 5)).isEmpty();
        }

        @Test
        void findsItAsTheWindowCloses() {
            assertThat(Incident.dueWithin(List.of(slow), "2025-04-10", 5)).hasSize(1);
        }

        @Test
        void doesNotFindOneThatIsAlreadyOverdue() {
            assertThat(Incident.dueWithin(List.of(slow), "2025-05-01", 5)).isEmpty();
        }
    }
}
