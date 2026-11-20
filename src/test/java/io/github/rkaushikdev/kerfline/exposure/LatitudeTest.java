package io.github.rkaushikdev.kerfline.exposure;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LatitudeTest {

    @Nested
    class LatitudeIn {

        @Test
        void givesAPositiveBand() {
            assertThat(Latitude.latitudeIn("IR192", "D7")).isGreaterThan(0);
        }

        @Test
        void givesAFineFilmLessLatitudeThanAFastOne() {
            double fine = Latitude.latitudeIn("IR192", "D4");
            double fast = Latitude.latitudeIn("IR192", "D8");
            assertThat(fine).isLessThan(fast);
        }

        @Test
        void givesAHarderSourceMoreLatitude() {
            double iridium = Latitude.latitudeIn("IR192", "D7");
            double cobalt = Latitude.latitudeIn("CO60", "D7");
            assertThat(cobalt).isGreaterThan(iridium);
        }

        @Test
        void growsWithTheDensitySpanAllowed() {
            double tight = Latitude.latitudeIn("IR192", "D7", 1);
            double wide = Latitude.latitudeIn("IR192", "D7", 2);
            assertThat(wide).isCloseTo(tight * 2, digits(3));
        }

        @Test
        void refusesASpanAtZero() {
            assertThatThrownBy(() -> Latitude.latitudeIn("IR192", "D7", 0)).hasMessageContaining("density span");
        }

        @Test
        void refusesASourceWithNoChart() {
            assertThatThrownBy(() -> Latitude.latitudeIn("YB169", "D7")).hasMessageContaining("no exposure chart");
        }
    }

    @Nested
    class CoveredBandOfAShot {

        @Test
        void bracketsTheThicknessItWasAimedAt() {
            CoveredBand band = Latitude.coveredBand("IR192", "D7", 1);
            assertThat(band.minimumIn()).isLessThan(1);
            assertThat(band.maximumIn()).isGreaterThan(1);
        }

        @Test
        void isAsWideAsTheLatitude() {
            CoveredBand band = Latitude.coveredBand("IR192", "D7", 1);
            assertThat(band.maximumIn() - band.minimumIn()).isCloseTo(band.latitudeIn(), digits(3));
        }

        @Test
        void neverGoesBelowNothingOnAThinSection() {
            CoveredBand band = Latitude.coveredBand("IR192", "D8", 0.3);
            assertThat(band.minimumIn()).isGreaterThanOrEqualTo(0);
        }

        @Test
        void refusesAThicknessAtZero() {
            assertThatThrownBy(() -> Latitude.coveredBand("IR192", "D7", 0)).hasMessageContaining("thickness");
        }
    }

    @Nested
    class CoversRange {

        @Test
        void coversASectionThatHardlyVaries() {
            RangeCover check = Latitude.coversRange("IR192", "D7", 0.5, 0.52);
            assertThat(check.covers()).isTrue();
            assertThat(check.shortIn()).isEqualTo(0);
        }

        @Test
        void doesNotCoverASectionThatVariesALotAndSaysByHowMuch() {
            RangeCover check = Latitude.coversRange("IR192", "D7", 0.5, 2);
            assertThat(check.covers()).isFalse();
            assertThat(check.shortIn()).isGreaterThan(0);
            assertThat(check.neededIn()).isCloseTo(check.availableIn() + check.shortIn(), digits(3));
        }

        @Test
        void coversMoreOnAFasterFilm() {
            RangeCover fine = Latitude.coversRange("IR192", "D4", 0.5, 1);
            RangeCover fast = Latitude.coversRange("IR192", "D8", 0.5, 1);
            assertThat(fast.availableIn()).isGreaterThan(fine.availableIn());
        }

        @Test
        void refusesARangeThatRunsBackwards() {
            assertThatThrownBy(() -> Latitude.coversRange("IR192", "D7", 2, 1))
                    .hasMessageContaining("thinner than the thinnest");
        }
    }

    @Nested
    class DoubleLoadBand {

        @Test
        void coversMoreThanEitherFilmAlone() {
            DoubleLoad pair = Latitude.doubleLoadBand("IR192", "D5", "D8");
            assertThat(pair.combinedIn()).isGreaterThan(pair.slowLatitudeIn());
            assertThat(pair.combinedIn()).isGreaterThan(pair.fastLatitudeIn());
        }

        @Test
        void offsetsTheTwoByTheirSpeedRatio() {
            DoubleLoad near = Latitude.doubleLoadBand("IR192", "D7", "D8");
            DoubleLoad far = Latitude.doubleLoadBand("IR192", "D4", "D8");
            assertThat(far.offsetIn()).isGreaterThan(near.offsetIn());
        }

        @Test
        void namesBothFilms() {
            DoubleLoad pair = Latitude.doubleLoadBand("IR192", "D5", "D8");
            assertThat(pair.slow()).isEqualTo("D5");
            assertThat(pair.fast()).isEqualTo("D8");
        }

        @Test
        void refusesAPairWhereTheSecondIsNotFaster() {
            assertThatThrownBy(() -> Latitude.doubleLoadBand("IR192", "D8", "D5"))
                    .hasMessageContaining("has to be faster");
            assertThatThrownBy(() -> Latitude.doubleLoadBand("IR192", "D7", "D7"))
                    .hasMessageContaining("has to be faster");
        }
    }

    @Nested
    class FilmsCovering {

        @Test
        void listsTheFilmsThatReachFinestFirst() {
            List<FilmLatitude> films = Latitude.filmsCovering("IR192", 0.5, 0.55);
            assertThat(films).isNotEmpty();
            List<Double> latitudes =
                    films.stream().map(FilmLatitude::latitudeIn).toList();
            List<Double> sorted = new ArrayList<>(latitudes);
            sorted.sort(Double::compare);
            assertThat(latitudes).isEqualTo(sorted);
        }

        @Test
        void dropsTheFineFilmsAsTheSectionVariesMore() {
            List<FilmLatitude> narrow = Latitude.filmsCovering("IR192", 0.5, 0.55);
            List<FilmLatitude> wide = Latitude.filmsCovering("IR192", 0.5, 0.8);
            assertThat(wide.size()).isLessThanOrEqualTo(narrow.size());
        }

        @Test
        void findsNothingForASectionThatVariesFarTooMuch() {
            assertThat(Latitude.filmsCovering("IR192", 0.3, 2.5)).isEmpty();
        }
    }
}
