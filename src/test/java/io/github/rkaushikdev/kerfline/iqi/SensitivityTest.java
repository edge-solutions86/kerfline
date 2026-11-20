package io.github.rkaushikdev.kerfline.iqi;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SensitivityTest {

    @Nested
    class TheCalledForSensitivity {

        @Test
        void isTwoPercentUnlessAJobSaysOtherwise() {
            assertThat(Sensitivity.calledForSensitivityPercent()).isEqualTo(2);
        }
    }

    @Nested
    class HoleSensitivityPercent {

        @Test
        void followsTheSquareRootFormula() {
            double value = Sensitivity.holeSensitivityPercent(0.025, 0.05, 1);
            assertThat(value).isCloseTo(100 * Math.sqrt((0.025 * 0.05) / 2), digits(9));
            assertThat(value).isCloseTo(2.5, digits(3));
        }

        @Test
        void improvesAsTheShotGoesThroughLessMetal() {
            double thin = Sensitivity.holeSensitivityPercent(0.025, 0.05, 0.5);
            double thick = Sensitivity.holeSensitivityPercent(0.025, 0.05, 2);
            assertThat(thin).isGreaterThan(thick);
        }

        @Test
        void halvesWhenTheThicknessDoubles() {
            double one = Sensitivity.holeSensitivityPercent(0.025, 0.05, 1);
            double two = Sensitivity.holeSensitivityPercent(0.025, 0.05, 2);
            assertThat(two).isCloseTo(one / 2, digits(9));
        }

        @Test
        void refusesAThicknessOrAHoleAtZero() {
            assertThatThrownBy(() -> Sensitivity.holeSensitivityPercent(0.025, 0.05, 0))
                    .hasMessageContaining("greater than 0");
            assertThatThrownBy(() -> Sensitivity.holeSensitivityPercent(0.025, 0, 1))
                    .hasMessageContaining("hole diameter");
        }
    }

    @Nested
    class HoleSensitivityFor {

        @Test
        void usesTheDrilledHoleAndNotTheNominalMultiple() {
            // The 5 plaque 1T hole is opened up to 0.010 in, well past 0.005 in.
            double drilled = Sensitivity.holeSensitivityFor(5, "1T", 0.1);
            double nominal = Sensitivity.holeSensitivityPercent(0.005, 0.005, 0.1);
            assertThat(drilled).isGreaterThan(nominal);
        }

        @Test
        void agreesWithTheFormulaOnAPlaqueWithNoFloorEffect() {
            double looked = Sensitivity.holeSensitivityFor(25, "2T", 1);
            double direct = Sensitivity.holeSensitivityPercent(0.025, 0.05, 1);
            assertThat(looked).isCloseTo(direct, digits(12));
        }

        @Test
        void getsBetterThroughTheBiggerHolesOnTheSamePlaque() {
            double one = Sensitivity.holeSensitivityFor(25, "1T", 1);
            double four = Sensitivity.holeSensitivityFor(25, "4T", 1);
            assertThat(one).isLessThan(four);
        }
    }

    @Nested
    class WireSensitivityPercent {

        @Test
        void isTheWireDiameterAsAPercentageOfTheThickness() {
            assertThat(Sensitivity.wireSensitivityPercent(0.01, 0.5)).isCloseTo(2, digits(9));
        }

        @Test
        void looksTheWireUpByNumber() {
            assertThat(Sensitivity.wireSensitivityFor(6, 0.5)).isCloseTo(2, digits(9));
        }

        @Test
        void improvesWithAFinerWire() {
            double fine = Sensitivity.wireSensitivityFor(4, 0.5);
            double coarse = Sensitivity.wireSensitivityFor(8, 0.5);
            assertThat(fine).isLessThan(coarse);
        }

        @Test
        void refusesAWireThatDoesNotExist() {
            assertThatThrownBy(() -> Sensitivity.wireSensitivityFor(99, 0.5))
                    .hasMessageContaining("no wire numbered 99");
        }
    }

    @Nested
    class JudgeHoleSensitivity {

        @Test
        void passesAShotThatBeatsTheCalledForFigure() {
            SensitivityVerdict verdict = Sensitivity.judgeHoleSensitivity(25, "2T", 2);
            assertThat(verdict.achievedPercent()).isCloseTo(1.25, digits(2));
            assertThat(verdict.calledForPercent()).isEqualTo(2);
            assertThat(verdict.pass()).isTrue();
            assertThat(verdict.marginPercent()).isGreaterThan(0);
        }

        @Test
        void failsAShotThatDoesNotReachIt() {
            SensitivityVerdict verdict = Sensitivity.judgeHoleSensitivity(25, "2T", 0.5);
            assertThat(verdict.pass()).isFalse();
            assertThat(verdict.marginPercent()).isLessThan(0);
        }

        @Test
        void takesAJobSpecificFigure() {
            SensitivityVerdict strict = Sensitivity.judgeHoleSensitivity(25, "2T", 2, 1);
            assertThat(strict.calledForPercent()).isEqualTo(1);
            assertThat(strict.pass()).isFalse();
        }

        @Test
        void judgesAWireTheSameWay() {
            SensitivityVerdict verdict = Sensitivity.judgeWireSensitivity(6, 1);
            assertThat(verdict.achievedPercent()).isCloseTo(1, digits(3));
            assertThat(verdict.pass()).isTrue();
        }

        @Test
        void refusesACalledForFigureAtZero() {
            assertThatThrownBy(() -> Sensitivity.judgeWireSensitivity(6, 1, 0)).hasMessageContaining("greater than 0");
        }
    }

    @Nested
    class SensitivityLabel {

        @Test
        void writesThePlaqueAsAPercentageNextToTheHole() {
            assertThat(Sensitivity.sensitivityLabel(25, "2T", 1.25)).isEqualTo("2-2T");
        }

        @Test
        void movesTheFirstNumberWithTheThickness() {
            assertThat(Sensitivity.sensitivityLabel(25, "2T", 2.5)).isEqualTo("1-2T");
        }

        @Test
        void namesTheHoleThatWasSeen() {
            assertThat(Sensitivity.sensitivityLabel(25, "1T", 1.25)).isEqualTo("2-1T");
        }
    }

    @Nested
    class ThicknessHeadroomIn {

        @Test
        void isPositiveWhileTheShotIsStillPassing() {
            assertThat(Sensitivity.thicknessHeadroomIn(25, "2T", 2)).isGreaterThan(0);
        }

        @Test
        void isNegativeOnceTheShotHasStoppedPassing() {
            assertThat(Sensitivity.thicknessHeadroomIn(25, "2T", 0.5)).isLessThan(0);
        }

        @Test
        void isNothingAtTheThicknessWhereTheVerdictFlips() {
            double headroom = Sensitivity.thicknessHeadroomIn(25, "2T", 1.25);
            assertThat(headroom).isCloseTo(0, digits(3));
            SensitivityVerdict verdict = Sensitivity.judgeHoleSensitivity(25, "2T", 1.25);
            assertThat(verdict.achievedPercent()).isCloseTo(2, digits(2));
        }
    }
}
