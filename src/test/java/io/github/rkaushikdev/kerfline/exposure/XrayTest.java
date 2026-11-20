package io.github.rkaushikdev.kerfline.exposure;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class XrayTest {

    @Nested
    class TheHead {

        @Test
        void namesItsReferenceConditions() {
            assertThat(Xray.xrayReference()).isEqualTo(new XrayReference(40, "D7", 2));
        }

        @Test
        void topsOutAtFourHundredKilovolts() {
            assertThat(Xray.maximumKilovolts()).isEqualTo(400);
        }

        @Test
        void coversAnEighthOfAnInchToThreeInches() {
            assertThat(Xray.thicknessRange()).isEqualTo(new ThicknessSpan(0.125, 3));
        }
    }

    @Nested
    class KilovoltsFor {

        @Test
        void readsTheCurveAtAChartedPoint() {
            assertThat(Xray.kilovoltsFor(1)).isEqualTo(200);
            assertThat(Xray.kilovoltsFor(0.5)).isEqualTo(150);
        }

        @Test
        void climbsWithTheWall() {
            assertThat(Xray.kilovoltsFor(2)).isGreaterThan(Xray.kilovoltsFor(1));
            assertThat(Xray.kilovoltsFor(1)).isGreaterThan(Xray.kilovoltsFor(0.25));
        }

        @Test
        void roundsUpToASettableStepRatherThanDown() {
            double between = Xray.kilovoltsFor(0.6);
            assertThat(between % 5).isEqualTo(0);
            assertThat(between).isGreaterThan(150);
            assertThat(between).isLessThan(180);
        }

        @Test
        void takesADifferentStep() {
            assertThat(Xray.kilovoltsFor(0.6, 10) % 10).isEqualTo(0);
        }

        @Test
        void neverGoesPastTheRatingOfTheHead() {
            assertThat(Xray.kilovoltsFor(3)).isEqualTo(400);
        }

        @Test
        void refusesAWallOutsideTheRangeRatherThanGuessing() {
            assertThatThrownBy(() -> Xray.kilovoltsFor(4)).hasMessageContaining("outside the range this head covers");
            assertThatThrownBy(() -> Xray.kilovoltsFor(0.05)).hasMessageContaining("outside the range");
        }
    }

    @Nested
    class ThicknessAtKilovolts {

        @Test
        void invertsTheCurve() {
            assertThat(Xray.thicknessAtKilovolts(200)).isCloseTo(1, digits(3));
            assertThat(Xray.thicknessAtKilovolts(150)).isCloseTo(0.5, digits(3));
        }

        @Test
        void reachesFurtherAtAHigherSetting() {
            assertThat(Xray.thicknessAtKilovolts(300)).isGreaterThan(Xray.thicknessAtKilovolts(200));
        }

        @Test
        void refusesASettingOffTheHead() {
            assertThatThrownBy(() -> Xray.thicknessAtKilovolts(500))
                    .hasMessageContaining("outside the range of this head");
            assertThatThrownBy(() -> Xray.thicknessAtKilovolts(50)).hasMessageContaining("outside the range");
        }
    }

    @Nested
    class MilliampereMinutesFor {

        @Test
        void readsAChartedPoint() {
            assertThat(Xray.milliampereMinutesFor(1)).isCloseTo(15, digits(2));
        }

        @Test
        void climbsWithTheWall() {
            assertThat(Xray.milliampereMinutesFor(2)).isGreaterThan(Xray.milliampereMinutesFor(1));
        }

        @Test
        void interpolatesOnTheLogScaleBetweenPoints() {
            double between = Xray.milliampereMinutesFor(0.625);
            assertThat(between).isCloseTo(Math.sqrt(9 * 12), digits(1));
        }
    }

    @Nested
    class PlanXray {

        private final XrayRequest base =
                XrayRequest.builder().thicknessIn(1).distanceIn(40).milliamps(5).build();

        @Test
        void takesTheShopMinimumKilovoltageByDefault() {
            XrayPlan plan = Xray.planXray(base);
            assertThat(plan.kilovolts()).isEqualTo(200);
            assertThat(plan.minimumKilovolts()).isEqualTo(200);
            assertThat(plan.aboveMinimum()).isFalse();
        }

        @Test
        void worksTheTimeOutFromTheCurrent() {
            XrayPlan plan = Xray.planXray(base);
            assertThat(plan.milliampereMinutes()).isCloseTo(15, digits(2));
            assertThat(plan.minutes()).isCloseTo(3, digits(2));
            assertThat(plan.seconds()).isEqualTo(180);
        }

        @Test
        void squaresTheExposureWithTheDistance() {
            XrayPlan near = Xray.planXray(base);
            XrayPlan far = Xray.planXray(base.toBuilder().distanceIn(80).build());
            assertThat(far.milliampereMinutes()).isCloseTo(near.milliampereMinutes() * 4, digits(2));
        }

        @Test
        void shortensTheTimeWhenTheCurrentGoesUp() {
            XrayPlan plan = Xray.planXray(base.toBuilder().milliamps(10).build());
            assertThat(plan.minutes()).isCloseTo(1.5, digits(2));
        }

        @Test
        void recordsADecisionToRunAboveTheMinimum() {
            XrayPlan plan = Xray.planXray(base.toBuilder().kilovolts(250).build());
            assertThat(plan.kilovolts()).isEqualTo(250);
            assertThat(plan.aboveMinimum()).isTrue();
        }

        @Test
        void refusesASettingThatWillNotPenetrate() {
            assertThatThrownBy(
                            () -> Xray.planXray(base.toBuilder().kilovolts(150).build()))
                    .hasMessageContaining("kilovoltage must not be below");
        }

        @Test
        void refusesADistanceOrACurrentAtZero() {
            assertThatThrownBy(
                            () -> Xray.planXray(base.toBuilder().distanceIn(0).build()))
                    .hasMessageContaining("distance");
            assertThatThrownBy(() -> Xray.planXray(base.toBuilder().milliamps(0).build()))
                    .hasMessageContaining("tube current");
        }
    }

    @Nested
    class HeadCanReach {

        @Test
        void saysAHeadIsUpToTheJob() {
            HeadCheck check = Xray.headCanReach(300, 1);
            assertThat(check.canReach()).isTrue();
            assertThat(check.shortBy()).isEqualTo(0);
        }

        @Test
        void saysAHeadIsNotAndByHowMuch() {
            HeadCheck check = Xray.headCanReach(160, 1);
            assertThat(check.canReach()).isFalse();
            assertThat(check.shortBy()).isEqualTo(40);
        }

        @Test
        void acceptsAHeadRatedExactlyAtWhatIsNeeded() {
            assertThat(Xray.headCanReach(200, 1).canReach()).isTrue();
        }

        @Test
        void refusesARatingAtZero() {
            assertThatThrownBy(() -> Xray.headCanReach(0, 1)).hasMessageContaining("rated kilovoltage");
        }
    }
}
