package io.github.rkaushikdev.kerfline.geom;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PipeTest {

    @Nested
    class TheBasicDimensions {

        @Test
        void halvesTheOutsideDiameter() {
            assertThat(Pipe.outsideRadius(12.75)).isCloseTo(6.375, digits(9));
        }

        @Test
        void takesTwoWallsOffForTheBore() {
            assertThat(Pipe.insideDiameter(12.75, 0.375)).isCloseTo(12, digits(9));
        }

        @Test
        void refusesAWallThatSwallowsTheBore() {
            assertThatThrownBy(() -> Pipe.insideDiameter(1, 0.5)).hasMessageContaining("two walls do not fit");
            assertThatThrownBy(() -> Pipe.insideDiameter(1, 0.6)).hasMessageContaining("two walls do not fit");
        }

        @Test
        void measuresTheCircumference() {
            assertThat(Pipe.circumference(12.75)).isCloseTo(40.055, digits(3));
        }

        @Test
        void refusesADiameterAtOrBelowZero() {
            assertThatThrownBy(() -> Pipe.circumference(0)).hasMessageContaining("greater than 0");
            assertThatThrownBy(() -> Pipe.outsideRadius(-1)).hasMessageContaining("greater than 0");
            assertThatThrownBy(() -> Pipe.circumference(Double.NaN)).hasMessageContaining("finite number");
        }
    }

    @Nested
    class ArcLength {

        @Test
        void isTheWholeCircumferenceAtAFullTurn() {
            assertThat(Pipe.arcLength(12.75, 360)).isCloseTo(Pipe.circumference(12.75), digits(9));
        }

        @Test
        void isAQuarterAtNinetyDegrees() {
            assertThat(Pipe.arcLength(12.75, 90)).isCloseTo(Pipe.circumference(12.75) / 4, digits(9));
        }

        @Test
        void inverts() {
            assertThat(Pipe.degreesForArc(12.75, Pipe.arcLength(12.75, 137))).isCloseTo(137, digits(9));
        }
    }

    @Nested
    class TheClockFace {

        @Test
        void putsTheTopOfThePipeAtTwelve() {
            assertThat(Pipe.clockFromDegrees(0)).isEqualTo("12:00");
            assertThat(Pipe.degreesFromClock("12:00")).isEqualTo(0);
        }

        @Test
        void walksTheQuartersClockwise() {
            assertThat(Pipe.clockFromDegrees(90)).isEqualTo("3:00");
            assertThat(Pipe.clockFromDegrees(180)).isEqualTo("6:00");
            assertThat(Pipe.clockFromDegrees(270)).isEqualTo("9:00");
        }

        @Test
        void readsAMinuteHand() {
            assertThat(Pipe.clockFromDegrees(212)).isEqualTo("7:04");
            assertThat(Pipe.degreesFromClock("7:04")).isCloseTo(212, digits(0));
        }

        @Test
        void wrapsAFullTurnBackToTwelve() {
            assertThat(Pipe.clockFromDegrees(360)).isEqualTo("12:00");
            assertThat(Pipe.clockFromDegrees(720)).isEqualTo("12:00");
        }

        @Test
        void wrapsANegativeAngleRoundTheOtherWay() {
            assertThat(Pipe.clockFromDegrees(-90)).isEqualTo("9:00");
        }

        @Test
        void roundTripsEveryWholeMinuteOnTheFace() {
            for (int minute = 0; minute < 720; minute++) {
                double degrees = (minute / 720.0) * 360;
                assertThat(Pipe.degreesFromClock(Pipe.clockFromDegrees(degrees)))
                        .isCloseTo(degrees, digits(9));
            }
        }

        @Test
        void refusesAClockPositionItCannotRead() {
            assertThatThrownBy(() -> Pipe.degreesFromClock("quarter past")).hasMessageContaining("cannot read");
            assertThatThrownBy(() -> Pipe.degreesFromClock("7:60")).hasMessageContaining("cannot read");
            assertThatThrownBy(() -> Pipe.degreesFromClock("13:00")).hasMessageContaining("runs from 1 to 12");
            assertThatThrownBy(() -> Pipe.degreesFromClock("0:30")).hasMessageContaining("runs from 1 to 12");
            assertThatThrownBy(() -> Pipe.degreesFromClock(null)).hasMessageContaining("must be a string");
        }

        @Test
        void measuresTheArcFromTheTopToAClockPosition() {
            assertThat(Pipe.arcFromTop(12.75, "3:00")).isCloseTo(Pipe.circumference(12.75) / 4, digits(9));
        }
    }

    @Nested
    class SpansAcrossTheClockFace {

        @Test
        void takesTheShortWayRound() {
            assertThat(Pipe.clockSpanDegrees("11:50", "12:10")).isCloseTo(10, digits(6));
            assertThat(Pipe.clockSpanDegrees("12:10", "11:50")).isCloseTo(10, digits(6));
        }

        @Test
        void isZeroBetweenAPositionAndItself() {
            assertThat(Pipe.clockSpanDegrees("7:04", "7:04")).isEqualTo(0);
        }

        @Test
        void capsAtHalfATurn() {
            assertThat(Pipe.clockSpanDegrees("12:00", "6:00")).isCloseTo(180, digits(9));
        }

        @Test
        void walksTheLongWayWhenGoingClockwise() {
            assertThat(Pipe.clockwiseSpanDegrees("12:10", "11:50")).isCloseTo(350, digits(6));
            assertThat(Pipe.clockwiseSpanDegrees("11:50", "12:10")).isCloseTo(10, digits(6));
        }
    }

    @Nested
    class IsSmallBore {

        @Test
        void callsThreeAndAHalfInchAndUnderSmallBore() {
            assertThat(Pipe.isSmallBore(2.375)).isTrue();
            assertThat(Pipe.isSmallBore(3.5)).isTrue();
            assertThat(Pipe.isSmallBore(4.5)).isFalse();
        }
    }

    @Nested
    class PipeSummaryBlock {

        @Test
        void fillsTheTechniqueSheetHeader() {
            assertThat(Pipe.pipeSummary(12.75, 0.375)).isEqualTo(new PipeSummary(12.75, 0.375, 12, 40.055, false));
        }

        @Test
        void flagsASmallBoreJoint() {
            assertThat(Pipe.pipeSummary(2.375, 0.218).smallBore()).isTrue();
        }
    }
}
