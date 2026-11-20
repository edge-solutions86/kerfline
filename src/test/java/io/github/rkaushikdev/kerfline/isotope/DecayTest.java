package io.github.rkaushikdev.kerfline.isotope;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.util.Num;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DecayTest {

    @Nested
    class DecayConstant {

        @Test
        void isLnTwoOverTheHalfLife() {
            assertThat(Decay.decayConstant(73.83)).isCloseTo(Math.log(2) / 73.83, digits(12));
        }

        @Test
        void refusesAHalfLifeThatIsNotPositive() {
            assertThatThrownBy(() -> Decay.decayConstant(0)).hasMessageContaining("greater than zero");
            assertThatThrownBy(() -> Decay.decayConstant(-1)).hasMessageContaining("greater than zero");
            assertThatThrownBy(() -> Decay.decayConstant(Double.NaN)).hasMessageContaining("finite number");
        }
    }

    @Nested
    class DecayFactor {

        @Test
        void halvesOverOneHalfLife() {
            assertThat(Decay.decayFactor(73.83, 73.83)).isCloseTo(0.5, digits(12));
            assertThat(Decay.decayFactor(73.83, 147.66)).isCloseTo(0.25, digits(12));
        }

        @Test
        void isOneAtTheAssay() {
            assertThat(Decay.decayFactor(73.83, 0)).isEqualTo(1);
        }

        @Test
        void goesAboveOneWhenBackDecayed() {
            assertThat(Decay.decayFactor(73.83, -73.83)).isCloseTo(2, digits(12));
        }

        @Test
        void countsHalfLives() {
            assertThat(Decay.halfLivesElapsed(73.83, 147.66)).isCloseTo(2, digits(12));
        }
    }

    @Nested
    class ActivityAfter {

        @Test
        void takesAHundredCurieSourceDownByHalf() {
            assertThat(Decay.activityAfter(100, 73.83, 73.83)).isCloseTo(50, digits(10));
        }

        @Test
        void matchesAThirtyDayStepOnIridium() {
            assertThat(Decay.activityAfter(85, 73.83, 30)).isCloseTo(64.14, digits(2));
        }

        @Test
        void barelyMovesCobaltOverAMonth() {
            assertThat(Decay.activityAfter(20, 1925.3, 30)).isCloseTo(19.79, digits(2));
        }

        @Test
        void refusesAnAssayThatIsNotPositive() {
            assertThatThrownBy(() -> Decay.activityAfter(0, 73.83, 10)).hasMessageContaining("greater than zero");
            assertThatThrownBy(() -> Decay.activityAfter(-5, 73.83, 10)).hasMessageContaining("greater than zero");
        }

        @Test
        void refusesAnElapsedSpanThatIsNotANumber() {
            assertThatThrownBy(() -> Decay.activityAfter(100, 73.83, Double.NaN))
                    .hasMessageContaining("finite number");
        }
    }

    @Nested
    class ActivityOf {

        @Test
        void takesTheHalfLifeFromTheCatalog() {
            assertThat(Decay.activityOf("IR192", 100, 73.83)).isCloseTo(50, digits(10));
            assertThat(Decay.activityOf("se-75", 40, 119.78)).isCloseTo(20, digits(10));
        }

        @Test
        void refusesAnIsotopeTheShopIsNotLicensedFor() {
            assertThatThrownBy(() -> Decay.activityOf("CS137", 100, 10)).hasMessageContaining("no isotope CS137");
        }
    }

    @Nested
    class DaysToActivity {

        @Test
        void reachesHalfInOneHalfLife() {
            assertThat(Decay.daysToActivity(100, 73.83, 50)).isCloseTo(73.83, digits(9));
        }

        @Test
        void reachesAQuarterInTwo() {
            assertThat(Decay.daysToActivity(100, 73.83, 25)).isCloseTo(147.66, digits(9));
        }

        @Test
        void isZeroWhenTheTargetIsTheAssay() {
            assertThat(Decay.daysToActivity(100, 73.83, 100)).isCloseTo(0, digits(12));
        }

        @Test
        void goesNegativeWhenTheTargetIsAboveTheAssay() {
            assertThat(Decay.daysToActivity(50, 73.83, 100)).isCloseTo(-73.83, digits(9));
        }

        @Test
        void refusesATargetThatIsNotPositive() {
            assertThatThrownBy(() -> Decay.daysToActivity(100, 73.83, 0)).hasMessageContaining("greater than zero");
        }
    }

    @Nested
    class UsableDays {

        @Test
        void roundsDownToAWholeDayTheSchedulerCanBook() {
            assertThat(Decay.usableDays("IR192", 100, 50)).isEqualTo(73);
        }

        @Test
        void isZeroWhenTheSourceIsAlreadyBelowTheFloor() {
            assertThat(Decay.usableDays("IR192", 20, 30)).isZero();
        }

        @Test
        void givesCobaltYearsRatherThanMonths() {
            assertThat(Decay.usableDays("CO60", 30, 15)).isEqualTo(1925);
        }
    }

    @Nested
    class DecayTable {

        @Test
        void walksTheOffsetsGiven() {
            assertThat(Decay.decayTable("IR192", 100, List.of(0.0, 73.83)))
                    .containsExactly(new DecayRow(0, 100, 1), new DecayRow(73.83, 50, 0.5));
        }

        @Test
        void roundsToAStatedNumberOfPlaces() {
            List<DecayRow> rows = Decay.decayTable("IR192", 100, List.of(10.0), 4);
            assertThat(rows.get(0).curies()).isCloseTo(91.0, digits(1));
            assertThat(Num.text(rows.get(0).curies()).split("\\.")[1].length()).isLessThanOrEqualTo(4);
        }

        @Test
        void refusesAnEmptyOffsetList() {
            assertThatThrownBy(() -> Decay.decayTable("IR192", 100, List.of()))
                    .hasMessageContaining("at least one day offset");
            assertThatThrownBy(() -> Decay.decayTable("IR192", 100, null))
                    .hasMessageContaining("at least one day offset");
        }

        @Test
        void refusesAnOffsetThatIsNotANumber() {
            assertThatThrownBy(() -> Decay.decayTable("IR192", 100, List.of(0.0, Double.NaN)))
                    .hasMessageContaining("finite number");
        }
    }

    @Nested
    class ExposureMultiplier {

        @Test
        void doublesTheTimeAfterOneHalfLife() {
            assertThat(Decay.exposureMultiplier(73.83, 73.83)).isCloseTo(2, digits(10));
        }

        @Test
        void isOneAtTheAssay() {
            assertThat(Decay.exposureMultiplier(73.83, 0)).isEqualTo(1);
        }

        @Test
        void isTheReciprocalOfTheDecayFactor() {
            int days = 41;
            assertThat(Decay.exposureMultiplier(119.78, days) * Decay.decayFactor(119.78, days))
                    .isCloseTo(1, digits(12));
        }
    }
}
