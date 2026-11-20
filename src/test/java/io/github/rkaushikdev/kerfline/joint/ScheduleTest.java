package io.github.rkaushikdev.kerfline.joint;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ScheduleTest {

    @Nested
    class TheSizeTable {

        @Test
        void carriesTheSizesTheShopShoots() {
            List<String> sizes = Schedule.nominalSizes();
            assertThat(sizes).contains("2", "12", "1-1/2");
            assertThat(sizes.size()).isGreaterThan(12);
        }

        @Test
        void knowsThatNominalSizeIsNotTheOutsideDiameter() {
            assertThat(Schedule.outsideDiameterIn("2")).isCloseTo(2.375, digits(4));
            assertThat(Schedule.outsideDiameterIn("6")).isCloseTo(6.625, digits(4));
            assertThat(Schedule.outsideDiameterIn("12")).isCloseTo(12.75, digits(4));
        }

        @Test
        void knowsWhereNominalSizeStartsTellingTheTruth() {
            assertThat(Schedule.outsideDiameterIn("14")).isEqualTo(14);
            assertThat(Schedule.outsideDiameterIn("24")).isEqualTo(24);
            assertThat(Schedule.outsideDiameterIn("12")).isNotEqualTo(12);
        }

        @Test
        void readsASizeHoweverItWasWritten() {
            assertThat(Schedule.normaliseSize("2 in")).isEqualTo("2");
            assertThat(Schedule.normaliseSize("2\"")).isEqualTo("2");
            assertThat(Schedule.normaliseSize(2)).isEqualTo("2");
            assertThat(Schedule.normaliseSize("1-1/2")).isEqualTo("1-1/2");
        }

        @Test
        void refusesASizeItDoesNotStock() {
            assertThatThrownBy(() -> Schedule.outsideDiameterIn("5")).hasMessageContaining("no pipe size 5");
            assertThatThrownBy(() -> Schedule.outsideDiameterIn("DN50")).hasMessageContaining("no pipe size");
        }
    }

    @Nested
    class WallThicknessIn {

        @Test
        void readsThePublishedWalls() {
            assertThat(Schedule.wallThicknessIn("6", "40")).isCloseTo(0.28, digits(4));
            assertThat(Schedule.wallThicknessIn("6", "80")).isCloseTo(0.432, digits(4));
            assertThat(Schedule.wallThicknessIn("2", "40")).isCloseTo(0.154, digits(4));
            assertThat(Schedule.wallThicknessIn("12", "40")).isCloseTo(0.406, digits(4));
        }

        @Test
        void readsAScheduleHoweverItWasWritten() {
            assertThat(Schedule.normaliseSchedule("SCH 40")).isEqualTo("40");
            assertThat(Schedule.normaliseSchedule("sch40")).isEqualTo("40");
            assertThat(Schedule.normaliseSchedule("xs")).isEqualTo("XS");
            assertThat(Schedule.wallThicknessIn("6", "SCH 40")).isCloseTo(0.28, digits(4));
        }

        @Test
        void refusesAScheduleThatSizeIsNotMadeIn() {
            assertThatThrownBy(() -> Schedule.wallThicknessIn("2", "10"))
                    .hasMessageContaining("not made in schedule 10");
        }

        @Test
        void answersHasScheduleWithoutThrowing() {
            assertThat(Schedule.hasSchedule("6", "40")).isTrue();
            assertThat(Schedule.hasSchedule("2", "10")).isFalse();
            assertThat(Schedule.hasSchedule("5", "40")).isFalse();
        }

        @Test
        void listsTheSchedulesASizeIsMadeIn() {
            assertThat(Schedule.schedulesFor("2")).contains("40", "XXS");
        }
    }

    @Nested
    class StdXsAndXxs {

        @Test
        void hasStdEqualToSchedule40OnTheSmallSizes() {
            assertThat(Schedule.stdMatchesSchedule40("2")).isTrue();
            assertThat(Schedule.stdMatchesSchedule40("6")).isTrue();
            assertThat(Schedule.stdMatchesSchedule40("10")).isTrue();
        }

        @Test
        void hasStdPartCompanyWithSchedule40FromTwelveInchUp() {
            assertThat(Schedule.stdMatchesSchedule40("12")).isFalse();
            assertThat(Schedule.stdMatchesSchedule40("14")).isFalse();
            assertThat(Schedule.stdMatchesSchedule40("24")).isFalse();
        }

        @Test
        void holdsStdAtThreeEighthsOnTheBigSizesWhatever40Does() {
            assertThat(Schedule.wallThicknessIn("12", "STD")).isCloseTo(0.375, digits(4));
            assertThat(Schedule.wallThicknessIn("24", "STD")).isCloseTo(0.375, digits(4));
            assertThat(Schedule.wallThicknessIn("24", "40")).isCloseTo(0.688, digits(4));
        }

        @Test
        void holdsXsAtHalfAnInchOnTheBigSizes() {
            assertThat(Schedule.wallThicknessIn("12", "XS")).isCloseTo(0.5, digits(4));
            assertThat(Schedule.wallThicknessIn("24", "XS")).isCloseTo(0.5, digits(4));
            assertThat(Schedule.wallThicknessIn("6", "XS")).isCloseTo(0.432, digits(4));
        }
    }

    @Nested
    class SchedulesForWall {

        @Test
        void findsTheSchedulesAMeasuredWallMatches() {
            assertThat(Schedule.schedulesForWall("6", 0.28)).contains("40", "STD");
        }

        @Test
        void findsNothingWhenTheWallMatchesNoSchedule() {
            assertThat(Schedule.schedulesForWall("6", 0.35)).isEmpty();
        }

        @Test
        void takesATolerance() {
            assertThat(Schedule.schedulesForWall("6", 0.284, 0.005)).contains("40");
            assertThat(Schedule.schedulesForWall("6", 0.284, 0.001)).isEmpty();
        }

        @Test
        void refusesAWallThatIsNotPositive() {
            assertThatThrownBy(() -> Schedule.schedulesForWall("6", 0)).hasMessageContaining("positive number");
        }
    }

    @Nested
    class PipeSpecs {

        @Test
        void givesTheWholeGeometryForAJoint() {
            assertThat(Schedule.pipeSpec("6", "40")).isEqualTo(new PipeSpec("6", "40", 6.625, 0.28, 6.065));
        }

        @Test
        void takesTheScheduleAsWrittenOnTheDrawing() {
            assertThat(Schedule.pipeSpec("12", "STD").wallIn()).isCloseTo(0.375, digits(4));
            assertThat(Schedule.pipeSpec("12", "sch 40").wallIn()).isCloseTo(0.406, digits(4));
        }

        @Test
        void keepsTheBoreConsistentWithTheWall() {
            for (String size : List.of("2", "6", "12", "24")) {
                PipeSpec spec = Schedule.pipeSpec(size, "STD");
                assertThat(spec.idIn()).isCloseTo(spec.odIn() - 2 * spec.wallIn(), digits(6));
            }
        }
    }
}
