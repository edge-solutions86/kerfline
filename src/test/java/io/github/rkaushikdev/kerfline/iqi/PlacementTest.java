package io.github.rkaushikdev.kerfline.iqi;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlacementTest {

    private static PlacementRequest.Builder hole() {
        return PlacementRequest.builder().iqiType("hole");
    }

    @Nested
    class Placements {

        @Test
        void namesTheTwoPlacesAnIndicatorGoes() {
            assertThat(Placement.placements()).containsExactly("on_weld", "beside_weld");
        }
    }

    @Nested
    class ShimRequired {

        @Test
        void wantsAShimUnderAPlaqueLaidBesideTheWeld() {
            assertThat(Placement.shimRequired("hole", "beside_weld")).isTrue();
        }

        @Test
        void wantsNoneUnderAPlaqueLaidOnTheWeld() {
            assertThat(Placement.shimRequired("hole", "on_weld")).isFalse();
        }

        @Test
        void neverWantsOneForAWireIndicator() {
            assertThat(Placement.shimRequired("wire", "beside_weld")).isFalse();
            assertThat(Placement.shimRequired("wire", "on_weld")).isFalse();
        }

        @Test
        void refusesAPlacementItDoesNotKnow() {
            assertThatThrownBy(() -> Placement.shimRequired("hole", "on_backing"))
                    .hasMessageContaining("on_weld or beside_weld");
        }

        @Test
        void refusesAnIndicatorTypeItHasNoRuleFor() {
            assertThatThrownBy(() -> Placement.shimRequired("step", "on_weld"))
                    .hasMessageContaining("no placement rule");
        }
    }

    @Nested
    class ShimThicknessIn {

        @Test
        void isNothingWhenTheWeldIsDressedFlush() {
            assertThat(Placement.shimThicknessIn(0, 0)).isEqualTo(0);
        }

        @Test
        void makesUpOneFaceOfReinforcement() {
            assertThat(Placement.shimThicknessIn(0.0625, 0)).isCloseTo(0.0625, digits(6));
        }

        @Test
        void makesUpBothFacesWhenTheRootIsCrownedToo() {
            assertThat(Placement.shimThicknessIn(0.0625, 0.0625)).isCloseTo(0.125, digits(6));
        }

        @Test
        void refusesANegativeFace() {
            assertThatThrownBy(() -> Placement.shimThicknessIn(-0.01, 0)).hasMessageContaining("not be below 0");
        }
    }

    @Nested
    class PlacementPlanFor {

        @Test
        void plansAPlaqueOnTheWeldWithNoShimAndNoMarking() {
            assertThat(Placement.placementPlan(PlacementRequest.of("hole")))
                    .isEqualTo(new PlacementPlan("hole", "on_weld", "source", false, 0, Optional.empty(), List.of()));
        }

        @Test
        void plansAPlaqueBesideTheWeldWithAShim() {
            PlacementPlan plan = Placement.placementPlan(
                    hole().placement("beside_weld").reinforcementIn(0.0625).build());
            assertThat(plan.shimRequired()).isTrue();
            assertThat(plan.shimThicknessIn()).isCloseTo(0.0625, digits(6));
        }

        @Test
        void sizesTheShimForBothFacesOfTheWeld() {
            PlacementPlan plan = Placement.placementPlan(hole().placement("beside_weld")
                    .reinforcementIn(0.0625)
                    .secondFaceIn(0.03125)
                    .build());
            assertThat(plan.shimThicknessIn()).isCloseTo(0.0938, digits(6));
        }

        @Test
        void leavesTheShimAtNothingWhenNoneIsNeededEvenWithACrown() {
            PlacementPlan plan = Placement.placementPlan(PlacementRequest.builder()
                    .iqiType("wire")
                    .reinforcementIn(0.0625)
                    .build());
            assertThat(plan.shimRequired()).isFalse();
            assertThat(plan.shimThicknessIn()).isEqualTo(0);
        }

        @Test
        void callsForTheLeadFOnTheFilmSide() {
            PlacementPlan plan = Placement.placementPlan(hole().side("film").build());
            assertThat(plan.leadMarking()).hasValue("F");
            assertThat(plan.marksRequired()).containsExactly("F");
        }

        @Test
        void refusesASideThatIsNotOneOfTheTwo() {
            assertThatThrownBy(() -> Placement.placementPlan(hole().side("back").build()))
                    .hasMessageContaining("source or film");
        }
    }

    @Nested
    class SideFromMarking {

        @Test
        void readsAnFAsTheFilmSide() {
            assertThat(Placement.sideFromMarking("F")).isEqualTo("film");
            assertThat(Placement.sideFromMarking(" f ")).isEqualTo("film");
        }

        @Test
        void readsNothingAtAllAsTheSourceSide() {
            assertThat(Placement.sideFromMarking(null)).isEqualTo("source");
            assertThat(Placement.sideFromMarking("")).isEqualTo("source");
        }

        @Test
        void refusesAMarkingItCannotReadRatherThanGuessing() {
            assertThatThrownBy(() -> Placement.sideFromMarking("S")).hasMessageContaining("cannot read S");
            assertThatThrownBy(() -> Placement.sideFromMarking("FF")).hasMessageContaining("cannot read FF");
        }
    }

    @Nested
    class MarkingAgrees {

        @Test
        void agreesWhenTheFilmIsMarkedTheWayTheSheetSays() {
            assertThat(Placement.markingAgrees("F", "film")).isTrue();
            assertThat(Placement.markingAgrees(null, "source")).isTrue();
        }

        @Test
        void catchesAFilmSideShotWithNoFOnIt() {
            assertThat(Placement.markingAgrees(null, "film")).isFalse();
        }

        @Test
        void catchesAnFOnAShotTheSheetCallsSourceSide() {
            assertThat(Placement.markingAgrees("F", "source")).isFalse();
        }
    }
}
