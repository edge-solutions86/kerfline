package io.github.rkaushikdev.kerfline.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IdsTest {

    @Nested
    class Sequence {

        @Test
        void padsToTheStatedWidth() {
            assertThat(Ids.sequence("J", 12)).isEqualTo("J-012");
            assertThat(Ids.sequence("J", 12, 5)).isEqualTo("J-00012");
        }

        @Test
        void upperCasesThePrefix() {
            assertThat(Ids.sequence("rt", 4, 2)).isEqualTo("RT-04");
        }

        @Test
        void doesNotCutANumberThatIsWiderThanThePad() {
            assertThat(Ids.sequence("J", 12345, 3)).isEqualTo("J-12345");
        }

        @Test
        void refusesAPrefixThatIsNotOneToFourLetters() {
            assertThatThrownBy(() -> Ids.sequence("", 1)).hasMessageContaining("one to four letters");
            assertThatThrownBy(() -> Ids.sequence("TOOLONG", 1)).hasMessageContaining("one to four letters");
            assertThatThrownBy(() -> Ids.sequence("J1", 1)).hasMessageContaining("one to four letters");
        }

        @Test
        void refusesABadNumberOrWidth() {
            assertThatThrownBy(() -> Ids.sequence("J", -1)).hasMessageContaining("whole number");
            assertThatThrownBy(() -> Ids.sequence("J", 1, 0)).hasMessageContaining("between one and six");
            assertThatThrownBy(() -> Ids.sequence("J", 1, 7)).hasMessageContaining("between one and six");
        }
    }

    @Nested
    class ParseSequence {

        @Test
        void readsTheFormTheFormatterWrites() {
            assertThat(Ids.parseSequence("J-012")).isEqualTo(new SequenceId("J", 12));
        }

        @Test
        void acceptsASpaceOrNothingWhereTheDashShouldBe() {
            assertThat(Ids.parseSequence("J 12")).isEqualTo(new SequenceId("J", 12));
            assertThat(Ids.parseSequence("J12")).isEqualTo(new SequenceId("J", 12));
        }

        @Test
        void acceptsLowerCaseAndSurroundingSpaceOffAScannedForm() {
            assertThat(Ids.parseSequence("  rt-4 ")).isEqualTo(new SequenceId("RT", 4));
        }

        @Test
        void refusesWhatItCannotRead() {
            assertThatThrownBy(() -> Ids.parseSequence("joint twelve")).hasMessageContaining("cannot read");
            assertThatThrownBy(() -> Ids.parseSequence("12")).hasMessageContaining("cannot read");
            assertThatThrownBy(() -> Ids.parseSequence("")).hasMessageContaining("cannot read");
            assertThatThrownBy(() -> Ids.parseSequence(null)).hasMessageContaining("must be a string");
        }

        @Test
        void answersIsSequenceWithoutThrowing() {
            assertThat(Ids.isSequence("J-012")).isTrue();
            assertThat(Ids.isSequence("nope")).isFalse();
        }
    }

    @Nested
    class NextInSequence {

        @Test
        void keepsTheWrittenWidth() {
            assertThat(Ids.nextInSequence("J-012")).isEqualTo("J-013");
            assertThat(Ids.nextInSequence("J-099")).isEqualTo("J-100");
        }

        @Test
        void growsPastTheWidthRatherThanWrapping() {
            assertThat(Ids.nextInSequence("J-999")).isEqualTo("J-1000");
        }

        @Test
        void keepsAnUnpaddedSeriesUnpadded() {
            assertThat(Ids.nextInSequence("J-7")).isEqualTo("J-8");
        }
    }

    @Nested
    class CompareIdsAndSortIds {

        @Test
        void putsJ2BeforeJ10() {
            assertThat(Ids.compareIds("J-2", "J-10")).isNegative();
            assertThat(Ids.compareIds("J-10", "J-2")).isPositive();
        }

        @Test
        void isZeroForTheSameId() {
            assertThat(Ids.compareIds("J-10", "J-10")).isZero();
        }

        @Test
        void ignoresCase() {
            assertThat(Ids.compareIds("j-10", "J-10")).isZero();
        }

        @Test
        void sortsARegisterTheWayItReads() {
            assertThat(Ids.sortIds(List.of("J-10", "J-2", "J-1", "J-20", "J-3")))
                    .containsExactly("J-1", "J-2", "J-3", "J-10", "J-20");
        }

        @Test
        void leavesTheCallerArrayAlone() {
            List<String> ids = new ArrayList<>(List.of("J-10", "J-2"));
            Ids.sortIds(ids);
            assertThat(ids).containsExactly("J-10", "J-2");
        }

        @Test
        void sortsAcrossDifferingPrefixes() {
            assertThat(Ids.sortIds(List.of("RT-2", "J-10"))).containsExactly("J-10", "RT-2");
        }

        @Test
        void putsAShorterIdFirstWhenOneIsAPrefixOfTheOther() {
            assertThat(Ids.sortIds(List.of("J-1A", "J-1"))).containsExactly("J-1", "J-1A");
        }

        @Test
        void putsANumberAheadOfALetterAtTheSamePosition() {
            assertThat(Ids.compareIds("1A", "A1")).isNegative();
        }

        @Test
        void refusesAnythingThatIsNotAnArray() {
            assertThatThrownBy(() -> Ids.sortIds(null)).hasMessageContaining("must be an array");
        }
    }

    @Nested
    class RequireStencil {

        @Test
        void acceptsTheStampsTheShopIssues() {
            assertThat(Ids.requireStencil("A17")).isEqualTo("A17");
            assertThat(Ids.requireStencil("42")).isEqualTo("42");
            assertThat(Ids.requireStencil(" bw9 ")).isEqualTo("BW9");
        }

        @Test
        void refusesAStampThatCannotBeStruck() {
            assertThatThrownBy(() -> Ids.requireStencil("BW-9")).hasMessageContaining("one to six letters or digits");
            assertThatThrownBy(() -> Ids.requireStencil("")).hasMessageContaining("one to six letters or digits");
            assertThatThrownBy(() -> Ids.requireStencil("TOOLONGX"))
                    .hasMessageContaining("one to six letters or digits");
            assertThatThrownBy(() -> Ids.requireStencil(null)).hasMessageContaining("one to six letters or digits");
        }

        @Test
        void answersIsStencilWithoutThrowing() {
            assertThat(Ids.isStencil("A17")).isTrue();
            assertThat(Ids.isStencil("BW-9")).isFalse();
        }
    }

    @Nested
    class Slug {

        @Test
        void reducesANameToAKey() {
            assertThat(Ids.slug("Line 12 Spool 4")).isEqualTo("line-12-spool-4");
        }

        @Test
        void collapsesRunsAndTrimsTheEnds() {
            assertThat(Ids.slug("  --Line   12--  ")).isEqualTo("line-12");
        }

        @Test
        void refusesANameWithNothingUsableInIt() {
            assertThatThrownBy(() -> Ids.slug("---")).hasMessageContaining("empty name");
            assertThatThrownBy(() -> Ids.slug("")).hasMessageContaining("empty name");
            assertThatThrownBy(() -> Ids.slug(null)).hasMessageContaining("empty name");
        }
    }
}
