package io.github.rkaushikdev.kerfline.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LayoutTest {

    @Nested
    class TheWidths {

        @Test
        void hasANormalPageAndAWideOne() {
            assertThat(Layout.pageWidth()).isEqualTo(78);
            assertThat(Layout.widePageWidth()).isGreaterThan(Layout.pageWidth());
        }
    }

    @Nested
    class Heading {

        @Test
        void centresTheTitleOverADoubleRule() {
            String[] lines = Layout.heading("REPORT").split("\n");
            assertThat(lines).hasSize(2);
            assertThat(lines[0].trim()).isEqualTo("REPORT");
            assertThat(lines[1]).isEqualTo("=".repeat(78));
        }

        @Test
        void takesAWiderPage() {
            assertThat(Layout.heading("REPORT", 96).split("\n")[1]).hasSize(96);
        }

        @Test
        void refusesAReportWithNoTitle() {
            assertThatThrownBy(() -> Layout.heading("  ")).hasMessageContaining("a report has a title");
        }

        @Test
        void refusesASillyWidth() {
            assertThatThrownBy(() -> Layout.heading("REPORT", 10)).hasMessageContaining("between 20 and 200");
            assertThatThrownBy(() -> Layout.heading("REPORT", 201)).hasMessageContaining("between 20 and 200");
        }
    }

    @Nested
    class Block {

        @Test
        void putsTheTitleOverASingleRuleAndThenTheBody() {
            String[] lines = Layout.block("FINDINGS", "nothing").split("\n");
            assertThat(lines[0]).isEqualTo("FINDINGS");
            assertThat(lines[1]).isEqualTo("-".repeat(78));
            assertThat(lines[2]).isEqualTo("nothing");
        }

        @Test
        void leavesTheBodyOffWhenThereIsNone() {
            assertThat(Layout.block("FINDINGS", "").split("\n")).hasSize(2);
            assertThat(Layout.block("FINDINGS", null).split("\n")).hasSize(2);
        }

        @Test
        void printsASectionThatHasNothingToShow() {
            assertThat(Layout.emptyBlock("FINDINGS", "Nothing to report.")).contains("Nothing to report.");
        }

        @Test
        void refusesASectionWithNoTitle() {
            assertThatThrownBy(() -> Layout.block("", "body")).hasMessageContaining("a section has a title");
        }
    }

    @Nested
    class Banner {

        @Test
        void centresAcrossThePage() {
            assertThat(Layout.banner("DRAFT").trim()).isEqualTo("DRAFT");
            assertThat(Layout.banner("DRAFT")).hasSize(78);
        }
    }

    @Nested
    class Page {

        @Test
        void joinsThePartsWithABlankLineBetween() {
            assertThat(Layout.page(List.of("a", "b"))).isEqualTo("a\n\nb");
        }

        @Test
        void dropsPartsThatAreEmpty() {
            assertThat(Layout.page(Arrays.asList("a", "", null, null, "b"))).isEqualTo("a\n\nb");
        }

        @Test
        void isEmptyForNothingAtAll() {
            assertThat(Layout.page(List.of())).isEmpty();
        }

        @Test
        void refusesAnythingThatIsNotAList() {
            assertThatThrownBy(() -> Layout.page(null)).hasMessageContaining("must be an array");
        }
    }

    @Nested
    class FitsWidth {

        @Test
        void acceptsAPageThatFits() {
            assertThat(Layout.fitsWidth(Layout.page(List.of(Layout.heading("REPORT"), Layout.block("A", "short")))))
                    .isTrue();
        }

        @Test
        void rejectsAPageWithALineThatRunsOver() {
            assertThat(Layout.fitsWidth("x".repeat(90))).isFalse();
        }

        @Test
        void acceptsALineExactlyAtTheWidth() {
            assertThat(Layout.fitsWidth("x".repeat(78))).isTrue();
        }

        @Test
        void namesTheLinesThatDoNotFit() {
            String text = String.join("\n", "short", "x".repeat(90), "short");
            List<OverlongLine> over = Layout.overlongLines(text);
            assertThat(over).hasSize(1);
            assertThat(over.get(0).line()).isEqualTo(2);
            assertThat(over.get(0).length()).isEqualTo(90);
        }

        @Test
        void findsNothingToNameOnAPageThatFits() {
            assertThat(Layout.overlongLines("short")).isEmpty();
        }

        @Test
        void judgesAgainstTheWidePageWhenToldTo() {
            assertThat(Layout.fitsWidth("x".repeat(90))).isFalse();
            assertThat(Layout.fitsWidth("x".repeat(90), 96)).isTrue();
        }
    }
}
