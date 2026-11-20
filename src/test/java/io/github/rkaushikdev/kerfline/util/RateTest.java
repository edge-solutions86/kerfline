package io.github.rkaushikdev.kerfline.util;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RateTest {

    @Nested
    class RateOf {

        @Test
        void divides() {
            assertThat(Rate.rateOf(1, 4)).isCloseTo(0.25, digits(6));
            assertThat(Rate.rateOf(3, 4)).isCloseTo(0.75, digits(6));
        }

        @Test
        void isNoughtOverNothingByDefault() {
            assertThat(Rate.rateOf(0, 0)).isEqualTo(0);
        }

        @Test
        void isOneOverNothingWhenThatIsWhatNothingMeans() {
            assertThat(Rate.rateOf(0, 0, 1)).isEqualTo(1);
        }

        @Test
        void isExactlyOneWhenEverythingCounted() {
            assertThat(Rate.rateOf(4, 4)).isEqualTo(1);
        }

        @Test
        void isExactlyNoughtWhenNothingDid() {
            assertThat(Rate.rateOf(0, 4)).isEqualTo(0);
        }

        @Test
        void roundsToFourPlacesByDefault() {
            assertThat(Num.text(Rate.rateOf(1, 3)).split("\\.")[1].length()).isLessThanOrEqualTo(4);
        }

        @Test
        void takesADifferentNumberOfPlaces() {
            assertThat(Rate.rateOf(1, 3, 0, 2)).isEqualTo(0.33);
        }

        @Test
        void refusesAPartBiggerThanTheWhole() {
            assertThatThrownBy(() -> Rate.rateOf(5, 4))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("more than all of them");
        }

        @Test
        void refusesCountsThatAreNotWholeNumbers() {
            assertThatThrownBy(() -> Rate.rateOf(1.5, 4)).hasMessageContaining("whole number");
            assertThatThrownBy(() -> Rate.rateOf(1, 4.5)).hasMessageContaining("whole number");
        }

        @Test
        void refusesANegativeCount() {
            assertThatThrownBy(() -> Rate.rateOf(-1, 4)).hasMessageContaining("not be below 0");
        }
    }

    @Nested
    class AsRate {

        @Test
        void carriesTheCountsTheRateCameFrom() {
            assertThat(Rate.asRate(1, 4)).isEqualTo(new CountedRate(1, 4, 0.25));
        }

        @Test
        void keepsThemApartSoAReportCanPrintAllThree() {
            CountedRate small = Rate.asRate(1, 3);
            CountedRate large = Rate.asRate(30, 90);
            assertThat(small.rate()).isCloseTo(large.rate(), digits(3));
            assertThat(small.whole()).isNotEqualTo(large.whole());
        }

        @Test
        void passesTheEmptyCaseThrough() {
            assertThat(Rate.asRate(0, 0, 1).rate()).isEqualTo(1);
        }
    }

    @Nested
    class PercentOf {

        @Test
        void isTheRateTimesAHundred() {
            assertThat(Rate.percentOf(1, 4)).isEqualTo(25);
            assertThat(Rate.percentOf(1, 3)).isEqualTo(33.3);
        }

        @Test
        void roundsToOnePlaceByDefault() {
            assertThat(Rate.percentOf(2, 3)).isEqualTo(66.7);
        }

        @Test
        void takesADifferentNumberOfPlaces() {
            assertThat(Rate.percentOf(2, 3, 0, 2)).isEqualTo(66.67);
        }

        @Test
        void carriesTheEmptyCaseThrough() {
            assertThat(Rate.percentOf(0, 0)).isEqualTo(0);
            assertThat(Rate.percentOf(0, 0, 1)).isEqualTo(100);
        }
    }

    @Nested
    class ItemsToReach {

        @Test
        void isNothingWhenTheRateIsAlreadyUnderTheTarget() {
            assertThat(Rate.itemsToReach(1, 100, 0.1)).isZero();
            assertThat(Rate.itemsToReach(0, 0, 0.1)).isZero();
        }

        @Test
        void countsWhatItTakesToGetUnder() {
            assertThat(Rate.itemsToReach(4, 20, 0.1)).isEqualTo(20);
        }

        @Test
        void actuallyGetsUnderWhenThoseItemsArrive() {
            int needed = Rate.itemsToReach(4, 20, 0.1);
            assertThat(Rate.rateOf(4, 20 + needed)).isLessThanOrEqualTo(0.1);
        }

        @Test
        void isNothingWhenTheRateSitsExactlyOnTheTarget() {
            assertThat(Rate.itemsToReach(2, 20, 0.1)).isZero();
        }

        @Test
        void refusesATargetOfNoughtOrAboveOne() {
            assertThatThrownBy(() -> Rate.itemsToReach(1, 10, 0))
                    .hasMessageContaining("above zero and no more than one");
            assertThatThrownBy(() -> Rate.itemsToReach(1, 10, 1.5))
                    .hasMessageContaining("above zero and no more than one");
        }
    }
}
