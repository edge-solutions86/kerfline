package io.github.rkaushikdev.kerfline.film;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ArtifactTest {

    @Nested
    class TheCatalog {

        @Test
        void carriesTheEightArtifactsTheShopSees() {
            assertThat(Artifact.artifactIds()).hasSize(8);
            assertThat(Artifact.artifactIds()).contains("CRIMP");
            assertThat(Artifact.artifactIds()).contains("STATIC");
        }

        @Test
        void givesEveryArtifactAnAppearanceACauseAndATest() {
            for (FilmArtifact record : Artifact.artifacts()) {
                assertThat(record.appearance().length()).isGreaterThan(10);
                assertThat(record.cause().length()).isGreaterThan(10);
                assertThat(record.test().length()).isGreaterThan(10);
                assertThat(record.mimics()).isNotNull();
            }
        }

        @Test
        void findsOneHoweverItWasWritten() {
            assertThat(Artifact.artifact("crimp").id()).isEqualTo("CRIMP");
            assertThat(Artifact.artifact(" Crimp ").id()).isEqualTo("CRIMP");
        }

        @Test
        void namesWhatItCarriesWhenItCannotFindOne() {
            assertThatThrownBy(() -> Artifact.artifact("COMET")).hasMessageContaining("no artifact COMET");
            assertThatThrownBy(() -> Artifact.artifact(null)).hasMessageContaining("named by a string");
        }

        @Test
        void answersIsArtifactWithoutThrowing() {
            assertThat(Artifact.isArtifact("FOG")).isTrue();
            assertThat(Artifact.isArtifact("COMET")).isFalse();
            assertThat(Artifact.isArtifact(null)).isFalse();
        }

        @Test
        void freezesARecordSoAReaderCannotEditTheCatalog() {
            assertThatThrownBy(() -> Artifact.artifact("CRIMP").mimics().add("porosity"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> Artifact.artifacts().clear()).isInstanceOf(UnsupportedOperationException.class);
            assertThat(Artifact.artifact("CRIMP").mimics()).containsExactly("crack", "incomplete fusion");
        }
    }

    @Nested
    class ArtifactsMimicking {

        @Test
        void findsTheTwoThingsThatLookLikeACrack() {
            List<String> ids = Artifact.artifactsMimicking("crack").stream()
                    .map(FilmArtifact::id)
                    .toList();
            assertThat(ids).contains("CRIMP");
            assertThat(ids).contains("STATIC");
        }

        @Test
        void findsWhatLooksLikePorosity() {
            List<String> ids = Artifact.artifactsMimicking("porosity").stream()
                    .map(FilmArtifact::id)
                    .toList();
            assertThat(ids).contains("WATER");
            assertThat(ids).contains("PRESSURE");
        }

        @Test
        void isNotCaseSensitive() {
            assertThat(Artifact.artifactsMimicking("CRACK"))
                    .hasSize(Artifact.artifactsMimicking("crack").size());
        }

        @Test
        void findsNothingForAnIndicationNothingMimics() {
            assertThat(Artifact.artifactsMimicking("burn through")).isEmpty();
        }

        @Test
        void refusesATypeThatIsNotAString() {
            assertThatThrownBy(() -> Artifact.artifactsMimicking(null)).hasMessageContaining("must be a string");
        }
    }

    @Nested
    class MimickedTypes {

        @Test
        void listsEveryIndicationTypeSomethingInTheCatalogLooksLike() {
            List<String> types = Artifact.mimickedTypes();
            assertThat(types).contains("crack");
            assertThat(types).contains("porosity");
            assertThat(types).isSorted();
        }

        @Test
        void leavesOutFogWhichLooksLikeNothingInAWeld() {
            assertThat(Artifact.artifact("FOG").mimics()).isEmpty();
        }
    }

    @Nested
    class RepeatsAcrossFilms {

        @Test
        void isTrueOnlyForTheCassetteFault() {
            assertThat(Artifact.repeatsAcrossFilms("DELAM")).isTrue();
            assertThat(Artifact.repeatsAcrossFilms("CRIMP")).isFalse();
            assertThat(Artifact.repeatsAcrossFilms("STATIC")).isFalse();
        }

        @Test
        void refusesAnArtifactItDoesNotCarry() {
            assertThatThrownBy(() -> Artifact.repeatsAcrossFilms("COMET")).hasMessageContaining("no artifact COMET");
        }
    }
}
