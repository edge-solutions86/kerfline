package io.github.rkaushikdev.kerfline.iqi;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SelectionTest {

    private static IqiBasis.Builder wall(double wallIn) {
        return IqiBasis.builder().wallIn(wallIn);
    }

    @Nested
    class TheSelectionTable {

        @Test
        void namesTheTwoSidesAndTheEssentialHole() {
            assertThat(Selection.sides()).containsExactly("source", "film");
            assertThat(Selection.essentialHole()).isEqualTo("2T");
        }

        @Test
        void climbsWithoutAGap() {
            List<SelectionRow> rows = Selection.selectionTable();
            for (int index = 0; index < rows.size(); index++) {
                SelectionRow row = rows.get(index);
                if (index > 0) {
                    assertThat(row.over()).isEqualTo(rows.get(index - 1).upTo());
                }
                assertThat(row.upTo()).isGreaterThan(row.over());
            }
        }

        @Test
        void alwaysAsksLessOfTheFilmSideThanTheSourceSide() {
            for (SelectionRow row : Selection.selectionTable()) {
                assertThat(row.film().hole()).isLessThan(row.source().hole());
                assertThat(row.film().wire()).isLessThan(row.source().wire());
            }
        }

        @Test
        void neverStepsBackwardsAsTheWallThickens() {
            List<SelectionRow> rows = Selection.selectionTable();
            for (int i = 1; i < rows.size(); i++) {
                assertThat(rows.get(i).source().hole())
                        .isGreaterThan(rows.get(i - 1).source().hole());
                assertThat(rows.get(i).source().wire())
                        .isGreaterThanOrEqualTo(rows.get(i - 1).source().wire());
            }
        }
    }

    @Nested
    class BasisThicknessIn {

        @Test
        void isTheBareWallWhenThereIsNoCrown() {
            assertThat(Selection.basisThicknessIn(IqiBasis.ofWall(0.375))).isEqualTo(0.375);
        }

        @Test
        void addsTheWeldReinforcement() {
            assertThat(Selection.basisThicknessIn(
                            wall(0.375).reinforcementIn(0.0625).build()))
                    .isCloseTo(0.4375, digits(6));
        }

        @Test
        void capsTheReinforcementAtWhatTheJobAllows() {
            double basis = Selection.basisThicknessIn(wall(0.375)
                    .reinforcementIn(0.25)
                    .maximumReinforcementIn(0.0625)
                    .build());
            assertThat(basis).isCloseTo(0.4375, digits(6));
        }

        @Test
        void leavesTheBackingOutOfItEntirely() {
            double withBacking =
                    Selection.basisThicknessIn(wall(0.375).backingIn(0.125).build());
            double without = Selection.basisThicknessIn(IqiBasis.ofWall(0.375));
            assertThat(withBacking).isEqualTo(without);
        }

        @Test
        void refusesAWallAtOrBelowZero() {
            assertThatThrownBy(() -> Selection.basisThicknessIn(IqiBasis.ofWall(0)))
                    .hasMessageContaining("greater than 0");
        }

        @Test
        void refusesAMaximumReinforcementThatIsNotANumber() {
            assertThatThrownBy(() -> Selection.basisThicknessIn(
                            wall(0.375).maximumReinforcementIn(Double.NaN).build()))
                    .hasMessageContaining("maximum reinforcement must be a number");
        }

        @Test
        void refusesANegativeCrownOrBacking() {
            assertThatThrownBy(() -> Selection.basisThicknessIn(
                            wall(0.375).reinforcementIn(-0.1).build()))
                    .hasMessageContaining("not be below 0");
            assertThatThrownBy(() -> Selection.basisThicknessIn(
                            wall(0.375).backingIn(-0.1).build()))
                    .hasMessageContaining("not be below 0");
        }
    }

    @Nested
    class SelectIqi {

        @Test
        void readsTheSourceSideRow() {
            IqiSelection chosen = Selection.selectIqi(0.375, "source");
            assertThat(chosen.holeIdentity()).isEqualTo(15);
            assertThat(chosen.wireNumber()).isEqualTo(6);
            assertThat(chosen.essentialHole()).isEqualTo("2T");
        }

        @Test
        void readsTheFilmSideRowAtTheSameThickness() {
            IqiSelection chosen = Selection.selectIqi(0.375, "film");
            assertThat(chosen.holeIdentity()).isEqualTo(12);
            assertThat(chosen.wireNumber()).isEqualTo(5);
        }

        @Test
        void defaultsToTheSourceSide() {
            assertThat(Selection.selectIqi(0.375).side()).isEqualTo("source");
        }

        @Test
        void putsEachBoundaryInTheRowBelowIt() {
            assertThat(Selection.selectIqi(0.25).holeIdentity()).isEqualTo(12);
            assertThat(Selection.selectIqi(0.2501).holeIdentity()).isEqualTo(15);
            assertThat(Selection.selectIqi(0.5).holeIdentity()).isEqualTo(17);
            assertThat(Selection.selectIqi(0.5001).holeIdentity()).isEqualTo(20);
        }

        @Test
        void carriesTheDrilledHoleSizeThroughFromThePlaque() {
            IqiSelection chosen = Selection.selectIqi(1, "source");
            assertThat(chosen.holeIdentity()).isEqualTo(25);
            assertThat(chosen.plaqueThicknessIn()).isCloseTo(0.025, digits(6));
            assertThat(chosen.essentialHoleIn()).isCloseTo(0.05, digits(6));
        }

        @Test
        void namesTheWireSetTheCrewHasToDraw() {
            assertThat(Selection.selectIqi(0.375, "source").wireSet()).isEqualTo("A");
            assertThat(Selection.selectIqi(2, "source").wireSet()).isEqualTo("B");
        }

        @Test
        void refusesASideThatIsNotOneOfTheTwo() {
            assertThatThrownBy(() -> Selection.selectIqi(0.375, "back")).hasMessageContaining("source or film");
        }

        @Test
        void refusesAThicknessPastTheEndOfTheTable() {
            assertThatThrownBy(() -> Selection.selectIqi(24)).hasMessageContaining("above the top of");
        }

        @Test
        void refusesAThicknessAtOrBelowZero() {
            assertThatThrownBy(() -> Selection.selectIqi(0)).hasMessageContaining("greater than 0");
        }
    }

    @Nested
    class SelectForJoint {

        @Test
        void runsTheBasisRuleAndThenTheTable() {
            IqiSelection chosen =
                    Selection.selectForJoint(wall(0.25).reinforcementIn(0.0625).build());
            assertThat(chosen.basisThicknessIn()).isCloseTo(0.3125, digits(6));
            assertThat(chosen.holeIdentity()).isEqualTo(15);
        }

        @Test
        void movesTheAnswerAWholeRowWhenTheCrownIsCounted() {
            IqiSelection bare = Selection.selectForJoint(IqiBasis.ofWall(0.25));
            IqiSelection crowned =
                    Selection.selectForJoint(wall(0.25).reinforcementIn(0.0625).build());
            assertThat(bare.holeIdentity()).isEqualTo(12);
            assertThat(crowned.holeIdentity()).isEqualTo(15);
        }

        @Test
        void doesNotMoveTheAnswerWhenABackingRingIsFitted() {
            IqiSelection plain = Selection.selectForJoint(IqiBasis.ofWall(0.25));
            IqiSelection backed =
                    Selection.selectForJoint(wall(0.25).backingIn(0.125).build());
            assertThat(backed.holeIdentity()).isEqualTo(plain.holeIdentity());
            assertThat(backed.backingIgnoredIn()).isEqualTo(0.125);
        }

        @Test
        void takesTheSourceSideWhenTheJointNamesNone() {
            assertThat(Selection.selectForJoint(wall(0.375).side(null).build()).side())
                    .isEqualTo("source");
        }

        @Test
        void takesTheSideFromTheJoint() {
            assertThat(Selection.selectForJoint(wall(0.375).side("film").build())
                            .holeIdentity())
                    .isEqualTo(12);
        }
    }

    @Nested
    class SelectionCovers {

        @Test
        void answersWithoutThrowing() {
            assertThat(Selection.selectionCovers(0.375)).isTrue();
            assertThat(Selection.selectionCovers(20)).isTrue();
            assertThat(Selection.selectionCovers(21)).isFalse();
            assertThat(Selection.selectionCovers(0)).isFalse();
            assertThat(Selection.selectionCovers(Double.NaN)).isFalse();
        }
    }
}
