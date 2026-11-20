package io.github.rkaushikdev.kerfline.indication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TypesTest {

    @Nested
    class TheTaxonomy {

        @Test
        void carriesTheElevenTypesTheShopReports() {
            assertThat(Types.indicationTypeIds()).hasSize(11);
            assertThat(Types.indicationTypeIds()).contains("CRACK");
            assertThat(Types.indicationTypeIds()).contains("HOLLOW_BEAD");
        }

        @Test
        void givesEveryTypeAMeasureAndASurface() {
            for (IndicationType type : Types.indicationTypes()) {
                assertThat(type.measure()).isIn(MeasuredBy.LENGTH, MeasuredBy.DIAMETER);
                assertThat(type.surface()).isIn(Surface.EITHER, Surface.ROOT, Surface.INTERNAL);
                assertThat(type.label()).isNotBlank();
            }
        }

        @Test
        void findsATypeHoweverItWasWritten() {
            assertThat(Types.indicationType("crack").id()).isEqualTo("CRACK");
            assertThat(Types.indicationType("hollow bead").id()).isEqualTo("HOLLOW_BEAD");
            assertThat(Types.indicationType("hollow-bead").id()).isEqualTo("HOLLOW_BEAD");
            assertThat(Types.indicationType(" POROSITY ").id()).isEqualTo("POROSITY");
        }

        @Test
        void namesWhatItKnowsWhenItCannotFindOne() {
            assertThatThrownBy(() -> Types.indicationType("LAMINATION"))
                    .hasMessageContaining("no indication type LAMINATION");
            assertThatThrownBy(() -> Types.indicationType(null)).hasMessageContaining("named by a string");
        }

        @Test
        void answersIsIndicationTypeWithoutThrowing() {
            assertThat(Types.isIndicationType("crack")).isTrue();
            assertThat(Types.isIndicationType("lamination")).isFalse();
        }

        @Test
        void freezesARecord() {
            List<IndicationType> types = Types.indicationTypes();
            assertThatThrownBy(() -> types.set(0, IndicationType.SLAG))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(Types.indicationType("CRACK").planar()).isTrue();
        }
    }

    @Nested
    class ThePlanarFlag {

        @Test
        void marksTheThreeThatNoCodeTolerates() {
            assertThat(Types.isPlanar("CRACK")).isTrue();
            assertThat(Types.isPlanar("IF")).isTrue();
            assertThat(Types.isPlanar("IP")).isTrue();
        }

        @Test
        void leavesTheVolumetricOnesAlone() {
            assertThat(Types.isPlanar("POROSITY")).isFalse();
            assertThat(Types.isPlanar("SLAG")).isFalse();
            assertThat(Types.isPlanar("UNDERCUT")).isFalse();
        }

        @Test
        void listsExactlyThePlanarTypes() {
            assertThat(Types.planarTypes().stream().map(IndicationType::id)).containsExactly("CRACK", "IF", "IP");
        }
    }

    @Nested
    class MeasuredByLookup {

        @Test
        void judgesRoundThingsOnTheirDiameter() {
            assertThat(Types.measuredBy("POROSITY")).isEqualTo(MeasuredBy.DIAMETER);
            assertThat(Types.measuredBy("TUNGSTEN")).isEqualTo(MeasuredBy.DIAMETER);
            assertThat(Types.measuredBy("CLUSTER")).isEqualTo(MeasuredBy.DIAMETER);
        }

        @Test
        void judgesEverythingElseOnItsLength() {
            assertThat(Types.measuredBy("SLAG")).isEqualTo(MeasuredBy.LENGTH);
            assertThat(Types.measuredBy("CRACK")).isEqualTo(MeasuredBy.LENGTH);
            assertThat(Types.measuredBy("UNDERCUT")).isEqualTo(MeasuredBy.LENGTH);
        }

        @Test
        void givesEveryPlanarTypeALength() {
            for (IndicationType type : Types.planarTypes()) {
                assertThat(type.measure()).isEqualTo(MeasuredBy.LENGTH);
            }
        }
    }

    @Nested
    class TheRootOnlyTypes {

        @Test
        void listsTheDefectsThatCanOnlyBeInTheRoot() {
            List<String> ids =
                    Types.rootOnlyTypes().stream().map(IndicationType::id).toList();
            assertThat(ids).contains("IP", "HOLLOW_BEAD", "BURN_THROUGH", "ROOT_CONCAVITY");
        }

        @Test
        void doesNotPutACrackInTheRootOnlyList() {
            assertThat(Types.isRootOnly("CRACK")).isFalse();
            assertThat(Types.isRootOnly("POROSITY")).isFalse();
        }

        @Test
        void answersForASingleType() {
            assertThat(Types.isRootOnly("IP")).isTrue();
            assertThat(Types.isRootOnly("SLAG")).isFalse();
        }
    }
}
