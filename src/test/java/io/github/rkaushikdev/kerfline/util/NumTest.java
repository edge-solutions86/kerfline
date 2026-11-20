package io.github.rkaushikdev.kerfline.util;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NumTest {

    @Nested
    class IsFiniteNumber {

        @Test
        void acceptsOrdinaryNumbersIncludingZeroAndNegatives() {
            assertThat(Num.isFiniteNumber(0)).isTrue();
            assertThat(Num.isFiniteNumber(-3.25)).isTrue();
        }

        @Test
        void rejectsTheNonNumbersThatTurnUpInParsedFieldData() {
            assertThat(Num.isFiniteNumber(Double.NaN)).isFalse();
            assertThat(Num.isFiniteNumber(Double.POSITIVE_INFINITY)).isFalse();
            assertThat(Num.isFiniteNumber((Object) "0.280")).isFalse();
            assertThat(Num.isFiniteNumber((Object) null)).isFalse();
        }
    }

    @Nested
    class Round {

        @Test
        void roundsHalfAwayFromZero() {
            assertThat(Num.round(0.5)).isEqualTo(1);
            assertThat(Num.round(-0.5)).isEqualTo(-1);
            assertThat(Num.round(1.5)).isEqualTo(2);
            assertThat(Num.round(2.5)).isEqualTo(3);
        }

        @Test
        void getsTheBinaryFloatCasesRight() {
            assertThat(Num.round(1.005, 2)).isEqualTo(1.01);
            assertThat(Num.round(1.045, 2)).isEqualTo(1.05);
            assertThat(Num.round(8.575, 2)).isEqualTo(8.58);
        }

        @Test
        void keepsWallThicknessesAtThreePlaces() {
            assertThat(Num.round(0.2795, 3)).isEqualTo(0.28);
            assertThat(Num.round(0.4375, 3)).isEqualTo(0.438);
        }

        @Test
        void neverHandsBackNegativeZero() {
            assertThat(Double.compare(Num.round(-0.0004, 2), 0.0)).isZero();
            assertThat(Double.compare(Num.round(0), 0.0)).isZero();
        }

        @Test
        void refusesASillyDecimalPlaceCount() {
            assertThatThrownBy(() -> Num.round(1, -1)).hasMessageContaining("decimal places");
            assertThatThrownBy(() -> Num.round(1, 13)).hasMessageContaining("decimal places");
        }

        @Test
        void refusesAValueThatIsNotANumber() {
            assertThatThrownBy(() -> Num.round(Double.NaN, 1)).hasMessageContaining("finite number");
            assertThatThrownBy(() -> Num.round(Double.NaN)).hasMessageContaining("finite number");
        }
    }

    @Nested
    class RoundToCeilToAndFloorTo {

        @Test
        void snapsToACaliperStep() {
            assertThat(Num.roundTo(0.2812, 0.005)).isEqualTo(0.28);
            assertThat(Num.roundTo(0.2837, 0.005)).isEqualTo(0.285);
        }

        @Test
        void roundsAStepAwayFromZeroOnBothSides() {
            assertThat(Num.roundTo(0.0025, 0.005)).isEqualTo(0.005);
            assertThat(Num.roundTo(-0.0025, 0.005)).isEqualTo(-0.005);
        }

        @Test
        void ceilsAndFloorsWithoutTrippingOnAnExactMultiple() {
            assertThat(Num.ceilTo(0.28, 0.005)).isEqualTo(0.28);
            assertThat(Num.floorTo(0.28, 0.005)).isEqualTo(0.28);
            assertThat(Num.ceilTo(0.2801, 0.005)).isEqualTo(0.285);
            assertThat(Num.floorTo(0.2799, 0.005)).isEqualTo(0.275);
        }

        @Test
        void refusesAStepThatIsZeroOrNegative() {
            assertThatThrownBy(() -> Num.roundTo(1, 0)).hasMessageContaining("step must be positive");
            assertThatThrownBy(() -> Num.ceilTo(1, -0.1)).hasMessageContaining("step must be positive");
            assertThatThrownBy(() -> Num.floorTo(1, 0)).hasMessageContaining("step must be positive");
        }
    }

    @Nested
    class Clamp {

        @Test
        void holdsAValueInsideTheBand() {
            assertThat(Num.clamp(5, 1, 10)).isEqualTo(5);
            assertThat(Num.clamp(0, 1, 10)).isEqualTo(1);
            assertThat(Num.clamp(99, 1, 10)).isEqualTo(10);
        }

        @Test
        void refusesAnInvertedBandRatherThanQuietlySwappingIt() {
            assertThatThrownBy(() -> Num.clamp(5, 10, 1)).hasMessageContaining("lower bound is above");
        }
    }

    @Nested
    class ApproxEqual {

        @Test
        void comparesWithinATolerance() {
            assertThat(Num.approxEqual(0.1 + 0.2, 0.3)).isTrue();
            assertThat(Num.approxEqual(1.0, 1.004, 0.005)).isTrue();
            assertThat(Num.approxEqual(1.0, 1.006, 0.005)).isFalse();
        }

        @Test
        void treatsAZeroToleranceAsExact() {
            assertThat(Num.approxEqual(1, 1, 0)).isTrue();
            assertThat(Num.approxEqual(1, 1.0000001, 0)).isFalse();
        }

        @Test
        void refusesANegativeTolerance() {
            assertThatThrownBy(() -> Num.approxEqual(1, 1, -1)).hasMessageContaining("must not be negative");
        }
    }

    @Nested
    class Interpolation {

        @Test
        void lerpsBetweenEndpoints() {
            assertThat(Num.lerp(10, 20, 0)).isEqualTo(10);
            assertThat(Num.lerp(10, 20, 1)).isEqualTo(20);
            assertThat(Num.lerp(10, 20, 0.25)).isEqualTo(12.5);
        }

        @Test
        void readsAStraightChartLine() {
            assertThat(Num.interpolate(0.5, 0, 0, 1, 100)).isEqualTo(50);
            assertThat(Num.interpolate(0.375, 0.25, 40, 0.5, 80)).isCloseTo(60, digits(9));
        }

        @Test
        void extrapolatesRatherThanClampingWhichIsTheCallerDecision() {
            assertThat(Num.interpolate(2, 0, 0, 1, 100)).isEqualTo(200);
        }

        @Test
        void refusesTwoPointsAtTheSameX() {
            assertThatThrownBy(() -> Num.interpolate(1, 2, 3, 2, 9)).hasMessageContaining("same x");
        }

        @Test
        void interpolatesExposureOnALogScale() {
            assertThat(Num.logInterpolate(0.5, 0, 10, 1, 1000)).isCloseTo(100, digits(9));
            assertThat(Num.logInterpolate(0.25, 0, 10, 1, 10000)).isCloseTo(56.234132, digits(5));
        }

        @Test
        void refusesANonPositiveEndpointOnTheLogScale() {
            assertThatThrownBy(() -> Num.logInterpolate(0.5, 0, 0, 1, 100)).hasMessageContaining("positive endpoints");
            assertThatThrownBy(() -> Num.logInterpolate(0.5, 0, 10, 1, -5)).hasMessageContaining("positive endpoints");
        }
    }

    @Nested
    class TheListReducers {

        @Test
        void sumsAveragesAndTakesAMedian() {
            assertThat(Num.sum(List.of(1.0, 2.0, 3.0, 4.0))).isEqualTo(10);
            assertThat(Num.mean(List.of(1.0, 2.0, 3.0, 4.0))).isEqualTo(2.5);
            assertThat(Num.median(List.of(3.0, 1.0, 2.0))).isEqualTo(2);
            assertThat(Num.median(List.of(4.0, 1.0, 3.0, 2.0))).isEqualTo(2.5);
        }

        @Test
        void leavesTheCallerArrayAloneWhenTakingAMedian() {
            List<Double> values = new ArrayList<>(List.of(3.0, 1.0, 2.0));
            Num.median(values);
            assertThat(values).containsExactly(3.0, 1.0, 2.0);
        }

        @Test
        void sumsAnEmptyListToZeroButRefusesAnEmptyAverage() {
            assertThat(Num.sum(List.of())).isEqualTo(0);
            assertThatThrownBy(() -> Num.mean(List.of())).hasMessageContaining("empty list");
            assertThatThrownBy(() -> Num.median(List.of())).hasMessageContaining("empty list");
            assertThatThrownBy(() -> Num.maxOf(List.of())).hasMessageContaining("empty list");
            assertThatThrownBy(() -> Num.minOf(List.of())).hasMessageContaining("empty list");
        }

        @Test
        void takesExtremes() {
            assertThat(Num.maxOf(List.of(0.28, 0.432, 0.154))).isEqualTo(0.432);
            assertThat(Num.minOf(List.of(0.28, 0.432, 0.154))).isEqualTo(0.154);
        }

        @Test
        void namesTheOffendingIndexWhenAListHoldsANonNumber() {
            assertThatThrownBy(() -> Num.sum(Arrays.asList(1.0, null, 3.0))).hasMessageContaining("values[1]");
            assertThatThrownBy(() -> Num.sum(null)).hasMessageContaining("must be an array");
        }
    }

    @Nested
    class Fixed {

        @Test
        void keepsTrailingZerosForTheReport() {
            assertThat(Num.fixed(0.28, 3)).isEqualTo("0.280");
            assertThat(Num.fixed(2, 2)).isEqualTo("2.00");
        }

        @Test
        void roundsBeforeFormatting() {
            assertThat(Num.fixed(1.005, 2)).isEqualTo("1.01");
        }

        @Test
        void refusesASillyPlaceCount() {
            assertThatThrownBy(() -> Num.fixed(1, -1)).hasMessageContaining("decimal places");
        }
    }

    @Nested
    class PercentChange {

        @Test
        void signsTheChangeAgainstTheReference() {
            assertThat(Num.percentChange(2.0, 2.6)).isCloseTo(30, digits(9));
            assertThat(Num.percentChange(2.0, 1.7)).isCloseTo(-15, digits(9));
        }

        @Test
        void refusesAZeroReference() {
            assertThatThrownBy(() -> Num.percentChange(0, 1)).hasMessageContaining("from zero");
        }
    }

    @Nested
    class ReportText {

        @Test
        void writesTheShortestDigitsWithNoTrailingPoint() {
            assertThat(Num.text(6)).isEqualTo("6");
            assertThat(Num.text(0.28)).isEqualTo("0.28");
            assertThat(Num.text(-3.25)).isEqualTo("-3.25");
            assertThat(Num.text(0.1 + 0.2)).isEqualTo("0.30000000000000004");
            assertThat(Num.text(123456789012345680000.0)).isEqualTo("123456789012345680000");
        }

        @Test
        void switchesToExponentsOutsideThePlainRange() {
            assertThat(Num.text(0.000001)).isEqualTo("0.000001");
            assertThat(Num.text(1e-7)).isEqualTo("1e-7");
            assertThat(Num.text(1.5e-7)).isEqualTo("1.5e-7");
            assertThat(Num.text(1e21)).isEqualTo("1e+21");
            assertThat(Num.text(-2.5e22)).isEqualTo("-2.5e+22");
        }

        @Test
        void writesTheSpecialValuesByName() {
            assertThat(Num.text(-0.0)).isEqualTo("0");
            assertThat(Num.text(Double.NaN)).isEqualTo("NaN");
            assertThat(Num.text(Double.POSITIVE_INFINITY)).isEqualTo("Infinity");
            assertThat(Num.text(Double.NEGATIVE_INFINITY)).isEqualTo("-Infinity");
        }

        @Test
        void fixesTheExactBinaryValueRoundingHalfUp() {
            assertThat(Num.toFixed(1.005, 2)).isEqualTo("1.00");
            assertThat(Num.toFixed(0.125, 2)).isEqualTo("0.13");
            assertThat(Num.toFixed(-0.0001, 2)).isEqualTo("-0.00");
            assertThat(Num.toFixed(-0.0, 1)).isEqualTo("0.0");
            assertThat(Num.toFixed(2, 0)).isEqualTo("2");
            assertThat(Num.toFixed(1e21, 2)).isEqualTo("1e+21");
            assertThat(Num.toFixed(Double.NaN, 2)).isEqualTo("NaN");
        }

        @Test
        void readsFormFieldsTheWayTheyAreWritten() {
            assertThat(Num.parse(" 12.5 ")).isEqualTo(12.5);
            assertThat(Num.parse("1e3")).isEqualTo(1000);
            assertThat(Num.parse(".5")).isEqualTo(0.5);
            assertThat(Num.parse("5.")).isEqualTo(5);
            assertThat(Num.parse("-Infinity")).isEqualTo(Double.NEGATIVE_INFINITY);
            assertThat(Num.parse("0x1A")).isEqualTo(26);
            assertThat(Num.parse("0b101")).isEqualTo(5);
            assertThat(Num.parse("0o17")).isEqualTo(15);
            assertThat(Num.parse("")).isEqualTo(0);
        }

        @Test
        void readsAnythingElseAsNotANumber() {
            assertThat(Num.parse("thick")).isNaN();
            assertThat(Num.parse("1d")).isNaN();
            assertThat(Num.parse("1,5")).isNaN();
            assertThat(Num.parse("-0x1A")).isNaN();
            assertThat(Num.parse("NaN")).isNaN();
            assertThat(Num.parse(null)).isNaN();
        }

        @Test
        void roundsHalvesTowardsPositiveInfinity() {
            assertThat(Num.roundHalfUp(2.5)).isEqualTo(3);
            assertThat(Num.roundHalfUp(-2.5)).isEqualTo(-2);
            assertThat(Num.roundHalfUp(-2.6)).isEqualTo(-3);
            assertThat(Num.roundHalfUp(0.49999999999999994)).isEqualTo(0);
            assertThat(Double.compare(Num.roundHalfUp(-0.4), -0.0)).isZero();
        }

        @Test
        void takesBaseTwoLogarithmsExactlyAtPowersOfTwo() {
            assertThat(Num.log2(8)).isEqualTo(3);
            assertThat(Num.log2(0.25)).isEqualTo(-2);
            assertThat(Num.log2(10)).isCloseTo(3.321928094887362, digits(14));
            assertThat(Num.log2(-1)).isNaN();
        }

        @Test
        void roundsThroughTheTextSoTinyValuesFollowTheExponentForm() {
            assertThat(Num.round(1e-7, 2)).isNaN();
            assertThat(Num.round(123.456, 1)).isEqualTo(123.5);
        }
    }
}
