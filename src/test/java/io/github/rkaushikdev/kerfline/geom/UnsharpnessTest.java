package io.github.rkaushikdev.kerfline.geom;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UnsharpnessTest {

    @Nested
    class UnsharpnessLimit {

        @Test
        void readsTheFourBands() {
            assertThat(Unsharpness.unsharpnessLimit(0.5)).isEqualTo(0.02);
            assertThat(Unsharpness.unsharpnessLimit(2.5)).isEqualTo(0.03);
            assertThat(Unsharpness.unsharpnessLimit(3.5)).isEqualTo(0.04);
            assertThat(Unsharpness.unsharpnessLimit(6)).isEqualTo(0.07);
        }

        @Test
        void putsEachBoundaryInTheBandBelowIt() {
            assertThat(Unsharpness.unsharpnessLimit(2)).isEqualTo(0.02);
            assertThat(Unsharpness.unsharpnessLimit(2.0001)).isEqualTo(0.03);
            assertThat(Unsharpness.unsharpnessLimit(3)).isEqualTo(0.03);
            assertThat(Unsharpness.unsharpnessLimit(4)).isEqualTo(0.04);
            assertThat(Unsharpness.unsharpnessLimit(4.0001)).isEqualTo(0.07);
        }

        @Test
        void refusesAThicknessAtOrBelowZero() {
            assertThatThrownBy(() -> Unsharpness.unsharpnessLimit(0)).hasMessageContaining("greater than 0");
            assertThatThrownBy(() -> Unsharpness.unsharpnessLimit(-1)).hasMessageContaining("greater than 0");
            assertThatThrownBy(() -> Unsharpness.unsharpnessLimit(Double.NaN)).hasMessageContaining("finite number");
        }

        @Test
        void publishesTheBandsForTheTechniqueSheet() {
            assertThat(Unsharpness.unsharpnessBands())
                    .containsExactly(
                            new UnsharpnessBand(0, 2, 0.02),
                            new UnsharpnessBand(2, 3, 0.03),
                            new UnsharpnessBand(3, 4, 0.04),
                            new UnsharpnessBand(4, Double.POSITIVE_INFINITY, 0.07));
        }
    }

    @Nested
    class GeometricUnsharpness {

        @Test
        void isSourceSizeTimesStandoffOverDistance() {
            assertThat(Unsharpness.geometricUnsharpness(0.12, 0.5, 12)).isCloseTo(0.005, digits(6));
        }

        @Test
        void isZeroWhenTheObjectSitsOnTheFilm() {
            assertThat(Unsharpness.geometricUnsharpness(0.12, 0, 12)).isEqualTo(0);
        }

        @Test
        void halvesWhenTheDistanceDoubles() {
            double near = Unsharpness.geometricUnsharpness(0.12, 1, 12);
            double far = Unsharpness.geometricUnsharpness(0.12, 1, 24);
            assertThat(far).isCloseTo(near / 2, digits(9));
        }

        @Test
        void refusesASourceSizeOrDistanceAtZero() {
            assertThatThrownBy(() -> Unsharpness.geometricUnsharpness(0, 1, 12)).hasMessageContaining("source size");
            assertThatThrownBy(() -> Unsharpness.geometricUnsharpness(0.12, 1, 0))
                    .hasMessageContaining("source to object distance");
        }

        @Test
        void refusesANegativeStandoffButAllowsZero() {
            assertThatThrownBy(() -> Unsharpness.geometricUnsharpness(0.12, -1, 12))
                    .hasMessageContaining("not be below 0");
        }
    }

    @Nested
    class MinimumSourceToObject {

        @Test
        void isTheDistanceThatLandsExactlyOnTheLimit() {
            double minimum = Unsharpness.minimumSourceToObject(0.12, 0.5, 0.02);
            assertThat(minimum).isCloseTo(3, digits(9));
            double ug = Unsharpness.geometricUnsharpness(0.12, 0.5, minimum);
            assertThat(ug).isCloseTo(0.02, digits(9));
        }

        @Test
        void isZeroWhenTheObjectSitsOnTheFilm() {
            assertThat(Unsharpness.minimumSourceToObject(0.12, 0, 0.02)).isEqualTo(0);
        }

        @Test
        void refusesALimitAtZero() {
            assertThatThrownBy(() -> Unsharpness.minimumSourceToObject(0.12, 1, 0))
                    .hasMessageContaining("unsharpness limit");
        }
    }

    @Nested
    class CheckUnsharpness {

        private final UnsharpnessSetup setup = UnsharpnessSetup.builder()
                .sourceSizeIn(0.12)
                .objectToFilmIn(0.5)
                .sourceToObjectIn(12)
                .materialThicknessIn(0.5)
                .build();

        @Test
        void passesASetupWithRoomToSpareAndReportsTheMargin() {
            UnsharpnessCheck result = Unsharpness.checkUnsharpness(setup);
            assertThat(result.pass()).isTrue();
            assertThat(result.unsharpnessIn()).isEqualTo(0.005);
            assertThat(result.limitIn()).isEqualTo(0.02);
            assertThat(result.marginIn()).isEqualTo(0.015);
            assertThat(result.shortfallIn()).isEqualTo(0);
        }

        @Test
        void failsASetupThatIsCrankedInTooCloseAndSaysByHowMuch() {
            UnsharpnessCheck result = Unsharpness.checkUnsharpness(
                    setup.toBuilder().sourceToObjectIn(2).build());
            assertThat(result.pass()).isFalse();
            assertThat(result.unsharpnessIn()).isEqualTo(0.03);
            assertThat(result.marginIn()).isLessThan(0);
            assertThat(result.minimumSourceToObjectIn()).isEqualTo(3);
            assertThat(result.shortfallIn()).isEqualTo(1);
        }

        @Test
        void passesExactlyOnTheLimit() {
            UnsharpnessCheck result = Unsharpness.checkUnsharpness(
                    setup.toBuilder().sourceToObjectIn(3).build());
            assertThat(result.unsharpnessIn()).isEqualTo(0.02);
            assertThat(result.pass()).isTrue();
            assertThat(result.marginIn()).isEqualTo(0);
        }

        @Test
        void movesTheVerdictWhenOnlyTheMaterialThicknessChanges() {
            UnsharpnessCheck thin = Unsharpness.checkUnsharpness(
                    setup.toBuilder().sourceToObjectIn(2.5).build());
            UnsharpnessCheck thick = Unsharpness.checkUnsharpness(setup.toBuilder()
                    .sourceToObjectIn(2.5)
                    .materialThicknessIn(2.5)
                    .build());
            assertThat(thin.pass()).isFalse();
            assertThat(thick.pass()).isTrue();
            assertThat(thin.unsharpnessIn()).isEqualTo(thick.unsharpnessIn());
        }
    }
}
