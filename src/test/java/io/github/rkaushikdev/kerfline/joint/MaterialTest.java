package io.github.rkaushikdev.kerfline.joint;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MaterialTest {

    @Nested
    class TheMaterialList {

        @Test
        void carriesWhatThisShopShoots() {
            assertThat(Material.materialIds()).contains("CARBON_STEEL", "ALUMINIUM", "STAINLESS");
        }

        @Test
        void takesCarbonSteelAsTheUnit() {
            assertThat(Material.material("CARBON_STEEL").equivalence()).isEqualTo(1);
        }

        @Test
        void findsAMaterialHoweverItWasWritten() {
            assertThat(Material.material("carbon steel").id()).isEqualTo("CARBON_STEEL");
            assertThat(Material.material("low-alloy").id()).isEqualTo("LOW_ALLOY");
            assertThat(Material.material(" STAINLESS ").id()).isEqualTo("STAINLESS");
        }

        @Test
        void carriesAPNumberForEach() {
            assertThat(Material.material("CARBON_STEEL").pNumber()).isEqualTo("P1");
            assertThat(Material.material("STAINLESS").pNumber()).isEqualTo("P8");
        }

        @Test
        void refusesAMaterialItDoesNotCarry() {
            assertThatThrownBy(() -> Material.material("LEAD")).hasMessageContaining("no material LEAD");
            assertThatThrownBy(() -> Material.material(null)).hasMessageContaining("named by a string");
        }

        @Test
        void namesTheIndicatorGroups() {
            assertThat(Material.iqiGroups()).containsExactly("01", "02", "03");
        }
    }

    @Nested
    class EquivalentSteelIn {

        @Test
        void leavesSteelAlone() {
            assertThat(Material.equivalentSteelIn("CARBON_STEEL", 0.5)).isEqualTo(0.5);
        }

        @Test
        void makesAnInchOfAluminiumBehaveLikeAFifthOfAnInchOfSteel() {
            assertThat(Material.equivalentSteelIn("ALUMINIUM", 1)).isCloseTo(0.18, digits(4));
        }

        @Test
        void makesTheHarderAlloysBehaveLikeMoreSteel() {
            assertThat(Material.equivalentSteelIn("INCONEL", 1)).isGreaterThan(1);
            assertThat(Material.equivalentSteelIn("COPPER", 1)).isGreaterThan(Material.equivalentSteelIn("INCONEL", 1));
        }

        @Test
        void makesStainlessSlightlyHarderThanCarbonSteel() {
            assertThat(Material.equivalentSteelIn("STAINLESS", 1)).isGreaterThan(1);
            assertThat(Material.equivalentSteelIn("STAINLESS", 1)).isLessThan(1.2);
        }

        @Test
        void inverts() {
            double steel = Material.equivalentSteelIn("ALUMINIUM", 1.4);
            assertThat(Material.fromEquivalentSteelIn("ALUMINIUM", steel)).isCloseTo(1.4, digits(3));
        }

        @Test
        void refusesAThicknessAtZero() {
            assertThatThrownBy(() -> Material.equivalentSteelIn("CARBON_STEEL", 0))
                    .hasMessageContaining("thickness");
            assertThatThrownBy(() -> Material.fromEquivalentSteelIn("CARBON_STEEL", 0))
                    .hasMessageContaining("steel thickness");
        }
    }

    @Nested
    class IndicatorAcceptable {

        @Test
        void acceptsAnIndicatorOfTheSameGroup() {
            IndicatorCheck check = Material.indicatorAcceptable("CARBON_STEEL", "STAINLESS");
            assertThat(check.sameGroup()).isTrue();
            assertThat(check.acceptable()).isTrue();
        }

        @Test
        void refusesASteelIndicatorOnAnAluminiumWeld() {
            IndicatorCheck check = Material.indicatorAcceptable("ALUMINIUM", "CARBON_STEEL");
            assertThat(check.acceptable()).isFalse();
            assertThat(check.sameGroup()).isFalse();
        }

        @Test
        void refusesAnAluminiumIndicatorOnASteelWeld() {
            IndicatorCheck check = Material.indicatorAcceptable("CARBON_STEEL", "ALUMINIUM");
            assertThat(check.acceptable()).isFalse();
        }

        @Test
        void letsASteelIndicatorServeOnANickelWeldButNotTheReverse() {
            assertThat(Material.indicatorAcceptable("INCONEL", "CARBON_STEEL").acceptable())
                    .isTrue();
            assertThat(Material.indicatorAcceptable("CARBON_STEEL", "INCONEL").acceptable())
                    .isFalse();
        }

        @Test
        void reportsBothGroupsSoARejectionCanBeArguedAbout() {
            IndicatorCheck check = Material.indicatorAcceptable("ALUMINIUM", "CARBON_STEEL");
            assertThat(check.weldGroup()).isEqualTo("01");
            assertThat(check.indicatorGroup()).isEqualTo("02");
        }
    }

    @Nested
    class IndicatorsFor {

        @Test
        void listsWhatWillServeOnASteelWeld() {
            List<String> serving = Material.indicatorsFor("CARBON_STEEL");
            assertThat(serving).contains("CARBON_STEEL", "STAINLESS");
            assertThat(serving).doesNotContain("ALUMINIUM");
        }

        @Test
        void listsMoreForANickelWeldWhichAcceptsSteelToo() {
            assertThat(Material.indicatorsFor("INCONEL").size())
                    .isGreaterThan(Material.indicatorsFor("ALUMINIUM").size());
        }

        @Test
        void keepsTheLightMaterialsToThemselves() {
            assertThat(Material.indicatorsFor("ALUMINIUM")).containsExactly("ALUMINIUM", "TITANIUM");
        }
    }

    @Nested
    class GoverningMaterial {

        @Test
        void takesTheHarderOfTwoWeldedTogether() {
            assertThat(Material.governingMaterial("CARBON_STEEL", "INCONEL").id())
                    .isEqualTo("INCONEL");
            assertThat(Material.governingMaterial("ALUMINIUM", "CARBON_STEEL").id())
                    .isEqualTo("CARBON_STEEL");
        }

        @Test
        void doesNotCareWhichWayRoundTheyAreGiven() {
            assertThat(Material.governingMaterial("INCONEL", "CARBON_STEEL").id())
                    .isEqualTo(Material.governingMaterial("CARBON_STEEL", "INCONEL")
                            .id());
        }

        @Test
        void takesEitherWhenTheyAreTheSame() {
            assertThat(Material.governingMaterial("CARBON_STEEL", "LOW_ALLOY").equivalence())
                    .isEqualTo(1);
        }
    }
}
