package io.github.rkaushikdev.kerfline.util;

import static io.github.rkaushikdev.kerfline.util.Band.between;
import static io.github.rkaushikdev.kerfline.util.Band.upTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BandTest {

    private static final double INF = Double.POSITIVE_INFINITY;

    private static final BandTable<String> THICKNESS = Band.bandTable(
            List.of(upTo(0.25, "A"), upTo(0.75, "B"), upTo(1.5, "C"), upTo(INF, "D")), "thickness bands");

    @Nested
    class BandTableBuilding {

        @Test
        void chainsEachRowOntoTheOneBelowIt() {
            assertThat(THICKNESS.rows().stream().map(r -> List.of(r.over(), r.upTo())))
                    .containsExactly(List.of(0.0, 0.25), List.of(0.25, 0.75), List.of(0.75, 1.5), List.of(1.5, INF));
        }

        @Test
        void recordsTheFloorAndTheCeiling() {
            assertThat(THICKNESS.floor()).isEqualTo(0);
            assertThat(THICKNESS.ceiling()).isEqualTo(INF);
        }

        @Test
        void takesAnExplicitLowerBoundOnTheFirstRow() {
            BandTable<String> table = Band.bandTable(List.of(between(2, 4, "x")));
            assertThat(table.floor()).isEqualTo(2);
        }

        @Test
        void freezesTheRowsSoACallerCannotEditTheCodeTable() {
            assertThatThrownBy(() -> THICKNESS.rows().set(0, new BandRow<>(0, 0.25, "Z")))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(THICKNESS.rows().get(0).value()).isEqualTo("A");
        }

        @Test
        void refusesAnEmptyTable() {
            assertThatThrownBy(() -> Band.bandTable(List.<BandSpec<String>>of()))
                    .hasMessageContaining("at least one row");
            assertThatThrownBy(() -> Band.<String>bandTable(null)).hasMessageContaining("at least one row");
        }

        @Test
        void refusesARowThatIsNotAnObject() {
            assertThatThrownBy(() -> Band.bandTable(Arrays.<BandSpec<String>>asList((BandSpec<String>) null)))
                    .hasMessageContaining("is not an object");
        }

        @Test
        void refusesARowThatDoesNotGoUpwards() {
            assertThatThrownBy(() -> Band.bandTable(List.of(between(1, 1, "x"))))
                    .hasMessageContaining("does not go upwards");
            assertThatThrownBy(() -> Band.bandTable(List.of(between(2, 1, "x"))))
                    .hasMessageContaining("does not go upwards");
        }

        @Test
        void refusesAGapBetweenRows() {
            List<BandSpec<String>> rows = List.of(upTo(0.25, "A"), between(0.5, 1, "B"));
            assertThatThrownBy(() -> Band.bandTable(rows)).hasMessageContaining("gap or overlaps");
        }

        @Test
        void refusesAnOverlapBetweenRows() {
            List<BandSpec<String>> rows = List.of(upTo(0.5, "A"), between(0.25, 1, "B"));
            assertThatThrownBy(() -> Band.bandTable(rows)).hasMessageContaining("gap or overlaps");
        }

        @Test
        void refusesAnOpenEndedRowAnywhereButLast() {
            List<BandSpec<String>> rows = List.of(upTo(INF, "A"), between(INF, 2, "B"));
            assertThatThrownBy(() -> Band.bandTable(rows)).hasMessageContaining("open ended but is not the last");
        }

        @Test
        void refusesABoundThatIsNotANumber() {
            assertThatThrownBy(() -> Band.bandTable(List.of(upTo(Double.NaN, "x"))))
                    .hasMessageContaining("bad upper bound");
            assertThatThrownBy(() -> Band.bandTable(List.of(between(Double.NaN, 1, "x"))))
                    .hasMessageContaining("bad lower bound");
        }
    }

    @Nested
    class BandRowAndBandValue {

        @Test
        void readsTheCodeConventionOverTheLowerThroughTheUpper() {
            assertThat(Band.bandValue(THICKNESS, 0.25)).isEqualTo("A");
            assertThat(Band.bandValue(THICKNESS, 0.2501)).isEqualTo("B");
            assertThat(Band.bandValue(THICKNESS, 0.75)).isEqualTo("B");
            assertThat(Band.bandValue(THICKNESS, 0.7501)).isEqualTo("C");
            assertThat(Band.bandValue(THICKNESS, 1.5)).isEqualTo("C");
            assertThat(Band.bandValue(THICKNESS, 1.5001)).isEqualTo("D");
        }

        @Test
        void includesTheVeryBottomOfTheTable() {
            assertThat(Band.bandValue(THICKNESS, 0)).isEqualTo("A");
        }

        @Test
        void runsOffToTheOpenEndedRow() {
            assertThat(Band.bandValue(THICKNESS, 12)).isEqualTo("D");
        }

        @Test
        void handsBackTheWholeRowWhenAsked() {
            assertThat(Band.bandRow(THICKNESS, 0.5)).isEqualTo(new BandRow<>(0.25, 0.75, "B"));
        }

        @Test
        void refusesAValueBelowTheFloor() {
            BandTable<String> table = Band.bandTable(List.of(between(1, 2, "x")), "shim table");
            assertThatThrownBy(() -> Band.bandValue(table, 0.5)).hasMessageContaining("below the bottom of shim table");
        }

        @Test
        void refusesAValueAboveAClosedCeiling() {
            BandTable<String> table = Band.bandTable(List.of(upTo(2, "x")), "shim table");
            assertThatThrownBy(() -> Band.bandValue(table, 2.5)).hasMessageContaining("above the top of shim table");
        }

        @Test
        void refusesALookupThatIsNotANumber() {
            assertThatThrownBy(() -> Band.bandValue(THICKNESS, Double.NaN)).hasMessageContaining("finite lookup value");
        }

        @Test
        void refusesSomethingThatIsNotATable() {
            assertThatThrownBy(() -> Band.bandValue(null, 1)).hasMessageContaining("not a band table");
            assertThatThrownBy(() -> Band.bandRow(null, 1)).hasMessageContaining("not a band table");
        }
    }

    @Nested
    class BandValueOr {

        private final BandTable<String> table = Band.bandTable(List.of(between(1, 2, "x")));

        @Test
        void readsAValueInsideTheTable() {
            assertThat(Band.bandValueOr(table, 1.5, "fallback")).isEqualTo("x");
        }

        @Test
        void fallsBackOffEitherEnd() {
            assertThat(Band.bandValueOr(table, 0, "fallback")).isEqualTo("fallback");
            assertThat(Band.bandValueOr(table, 9, "fallback")).isEqualTo("fallback");
        }

        @Test
        void stillThrowsOnABadInputRatherThanSwallowingIt() {
            assertThatThrownBy(() -> Band.bandValueOr(table, Double.NaN, "fallback"))
                    .hasMessageContaining("finite lookup value");
        }
    }

    @Nested
    class Covers {

        @Test
        void answersWithoutThrowing() {
            BandTable<String> table = Band.bandTable(List.of(between(1, 2, "x")));
            assertThat(Band.covers(table, 1.5)).isTrue();
            assertThat(Band.covers(table, 0)).isFalse();
            assertThat(Band.covers(table, Double.NaN)).isFalse();
        }
    }

    @Nested
    class DescribeBands {

        @Test
        void rendersTheBandsTheWayTheCodePrintsThem() {
            assertThat(Band.describeBands(THICKNESS))
                    .containsExactly(
                            "over 0 through 0.25: A",
                            "over 0.25 through 0.75: B",
                            "over 0.75 through 1.5: C",
                            "over 1.5 and above: D");
        }

        @Test
        void takesAFormatterForTheBounds() {
            List<String> lines = Band.describeBands(THICKNESS, v -> Num.text(v) + " in");
            assertThat(lines.get(0)).isEqualTo("over 0 in through 0.25 in: A");
        }
    }
}
