package io.github.rkaushikdev.kerfline.personnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TrainingTest {

    private static TrainingRecord course(String method, double hours, String day, String name) {
        return Training.makeTraining(TrainingInput.builder()
                .person("R. Ibarra")
                .method(method)
                .course(name)
                .hours(hours)
                .day(day)
                .build());
    }

    private static TrainingRecord course(String method, double hours, String day) {
        return course(method, hours, day, "course");
    }

    private static final List<TrainingRecord> RECORDS = List.of(
            course("RT", 40, "2023-02-06", "radiography level I"),
            course("RT", 40, "2023-09-11", "radiography level II"),
            course("UT", 40, "2024-01-15", "ultrasonics level I"),
            course("SAFETY", 8, "2025-01-06", "radiation safety refresher"));

    @Nested
    class TheMethods {

        @Test
        void namesTheOnesTheShopTrainsIn() {
            assertThat(Training.methods()).contains("RT", "SAFETY");
        }

        @Test
        void saysWhichNeedRefreshing() {
            assertThat(Training.refreshYearsFor("SAFETY")).hasValue(1);
            assertThat(Training.refreshYearsFor("RT")).isEmpty();
        }

        @Test
        void refusesAMethodNobodyTrainsIn() {
            assertThatThrownBy(() -> Training.refreshYearsFor("ET")).hasMessageContaining("method must be one of");
        }
    }

    @Nested
    class MakeTraining {

        @Test
        void recordsACourse() {
            TrainingRecord record = course("RT", 40, "2023-02-06");
            assertThat(record.hours()).isEqualTo(40);
            assertThat(record.method()).isEqualTo("RT");
        }

        @Test
        void setsAnExpiryOnlyOnTrainingThatNeedsRefreshing() {
            assertThat(course("SAFETY", 8, "2025-01-06").expiresOn()).contains("2026-01-06");
            assertThat(course("RT", 40, "2023-02-06").expiresOn()).isEmpty();
        }

        @Test
        void refusesARecordWithNobodyOrNoCourseOnIt() {
            assertThatThrownBy(() -> Training.makeTraining(TrainingInput.builder()
                            .person(" ")
                            .method("RT")
                            .course("x")
                            .hours(1)
                            .day("2025-01-06")
                            .build()))
                    .hasMessageContaining("names who attended");
            assertThatThrownBy(() -> Training.makeTraining(TrainingInput.builder()
                            .person("x")
                            .method("RT")
                            .course("")
                            .hours(1)
                            .day("2025-01-06")
                            .build()))
                    .hasMessageContaining("names the course");
        }

        @Test
        void refusesNegativeHours() {
            assertThatThrownBy(() -> course("RT", -1, "2025-01-06")).hasMessageContaining("not be below 0");
        }
    }

    @Nested
    class HoursToward {

        @Test
        void addsUpOneMethodOnly() {
            assertThat(Training.hoursToward(RECORDS, "RT")).isEqualTo(80);
            assertThat(Training.hoursToward(RECORDS, "UT")).isEqualTo(40);
        }

        @Test
        void doesNotLetAnotherMethodCountTowardsIt() {
            assertThat(Training.hoursToward(RECORDS, "MT")).isEqualTo(0);
        }

        @Test
        void splitsTheHoursByMethod() {
            Map<String, Double> split = Training.hoursByMethod(RECORDS);
            assertThat(split.get("RT")).isEqualTo(80);
            assertThat(split.get("SAFETY")).isEqualTo(8);
            assertThat(split.get("PT")).isEqualTo(0);
        }
    }

    @Nested
    class ReadyFor {

        @Test
        void isReadyWhenBothTheTrainingAndTheExperienceReach() {
            Readiness check = Training.readyFor(RECORDS, "II", 900);
            assertThat(check.ready()).isTrue();
            assertThat(check.trainingShort()).isEqualTo(0);
        }

        @Test
        void isShortOnExperienceEvenWithPlentyOfTraining() {
            Readiness check = Training.readyFor(RECORDS, "II", 100);
            assertThat(check.ready()).isFalse();
            assertThat(check.experienceShort()).isEqualTo(530);
            assertThat(check.trainingShort()).isEqualTo(0);
        }

        @Test
        void isShortOnTrainingWhenTheHoursAreInAnotherMethod() {
            Readiness check = Training.readyFor(RECORDS, "MT", "I", 900);
            assertThat(check.ready()).isFalse();
            assertThat(check.trainingShort()).isEqualTo(40);
        }

        @Test
        void refusesALevelNobodyHas() {
            assertThatThrownBy(() -> Training.readyFor(RECORDS, "IV", 0)).hasMessageContaining("certification level");
        }
    }

    @Nested
    class LapsedAndCurrentToWork {

        @Test
        void isCurrentInsideTheYear() {
            SafetyStatus check = Training.currentToWork(RECORDS, "2025-06-01");
            assertThat(check.current()).isTrue();
            assertThat(check.daysLeft().getAsInt()).isPositive();
        }

        @Test
        void hasLapsedAYearAndADayLater() {
            SafetyStatus check = Training.currentToWork(RECORDS, "2026-01-07");
            assertThat(check.current()).isFalse();
            assertThat(check.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("has lapsed"));
        }

        @Test
        void isNotCurrentWithNoSafetyTrainingAtAll() {
            SafetyStatus check = Training.currentToWork(List.of(course("RT", 40, "2023-02-06")), "2025-06-01");
            assertThat(check.current()).isFalse();
            assertThat(check.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("no radiation safety training"));
        }

        @Test
        void takesTheMostRecentSafetyCourse() {
            List<TrainingRecord> refreshed = new ArrayList<>(RECORDS);
            refreshed.add(course("SAFETY", 8, "2025-11-04"));
            assertThat(Training.currentToWork(refreshed, "2026-06-01").current())
                    .isTrue();
        }

        @Test
        void ignoresASafetyCourseTakenAfterTheDayAskedAbout() {
            List<TrainingRecord> later = List.of(course("SAFETY", 8, "2026-01-06"));
            assertThat(Training.currentToWork(later, "2025-06-01").current()).isFalse();
        }

        @Test
        void listsTheLapsedRecords() {
            assertThat(Training.lapsed(RECORDS, "2026-06-01")).hasSize(1);
            assertThat(Training.lapsed(RECORDS, "2025-06-01")).isEmpty();
        }

        @Test
        void neverCallsTrainingWithNoExpiryLapsed() {
            List<TrainingRecord> rt = List.of(course("RT", 40, "2015-01-01"));
            assertThat(Training.lapsed(rt, "2030-01-01")).isEmpty();
        }
    }

    @Nested
    class TakenBetween {

        @Test
        void findsTheCoursesInsideAWindow() {
            assertThat(Training.takenBetween(RECORDS, "2023-01-01", "2023-12-31"))
                    .hasSize(2);
        }

        @Test
        void includesBothEndsOfTheWindow() {
            assertThat(Training.takenBetween(RECORDS, "2023-02-06", "2023-02-06"))
                    .hasSize(1);
        }

        @Test
        void findsNothingInAQuietYear() {
            assertThat(Training.takenBetween(RECORDS, "2022-01-01", "2022-12-31"))
                    .isEmpty();
        }
    }
}
