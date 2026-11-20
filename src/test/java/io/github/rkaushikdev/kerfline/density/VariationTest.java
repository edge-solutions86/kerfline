package io.github.rkaushikdev.kerfline.density;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VariationTest {

    private static List<AreaReading> bare(Double... densities) {
        return AreaReading.unlocated(List.of(densities));
    }

    @Nested
    class TheRuleItself {

        @Test
        void isTighterBelowTheReferenceThanAboveIt() {
            VariationFractions fractions = Variation.variationFractions();
            assertThat(fractions.below()).isEqualTo(0.15);
            assertThat(fractions.above()).isEqualTo(0.3);
            assertThat(fractions.below()).isLessThan(fractions.above());
        }
    }

    @Nested
    class VariationLimitsOfAReference {

        @Test
        void bracketsTheReference() {
            VariationLimits limits = Variation.variationLimits(2);
            assertThat(limits.minimum()).isCloseTo(1.7, digits(6));
            assertThat(limits.maximum()).isCloseTo(2.6, digits(6));
        }

        @Test
        void movesWithTheReferenceRatherThanSittingStill() {
            VariationLimits low = Variation.variationLimits(2);
            VariationLimits high = Variation.variationLimits(3);
            assertThat(high.minimum()).isGreaterThan(low.minimum());
            assertThat(high.maximum()).isGreaterThan(low.maximum());
        }

        @Test
        void refusesAReferenceAtOrBelowZero() {
            assertThatThrownBy(() -> Variation.variationLimits(0)).hasMessageContaining("greater than zero");
        }
    }

    @Nested
    class WithinVariation {

        @Test
        void acceptsAReadingInsideTheBand() {
            assertThat(Variation.withinVariation(2.4, 2)).isTrue();
        }

        @Test
        void acceptsBothEdges() {
            assertThat(Variation.withinVariation(1.7, 2)).isTrue();
            assertThat(Variation.withinVariation(2.6, 2)).isTrue();
        }

        @Test
        void rejectsOutside() {
            assertThat(Variation.withinVariation(1.69, 2)).isFalse();
            assertThat(Variation.withinVariation(2.61, 2)).isFalse();
        }

        @Test
        void flipsTheVerdictOnTheSameReadingWhenTheReferenceMoves() {
            // 2.6 sits right on the dark edge of a film shot at 2.0 and is too light
            // for one shot at 3.5. Same reading, same weld, opposite verdicts.
            assertThat(Variation.withinVariation(2.6, 2)).isTrue();
            assertThat(Variation.withinVariation(2.6, 3.5)).isFalse();
        }
    }

    @Nested
    class JudgeVariation {

        @Test
        void signsThePercentageAgainstTheReference() {
            assertThat(Variation.judgeVariation(2.6, 2).percentFromReference()).isCloseTo(30, digits(2));
            assertThat(Variation.judgeVariation(1.7, 2).percentFromReference()).isCloseTo(-15, digits(2));
        }

        @Test
        void namesWhichWayAFailingReadingWent() {
            assertThat(Variation.judgeVariation(1.5, 2).verdict()).isEqualTo(VariationCall.TOO_LIGHT);
            assertThat(Variation.judgeVariation(3, 2).verdict()).isEqualTo(VariationCall.TOO_DARK);
            assertThat(Variation.judgeVariation(2.1, 2).verdict()).isEqualTo(VariationCall.ACCEPT);
        }

        @Test
        void refusesAReadingAtOrBelowZero() {
            assertThatThrownBy(() -> Variation.judgeVariation(0, 2)).hasMessageContaining("greater than zero");
        }
    }

    @Nested
    class JudgeArea {

        @Test
        void passesAnAreaWhereEveryReadingIsInsideTheBand() {
            AreaJudgement area = Variation.judgeArea(bare(2.1, 2.3, 1.9), 2);
            assertThat(area.pass()).isTrue();
            assertThat(area.failures()).isEmpty();
            assertThat(area.readings()).hasSize(3);
        }

        @Test
        void failsOnASingleReadingOutsideTheBand() {
            AreaJudgement area = Variation.judgeArea(bare(2.1, 2.3, 1.5), 2);
            assertThat(area.pass()).isFalse();
            assertThat(area.failures()).hasSize(1);
        }

        @Test
        void reportsTheExtremesOfTheArea() {
            AreaJudgement area = Variation.judgeArea(bare(2.1, 2.5, 1.8), 2);
            assertThat(area.lightest().density()).isEqualTo(1.8);
            assertThat(area.darkest().density()).isEqualTo(2.5);
        }

        @Test
        void takesReadingsWithALocationOnThem() {
            AreaJudgement area = Variation.judgeArea(List.of(AreaReading.at("A", 2.1), AreaReading.at("B", 1.5)), 2);
            assertThat(area.failures().get(0).at()).contains("B");
            assertThat(area.readings().get(0).at()).contains("A");
        }

        @Test
        void refusesAnEmptyArea() {
            assertThatThrownBy(() -> Variation.judgeArea(List.of(), 2)).hasMessageContaining("at least one reading");
            assertThatThrownBy(() -> Variation.judgeArea(null, 2)).hasMessageContaining("must be an array");
        }

        @Test
        void namesTheReadingThatCarriesNoDensity() {
            List<AreaReading> readings =
                    List.of(AreaReading.of(2.1), new AreaReading(Optional.of("B"), OptionalDouble.empty()));
            assertThatThrownBy(() -> Variation.judgeArea(readings, 2)).hasMessageContaining("reading 2 has no density");
        }
    }

    @Nested
    class RemedyFor {

        @Test
        void asksForNothingWhenTheAreaPassed() {
            AreaJudgement area = Variation.judgeArea(bare(2.1, 2.2), 2);
            assertThat(Variation.remedyFor(area)).isEqualTo(new Remedy(RemedyAction.NONE, false, List.of()));
        }

        @Test
        void addsAnExposureWhenOneEndIsOut() {
            AreaJudgement area = Variation.judgeArea(List.of(AreaReading.at("A", 2.1), AreaReading.at("B", 1.4)), 2);
            Remedy remedy = Variation.remedyFor(area);
            assertThat(remedy.action()).isEqualTo(RemedyAction.ADD_EXPOSURE);
            assertThat(remedy.action().label()).isEqualTo("add an exposure");
            assertThat(remedy.reshoot()).isFalse();
            assertThat(remedy.at()).containsExactly("B");
        }

        @Test
        void addsExposuresAtBothEndsWhenBothAreOut() {
            AreaJudgement area = Variation.judgeArea(List.of(AreaReading.at("A", 1.4), AreaReading.at("B", 1.4)), 2);
            assertThat(Variation.remedyFor(area).at()).containsExactly("A", "B");
        }

        @Test
        void reshootsTheJointWhenTheFailuresAreSpreadAcrossMoreThanTwoSpots() {
            AreaJudgement area = Variation.judgeArea(
                    List.of(AreaReading.at("A", 1.4), AreaReading.at("B", 1.4), AreaReading.at("C", 1.4)), 2);
            assertThat(Variation.remedyFor(area).reshoot()).isTrue();
        }

        @Test
        void reshootsWhenTheFailingReadingsCarryNoLocation() {
            AreaJudgement area = Variation.judgeArea(bare(1.4, 2.1), 2);
            assertThat(Variation.remedyFor(area).reshoot()).isTrue();
        }
    }
}
