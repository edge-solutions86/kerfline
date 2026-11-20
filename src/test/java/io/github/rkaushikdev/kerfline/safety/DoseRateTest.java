package io.github.rkaushikdev.kerfline.safety;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DoseRateTest {

    private static final Camera CAMERA = new Camera("IR192", 40);

    @Nested
    class DoseRateAt {

        @Test
        void followsTheInverseSquare() {
            double near = DoseRate.doseRateAt(CAMERA, 10);
            double far = DoseRate.doseRateAt(CAMERA, 20);
            assertThat(far).isCloseTo(near / 4, digits(2));
        }

        @Test
        void readsAboutElevenTimesHotterAtAFootThanAtAMetre() {
            double atFoot = DoseRate.doseRateAt(CAMERA, 1);
            double atMetre = DoseRate.doseRateAtMetres(CAMERA, 1);
            assertThat(atFoot / atMetre).isCloseTo(10.764, digits(2));
        }

        @Test
        void givesTheCatalogConstantBackAtOneMetre() {
            // 0.48 R/h per curie at a metre, forty curies, in milliroentgen.
            assertThat(DoseRate.doseRateAtMetres(CAMERA, 1)).isCloseTo(19200, digits(0));
        }

        @Test
        void scalesWithTheActivity() {
            double half = DoseRate.doseRateAt(new Camera("IR192", 20), 10);
            double full = DoseRate.doseRateAt(CAMERA, 10);
            assertThat(full).isCloseTo(half * 2, digits(2));
        }

        @Test
        void readsCobaltHotterThanIridiumAtTheSameActivity() {
            double iridium = DoseRate.doseRateAt(new Camera("IR192", 10), 10);
            double cobalt = DoseRate.doseRateAt(new Camera("CO60", 10), 10);
            assertThat(cobalt).isGreaterThan(iridium);
        }

        @Test
        void refusesADistanceOrActivityAtZero() {
            assertThatThrownBy(() -> DoseRate.doseRateAt(CAMERA, 0)).hasMessageContaining("greater than 0");
            assertThatThrownBy(() -> DoseRate.doseRateAt(new Camera("IR192", 0), 10))
                    .hasMessageContaining("source activity");
        }
    }

    @Nested
    class DistanceForRateFt {

        @Test
        void invertsDoseRateAt() {
            double rate = DoseRate.doseRateAt(CAMERA, 25);
            assertThat(DoseRate.distanceForRateFt(CAMERA, rate)).isCloseTo(25, digits(1));
        }

        @Test
        void wantsMoreDistanceForALowerRate() {
            double close = DoseRate.distanceForRateFt(CAMERA, 100);
            double far = DoseRate.distanceForRateFt(CAMERA, 2);
            assertThat(far).isGreaterThan(close);
        }

        @Test
        void refusesARateAtZero() {
            assertThatThrownBy(() -> DoseRate.distanceForRateFt(CAMERA, 0)).hasMessageContaining("dose rate");
        }
    }

    @Nested
    class ShieldedRateAt {

        @Test
        void isTheBareRateWithNoShield() {
            double bare = DoseRate.doseRateAt(CAMERA, 20);
            double shielded = DoseRate.shieldedRateAt(CAMERA, 20, "lead", 0);
            assertThat(shielded).isCloseTo(bare, digits(2));
        }

        @Test
        void halvesThroughOneHalfValueLayer() {
            double bare = DoseRate.doseRateAt(CAMERA, 20);
            double shielded = DoseRate.shieldedRateAt(CAMERA, 20, "lead", 4.8);
            assertThat(shielded).isCloseTo(bare / 2, digits(1));
        }

        @Test
        void fallsAwayFastThroughRealShielding() {
            double shielded = DoseRate.shieldedRateAt(CAMERA, 20, "concrete", 300);
            assertThat(shielded).isLessThan(DoseRate.doseRateAt(CAMERA, 20) / 100);
        }

        @Test
        void refusesANegativeShield() {
            assertThatThrownBy(() -> DoseRate.shieldedRateAt(CAMERA, 20, "lead", -1))
                    .hasMessageFindingMatch("not be negative|not be below 0");
        }
    }

    @Nested
    class DoseForExposure {

        @Test
        void isTheRateOverTheFractionOfAnHourWorked() {
            double rate = DoseRate.doseRateAt(CAMERA, 50);
            assertThat(DoseRate.doseForExposure(CAMERA, 50, 60)).isCloseTo(rate, digits(2));
            assertThat(DoseRate.doseForExposure(CAMERA, 50, 30)).isCloseTo(rate / 2, digits(2));
        }

        @Test
        void isNothingForNoTimeAtAll() {
            assertThat(DoseRate.doseForExposure(CAMERA, 50, 0)).isEqualTo(0);
        }

        @Test
        void convertsToTheUnitADosimeterReads() {
            double mr = DoseRate.doseForExposure(CAMERA, 50, 10);
            double usv = DoseRate.doseForExposureMicroSievert(CAMERA, 50, 10);
            assertThat(usv).isCloseTo(mr * 10, digits(1));
        }
    }

    @Nested
    class MinutesToDose {

        @Test
        void allowsLongerFurtherAway() {
            double close = DoseRate.minutesToDose(CAMERA, 20, 100);
            double far = DoseRate.minutesToDose(CAMERA, 200, 100);
            assertThat(far).isGreaterThan(close);
        }

        @Test
        void scalesWithTheLimit() {
            double small = DoseRate.minutesToDose(CAMERA, 100, 50);
            double large = DoseRate.minutesToDose(CAMERA, 100, 100);
            assertThat(large).isCloseTo(small * 2, digits(1));
        }

        @Test
        void refusesALimitAtZero() {
            assertThatThrownBy(() -> DoseRate.minutesToDose(CAMERA, 100, 0)).hasMessageContaining("dose limit");
        }
    }

    @Nested
    class CompareSources {

        @Test
        void ranksTheSourcesCoolestFirstAtTheSameActivity() {
            List<SourceRate> ranked = DoseRate.compareSources(List.of("CO60", "IR192", "SE75"), 20, 30);
            assertThat(ranked.stream().map(SourceRate::isotope)).containsExactly("SE75", "IR192", "CO60");
        }

        @Test
        void reportsARateForEach() {
            List<SourceRate> ranked = DoseRate.compareSources(List.of("IR192"), 20, 30);
            assertThat(ranked.get(0).rateMrPerHour()).isGreaterThan(0);
        }
    }
}
