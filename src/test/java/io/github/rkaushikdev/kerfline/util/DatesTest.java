package io.github.rkaushikdev.kerfline.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DatesTest {

    @Nested
    class RequireDay {

        @Test
        void acceptsARealDayAndHandsItStraightBack() {
            assertThat(Dates.requireDay("2025-03-14")).isEqualTo("2025-03-14");
            assertThat(Dates.requireDay("2024-02-29")).isEqualTo("2024-02-29");
        }

        @Test
        void refusesADayThatDoesNotExist() {
            assertThatThrownBy(() -> Dates.requireDay("2025-02-30")).hasMessageContaining("not a real calendar day");
            assertThatThrownBy(() -> Dates.requireDay("2025-13-01")).hasMessageContaining("not a real calendar day");
            assertThatThrownBy(() -> Dates.requireDay("2025-02-29")).hasMessageContaining("not a real calendar day");
        }

        @Test
        void refusesAnythingNotWrittenTheOneWay() {
            assertThatThrownBy(() -> Dates.requireDay("14/03/2025")).hasMessageContaining("YYYY-MM-DD");
            assertThatThrownBy(() -> Dates.requireDay("2025-3-14")).hasMessageContaining("YYYY-MM-DD");
            assertThatThrownBy(() -> Dates.requireDay("")).hasMessageContaining("YYYY-MM-DD");
            assertThatThrownBy(() -> Dates.requireDay(null)).hasMessageContaining("YYYY-MM-DD");
        }

        @Test
        void namesTheFieldInTheMessage() {
            assertThatThrownBy(() -> Dates.requireDay("nope", "assay date"))
                    .hasMessageContaining("assay date must be written");
        }

        @Test
        void answersIsDayWithoutThrowing() {
            assertThat(Dates.isDay("2025-03-14")).isTrue();
            assertThat(Dates.isDay("2025-02-30")).isFalse();
            assertThat(Dates.isDay(null)).isFalse();
        }
    }

    @Nested
    class DaysBetweenAndAddDays {

        @Test
        void countsForward() {
            assertThat(Dates.daysBetween("2025-03-01", "2025-03-14")).isEqualTo(13);
        }

        @Test
        void countsBackwardAsANegative() {
            assertThat(Dates.daysBetween("2025-03-14", "2025-03-01")).isEqualTo(-13);
        }

        @Test
        void isZeroOnTheSameDay() {
            assertThat(Dates.daysBetween("2025-03-14", "2025-03-14")).isZero();
        }

        @Test
        void crossesALeapDay() {
            assertThat(Dates.daysBetween("2024-02-28", "2024-03-01")).isEqualTo(2);
            assertThat(Dates.daysBetween("2025-02-28", "2025-03-01")).isEqualTo(1);
        }

        @Test
        void crossesADaylightSavingChangeWithoutLosingAnHour() {
            assertThat(Dates.daysBetween("2025-03-08", "2025-03-10")).isEqualTo(2);
            assertThat(Dates.daysBetween("2025-11-01", "2025-11-03")).isEqualTo(2);
        }

        @Test
        void addsAndSubtractsDays() {
            assertThat(Dates.addDays("2025-03-14", 10)).isEqualTo("2025-03-24");
            assertThat(Dates.addDays("2025-03-01", -1)).isEqualTo("2025-02-28");
            assertThat(Dates.addDays("2024-03-01", -1)).isEqualTo("2024-02-29");
        }

        @Test
        void roundTripsASpan() {
            int days = Dates.daysBetween("2024-06-03", "2025-12-15");
            assertThat(Dates.addDays("2024-06-03", days)).isEqualTo("2025-12-15");
        }

        @Test
        void refusesAFractionalDayCount() {
            assertThatThrownBy(() -> Dates.addDays("2025-03-14", 1.5)).hasMessageContaining("whole number");
        }

        @Test
        void formatsAStampBackToADay() {
            assertThat(Dates.formatDay(LocalDate.of(2025, 3, 14))).isEqualTo("2025-03-14");
        }
    }

    @Nested
    class AddMonths {

        @Test
        void walksTheCalendar() {
            assertThat(Dates.addMonths("2025-03-14", 6)).isEqualTo("2025-09-14");
            assertThat(Dates.addMonths("2025-03-14", -3)).isEqualTo("2024-12-14");
        }

        @Test
        void clampsOntoTheEndOfAShortMonth() {
            assertThat(Dates.addMonths("2025-03-31", 6)).isEqualTo("2025-09-30");
            assertThat(Dates.addMonths("2025-01-31", 1)).isEqualTo("2025-02-28");
            assertThat(Dates.addMonths("2024-01-31", 1)).isEqualTo("2024-02-29");
        }

        @Test
        void crossesAYearBoundary() {
            assertThat(Dates.addMonths("2025-11-15", 3)).isEqualTo("2026-02-15");
            assertThat(Dates.addMonths("2025-01-15", -2)).isEqualTo("2024-11-15");
        }

        @Test
        void addsYearsThroughMonths() {
            assertThat(Dates.addYears("2025-03-14", 3)).isEqualTo("2028-03-14");
            assertThat(Dates.addYears("2024-02-29", 1)).isEqualTo("2025-02-28");
        }

        @Test
        void refusesAFractionalMonthCount() {
            assertThatThrownBy(() -> Dates.addMonths("2025-03-14", 0.5)).hasMessageContaining("whole number");
        }
    }

    @Nested
    class TheWorkingWeek {

        @Test
        void namesTheWeekday() {
            assertThat(Dates.weekday("2025-03-14")).isEqualTo(5);
            assertThat(Dates.weekday("2025-03-16")).isZero();
        }

        @Test
        void spotsAWeekend() {
            assertThat(Dates.isWeekend("2025-03-15")).isTrue();
            assertThat(Dates.isWeekend("2025-03-16")).isTrue();
            assertThat(Dates.isWeekend("2025-03-14")).isFalse();
        }

        @Test
        void skipsTheWeekendWhenAddingWorkingDays() {
            assertThat(Dates.addWorkDays("2025-03-14", 1)).isEqualTo("2025-03-17");
            assertThat(Dates.addWorkDays("2025-03-14", 5)).isEqualTo("2025-03-21");
        }

        @Test
        void addingNoWorkingDaysStaysPutEvenOnASaturday() {
            assertThat(Dates.addWorkDays("2025-03-15", 0)).isEqualTo("2025-03-15");
        }

        @Test
        void refusesANegativeWorkingDayCount() {
            assertThatThrownBy(() -> Dates.addWorkDays("2025-03-14", -1)).hasMessageContaining("whole number of days");
        }
    }

    @Nested
    class EarliestAndLatest {

        @Test
        void picksTheEndsOfAList() {
            List<String> days = List.of("2025-03-14", "2024-12-31", "2025-11-02");
            assertThat(Dates.earliest(days)).isEqualTo("2024-12-31");
            assertThat(Dates.latest(days)).isEqualTo("2025-11-02");
        }

        @Test
        void handlesASingleDay() {
            assertThat(Dates.earliest(List.of("2025-03-14"))).isEqualTo("2025-03-14");
        }

        @Test
        void refusesAnEmptyList() {
            assertThatThrownBy(() -> Dates.earliest(List.of())).hasMessageContaining("earliest of no days");
            assertThatThrownBy(() -> Dates.latest(List.of())).hasMessageContaining("latest of no days");
        }

        @Test
        void refusesAListHoldingSomethingThatIsNotADay() {
            assertThatThrownBy(() -> Dates.earliest(List.of("2025-03-14", "soon")))
                    .hasMessageContaining("YYYY-MM-DD");
        }
    }

    @Nested
    class WithinSpan {

        @Test
        void includesBothBounds() {
            assertThat(Dates.withinSpan("2025-03-01", "2025-03-01", "2025-03-31"))
                    .isTrue();
            assertThat(Dates.withinSpan("2025-03-31", "2025-03-01", "2025-03-31"))
                    .isTrue();
        }

        @Test
        void excludesOutside() {
            assertThat(Dates.withinSpan("2025-02-28", "2025-03-01", "2025-03-31"))
                    .isFalse();
            assertThat(Dates.withinSpan("2025-04-01", "2025-03-01", "2025-03-31"))
                    .isFalse();
        }

        @Test
        void treatsAMissingBoundAsOpen() {
            assertThat(Dates.withinSpan("2030-01-01", "2025-03-01", null)).isTrue();
            assertThat(Dates.withinSpan("1999-01-01", null, "2025-03-31")).isTrue();
            assertThat(Dates.withinSpan("1999-01-01", null, null)).isTrue();
        }
    }

    @Nested
    class MonthsBetween {

        @Test
        void countsOnlyCompletedMonths() {
            assertThat(Dates.monthsBetween("2025-01-15", "2025-07-15")).isEqualTo(6);
            assertThat(Dates.monthsBetween("2025-01-15", "2025-07-14")).isEqualTo(5);
            assertThat(Dates.monthsBetween("2025-01-15", "2025-07-16")).isEqualTo(6);
        }

        @Test
        void isZeroInsideOneMonth() {
            assertThat(Dates.monthsBetween("2025-01-01", "2025-01-31")).isZero();
        }

        @Test
        void goesNegativeRunningBackwards() {
            assertThat(Dates.monthsBetween("2025-07-15", "2025-01-15")).isEqualTo(-6);
        }
    }
}
