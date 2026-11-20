package io.github.rkaushikdev.kerfline.isotope;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AttenuationTest {

    @Nested
    class TheHalfValueLayerTable {

        @Test
        void listsTheMaterialsItCarries() {
            assertThat(Attenuation.shieldMaterials()).containsExactly("concrete", "lead", "steel");
        }

        @Test
        void quotesThePublishedMillimetreValues() {
            assertThat(Attenuation.halfValueLayerMm("IR192", "lead")).isCloseTo(4.8, digits(2));
            assertThat(Attenuation.halfValueLayerMm("IR192", "steel")).isCloseTo(12.7, digits(2));
            assertThat(Attenuation.halfValueLayerMm("CO60", "lead")).isCloseTo(12.5, digits(2));
            assertThat(Attenuation.halfValueLayerMm("SE75", "lead")).isCloseTo(2.0, digits(2));
        }

        @Test
        void getsHarderAsTheSourceGetsHarder() {
            List<Double> lead = List.of("TM170", "YB169", "SE75", "IR192", "CO60").stream()
                    .map(id -> Attenuation.halfValueLayerMm(id, "lead"))
                    .toList();
            List<Double> ascending = new ArrayList<>(lead);
            ascending.sort(Double::compare);
            assertThat(lead).isEqualTo(ascending);
        }

        @Test
        void convertsToInches() {
            assertThat(Attenuation.halfValueLayerIn("IR192", "steel")).isCloseTo(0.5, digits(2));
        }

        @Test
        void namesTheIsotopeAndTheMaterialWhenItHasNoRow() {
            assertThatThrownBy(() -> Attenuation.halfValueLayerMm("IR192", "water"))
                    .hasMessageContaining("no half value layer");
            assertThatThrownBy(() -> Attenuation.halfValueLayerMm("IR192", "water"))
                    .hasMessageContaining("in water");
        }

        @Test
        void refusesAnIsotopeOutsideTheCatalog() {
            assertThatThrownBy(() -> Attenuation.halfValueLayerMm("CS137", "lead"))
                    .hasMessageContaining("no isotope CS137");
        }
    }

    @Nested
    class TenthValueLayerMm {

        @Test
        void isTheHalfValueLayerTimesLogTwoOfTen() {
            double hvl = Attenuation.halfValueLayerMm("IR192", "lead");
            assertThat(Attenuation.tenthValueLayerMm("IR192", "lead")).isCloseTo(hvl * Num.log2(10), digits(9));
            assertThat(Attenuation.tenthValueLayerMm("IR192", "lead")).isCloseTo(15.945, digits(3));
        }

        @Test
        void cutsTheBeamToATenth() {
            double tvl = Attenuation.tenthValueLayerMm("CO60", "lead");
            assertThat(Attenuation.transmission("CO60", "lead", tvl)).isCloseTo(0.1, digits(9));
        }
    }

    @Nested
    class Transmission {

        @Test
        void passesEverythingThroughNoShield() {
            assertThat(Attenuation.transmission("IR192", "lead", 0)).isEqualTo(1);
        }

        @Test
        void halvesThroughOneHalfValueLayer() {
            assertThat(Attenuation.transmission("IR192", "lead", 4.8)).isCloseTo(0.5, digits(9));
            assertThat(Attenuation.transmission("IR192", "lead", 9.6)).isCloseTo(0.25, digits(9));
        }

        @Test
        void fallsOffAsTheShieldThickens() {
            double thin = Attenuation.transmission("IR192", "steel", 10);
            double thick = Attenuation.transmission("IR192", "steel", 40);
            assertThat(thick).isLessThan(thin);
        }

        @Test
        void refusesANegativeThickness() {
            assertThatThrownBy(() -> Attenuation.transmission("IR192", "lead", -1))
                    .hasMessageContaining("must not be negative");
            assertThatThrownBy(() -> Attenuation.transmission("IR192", "lead", Double.NaN))
                    .hasMessageContaining("finite number");
        }
    }

    @Nested
    class ShieldForFactor {

        @Test
        void needsOneHalfValueLayerToHalveTheBeam() {
            assertThat(Attenuation.shieldForFactor("IR192", "lead", 2)).isCloseTo(4.8, digits(9));
        }

        @Test
        void needsNoneToLeaveTheBeamAlone() {
            assertThat(Attenuation.shieldForFactor("IR192", "lead", 1)).isCloseTo(0, digits(12));
        }

        @Test
        void invertsTransmission() {
            double mm = Attenuation.shieldForFactor("CO60", "concrete", 100);
            assertThat(Attenuation.transmission("CO60", "concrete", mm)).isCloseTo(0.01, digits(9));
        }

        @Test
        void refusesAFactorBelowOne() {
            assertThatThrownBy(() -> Attenuation.shieldForFactor("IR192", "lead", 0.5))
                    .hasMessageContaining("below one is not a shield");
        }
    }

    @Nested
    class ShieldForReading {

        @Test
        void takesASurveyReadingDownToATarget() {
            double mm = Attenuation.shieldForReading("IR192", "lead", 400, 2);
            assertThat(Attenuation.transmission("IR192", "lead", mm) * 400).isCloseTo(2, digits(9));
        }

        @Test
        void needsNoShieldWhenTheReadingIsAlreadyAtTheTarget() {
            assertThat(Attenuation.shieldForReading("IR192", "lead", 2, 2)).isCloseTo(0, digits(12));
        }

        @Test
        void refusesATargetAboveTheUnshieldedReading() {
            assertThatThrownBy(() -> Attenuation.shieldForReading("IR192", "lead", 2, 400))
                    .hasMessageContaining("target reading is above");
        }

        @Test
        void refusesAReadingAtOrBelowZero() {
            assertThatThrownBy(() -> Attenuation.shieldForReading("IR192", "lead", 0, 0))
                    .hasMessageContaining("above zero");
            assertThatThrownBy(() -> Attenuation.shieldForReading("IR192", "lead", 400, -1))
                    .hasMessageContaining("above zero");
        }
    }

    @Nested
    class WallInHalfValueLayers {

        @Test
        void countsTheWallInHalfValueLayersOfSteel() {
            assertThat(Attenuation.wallInHalfValueLayers("IR192", 0.5)).isCloseTo(1, digits(1));
            assertThat(Attenuation.wallInHalfValueLayers("IR192", 1.0)).isCloseTo(2, digits(1));
        }

        @Test
        void makesAThickWallLookWorseOnIridiumThanOnCobalt() {
            assertThat(Attenuation.wallInHalfValueLayers("IR192", 2))
                    .isGreaterThan(Attenuation.wallInHalfValueLayers("CO60", 2));
        }

        @Test
        void refusesAWallAtOrBelowZero() {
            assertThatThrownBy(() -> Attenuation.wallInHalfValueLayers("IR192", 0))
                    .hasMessageContaining("above zero");
        }
    }
}
