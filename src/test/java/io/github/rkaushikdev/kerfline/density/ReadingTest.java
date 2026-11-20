package io.github.rkaushikdev.kerfline.density;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReadingTest {

    private static final List<Double> CERTIFIED = List.of(1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0);

    private static final CalibrationRecord CALIBRATION =
            Stepwedge.calibrationRecord("X-Rite 301", "2025-01-10", CERTIFIED, CERTIFIED);

    private static final ReadingSetSpec BASE = ReadingSetSpec.builder()
            .film("J-118-1")
            .day("2025-03-14")
            .instrument("X-Rite 301")
            .calibration(CALIBRATION)
            .indicatorDensity(2.4)
            .area(List.of(AreaReading.at("A", 2.3), AreaReading.at("B", 2.5), AreaReading.at("C", 2.4)))
            .build();

    private static ReadingSet set(ReadingSetSpec.Builder changes) {
        return Reading.makeReadingSet(changes.build());
    }

    @Nested
    class MakeReadingSet {

        private final ReadingSet made = Reading.makeReadingSet(BASE);

        @Test
        void keepsTheReferenceApartFromTheAreaReadings() {
            assertThat(made.indicatorDensity()).isEqualTo(2.4);
            assertThat(made.area()).hasSize(3);
        }

        @Test
        void recordsTheInstrumentAndThatItWasCalibrated() {
            assertThat(made.instrument()).isEqualTo("X-Rite 301");
            assertThat(made.calibrated()).isTrue();
        }

        @Test
        void refusesAnInstrumentThatWasOutOfCalibrationThatDay() {
            assertThatThrownBy(() -> set(BASE.toBuilder().day("2025-08-01")))
                    .hasMessageContaining("was not in calibration on 2025-08-01");
        }

        @Test
        void refusesACalibrationBelongingToAnotherInstrument() {
            assertThatThrownBy(() -> set(BASE.toBuilder().instrument("Macbeth TD-903")))
                    .hasMessageContaining("for a different instrument");
        }

        @Test
        void allowsASetWithNoCalibrationOnFileAndSaysSo() {
            ReadingSet uncalibrated = set(BASE.toBuilder().calibration(null));
            assertThat(uncalibrated.calibrated()).isFalse();
        }

        @Test
        void refusesASetWithNoFilmOrNoInstrumentNamed() {
            assertThatThrownBy(() -> set(BASE.toBuilder().film(" "))).hasMessageContaining("names the film");
            assertThatThrownBy(() -> set(BASE.toBuilder().instrument("").calibration(null)))
                    .hasMessageContaining("names the instrument");
        }

        @Test
        void namesTheAreaReadingThatCarriesNoDensity() {
            List<AreaReading> area = List.of(new AreaReading(Optional.of("A"), OptionalDouble.empty()));
            assertThatThrownBy(() -> set(BASE.toBuilder().area(area)))
                    .hasMessageContaining("area reading 1 has no usable density");
        }

        @Test
        void takesABareNumberAsAnAreaReading() {
            ReadingSet bare = set(BASE.toBuilder().area(AreaReading.unlocated(List.of(2.3, 2.5))));
            assertThat(bare.area().get(0)).isEqualTo(new AreaReading(Optional.empty(), OptionalDouble.of(2.3)));
        }
    }

    @Nested
    class Profile {

        @Test
        void describesTheShapeOfTheFilm() {
            DensityProfile shape = Reading.profile(Reading.makeReadingSet(BASE));
            assertThat(shape.readings()).isEqualTo(3);
            assertThat(shape.lightestDensity()).isEqualTo(2.3);
            assertThat(shape.darkestDensity()).isEqualTo(2.5);
            assertThat(shape.meanDensity()).isCloseTo(2.4, digits(2));
            assertThat(shape.spread()).isCloseTo(0.2, digits(2));
        }

        @Test
        void fallsBackToTheReferenceWhenNothingWasReadAcrossTheArea() {
            DensityProfile shape = Reading.profile(set(BASE.toBuilder().area(List.of())));
            assertThat(shape.readings()).isZero();
            assertThat(shape.meanDensity()).isEqualTo(2.4);
            assertThat(shape.spread()).isEqualTo(0);
        }
    }

    @Nested
    class JudgeSet {

        @Test
        void passesAGoodFilm() {
            SetJudgement judged = Reading.judgeSet(Reading.makeReadingSet(BASE));
            assertThat(judged.pass()).isTrue();
            assertThat(judged.reference().pass()).isTrue();
            assertThat(judged.area().orElseThrow().pass()).isTrue();
        }

        @Test
        void failsWhenTheReferenceIsOutsideTheCodeLimits() {
            SetJudgement judged =
                    Reading.judgeSet(set(BASE.toBuilder().indicatorDensity(1.6).area(List.of())));
            assertThat(judged.pass()).isFalse();
            assertThat(judged.reference().verdict()).isEqualTo(DensityCall.LIGHT);
        }

        @Test
        void failsWhenTheAreaWandersOutsideTheVariationBand() {
            SetJudgement judged = Reading.judgeSet(set(BASE.toBuilder().area(List.of(AreaReading.at("A", 1.5)))));
            assertThat(judged.pass()).isFalse();
            assertThat(judged.area().orElseThrow().pass()).isFalse();
        }

        @Test
        void judgesTheAreaAgainstTheReferenceAndNotAConstant() {
            SetJudgement light =
                    Reading.judgeSet(set(BASE.toBuilder().indicatorDensity(2).area(List.of(AreaReading.of(2.5)))));
            SetJudgement dark =
                    Reading.judgeSet(set(BASE.toBuilder().indicatorDensity(3.5).area(List.of(AreaReading.of(2.5)))));
            assertThat(light.area().orElseThrow().pass()).isTrue();
            assertThat(dark.area().orElseThrow().pass()).isFalse();
        }

        @Test
        void hasNoAreaVerdictWhenNothingWasReadAcrossIt() {
            assertThat(Reading.judgeSet(set(BASE.toBuilder().area(List.of()))).area())
                    .isEmpty();
        }

        @Test
        void listsTheReadingsThatFailed() {
            ReadingSet failing = set(BASE.toBuilder().area(List.of(AreaReading.at("A", 1.5))));
            assertThat(Reading.failingReadings(failing)).hasSize(1);
        }

        @Test
        void listsNothingFailingOnAGoodFilm() {
            assertThat(Reading.failingReadings(Reading.makeReadingSet(BASE))).isEmpty();
        }
    }

    @Nested
    class CompareSets {

        private final ReadingSet first = Reading.makeReadingSet(BASE);

        @Test
        void agreesWhenTwoInstrumentsReadTheSame() {
            ReadingSet second = set(BASE.toBuilder().instrument("X-Rite 301").indicatorDensity(2.42));
            SetComparison check = Reading.compareSets(first, second);
            assertThat(check.agree()).isTrue();
            assertThat(check.difference()).isCloseTo(0.02, digits(3));
        }

        @Test
        void disagreesWhenTheyDoNot() {
            ReadingSet second = set(BASE.toBuilder().indicatorDensity(2.7));
            assertThat(Reading.compareSets(first, second).agree()).isFalse();
        }

        @Test
        void namesBothInstruments() {
            ReadingSet second = set(BASE.toBuilder().indicatorDensity(2.42));
            assertThat(Reading.compareSets(first, second).instruments()).hasSize(2);
        }

        @Test
        void refusesToCompareTwoDifferentFilms() {
            ReadingSet other = set(BASE.toBuilder().film("J-119-1"));
            assertThatThrownBy(() -> Reading.compareSets(first, other)).hasMessageContaining("off different films");
        }

        @Test
        void takesATighterTolerance() {
            ReadingSet second = set(BASE.toBuilder().indicatorDensity(2.42));
            assertThat(Reading.compareSets(first, second, 0.01).agree()).isFalse();
        }
    }
}
