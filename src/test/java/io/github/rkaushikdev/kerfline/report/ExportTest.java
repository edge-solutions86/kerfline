package io.github.rkaushikdev.kerfline.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.util.Checksum;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExportTest {

    private static Map<String, Object> row(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    @Nested
    class CsvField {

        @Test
        void leavesAPlainValueAlone() {
            assertThat(Export.csvField("J-118")).isEqualTo("J-118");
            assertThat(Export.csvField(0.28)).isEqualTo("0.28");
        }

        @Test
        void rendersNothingForAMissingValue() {
            assertThat(Export.csvField(null)).isEmpty();
            assertThat(Export.csvField(Optional.empty())).isEmpty();
        }

        @Test
        void quotesAValueWithACommaInIt() {
            assertThat(Export.csvField("slag, elongated")).isEqualTo("\"slag, elongated\"");
        }

        @Test
        void quotesAndDoublesAnEmbeddedQuote() {
            assertThat(Export.csvField("the \"root\" pass")).isEqualTo("\"the \"\"root\"\" pass\"");
        }

        @Test
        void quotesAValueWithANewlineInIt() {
            assertThat(Export.csvField("two\nlines")).isEqualTo("\"two\nlines\"");
        }

        @Test
        void doesNotQuoteAValueThatMerelyHasASpace() {
            assertThat(Export.csvField("R. Ibarra")).isEqualTo("R. Ibarra");
        }
    }

    @Nested
    class CsvRow {

        @Test
        void joinsFieldsWithCommas() {
            assertThat(Export.csvRow(List.of("J-118", "A17", 0.28))).isEqualTo("J-118,A17,0.28");
        }

        @Test
        void keepsAnEmptyFieldInPlace() {
            assertThat(Export.csvRow(Arrays.asList("J-118", null, 0.28))).isEqualTo("J-118,,0.28");
        }

        @Test
        void refusesAnythingThatIsNotAList() {
            assertThatThrownBy(() -> Export.csvRow(null)).hasMessageContaining("must be an array");
        }
    }

    @Nested
    class ToCsv {

        private final List<Map<String, Object>> rows = List.of(
                row("id", "J-1", "welder", "A17", "wallIn", 0.28), row("id", "J-2", "welder", "B04", "wallIn", 0.432));

        @Test
        void writesAHeaderAndARowEach() {
            String out = Export.toCsv(rows, CsvColumn.keys("id", "welder", "wallIn"));
            assertThat(out.split("\n")).containsExactly("id,welder,wallIn", "J-1,A17,0.28", "J-2,B04,0.432");
        }

        @Test
        void takesALabelForAColumn() {
            String out = Export.toCsv(rows, List.of(CsvColumn.of("id", "joint")));
            assertThat(out.split("\n")[0]).isEqualTo("joint");
        }

        @Test
        void dropsTheHeaderWhenAsked() {
            String out = Export.toCsv(rows, CsvColumn.keys("id"), false);
            assertThat(out.split("\n")).hasSize(2);
        }

        @Test
        void leavesAnEmptyCellRatherThanShiftingTheColumns() {
            List<Map<String, Object>> ragged = List.of(row("id", "J-1"), row("id", "J-2", "welder", "B04"));
            String out = Export.toCsv(ragged, CsvColumn.keys("id", "welder"));
            assertThat(out.split("\n", -1)[1]).isEqualTo("J-1,");
            assertThat(out.split("\n", -1)[2]).isEqualTo("J-2,B04");
        }

        @Test
        void writesOnlyAHeaderForNoRows() {
            assertThat(Export.toCsv(List.of(), CsvColumn.keys("id"))).isEqualTo("id");
        }

        @Test
        void refusesAnExportWithNoColumns() {
            assertThatThrownBy(() -> Export.toCsv(rows, List.of())).hasMessageContaining("at least one column");
        }

        @Test
        void refusesAColumnWithNoKey() {
            assertThatThrownBy(() -> Export.toCsv(rows, List.of(new CsvColumn(null, "joint"))))
                    .hasMessageContaining("column 1 has no key");
        }
    }

    @Nested
    class ToJson {

        @Test
        void sortsTheKeysSoTwoExportsCompareEqual() {
            assertThat(Export.toJson(row("b", 1, "a", 2))).isEqualTo(Export.toJson(row("a", 2, "b", 1)));
        }

        @Test
        void prettyPrintsWhenAsked() {
            assertThat(Export.toJson(Map.of("a", 1), true)).contains("\n");
            assertThat(Export.toJson(Map.of("a", 1))).doesNotContain("\n");
            assertThat(Export.toJson(row("b", List.of(1, Map.of()), "a", List.of()), true))
                    .isEqualTo("{\n  \"a\": [],\n  \"b\": [\n    1,\n    {}\n  ]\n}");
        }

        @Test
        void handlesAListOfRecords() {
            assertThat(Export.toJson(List.of(Map.of("id", "J-1")))).isEqualTo("[{\"id\":\"J-1\"}]");
        }
    }

    @Nested
    class WithChecksumAndVerifyChecksum {

        private final String body = "id,welder\nJ-1,A17";

        @Test
        void appendsAChecksumLine() {
            String out = Export.withChecksum(body);
            assertThat(out).startsWith(body);
            String[] lines = out.split("\n");
            assertThat(lines[lines.length - 1]).matches("# crc32 [0-9a-f]{8}");
            assertThat(lines[lines.length - 1]).isEqualTo("# crc32 " + Checksum.crc32(body));
        }

        @Test
        void verifiesAFileItWrote() {
            ChecksumCheck check = Export.verifyChecksum(Export.withChecksum(body));
            assertThat(check.checked()).isTrue();
            assertThat(check.intact()).isTrue();
            assertThat(check.reason()).isEmpty();
        }

        @Test
        void catchesABodyThatWasEdited() {
            String out = Export.withChecksum(body).replace("A17", "B04");
            ChecksumCheck check = Export.verifyChecksum(out);
            assertThat(check.intact()).isFalse();
            assertThat(check.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("does not match"));
        }

        @Test
        void catchesAFileThatArrivedTruncated() {
            String out = Export.withChecksum("id,welder\nJ-1,A17\nJ-2,B04");
            String truncated = String.join(
                    "\n",
                    Arrays.stream(out.split("\n"))
                            .filter(line -> !line.equals("J-2,B04"))
                            .toList());
            assertThat(Export.verifyChecksum(truncated).intact()).isFalse();
        }

        @Test
        void saysSoWhenThereIsNoChecksumLine() {
            ChecksumCheck check = Export.verifyChecksum("id,welder\nJ-1,A17");
            assertThat(check.checked()).isFalse();
            assertThat(check.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("no checksum line"));
        }

        @Test
        void saysSoWhenTheChecksumLineIsMalformed() {
            ChecksumCheck check = Export.verifyChecksum(body + "\n# crc32");
            assertThat(check.checked()).isFalse();
            assertThat(check.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("malformed"));
        }

        @Test
        void takesADifferentLabel() {
            assertThat(Export.withChecksum(body, "sum")).contains("# sum ");
        }

        @Test
        void refusesABodyThatIsNotText() {
            assertThatThrownBy(() -> Export.withChecksum(null)).hasMessageContaining("must be text");
            assertThatThrownBy(() -> Export.verifyChecksum(null)).hasMessageContaining("must be text");
        }
    }

    @Nested
    class ExportRegister {

        private final List<Map<String, Object>> rows = List.of(row(
                "id", "J-118",
                "spool", "SP-4",
                "welder", "A17",
                "nominalSize", "6",
                "schedule", "40",
                "wallIn", 0.28,
                "day", "2025-03-14",
                "disposition", "accept",
                "clause", "9.3"));

        @Test
        void writesTheColumnsAClientExpectsInOrder() {
            assertThat(Export.registerColumns().stream().map(CsvColumn::label))
                    .containsExactly("joint", "spool", "welder", "size", "sch", "wall", "shot", "result", "clause");
        }

        @Test
        void writesAChecksummedRegister() {
            String out = Export.exportRegister(rows);
            assertThat(out).contains("J-118");
            assertThat(out.split("\n")[1]).isEqualTo("J-118,SP-4,A17,6,40,0.28,2025-03-14,accept,9.3");
            assertThat(Export.verifyChecksum(out).intact()).isTrue();
        }

        @Test
        void writesAHeaderEvenWithNothingToReport() {
            String out = Export.exportRegister(List.of());
            assertThat(out.split("\n")[0]).contains("joint");
            assertThat(Export.verifyChecksum(out).intact()).isTrue();
        }
    }
}
