package io.github.rkaushikdev.kerfline.density;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.isotope.SourceKind;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LimitsTest {

    @Nested
    class SourceKindOf {

        @Test
        void namesTheTwoKinds() {
            assertThat(Limits.sourceKinds()).containsExactly(SourceKind.XRAY, SourceKind.GAMMA);
            assertThat(Limits.sourceKinds().stream().map(SourceKind::label)).containsExactly("xray", "gamma");
        }

        @Test
        void passesAKindStraightThrough() {
            assertThat(Limits.sourceKindOf("gamma")).isEqualTo(SourceKind.GAMMA);
            assertThat(Limits.sourceKindOf("xray")).isEqualTo(SourceKind.XRAY);
        }

        @Test
        void readsAnIsotopeAsAGammaSource() {
            assertThat(Limits.sourceKindOf("IR192")).isEqualTo(SourceKind.GAMMA);
            assertThat(Limits.sourceKindOf("co-60")).isEqualTo(SourceKind.GAMMA);
        }

        @Test
        void refusesSomethingThatIsNeither() {
            assertThatThrownBy(() -> Limits.sourceKindOf("neutron")).hasMessageContaining("no isotope neutron");
        }
    }

    @Nested
    class DensityLimitsBySource {

        @Test
        void setsAHigherFloorForGammaThanForXray() {
            assertThat(Limits.densityLimits("gamma").minimum()).isEqualTo(2);
            assertThat(Limits.densityLimits("xray").minimum()).isEqualTo(1.8);
        }

        @Test
        void setsTheSameCeilingForBoth() {
            assertThat(Limits.densityLimits("gamma").maximum()).isEqualTo(4);
            assertThat(Limits.densityLimits("xray").maximum()).isEqualTo(4);
            assertThat(Limits.ceilingDensity()).isEqualTo(4);
        }

        @Test
        void dropsTheFloorForEachFilmOfACompositeSet() {
            assertThat(Limits.densityLimits("gamma", true).minimum()).isEqualTo(1.3);
            assertThat(Limits.densityLimits("xray", true).minimum()).isEqualTo(1.3);
        }

        @Test
        void leavesTheCeilingAloneForACompositeSet() {
            assertThat(Limits.densityLimits("gamma", true).maximum()).isEqualTo(4);
        }
    }

    @Nested
    class WithinLimits {

        @Test
        void acceptsAReadingInsideTheBand() {
            assertThat(Limits.withinLimits(2.5, "gamma")).isTrue();
        }

        @Test
        void rejectsOneOutsideIt() {
            assertThat(Limits.withinLimits(1.9, "gamma")).isFalse();
            assertThat(Limits.withinLimits(4.1, "gamma")).isFalse();
        }

        @Test
        void acceptsAtExactlyTheFloorAndTheCeiling() {
            assertThat(Limits.withinLimits(2, "gamma")).isTrue();
            assertThat(Limits.withinLimits(4, "gamma")).isTrue();
        }

        @Test
        void turnsALightGammaFilmIntoAnAcceptableXrayOne() {
            assertThat(Limits.withinLimits(1.9, "gamma")).isFalse();
            assertThat(Limits.withinLimits(1.9, "xray")).isTrue();
        }

        @Test
        void answersFalseRatherThanThrowingOnABadReading() {
            assertThat(Limits.withinLimits(Double.NaN, "gamma")).isFalse();
        }
    }

    @Nested
    class JudgeDensity {

        @Test
        void acceptsAGoodFilm() {
            DensityJudgement verdict = Limits.judgeDensity(2.5, "gamma");
            assertThat(verdict.pass()).isTrue();
            assertThat(verdict.verdict()).isEqualTo(DensityCall.ACCEPT);
            assertThat(verdict.kind()).isEqualTo(SourceKind.GAMMA);
        }

        @Test
        void callsAThinFilmLight() {
            assertThat(Limits.judgeDensity(1.7, "gamma").verdict()).isEqualTo(DensityCall.LIGHT);
        }

        @Test
        void callsAnOverexposedFilmDark() {
            assertThat(Limits.judgeDensity(4.2, "gamma").verdict()).isEqualTo(DensityCall.DARK);
        }

        @Test
        void reportsTheLimitsItJudgedAgainst() {
            DensityJudgement verdict = Limits.judgeDensity(1.9, "xray");
            assertThat(verdict.minimum()).isEqualTo(1.8);
            assertThat(verdict.maximum()).isEqualTo(4);
            assertThat(verdict.pass()).isTrue();
        }

        @Test
        void refusesADensityThatIsNotAPositiveNumber() {
            assertThatThrownBy(() -> Limits.judgeDensity(0, "gamma")).hasMessageContaining("greater than zero");
            assertThatThrownBy(() -> Limits.judgeDensity(Double.NaN, "gamma")).hasMessageContaining("finite number");
        }
    }

    @Nested
    class JudgeComposite {

        @Test
        void passesAPairWhereEachFilmClearsTheCompositeFloorAndTheSumClearsTheSingleOne() {
            CompositeJudgement verdict = Limits.judgeComposite(List.of(1.4, 1.4), "gamma");
            assertThat(verdict.stackedDensity()).isCloseTo(2.8, digits(6));
            assertThat(verdict.everyFilmPasses()).isTrue();
            assertThat(verdict.stackedPasses()).isTrue();
            assertThat(verdict.pass()).isTrue();
        }

        @Test
        void failsWhenOneFilmOfThePairIsUnderTheCompositeFloor() {
            CompositeJudgement verdict = Limits.judgeComposite(List.of(1.2, 1.8), "gamma");
            assertThat(verdict.everyFilmPasses()).isFalse();
            assertThat(verdict.films().get(0).verdict()).isEqualTo(DensityCall.LIGHT);
            assertThat(verdict.pass()).isFalse();
        }

        @Test
        void failsAPairThatStacksUpShortEvenWithBothFilmsLegalOnTheirOwn() {
            CompositeJudgement verdict = Limits.judgeComposite(List.of(1.3, 1.3), "xray");
            assertThat(verdict.everyFilmPasses()).isTrue();
            assertThat(verdict.stackedDensity()).isCloseTo(2.6, digits(6));
            assertThat(verdict.stackedPasses()).isTrue();
        }

        @Test
        void failsAPairThatStacksPastTheCeiling() {
            CompositeJudgement verdict = Limits.judgeComposite(List.of(2.2, 2.2), "gamma");
            assertThat(verdict.stackedDensity()).isCloseTo(4.4, digits(6));
            assertThat(verdict.stackedPasses()).isFalse();
            assertThat(verdict.pass()).isFalse();
        }

        @Test
        void refusesAnythingThatIsNotASetOfTwoOrMore() {
            assertThatThrownBy(() -> Limits.judgeComposite(List.of(2.5), "gamma"))
                    .hasMessageContaining("two or more films");
            assertThatThrownBy(() -> Limits.judgeComposite(null, "gamma")).hasMessageContaining("two or more films");
        }

        @Test
        void namesTheFilmThatIsNotANumber() {
            assertThatThrownBy(() -> Limits.judgeComposite(Arrays.asList(1.4, null), "gamma"))
                    .hasMessageContaining("film 2 density");
        }
    }
}
