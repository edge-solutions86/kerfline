package io.github.rkaushikdev.kerfline.safety;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SourceLogTest {

    private static final SealedSource SOURCE = SourceLog.makeSource("IR-7741", "IR192", 80, "2025-01-06");

    @Nested
    class TheLeakTestRules {

        @Test
        void setsTheLimitAtFiveNanocuriesAndTheIntervalAtSixMonths() {
            assertThat(SourceLog.leakLimitMicroCuries()).isEqualTo(0.005);
            assertThat(SourceLog.leakIntervalMonths()).isEqualTo(6);
        }
    }

    @Nested
    class MakeSourceAndActivityOn {

        @Test
        void recordsTheSourceOffItsCertificate() {
            assertThat(SOURCE.serial()).isEqualTo("IR-7741");
            assertThat(SOURCE.isotope()).isEqualTo("IR192");
            assertThat(SOURCE.assayCi()).isEqualTo(80);
        }

        @Test
        void readsTheAssayActivityOnTheAssayDay() {
            assertThat(SourceLog.activityOn(SOURCE, "2025-01-06")).isEqualTo(80);
        }

        @Test
        void decaysItForward() {
            assertThat(SourceLog.activityOn(SOURCE, "2025-03-21")).isCloseTo(40, digits(0));
        }

        @Test
        void backDecaysToBeforeTheAssay() {
            assertThat(SourceLog.activityOn(SOURCE, "2024-10-24")).isGreaterThan(80);
        }

        @Test
        void refusesASourceWithNoSerial() {
            assertThatThrownBy(() -> SourceLog.makeSource("  ", "IR192", 80, "2025-01-06"))
                    .hasMessageContaining("identified by its serial");
        }

        @Test
        void refusesAnIsotopeTheShopIsNotLicensedFor() {
            assertThatThrownBy(() -> SourceLog.makeSource("X-1", "CS137", 80, "2025-01-06"))
                    .hasMessageContaining("no isotope CS137");
        }
    }

    @Nested
    class TheUtilisationLog {

        private final List<SourceUse> uses = List.of(
                SourceLog.makeUse("IR-7741", "2025-02-03", "R. Ibarra", "Unit 4", 45, 12),
                SourceLog.makeUse("IR-7741", "2025-02-04", "D. Whyte", "Unit 4", 30, 8),
                SourceLog.makeUse("CO-2210", "2025-02-04", "R. Ibarra", "Shop", 15, 2));

        @Test
        void totalsOneSourceOverAPeriod() {
            Utilisation summary = SourceLog.utilisation(uses, "IR-7741", "2025-02-01", "2025-02-28");
            assertThat(summary.entries()).isEqualTo(2);
            assertThat(summary.minutes()).isCloseTo(75, digits(1));
            assertThat(summary.exposures()).isEqualTo(20);
        }

        @Test
        void leavesOtherSourcesOutOfIt() {
            Utilisation summary = SourceLog.utilisation(uses, "CO-2210", "2025-02-01", "2025-02-28");
            assertThat(summary.exposures()).isEqualTo(2);
        }

        @Test
        void listsWhoHadItAndOnWhatDays() {
            Utilisation summary = SourceLog.utilisation(uses, "IR-7741", "2025-02-01", "2025-02-28");
            assertThat(summary.users()).containsExactly("D. Whyte", "R. Ibarra");
            assertThat(summary.days()).containsExactly("2025-02-03", "2025-02-04");
        }

        @Test
        void isEmptyOverAPeriodWithNothingInIt() {
            Utilisation summary = SourceLog.utilisation(uses, "IR-7741", "2025-06-01", "2025-06-30");
            assertThat(summary.entries()).isZero();
            assertThat(summary.minutes()).isEqualTo(0);
        }

        @Test
        void answersWhoHadASourceOnADay() {
            List<Holder> holders = SourceLog.whoHadIt(uses, "IR-7741", "2025-02-03");
            assertThat(holders).hasSize(1);
            assertThat(holders.get(0).user()).isEqualTo("R. Ibarra");
            assertThat(holders.get(0).location()).contains("Unit 4");
        }

        @Test
        void answersWithNothingWhenTheSourceWasInTheStore() {
            assertThat(SourceLog.whoHadIt(uses, "IR-7741", "2025-02-05")).isEmpty();
        }

        @Test
        void refusesALogEntryWithNobodyOnIt() {
            assertThatThrownBy(() -> SourceLog.makeUse("IR-7741", "2025-02-03", "", null, 1, 1))
                    .hasMessageContaining("names who had it");
        }
    }

    @Nested
    class MakeLeakTest {

        @Test
        void passesACleanSourceAndSetsTheNextDueDate() {
            LeakTest test = SourceLog.makeLeakTest("IR-7741", "2025-01-10", 0.0001);
            assertThat(test.pass()).isTrue();
            assertThat(test.dueBy()).isEqualTo("2025-07-10");
            assertThat(test.action()).isEmpty();
        }

        @Test
        void failsASourceOverTheLimitAndSaysWhatHappens() {
            LeakTest test = SourceLog.makeLeakTest("IR-7741", "2025-01-10", 0.006);
            assertThat(test.pass()).isFalse();
            assertThat(test.action())
                    .hasValueSatisfying(action -> assertThat(action).contains("remove the source from service"));
        }

        @Test
        void failsAtExactlyTheLimitRatherThanPassingOnTheBoundary() {
            LeakTest test = SourceLog.makeLeakTest("IR-7741", "2025-01-10", 0.005);
            assertThat(test.pass()).isFalse();
        }

        @Test
        void refusesANegativeReading() {
            assertThatThrownBy(() -> SourceLog.makeLeakTest("IR-7741", "2025-01-10", -1))
                    .hasMessageContaining("not be below 0");
        }
    }

    @Nested
    class LeakTestCurrent {

        private final LeakTest good = SourceLog.makeLeakTest("IR-7741", "2025-01-10", 0.0001);
        private final LeakTest bad = SourceLog.makeLeakTest("IR-7741", "2025-01-10", 0.02);

        @Test
        void isCurrentInsideTheSixMonths() {
            LeakTestStatus check = SourceLog.leakTestCurrent(List.of(good), "IR-7741", "2025-04-01");
            assertThat(check.current()).isTrue();
            assertThat(check.daysLeft().getAsInt()).isPositive();
        }

        @Test
        void hasRunOutAfterTheSixMonths() {
            LeakTestStatus check = SourceLog.leakTestCurrent(List.of(good), "IR-7741", "2025-07-11");
            assertThat(check.current()).isFalse();
            assertThat(check.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("run out"));
        }

        @Test
        void isNeverCurrentAfterAFailedTest() {
            LeakTestStatus check = SourceLog.leakTestCurrent(List.of(bad), "IR-7741", "2025-01-11");
            assertThat(check.current()).isFalse();
            assertThat(check.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("failed"));
        }

        @Test
        void saysSoWhenThereIsNoTestAtAll() {
            LeakTestStatus check = SourceLog.leakTestCurrent(List.of(), "IR-7741", "2025-01-11");
            assertThat(check.current()).isFalse();
            assertThat(check.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("no leak test on file"));
        }

        @Test
        void ignoresATestDatedAfterTheDayBeingAskedAbout() {
            LeakTestStatus check = SourceLog.leakTestCurrent(List.of(good), "IR-7741", "2025-01-05");
            assertThat(check.current()).isFalse();
        }

        @Test
        void takesTheMostRecentTestWhenThereAreSeveral() {
            LeakTest later = SourceLog.makeLeakTest("IR-7741", "2025-06-01", 0.0001);
            LeakTestStatus check = SourceLog.leakTestCurrent(List.of(good, later), "IR-7741", "2025-08-01");
            assertThat(check.current()).isTrue();
            assertThat(check.latest().orElseThrow().day()).isEqualTo("2025-06-01");
        }
    }

    @Nested
    class CheckOut {

        private final LeakTest good = SourceLog.makeLeakTest("IR-7741", "2025-01-10", 0.0001);

        @Test
        void booksASourceOutWithItsActivityForTheDay() {
            Booking out = SourceLog.checkOut(SOURCE, List.of(good), "2025-02-03");
            assertThat(out.serial()).isEqualTo("IR-7741");
            assertThat(out.activityCi()).isLessThan(80);
        }

        @Test
        void refusesASourceWhoseLeakTestHasRunOut() {
            assertThatThrownBy(() -> SourceLog.checkOut(SOURCE, List.of(good), "2025-08-01"))
                    .hasMessageContaining("run out");
        }

        @Test
        void refusesASourceThatHasNeverBeenTested() {
            assertThatThrownBy(() -> SourceLog.checkOut(SOURCE, List.of(), "2025-02-03"))
                    .hasMessageContaining("no leak test");
        }
    }

    @Nested
    class LeakTestsDueBy {

        private final LeakTest good = SourceLog.makeLeakTest("IR-7741", "2025-01-10", 0.0001);

        @Test
        void findsNothingWhenTheTestHasALongTimeLeft() {
            List<LeakTestDue> due = SourceLog.leakTestsDueBy(List.of(SOURCE), List.of(good), "2025-02-01", 30);
            assertThat(due).isEmpty();
        }

        @Test
        void findsASourceComingUpForRenewal() {
            List<LeakTestDue> due = SourceLog.leakTestsDueBy(List.of(SOURCE), List.of(good), "2025-06-20", 30);
            assertThat(due).hasSize(1);
            assertThat(due.get(0).dueBy()).contains("2025-07-10");
        }

        @Test
        void findsASourceWithNoTestAtAll() {
            List<LeakTestDue> due = SourceLog.leakTestsDueBy(List.of(SOURCE), List.of(), "2025-02-01", 30);
            assertThat(due.get(0).reason()).contains("no leak test");
        }
    }
}
