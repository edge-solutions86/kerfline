package io.github.rkaushikdev.kerfline.geom;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CoverageTest {

    private static final double OD = 12.75;
    private static final double SOD = 24;

    @Nested
    class IncidenceAngleDegrees {

        @Test
        void isSquareOnUnderTheSource() {
            assertThat(Coverage.incidenceAngleDegrees(OD, SOD, 0)).isCloseTo(0, digits(9));
        }

        @Test
        void growsAsTheOffsetGrows() {
            double near = Coverage.incidenceAngleDegrees(OD, SOD, 10);
            double far = Coverage.incidenceAngleDegrees(OD, SOD, 25);
            assertThat(far).isGreaterThan(near);
            assertThat(near).isGreaterThan(0);
        }

        @Test
        void doesNotCareWhichWayRoundTheOffsetRuns() {
            double left = Coverage.incidenceAngleDegrees(OD, SOD, -20);
            double right = Coverage.incidenceAngleDegrees(OD, SOD, 20);
            assertThat(left).isCloseTo(right, digits(12));
        }

        @Test
        void fallsAsTheSourceIsCrankedFurtherOut() {
            double near = Coverage.incidenceAngleDegrees(12.75, 12, 20);
            double far = Coverage.incidenceAngleDegrees(12.75, 96, 20);
            assertThat(far).isLessThan(near);
        }

        @Test
        void refusesAnOffsetPastTheTangentPoint() {
            double tangent = Coverage.tangentOffsetDegrees(OD, SOD);
            assertThatThrownBy(() -> Coverage.incidenceAngleDegrees(OD, SOD, tangent + 1))
                    .hasMessageContaining("past the tangent point");
        }
    }

    @Nested
    class TangentOffsetDegrees {

        @Test
        void opensUpAsTheSourceIsCrankedOut() {
            double near = Coverage.tangentOffsetDegrees(12.75, 6);
            double far = Coverage.tangentOffsetDegrees(12.75, 96);
            assertThat(far).isGreaterThan(near);
            assertThat(far).isLessThan(90);
        }

        @Test
        void closesTowardsNothingAsTheSourceComesOntoThePipe() {
            assertThat(Coverage.tangentOffsetDegrees(12.75, 0.001)).isLessThan(2);
        }
    }

    @Nested
    class ThicknessRatio {

        @Test
        void isOneUnderTheSource() {
            assertThat(Coverage.thicknessRatio(OD, SOD, 0)).isCloseTo(1, digits(9));
        }

        @Test
        void risesWithTheOffset() {
            assertThat(Coverage.thicknessRatio(OD, SOD, 20)).isGreaterThan(1);
            assertThat(Coverage.thicknessRatio(OD, SOD, 25)).isGreaterThan(Coverage.thicknessRatio(OD, SOD, 20));
        }
    }

    @Nested
    class MaxHalfAngleDegrees {

        @Test
        void landsWhereTheRatioMeetsTheCap() {
            double half = Coverage.maxHalfAngleDegrees(OD, SOD, 1.2);
            double at = Coverage.thicknessRatio(OD, SOD, half);
            assertThat(at).isLessThanOrEqualTo(1.2 + 1e-6);
            assertThat(at).isGreaterThan(1.19);
        }

        @Test
        void opensUpWhenTheCapIsLoosened() {
            double tight = Coverage.maxHalfAngleDegrees(OD, SOD, 1.1);
            double loose = Coverage.maxHalfAngleDegrees(OD, SOD, 1.3);
            assertThat(loose).isGreaterThan(tight);
        }

        @Test
        void neverRunsPastTheTangentPoint() {
            double tangent = Coverage.tangentOffsetDegrees(OD, SOD);
            double half = Coverage.maxHalfAngleDegrees(OD, SOD, 100);
            assertThat(half).isLessThanOrEqualTo(tangent);
        }

        @Test
        void refusesACapAtOrBelowOne() {
            assertThatThrownBy(() -> Coverage.maxHalfAngleDegrees(OD, SOD, 1)).hasMessageContaining("greater than 1");
        }
    }

    @Nested
    class ExposuresForCircumference {

        @Test
        void needsMoreShotsOnATighterCap() {
            int tight = Coverage.exposuresForCircumference(OD, SOD, 1.1);
            int loose = Coverage.exposuresForCircumference(OD, SOD, 1.3);
            assertThat(tight).isGreaterThanOrEqualTo(loose);
        }

        @Test
        void needsFewerShotsWithTheSourceCrankedFurtherOut() {
            int near = Coverage.exposuresForCircumference(12.75, 12, 1.2);
            int far = Coverage.exposuresForCircumference(12.75, 96, 1.2);
            assertThat(far).isLessThanOrEqualTo(near);
        }

        @Test
        void needsMoreShotsOnABiggerPipeAtTheSameStandoff() {
            int small = Coverage.exposuresForCircumference(6.625, 24, 1.2);
            int large = Coverage.exposuresForCircumference(24, 24, 1.2);
            assertThat(large).isGreaterThan(small);
        }

        @Test
        void isAlwaysAtLeastOne() {
            assertThat(Coverage.exposuresForCircumference(2.375, 240, 1.5)).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    class ShotPlan {

        private final List<PlannedShot> plan = Coverage.shotPlan(OD, SOD, 1.2);

        @Test
        void makesOneEntryPerShot() {
            assertThat(plan).hasSize(Coverage.exposuresForCircumference(OD, SOD, 1.2));
        }

        @Test
        void numbersTheShotsFromOne() {
            assertThat(plan.stream().map(PlannedShot::shot))
                    .containsExactlyElementsOf(
                            IntStream.rangeClosed(1, plan.size()).boxed().toList());
        }

        @Test
        void spreadsTheCentresEvenlyRoundTheJoint() {
            double step = 360.0 / plan.size();
            for (int index = 0; index < plan.size(); index++) {
                assertThat(plan.get(index).centreDegrees()).isCloseTo((index * step) % 360, digits(3));
            }
        }

        @Test
        void startsAtTheTopOfThePipeByDefault() {
            assertThat(plan.get(0).clock()).isEqualTo("12:00");
            assertThat(plan.get(0).centreDegrees()).isEqualTo(0);
        }

        @Test
        void takesAStartPositionAndStillWrapsInsideATurn() {
            List<PlannedShot> offset = Coverage.shotPlan(OD, SOD, 1.2, 90);
            assertThat(offset.get(0).clock()).isEqualTo("3:00");
            for (PlannedShot shot : offset) {
                assertThat(shot.centreDegrees()).isGreaterThanOrEqualTo(0);
                assertThat(shot.centreDegrees()).isLessThan(360);
            }
        }

        @Test
        void reportsTheOverlapThatCoversTheSeamsBetweenFilms() {
            for (PlannedShot shot : plan) {
                assertThat(shot.overlapDegrees()).isGreaterThanOrEqualTo(0);
                assertThat(shot.coversDegrees()).isGreaterThan(0);
            }
        }
    }

    @Nested
    class RequiredDistanceForShots {

        @Test
        void findsADistanceThatDeliversTheAskedForShotCount() {
            double sod = Coverage.requiredDistanceForShots(12.75, 6, 1.2);
            int got = Coverage.exposuresForCircumference(12.75, sod, 1.2);
            assertThat(got).isLessThanOrEqualTo(6);
        }

        @Test
        void wantsALongerCrankForFewerShots() {
            double six = Coverage.requiredDistanceForShots(12.75, 6, 1.2);
            double eight = Coverage.requiredDistanceForShots(12.75, 8, 1.2);
            assertThat(six).isGreaterThan(eight);
        }

        @Test
        void cannotBeatTheParallelBeamLimitHoweverFarTheSourceIsCranked() {
            // As the source goes to infinity the beam arrives parallel, so the
            // incidence angle equals the angular position and the half angle can
            // never exceed acos(1 / ratio). A 1.2 cap therefore never covers more
            // than about 67 degrees a shot, whatever the distance.
            double ceiling = (Math.acos(1 / 1.2) * 180) / Math.PI;
            double half = Coverage.maxHalfAngleDegrees(12.75, 100000, 1.2);
            assertThat(half).isLessThanOrEqualTo(ceiling + 1e-3);
            assertThat(half).isGreaterThan(ceiling - 0.5);
            assertThatThrownBy(() -> Coverage.requiredDistanceForShots(12.75, 4, 1.2))
                    .hasMessageContaining("cannot cover the joint at any distance");
        }

        @Test
        void refusesAShotCountNoDistanceCanReach() {
            assertThatThrownBy(() -> Coverage.requiredDistanceForShots(12.75, 1, 1.05))
                    .hasMessageContaining("cannot cover the joint at any distance");
        }

        @Test
        void refusesAShotCountThatIsNotAWholeNumber() {
            assertThatThrownBy(() -> Coverage.requiredDistanceForShots(12.75, 0, 1.2))
                    .hasMessageContaining("whole number of shots");
        }
    }
}
