package io.github.rkaushikdev.kerfline.density;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StepwedgeTest {

    private static final List<Double> CERTIFIED = List.of(1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0);

    private static List<Double> shifted(double by) {
        return CERTIFIED.stream().map(value -> value + by).toList();
    }

    private static List<Double> certifiedCopy() {
        return new ArrayList<>(CERTIFIED);
    }

    @Nested
    class TheCalibrationRules {

        @Test
        void holdsEachStepToFiveHundredths() {
            assertThat(Stepwedge.calibrationTolerance()).isEqualTo(0.05);
        }

        @Test
        void lapsesAfterNinetyDays() {
            assertThat(Stepwedge.calibrationValidDays()).isEqualTo(90);
        }
    }

    @Nested
    class CompareSteps {

        @Test
        void passesAnInstrumentReadingTrue() {
            StepComparison result = Stepwedge.compareSteps(List.of(1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0), CERTIFIED);
            assertThat(result.pass()).isTrue();
            assertThat(result.largestError()).isEqualTo(0);
        }

        @Test
        void passesAnInstrumentInsideTheToleranceOnEveryStep() {
            assertThat(Stepwedge.compareSteps(shifted(0.04), CERTIFIED).pass()).isTrue();
        }

        @Test
        void acceptsAStepSittingExactlyOnTheTolerance() {
            assertThat(Stepwedge.compareSteps(shifted(0.05), CERTIFIED).pass()).isTrue();
        }

        @Test
        void failsOnOneStepOutsideTheTolerance() {
            List<Double> readings = certifiedCopy();
            readings.set(3, readings.get(3) + 0.09);
            StepComparison result = Stepwedge.compareSteps(readings, CERTIFIED);
            assertThat(result.pass()).isFalse();
            assertThat(result.worstStep()).isEqualTo(4);
            assertThat(result.worstError()).isCloseTo(0.09, digits(4));
        }

        @Test
        void failsAnInstrumentThatAveragesToNothingButIsOutAtBothEnds() {
            List<Double> readings = certifiedCopy();
            readings.set(0, readings.get(0) + 0.09);
            readings.set(6, readings.get(6) - 0.09);
            StepComparison result = Stepwedge.compareSteps(readings, CERTIFIED);
            assertThat(result.pass()).isFalse();
            double average = 0;
            for (StepCheck step : result.steps()) {
                average += step.error();
            }
            assertThat(average).isCloseTo(0, digits(6));
        }

        @Test
        void signsTheErrorTheWayTheInstrumentReads() {
            List<Double> readings = certifiedCopy();
            readings.set(2, readings.get(2) - 0.08);
            assertThat(Stepwedge.compareSteps(readings, CERTIFIED).worstError()).isCloseTo(-0.08, digits(4));
        }

        @Test
        void takesATighterTolerance() {
            assertThat(Stepwedge.compareSteps(shifted(0.04), CERTIFIED, 0.02).pass())
                    .isFalse();
        }

        @Test
        void refusesListsOfDifferentLengths() {
            assertThatThrownBy(() -> Stepwedge.compareSteps(List.of(1.0, 1.5), CERTIFIED))
                    .hasMessageContaining("without a certified step");
        }

        @Test
        void refusesAnEmptyCalibration() {
            assertThatThrownBy(() -> Stepwedge.compareSteps(List.of(), List.of()))
                    .hasMessageContaining("at least one step");
        }

        @Test
        void namesTheStepThatIsNotAPairOfNumbers() {
            assertThatThrownBy(() -> Stepwedge.compareSteps(Arrays.asList(1.0, null), List.of(1.0, 1.5)))
                    .hasMessageContaining("step 2 is not a pair");
        }
    }

    @Nested
    class CalibrationRecordOfAnInstrument {

        private final CalibrationRecord record =
                Stepwedge.calibrationRecord("X-Rite 301", "2025-03-14", CERTIFIED, CERTIFIED);

        @Test
        void storesTheExpiryRatherThanWorkingItOutOnRead() {
            assertThat(record.day()).isEqualTo("2025-03-14");
            assertThat(record.expires()).isEqualTo("2025-06-12");
        }

        @Test
        void carriesTheComparisonThrough() {
            assertThat(record.pass()).isTrue();
            assertThat(record.steps()).hasSize(7);
            assertThat(record.instrument()).isEqualTo("X-Rite 301");
        }

        @Test
        void refusesARecordWithNoInstrumentOnIt() {
            assertThatThrownBy(() -> Stepwedge.calibrationRecord("  ", "2025-03-14", CERTIFIED, CERTIFIED))
                    .hasMessageContaining("names the instrument");
        }

        @Test
        void refusesADateThatIsNotARealDay() {
            assertThatThrownBy(() -> Stepwedge.calibrationRecord("X-Rite 301", "2025-02-30", CERTIFIED, CERTIFIED))
                    .hasMessageContaining("not a real calendar day");
        }
    }

    @Nested
    class CalibrationCurrent {

        private final CalibrationRecord good =
                Stepwedge.calibrationRecord("X-Rite 301", "2025-03-14", CERTIFIED, CERTIFIED);

        @Test
        void isCurrentOnTheDayItWasDone() {
            assertThat(Stepwedge.calibrationCurrent(good, "2025-03-14")).isTrue();
        }

        @Test
        void isCurrentOnTheDayItExpires() {
            assertThat(Stepwedge.calibrationCurrent(good, "2025-06-12")).isTrue();
        }

        @Test
        void hasLapsedTheDayAfter() {
            assertThat(Stepwedge.calibrationCurrent(good, "2025-06-13")).isFalse();
        }

        @Test
        void isNotCurrentBeforeItWasDone() {
            assertThat(Stepwedge.calibrationCurrent(good, "2025-03-13")).isFalse();
        }

        @Test
        void isNeverCurrentWhenTheCalibrationItselfFailed() {
            List<Double> readings = certifiedCopy();
            readings.set(0, readings.get(0) + 0.5);
            CalibrationRecord bad = Stepwedge.calibrationRecord("X-Rite 301", "2025-03-14", readings, CERTIFIED);
            assertThat(bad.pass()).isFalse();
            assertThat(Stepwedge.calibrationCurrent(bad, "2025-03-14")).isFalse();
        }

        @Test
        void countsTheDaysLeft() {
            assertThat(Stepwedge.daysUntilExpiry(good, "2025-06-01")).isEqualTo(11);
            assertThat(Stepwedge.daysUntilExpiry(good, "2025-06-13")).isEqualTo(-1);
        }
    }

    @Nested
    class InterpolateStep {

        @Test
        void landsOnAWholeStepAtACertifiedDensity() {
            assertThat(Stepwedge.interpolateStep(CERTIFIED, 1)).isEqualTo(1);
            assertThat(Stepwedge.interpolateStep(CERTIFIED, 2)).isEqualTo(3);
            assertThat(Stepwedge.interpolateStep(CERTIFIED, 4)).isEqualTo(7);
        }

        @Test
        void landsBetweenTwoStepsInTheMiddle() {
            assertThat(Stepwedge.interpolateStep(CERTIFIED, 1.75)).isCloseTo(2.5, digits(3));
        }

        @Test
        void refusesADensityOffTheEndOfTheWedge() {
            assertThatThrownBy(() -> Stepwedge.interpolateStep(CERTIFIED, 0.5))
                    .hasMessageContaining("off the end of the wedge");
            assertThatThrownBy(() -> Stepwedge.interpolateStep(CERTIFIED, 4.5))
                    .hasMessageContaining("off the end of the wedge");
        }

        @Test
        void refusesAWedgeWithFewerThanTwoSteps() {
            assertThatThrownBy(() -> Stepwedge.interpolateStep(List.of(2.0), 2))
                    .hasMessageContaining("at least two steps");
        }
    }
}
