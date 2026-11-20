package io.github.rkaushikdev.kerfline.sampling;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CoverageTest {

    @Nested
    class TheLevels {

        @Test
        void runsFromFullDownToNone() {
            assertThat(Coverage.levels()).containsExactly("full", "ten", "five", "spot", "none");
        }

        @Test
        void carriesAFractionAndALabelForEach() {
            assertThat(Coverage.levelFraction("full")).isEqualTo(1);
            assertThat(Coverage.levelFraction("ten")).isCloseTo(0.1, digits(4));
            assertThat(Coverage.levelFraction("none")).isEqualTo(0);
            assertThat(Coverage.levelLabel("five")).isEqualTo("five percent");
        }

        @Test
        void refusesALevelNobodySpecified() {
            assertThatThrownBy(() -> Coverage.levelFraction("most"))
                    .hasMessageContaining("no radiography level called most");
            assertThatThrownBy(() -> Coverage.levelLabel("most")).hasMessageContaining("no radiography level");
        }
    }

    @Nested
    class LevelFor {

        @Test
        void sendsSevereCyclicServiceToFullRadiography() {
            assertThat(Coverage.levelFor("severe_cyclic")).isEqualTo("full");
        }

        @Test
        void leavesCategoryDWithNone() {
            assertThat(Coverage.levelFor("category_d")).isEqualTo("none");
        }

        @Test
        void putsNormalServiceAtFivePercent() {
            assertThat(Coverage.levelFor("normal")).isEqualTo("five");
            assertThat(Coverage.levelFor()).isEqualTo("five");
        }

        @Test
        void letsAClientOverrideTheLot() {
            assertThat(Coverage.levelFor("category_d", "full")).isEqualTo("full");
        }

        @Test
        void refusesAClientLevelThatDoesNotExist() {
            assertThatThrownBy(() -> Coverage.levelFor("normal", "most")).hasMessageContaining("no radiography level");
        }
    }

    @Nested
    class JointsToShoot {

        @Test
        void roundsUpRatherThanDown() {
            assertThat(Coverage.jointsToShoot(7, "ten")).isEqualTo(1);
            assertThat(Coverage.jointsToShoot(11, "ten")).isEqualTo(2);
            assertThat(Coverage.jointsToShoot(100, "ten")).isEqualTo(10);
        }

        @Test
        void shootsEverythingAtFullRadiography() {
            assertThat(Coverage.jointsToShoot(37, "full")).isEqualTo(37);
        }

        @Test
        void shootsNothingWhenNothingIsCalledFor() {
            assertThat(Coverage.jointsToShoot(37, "none")).isZero();
        }

        @Test
        void neverAsksForMoreJointsThanTheJobHas() {
            assertThat(Coverage.jointsToShoot(1, "full")).isEqualTo(1);
            assertThat(Coverage.jointsToShoot(0, "ten")).isZero();
        }

        @Test
        void refusesACountThatIsNotAWholeNumber() {
            assertThatThrownBy(() -> Coverage.jointsToShoot(-1, "ten")).hasMessageContaining("not be below 0");
        }
    }

    @Nested
    class CoverageAchieved {

        @Test
        void clearsTheFloorWhenEnoughJointsWereShot() {
            CoverageResult coverage = Coverage.coverageAchieved(100, 10, "ten");
            assertThat(coverage.compliant()).isTrue();
            assertThat(coverage.shortBy()).isZero();
        }

        @Test
        void isShortWhenOneJointIsMissing() {
            CoverageResult coverage = Coverage.coverageAchieved(100, 9, "ten");
            assertThat(coverage.compliant()).isFalse();
            assertThat(coverage.shortBy()).isEqualTo(1);
        }

        @Test
        void reportsTheAchievedFractionNextToTheRequiredOne() {
            CoverageResult coverage = Coverage.coverageAchieved(7, 1, "ten");
            assertThat(coverage.requiredFraction()).isCloseTo(0.1, digits(4));
            assertThat(coverage.achievedFraction()).isCloseTo(0.1429, digits(3));
            assertThat(coverage.compliant()).isTrue();
        }

        @Test
        void isContentWithShootingMoreThanCalledFor() {
            assertThat(Coverage.coverageAchieved(100, 20, "ten").compliant()).isTrue();
        }

        @Test
        void refusesMoreJointsShotThanExist() {
            assertThatThrownBy(() -> Coverage.coverageAchieved(10, 11, "ten"))
                    .hasMessageContaining("more joints were shot");
        }
    }

    @Nested
    class CoverageByWelder {

        private final List<WelderShot> joints = List.of(
                WelderShot.of("A17", true),
                WelderShot.of("A17", false),
                WelderShot.of("B04", false),
                WelderShot.of("B04", false));

        @Test
        void splitsTheCoverageByStencil() {
            List<WelderCoverage> rows = Coverage.coverageByWelder(joints, "full");
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).welder()).isEqualTo("A17");
        }

        @Test
        void showsAWelderShortEvenWhenAnotherIsCovered() {
            List<WelderCoverage> rows = Coverage.coverageByWelder(joints, "five");
            WelderCoverage b04 = rows.stream()
                    .filter(row -> row.welder().equals("B04"))
                    .findFirst()
                    .orElseThrow();
            assertThat(b04.coverage().compliant()).isFalse();
        }

        @Test
        void gathersJointsWithNoWelderUnderOneHeading() {
            List<WelderCoverage> rows = Coverage.coverageByWelder(List.of(WelderShot.of(null, true)), "five");
            assertThat(rows.get(0).welder()).isEqualTo("unknown");
        }
    }

    @Nested
    class WeldersNotCovered {

        @Test
        void namesWeldersWhoHaveNeverBeenShot() {
            List<WelderShot> joints =
                    List.of(WelderShot.of("A17", true), WelderShot.of("B04", false), WelderShot.of("C09", false));
            assertThat(Coverage.weldersNotCovered(joints)).containsExactly("B04", "C09");
        }

        @Test
        void isEmptyWhenEverybodyHasBeenCoveredOnce() {
            List<WelderShot> joints = List.of(WelderShot.of("A17", true), WelderShot.of("B04", true));
            assertThat(Coverage.weldersNotCovered(joints)).isEmpty();
        }

        @Test
        void countsAWelderAsCoveredOnAnyOneShotJoint() {
            List<WelderShot> joints = List.of(WelderShot.of("A17", false), WelderShot.of("A17", true));
            assertThat(Coverage.weldersNotCovered(joints)).isEmpty();
        }

        @Test
        void catchesTheCaseTheOverallPercentageHides() {
            // Ninety joints by one welder all shot, ten by another none shot. The job
            // is at ninety percent coverage and one welder has never been checked.
            List<WelderShot> joints = new ArrayList<>(Collections.nCopies(90, WelderShot.of("A17", true)));
            joints.addAll(Collections.nCopies(10, WelderShot.of("B04", false)));
            CoverageResult overall = Coverage.coverageAchieved(100, 90, "ten");
            assertThat(overall.compliant()).isTrue();
            assertThat(Coverage.weldersNotCovered(joints)).containsExactly("B04");
        }
    }
}
