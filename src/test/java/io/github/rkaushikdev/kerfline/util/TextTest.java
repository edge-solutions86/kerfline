package io.github.rkaushikdev.kerfline.util;

import static io.github.rkaushikdev.kerfline.util.Text.pair;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TextTest {

    @Nested
    class Padding {

        @Test
        void padsOnEachSide() {
            assertThat(Text.padRight("J-1", 6)).isEqualTo("J-1   ");
            assertThat(Text.padLeft("J-1", 6)).isEqualTo("   J-1");
            assertThat(Text.padCenter("J-1", 7)).isEqualTo("  J-1  ");
        }

        @Test
        void putsTheOddColumnOnTheRightWhenCentring() {
            assertThat(Text.padCenter("ab", 5)).isEqualTo(" ab  ");
        }

        @Test
        void leavesAStringThatIsAlreadyWideEnoughAlone() {
            assertThat(Text.padRight("J-1234", 3)).isEqualTo("J-1234");
        }

        @Test
        void takesAFillCharacter() {
            assertThat(Text.padLeft("7", 3, "0")).isEqualTo("007");
        }

        @Test
        void rendersNullAndUndefinedAsEmpty() {
            assertThat(Text.padRight(null, 3)).isEqualTo("   ");
            assertThat(Text.padRight(Optional.empty(), 3)).isEqualTo("   ");
        }

        @Test
        void refusesAWidthThatIsNotAWholeNumberOfColumns() {
            assertThatThrownBy(() -> Text.padRight("x", -1)).hasMessageContaining("whole number of columns");
            assertThatThrownBy(() -> Text.padLeft("x", -2)).hasMessageContaining("whole number of columns");
        }

        @Test
        void refusesAFillThatIsNotOneCharacter() {
            assertThatThrownBy(() -> Text.padRight("x", 5, "")).hasMessageContaining("single character");
            assertThatThrownBy(() -> Text.padRight("x", 5, "..")).hasMessageContaining("single character");
        }
    }

    @Nested
    class Truncate {

        @Test
        void leavesAShortStringAlone() {
            assertThat(Text.truncate("J-1", 6)).isEqualTo("J-1");
            assertThat(Text.truncate("J-1234", 6)).isEqualTo("J-1234");
        }

        @Test
        void marksTheCutSoAClippedIdIsObvious() {
            assertThat(Text.truncate("J-123456", 6)).isEqualTo("J-123~");
        }

        @Test
        void degeneratesToTheMarkerAtOneColumn() {
            assertThat(Text.truncate("J-123456", 1)).isEqualTo("~");
        }

        @Test
        void refusesAWidthBelowOne() {
            assertThatThrownBy(() -> Text.truncate("x", 0)).hasMessageContaining("at least one column");
        }
    }

    @Nested
    class Rule {

        @Test
        void drawsADivider() {
            assertThat(Text.rule(4)).isEqualTo("----");
            assertThat(Text.rule(3, "=")).isEqualTo("===");
            assertThat(Text.rule(0)).isEmpty();
        }

        @Test
        void refusesABadWidthOrCharacter() {
            assertThatThrownBy(() -> Text.rule(-1)).hasMessageContaining("whole number of columns");
            assertThatThrownBy(() -> Text.rule(3, "--")).hasMessageContaining("single character");
        }
    }

    @Nested
    class RenderTable {

        private final List<Map<String, Object>> rows = List.of(
                Map.of("joint", "J-101", "wall", 0.28, "verdict", "accept"),
                Map.of("joint", "J-102", "wall", 0.432, "verdict", "repair"));
        private final List<Column> columns = List.of(
                Column.of("joint").label("joint"),
                Column.of("wall").label("wall").right().formatValue(v -> Num.toFixed((Double) v, 3)),
                Column.of("verdict").label("verdict"));

        @Test
        void linesTheColumnsUpUnderTheirLabels() {
            assertThat(Text.renderTable(rows, columns))
                    .isEqualTo(String.join(
                            "\n",
                            "joint   wall  verdict",
                            "-----  -----  -------",
                            "J-101  0.280  accept",
                            "J-102  0.432  repair"));
        }

        @Test
        void dropsTheHeaderWhenAsked() {
            String out = Text.renderTable(rows, columns, TableOptions.DEFAULTS.withoutHeader());
            assertThat(out.split("\n")).hasSize(2);
            assertThat(out).startsWith("J-101");
        }

        @Test
        void takesAGapWidth() {
            String out = Text.renderTable(rows, columns, TableOptions.DEFAULTS.withGap(1));
            assertThat(out.split("\n")[0]).isEqualTo("joint  wall verdict");
        }

        @Test
        void growsAColumnToItsWidestCellAndHonoursAFloorWidth() {
            String wide =
                    Text.renderTable(rows, List.of(Column.of("joint").label("j").width(8)));
            assertThat(wide.split("\n")[1]).isEqualTo("--------");
        }

        @Test
        void cutsACellAtAnExplicitMax() {
            String out = Text.renderTable(
                    List.of(Map.of("note", "a very long remark indeed")),
                    List.of(Column.of("note").max(8)));
            assertThat(out.split("\n")[2]).isEqualTo("a very ~");
        }

        @Test
        void rendersAMissingValueAsEmptyRatherThanAsUndefined() {
            String out =
                    Text.renderTable(List.of(Map.of("joint", "J-1")), List.of(Column.of("joint"), Column.of("wall")));
            assertThat(out.split("\n")[2]).isEqualTo("J-1");
        }

        @Test
        void trimsTheTrailingRunSoLinesDoNotCarryInvisiblePadding() {
            String out = Text.renderTable(rows, columns);
            for (String line : out.split("\n")) {
                assertThat(line).isEqualTo(Text.trimEnd(line));
            }
        }

        @Test
        void rendersAHeaderAndDividerEvenWithNoRows() {
            assertThat(Text.renderTable(List.of(), columns).split("\n")).hasSize(2);
        }

        @Test
        void refusesRowsOrColumnsOfTheWrongShape() {
            assertThatThrownBy(() -> Text.renderTable(null, columns)).hasMessageContaining("rows must be an array");
            assertThatThrownBy(() -> Text.renderTable(rows, List.of())).hasMessageContaining("at least one column");
            assertThatThrownBy(
                            () -> Text.renderTable(rows, List.of(Column.of(null).label("x"))))
                    .hasMessageContaining("needs a key");
        }
    }

    @Nested
    class Wrap {

        @Test
        void breaksOnSpacesAtTheWidth() {
            assertThat(Text.wrap("the quick brown fox jumps", 10)).isEqualTo("the quick\nbrown fox\njumps");
        }

        @Test
        void keepsABreakTheAuthorPutIn() {
            assertThat(Text.wrap("one\ntwo three", 20)).isEqualTo("one\ntwo three");
        }

        @Test
        void doesNotBreakAWordThatIsLongerThanTheWidth() {
            assertThat(Text.wrap("supercalifragilistic yes", 10)).isEqualTo("supercalifragilistic\nyes");
        }

        @Test
        void collapsesRunsOfWhitespace() {
            assertThat(Text.wrap("a    b", 10)).isEqualTo("a b");
        }

        @Test
        void refusesAWidthBelowOne() {
            assertThatThrownBy(() -> Text.wrap("x", 0)).hasMessageContaining("at least one column");
        }
    }

    @Nested
    class Indent {

        @Test
        void shiftsEveryNonEmptyLine() {
            assertThat(Text.indent("a\n\nb", 2)).isEqualTo("  a\n\n  b");
        }

        @Test
        void takesAWidthAndRefusesABadOne() {
            assertThat(Text.indent("a", 4)).isEqualTo("    a");
            assertThatThrownBy(() -> Text.indent("a", -1)).hasMessageContaining("whole number of columns");
        }
    }

    @Nested
    class KeyValueBlock {

        @Test
        void linesTheValuesUpPastTheWidestLabel() {
            assertThat(Text.keyValueBlock(List.of(pair("source", "Ir-192"), pair("activity", "38 Ci"))))
                    .isEqualTo("source    Ir-192\nactivity  38 Ci");
        }

        @Test
        void acceptsAPlainObject() {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("a", 1);
            block.put("bb", 2);
            assertThat(Text.keyValueBlock(block)).isEqualTo("a   1\nbb  2");
        }

        @Test
        void rendersAnEmptyBlockAsAnEmptyString() {
            assertThat(Text.keyValueBlock(List.<Map.Entry<String, Object>>of())).isEmpty();
            assertThat(Text.keyValueBlock(new HashMap<String, Object>())).isEmpty();
            assertThat(Text.keyValueBlock((Map<String, Object>) null)).isEmpty();
        }

        @Test
        void leavesNoTrailingPaddingOnABlankValue() {
            assertThat(Text.keyValueBlock(List.of(pair("source", null)))).isEqualTo("source");
        }
    }

    @Nested
    class ValuesAsText {

        @Test
        void writesNumbersListsAndAbsenceTheWayASheetShowsThem() {
            assertThat(Text.str(6.0)).isEqualTo("6");
            assertThat(Text.str(List.of(1.0, 2.5))).isEqualTo("1,2.5");
            assertThat(Text.str(Arrays.asList("a", null, "b"))).isEqualTo("a,,b");
            assertThat(Text.str(null)).isEqualTo("null");
            assertThat(Text.str(Optional.empty())).isEqualTo("undefined");
            assertThat(Text.str(true)).isEqualTo("true");
        }

        @Test
        void trimsUnicodeSpacesAsWellAsAsciiOnes() {
            assertThat(Text.trim("\u00a0 J-1\t\n")).isEqualTo("J-1");
            assertThat(Text.trimEnd("J-1 \u3000")).isEqualTo("J-1");
            assertThat(Text.trim("")).isEmpty();
        }

        @Test
        void splitsOnALiteralKeepingEveryEmptyPiece() {
            assertThat(Text.split("a\n", "\n")).containsExactly("a", "");
            assertThat(Text.split("a.b", ".")).containsExactly("a", "b");
            assertThat(Text.split("", ":")).containsExactly("");
            assertThat(Text.split("ab", "")).containsExactly("a", "b");
        }

        @Test
        void findsTheWordsBetweenRunsOfSpace() {
            assertThat(Text.words("  one\ttwo \u00a0three ")).containsExactly("one", "two", "three");
            assertThat(Text.words("   ")).isEmpty();
        }
    }
}
