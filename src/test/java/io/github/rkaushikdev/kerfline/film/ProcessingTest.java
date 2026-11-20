package io.github.rkaushikdev.kerfline.film;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProcessingTest {

    private static final List<FilmSheet> FILMS = List.of(new FilmSheet(4.5, 17), new FilmSheet(4.5, 17));

    @Nested
    class TheCurve {

        @Test
        void isFiveMinutesAtSixtyEight() {
            assertThat(Processing.referenceTemperatureF()).isEqualTo(68);
            assertThat(Processing.developmentMinutes(68)).isEqualTo(5);
        }

        @Test
        void runsFromSixtyToEighty() {
            assertThat(Processing.temperatureBandF()).isEqualTo(new TemperatureBand(60, 80));
        }

        @Test
        void getsFasterAsTheBathGetsWarmer() {
            List<Double> times = Processing.timeTemperatureCurve().stream()
                    .map(CurvePoint::minutes)
                    .toList();
            List<Double> descending = new ArrayList<>(times);
            descending.sort(Comparator.reverseOrder());
            assertThat(times).isEqualTo(descending);
        }

        @Test
        void readsAChartedPointBackExactly() {
            assertThat(Processing.developmentMinutes(72)).isEqualTo(4);
            assertThat(Processing.developmentMinutes(60)).isEqualTo(8);
        }

        @Test
        void interpolatesBetweenTwoPoints() {
            double between = Processing.developmentMinutes(71);
            assertThat(between).isLessThan(4.5);
            assertThat(between).isGreaterThan(4);
        }

        @Test
        void refusesABathOutsideTheBandRatherThanGuessing() {
            assertThatThrownBy(() -> Processing.developmentMinutes(55)).hasMessageContaining("outside the band");
            assertThatThrownBy(() -> Processing.developmentMinutes(90)).hasMessageContaining("outside the band");
        }

        @Test
        void refusesATemperatureAtOrBelowZero() {
            assertThatThrownBy(() -> Processing.developmentMinutes(0)).hasMessageContaining("developer temperature");
        }
    }

    @Nested
    class TemperatureForMinutes {

        @Test
        void invertsTheCurveAtAChartedPoint() {
            assertThat(Processing.temperatureForMinutes(5)).isCloseTo(68, digits(1));
            assertThat(Processing.temperatureForMinutes(4)).isCloseTo(72, digits(1));
        }

        @Test
        void roundTrips() {
            for (double temperature : new double[] {62, 68, 74, 79}) {
                double minutes = Processing.developmentMinutes(temperature);
                assertThat(Processing.temperatureForMinutes(minutes)).isCloseTo(temperature, digits(0));
            }
        }

        @Test
        void refusesATimeOffTheCurve() {
            assertThatThrownBy(() -> Processing.temperatureForMinutes(12))
                    .hasMessageContaining("off the time temperature curve");
            assertThatThrownBy(() -> Processing.temperatureForMinutes(1))
                    .hasMessageContaining("off the time temperature curve");
        }
    }

    @Nested
    class JudgeCycle {

        @Test
        void passesACycleRunToTheCurve() {
            CycleJudgement verdict = Processing.judgeCycle(68, 5);
            assertThat(verdict.pass()).isTrue();
            assertThat(verdict.verdict()).isEqualTo(DevelopmentVerdict.CORRECT);
            assertThat(verdict.verdict().label()).isEqualTo("correct");
            assertThat(verdict.effect()).isEmpty();
        }

        @Test
        void allowsALittleSlackEitherSide() {
            assertThat(Processing.judgeCycle(68, 5.2).pass()).isTrue();
            assertThat(Processing.judgeCycle(68, 4.8).pass()).isTrue();
        }

        @Test
        void callsALongCycleOverdeveloped() {
            CycleJudgement verdict = Processing.judgeCycle(68, 7);
            assertThat(verdict.verdict()).isEqualTo(DevelopmentVerdict.OVERDEVELOPED);
            assertThat(verdict.effect())
                    .hasValueSatisfying(effect -> assertThat(effect).contains("fog both up"));
        }

        @Test
        void callsAShortCycleUnderdevelopedAndSaysWhatItHides() {
            CycleJudgement verdict = Processing.judgeCycle(68, 3);
            assertThat(verdict.verdict()).isEqualTo(DevelopmentVerdict.UNDERDEVELOPED);
            assertThat(verdict.effect())
                    .hasValueSatisfying(effect -> assertThat(effect).contains("hides indications"));
        }

        @Test
        void movesTheVerdictOnTheSameClockWhenTheBathIsWarmer() {
            assertThat(Processing.judgeCycle(68, 5).pass()).isTrue();
            assertThat(Processing.judgeCycle(78, 5).verdict()).isEqualTo(DevelopmentVerdict.OVERDEVELOPED);
        }

        @Test
        void takesATighterTolerance() {
            assertThat(Processing.judgeCycle(68, 5.2, 0.05).pass()).isFalse();
        }

        @Test
        void refusesABathOutsideTheBand() {
            assertThatThrownBy(() -> Processing.judgeCycle(95, 5)).hasMessageContaining("outside the band");
        }
    }

    @Nested
    class ReplenisherMlAndBatchAreaSquareInches {

        @Test
        void addsTheFilmAreasUp() {
            assertThat(Processing.batchAreaSquareInches(FILMS)).isCloseTo(153, digits(1));
        }

        @Test
        void isNothingForAnEmptyBatch() {
            assertThat(Processing.batchAreaSquareInches(List.of())).isEqualTo(0);
            assertThat(Processing.replenisherMl(0)).isEqualTo(0);
        }

        @Test
        void dosesOnAreaAndNotOnFilmCount() {
            List<FilmSheet> big = List.of(new FilmSheet(14, 17));
            assertThat(Processing.batchAreaSquareInches(big)).isGreaterThan(Processing.batchAreaSquareInches(FILMS));
            assertThat(Processing.replenisherMl(Processing.batchAreaSquareInches(big)))
                    .isGreaterThan(Processing.replenisherMl(Processing.batchAreaSquareInches(FILMS)));
        }

        @Test
        void takesADifferentRate() {
            assertThat(Processing.replenisherMl(100, 50)).isEqualTo(50);
            assertThat(Processing.replenisherMl(100)).isEqualTo(75);
        }

        @Test
        void refusesAFilmWithNoSizeOnIt() {
            assertThatThrownBy(() -> Processing.batchAreaSquareInches(List.of(new FilmSheet(4.5, Double.NaN))))
                    .hasMessageContaining("needs a width and a length");
        }

        @Test
        void refusesANegativeAreaOrRate() {
            assertThatThrownBy(() -> Processing.replenisherMl(-1)).hasMessageContaining("not be below 0");
            assertThatThrownBy(() -> Processing.replenisherMl(100, 0)).hasMessageContaining("replenishment rate");
        }
    }

    @Nested
    class CycleRecords {

        private final CycleInput run =
                CycleInput.builder().temperatureF(68).minutes(5).films(FILMS).build();

        @Test
        void fillsTheDarkroomLog() {
            CycleRecord record = Processing.cycleRecord(run);
            assertThat(record.pass()).isTrue();
            assertThat(record.films()).isEqualTo(2);
            assertThat(record.areaSquareInches()).isCloseTo(153, digits(1));
            assertThat(record.replenisherMl()).isCloseTo(114.8, digits(1));
        }

        @Test
        void totalsTheWholeCycleIncludingFixAndWash() {
            assertThat(Processing.cycleRecord(run).totalMinutes()).isEqualTo(35);
        }

        @Test
        void takesDifferentFixAndWashTimes() {
            CycleRecord record = Processing.cycleRecord(
                    run.toBuilder().fixMinutes(8).washMinutes(15).build());
            assertThat(record.totalMinutes()).isEqualTo(28);
        }

        @Test
        void carriesAFailedDevelopmentThroughToTheLog() {
            CycleRecord record =
                    Processing.cycleRecord(run.toBuilder().temperatureF(78).build());
            assertThat(record.pass()).isFalse();
            assertThat(record.verdict()).isEqualTo(DevelopmentVerdict.OVERDEVELOPED);
        }
    }
}
