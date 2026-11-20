package io.github.rkaushikdev.kerfline.exposure;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AimTest {

    @Nested
    class FilmGradient {

        @Test
        void isSteeperOnAFinerFilm() {
            assertThat(Aim.filmGradient("D4")).isGreaterThan(Aim.filmGradient("D7"));
            assertThat(Aim.filmGradient("D7")).isGreaterThan(Aim.filmGradient("D8"));
        }

        @Test
        void carriesAGradientForEveryQualifiedFilm() {
            for (String id : List.of("D2", "D3", "D4", "D5", "D7", "D8")) {
                assertThat(Aim.filmGradient(id)).isGreaterThan(1);
            }
        }

        @Test
        void refusesAFilmOffTheList() {
            assertThatThrownBy(() -> Aim.filmGradient("AA400")).hasMessageContaining("no film AA400");
        }
    }

    @Nested
    class ExposureRatioForDensity {

        @Test
        void isOneWhenTheDensityDoesNotMove() {
            assertThat(Aim.exposureRatioForDensity("D7", 2, 2)).isCloseTo(1, digits(12));
        }

        @Test
        void needsMoreExposureForMoreDensity() {
            assertThat(Aim.exposureRatioForDensity("D7", 2, 2.5)).isGreaterThan(1);
        }

        @Test
        void needsLessExposureForLessDensity() {
            assertThat(Aim.exposureRatioForDensity("D7", 2.5, 2)).isLessThan(1);
        }

        @Test
        void inverts() {
            double up = Aim.exposureRatioForDensity("D7", 2, 2.5);
            double down = Aim.exposureRatioForDensity("D7", 2.5, 2);
            assertThat(up * down).isCloseTo(1, digits(12));
        }

        @Test
        void costsAFineFilmLessThanAFastOneForTheSameHalfPoint() {
            double fine = Aim.exposureRatioForDensity("D4", 2, 2.5);
            double fast = Aim.exposureRatioForDensity("D8", 2, 2.5);
            assertThat(fine).isLessThan(fast);
        }

        @Test
        void multipliesTheExposureByTenAcrossAWholeGradient() {
            double gradient = Aim.filmGradient("D7");
            assertThat(Aim.exposureRatioForDensity("D7", 2, 2 + gradient)).isCloseTo(10, digits(9));
        }

        @Test
        void refusesADensityAtOrBelowZero() {
            assertThatThrownBy(() -> Aim.exposureRatioForDensity("D7", 0, 2)).hasMessageContaining("greater than zero");
            assertThatThrownBy(() -> Aim.exposureRatioForDensity("D7", 2, -1))
                    .hasMessageContaining("greater than zero");
        }
    }

    @Nested
    class DensityForExposureRatio {

        @Test
        void leavesTheDensityAloneAtARatioOfOne() {
            assertThat(Aim.densityForExposureRatio("D7", 2, 1)).isEqualTo(2);
        }

        @Test
        void invertsExposureRatioForDensity() {
            double ratio = Aim.exposureRatioForDensity("D5", 2, 2.8);
            assertThat(Aim.densityForExposureRatio("D5", 2, ratio)).isCloseTo(2.8, digits(3));
        }

        @Test
        void dropsTheDensityWhenTheExposureIsCut() {
            assertThat(Aim.densityForExposureRatio("D7", 2.5, 0.5)).isLessThan(2.5);
        }

        @Test
        void refusesARatioAtOrBelowZero() {
            assertThatThrownBy(() -> Aim.densityForExposureRatio("D7", 2, 0)).hasMessageContaining("greater than zero");
        }
    }

    @Nested
    class AimAtDensity {

        @Test
        void liftsAChartExposureOntoAHigherWantedDensity() {
            double out = Aim.aimAtDensity(100, "D7", 2, 2.5);
            assertThat(out).isGreaterThan(100);
            assertThat(out).isCloseTo(100 * Aim.exposureRatioForDensity("D7", 2, 2.5), digits(9));
        }

        @Test
        void leavesItAloneWhenTheJobAsksForTheChartDensity() {
            assertThat(Aim.aimAtDensity(100, "D7", 2, 2)).isCloseTo(100, digits(9));
        }

        @Test
        void refusesANegativeExposure() {
            assertThatThrownBy(() -> Aim.aimAtDensity(-1, "D7", 2, 2)).hasMessageContaining("not be negative");
        }
    }

    @Nested
    class DensityIsReadable {

        @Test
        void acceptsTheSpanTheShopReadsFilmIn() {
            assertThat(Aim.readableDensityRange()).isEqualTo(new DensityRange(1.5, 4));
            assertThat(Aim.densityIsReadable(1.5)).isTrue();
            assertThat(Aim.densityIsReadable(2.7)).isTrue();
            assertThat(Aim.densityIsReadable(4)).isTrue();
        }

        @Test
        void rejectsFilmThatIsTooLightOrTooDarkToRead() {
            assertThat(Aim.densityIsReadable(1.4)).isFalse();
            assertThat(Aim.densityIsReadable(4.1)).isFalse();
            assertThat(Aim.densityIsReadable(Double.NaN)).isFalse();
        }
    }

    @Nested
    class ReshootFactor {

        @Test
        void asksForMoreTimeAfterALightFilm() {
            assertThat(Aim.reshootFactor("D7", 1.6, 2.2)).isGreaterThan(1);
        }

        @Test
        void asksForLessTimeAfterADarkFilm() {
            assertThat(Aim.reshootFactor("D7", 3.4, 2.5)).isLessThan(1);
        }

        @Test
        void asksForTheSameTimeWhenTheFilmLandedOnTarget() {
            assertThat(Aim.reshootFactor("D7", 2.5, 2.5)).isEqualTo(1);
        }
    }
}
