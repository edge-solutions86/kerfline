package io.github.rkaushikdev.kerfline.isotope;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CatalogTest {

    @Nested
    class TheCatalog {

        @Test
        void carriesTheFiveLicensedGammaSources() {
            assertThat(Catalog.isotopeIds()).containsExactly("IR192", "SE75", "CO60", "YB169", "TM170");
        }

        @Test
        void quotesTheHalfLivesOffTheCertificates() {
            assertThat(Catalog.isotope("IR192").halfLifeDays()).isCloseTo(73.83, digits(2));
            assertThat(Catalog.isotope("SE75").halfLifeDays()).isCloseTo(119.78, digits(2));
            assertThat(Catalog.isotope("CO60").halfLifeDays()).isCloseTo(1925.3, digits(1));
            assertThat(Catalog.isotope("YB169").halfLifeDays()).isCloseTo(32.02, digits(2));
            assertThat(Catalog.isotope("TM170").halfLifeDays()).isCloseTo(128.6, digits(1));
        }

        @Test
        void quotesTheGammaRayConstantsInRPerHourAtAMetrePerCurie() {
            assertThat(Catalog.isotope("IR192").gammaFactor()).isCloseTo(0.48, digits(3));
            assertThat(Catalog.isotope("CO60").gammaFactor()).isCloseTo(1.32, digits(3));
            assertThat(Catalog.isotope("SE75").gammaFactor()).isCloseTo(0.203, digits(3));
        }

        @Test
        void carriesTheTwoCobaltLinesAndNothingElse() {
            assertThat(Catalog.isotope("CO60").energiesMeV()).containsExactly(1.173, 1.332);
        }

        @Test
        void ordersEveryEnergyListAscending() {
            for (Isotope source : Catalog.isotopes()) {
                List<Double> sorted = new ArrayList<>(source.energiesMeV());
                sorted.sort(Double::compare);
                assertThat(source.energiesMeV()).isEqualTo(sorted);
            }
        }

        @Test
        void keepsTheMeanEnergyInsideTheQuotedLines() {
            for (Isotope source : Catalog.isotopes()) {
                List<Double> lines = source.energiesMeV();
                assertThat(source.meanEnergyMeV()).isGreaterThanOrEqualTo(lines.get(0) * 0.9);
                assertThat(source.meanEnergyMeV()).isLessThanOrEqualTo(lines.get(lines.size() - 1) * 1.1);
            }
        }

        @Test
        void givesEverySourceASteelBandThatGoesUpwards() {
            for (Isotope source : Catalog.isotopes()) {
                assertThat(source.steelRangeIn().max())
                        .isGreaterThan(source.steelRangeIn().min());
                assertThat(source.steelRangeIn().min()).isGreaterThan(0);
            }
        }

        @Test
        void freezesARecordSoATechniqueCannotEditTheCatalog() {
            Isotope source = Catalog.isotope("IR192");
            assertThatThrownBy(() -> source.energiesMeV().add(9.0)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> Catalog.isotopes().clear()).isInstanceOf(UnsupportedOperationException.class);
            assertThat(Catalog.isotope("IR192").energiesMeV()).hasSize(5);
        }
    }

    @Nested
    class Lookup {

        @Test
        void findsASourceByItsId() {
            assertThat(Catalog.isotope("IR192").label()).isEqualTo("Ir-192");
        }

        @Test
        void findsASourceByTheLabelWrittenOnTheForm() {
            assertThat(Catalog.isotope("Ir-192").id()).isEqualTo("IR192");
            assertThat(Catalog.isotope("ir 192").id()).isEqualTo("IR192");
            assertThat(Catalog.isotope("ir_192").id()).isEqualTo("IR192");
        }

        @Test
        void namesWhatItKnowsWhenItCannotFindOne() {
            assertThatThrownBy(() -> Catalog.isotope("CS137")).hasMessageContaining("no isotope CS137");
            NotFoundException err = catchThrowableOfType(NotFoundException.class, () -> Catalog.isotope("CS137"));
            assertThat(err.details().get("known"))
                    .asInstanceOf(InstanceOfAssertFactories.LIST)
                    .contains("IR192");
        }

        @Test
        void refusesANonString() {
            assertThatThrownBy(() -> Catalog.isotope(null)).hasMessageContaining("named by a string");
        }

        @Test
        void answersHasIsotopeWithoutThrowing() {
            assertThat(Catalog.hasIsotope("co-60")).isTrue();
            assertThat(Catalog.hasIsotope("CS137")).isFalse();
            assertThat(Catalog.hasIsotope(null)).isFalse();
        }

        @Test
        void normalisesTheWayThePaperworkIsWritten() {
            assertThat(Catalog.normaliseId("ir-192")).isEqualTo("IR192");
            assertThat(Catalog.normaliseId("Se 75")).isEqualTo("SE75");
        }
    }

    @Nested
    class TheShopSteelBands {

        @Test
        void putsAQuarterInchWallInsideIridiumAndOutsideCobalt() {
            assertThat(Catalog.withinSteelRange("IR192", 0.25)).isTrue();
            assertThat(Catalog.withinSteelRange("CO60", 0.25)).isFalse();
        }

        @Test
        void includesBothEndsOfTheBand() {
            assertThat(Catalog.withinSteelRange("SE75", 0.2)).isTrue();
            assertThat(Catalog.withinSteelRange("SE75", 1.1)).isTrue();
            assertThat(Catalog.withinSteelRange("SE75", 1.1001)).isFalse();
        }

        @Test
        void refusesAThicknessThatIsNotANumber() {
            assertThatThrownBy(() -> Catalog.withinSteelRange("IR192", Double.NaN))
                    .hasMessageContaining("finite number");
        }

        @Test
        void listsTheSourcesThatCoverAWallHardestFirst() {
            assertThat(Catalog.sourcesForThickness(0.28).stream().map(Isotope::id))
                    .containsExactly("IR192", "SE75", "YB169");
        }

        @Test
        void putsCobaltAloneOnAThickWall() {
            assertThat(Catalog.sourcesForThickness(4).stream().map(Isotope::id)).containsExactly("CO60");
        }

        @Test
        void handsBackNothingWhenNoLicensedSourceCoversTheWall() {
            assertThat(Catalog.sourcesForThickness(12)).isEmpty();
        }
    }
}
