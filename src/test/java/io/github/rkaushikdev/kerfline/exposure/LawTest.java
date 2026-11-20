package io.github.rkaushikdev.kerfline.exposure;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LawTest {

    @Nested
    class IntensityAtDistance {

        @Test
        void quartersWhenTheDistanceDoubles() {
            assertThat(Law.intensityAtDistance(400, 12, 24)).isCloseTo(100, digits(9));
        }

        @Test
        void quadruplesWhenTheDistanceHalves() {
            assertThat(Law.intensityAtDistance(100, 24, 12)).isCloseTo(400, digits(9));
        }

        @Test
        void leavesTheReadingAloneAtTheSameDistance() {
            assertThat(Law.intensityAtDistance(400, 12, 12)).isCloseTo(400, digits(9));
        }

        @Test
        void refusesADistanceAtZero() {
            assertThatThrownBy(() -> Law.intensityAtDistance(400, 0, 12)).hasMessageContaining("greater than 0");
            assertThatThrownBy(() -> Law.intensityAtDistance(400, 12, 0)).hasMessageContaining("greater than 0");
        }
    }

    @Nested
    class TimeAtDistance {

        @Test
        void goesTheOtherWayFromIntensity() {
            assertThat(Law.timeAtDistance(3, 12, 24)).isCloseTo(12, digits(9));
            assertThat(Law.timeAtDistance(12, 24, 12)).isCloseTo(3, digits(9));
        }

        @Test
        void invertsBackThroughDistanceForTime() {
            double longer = Law.timeAtDistance(3, 12, 30);
            assertThat(Law.distanceForTime(3, 12, longer)).isCloseTo(30, digits(9));
        }

        @Test
        void refusesANegativeTime() {
            assertThatThrownBy(() -> Law.timeAtDistance(-1, 12, 24)).hasMessageContaining("not be below 0");
        }
    }

    @Nested
    class TheExposureProducts {

        @Test
        void multipliesActivityByTime() {
            assertThat(Law.curieMinutes(38, 4.5)).isCloseTo(171, digits(9));
        }

        @Test
        void dividesBackToATime() {
            assertThat(Law.minutesForCurieMinutes(171, 38)).isCloseTo(4.5, digits(9));
        }

        @Test
        void asksForLongerAsTheSourceDecays() {
            double fresh = Law.minutesForCurieMinutes(171, 38);
            double aged = Law.minutesForCurieMinutes(171, 19);
            assertThat(aged).isCloseTo(fresh * 2, digits(9));
        }

        @Test
        void doesTheSameForAnXrayHead() {
            assertThat(Law.milliampereMinutes(5, 2)).isCloseTo(10, digits(9));
            assertThat(Law.minutesForMilliampereMinutes(10, 5)).isCloseTo(2, digits(9));
        }

        @Test
        void refusesAnActivityOrCurrentAtZero() {
            assertThatThrownBy(() -> Law.curieMinutes(0, 4.5)).hasMessageContaining("greater than 0");
            assertThatThrownBy(() -> Law.minutesForCurieMinutes(171, 0)).hasMessageContaining("greater than 0");
            assertThatThrownBy(() -> Law.milliampereMinutes(0, 2)).hasMessageContaining("greater than 0");
        }
    }

    @Nested
    class ScaleExposure {

        @Test
        void movesAChartExposureOntoTheRealSetup() {
            assertThat(Law.scaleExposure(100, 24, 48)).isCloseTo(400, digits(9));
        }

        @Test
        void shrinksTheExposureWhenTheCrewWorksCloserThanTheChart() {
            assertThat(Law.scaleExposure(400, 48, 24)).isCloseTo(100, digits(9));
        }

        @Test
        void leavesItAloneAtTheChartDistance() {
            assertThat(Law.scaleExposure(171, 24, 24)).isCloseTo(171, digits(12));
        }
    }

    @Nested
    class EquivalentExposure {

        private final ShotExposure base = new ShotExposure(38, 4.5, 24);

        @Test
        void acceptsASetupThatLaysDownTheSameExposure() {
            ShotExposure twiceTheDistance = new ShotExposure(38, 18, 48);
            assertThat(Law.equivalentExposure(base, twiceTheDistance)).isTrue();
        }

        @Test
        void acceptsHalvingTheSourceAndDoublingTheTime() {
            assertThat(Law.equivalentExposure(base, new ShotExposure(19, 9, 24)))
                    .isTrue();
        }

        @Test
        void rejectsASetupThatIsPlainlyShort() {
            assertThat(Law.equivalentExposure(base, new ShotExposure(38, 2, 24)))
                    .isFalse();
        }

        @Test
        void takesATolerance() {
            ShotExposure close = new ShotExposure(38, 4.6, 24);
            assertThat(Law.equivalentExposure(base, close, 0.05)).isTrue();
            assertThat(Law.equivalentExposure(base, close, 0.001)).isFalse();
        }

        @Test
        void callsTwoDeadSourcesEquivalentRatherThanDividingByNothing() {
            ShotExposure dead = new ShotExposure(38, 0, 24);
            assertThat(Law.equivalentExposure(dead, dead)).isTrue();
        }
    }
}
