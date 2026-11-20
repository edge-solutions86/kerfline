package io.github.rkaushikdev.kerfline.exposure;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FilmClassTest {

    @Nested
    class TheQualifiedFilmList {

        @Test
        void carriesTheSixFilmsTheProcedureNames() {
            assertThat(FilmClass.filmIds()).containsExactly("D2", "D3", "D4", "D5", "D7", "D8");
        }

        @Test
        void getsFasterAsTheGrainGetsCoarser() {
            List<Double> speeds = FilmClass.films().stream().map(Film::speed).toList();
            List<Double> sorted = new ArrayList<>(speeds);
            sorted.sort(Double::compare);
            assertThat(speeds).isEqualTo(sorted);
        }

        @Test
        void takesTheMediumFilmAsTheUnitOfSpeed() {
            assertThat(FilmClass.filmSpeed("D7")).isEqualTo(1);
        }

        @Test
        void doublesFromTheMediumFilmToTheFastOne() {
            assertThat(FilmClass.filmSpeed("D8")).isCloseTo(FilmClass.filmSpeed("D7") * 2, digits(9));
            assertThat(FilmClass.filmSpeed("D5")).isCloseTo(FilmClass.filmSpeed("D7") / 2, digits(9));
        }

        @Test
        void findsAFilmHoweverTheTechnicianWroteIt() {
            assertThat(FilmClass.film("d5").id()).isEqualTo("D5");
            assertThat(FilmClass.film(" D5 ").id()).isEqualTo("D5");
        }

        @Test
        void namesTheQualifiedListWhenItCannotFindOne() {
            assertThatThrownBy(() -> FilmClass.film("AA400")).hasMessageContaining("no film AA400");
            assertThatThrownBy(() -> FilmClass.film(null)).hasMessageContaining("named by a string");
        }

        @Test
        void freezesARecord() {
            assertThatThrownBy(() -> FilmClass.films().add(FilmClass.film("D7")))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(FilmClass.film("D7").speed()).isEqualTo(1);
        }
    }

    @Nested
    class TheClasses {

        @Test
        void runsFromSpecialDownToThree() {
            assertThat(FilmClass.grades()).containsExactly("SPECIAL", "I", "II", "III");
        }

        @Test
        void placesEachFilmInItsClass() {
            assertThat(FilmClass.filmGrade("D2")).isEqualTo("SPECIAL");
            assertThat(FilmClass.filmGrade("D4")).isEqualTo("I");
            assertThat(FilmClass.filmGrade("D7")).isEqualTo("II");
            assertThat(FilmClass.filmGrade("D8")).isEqualTo("III");
        }

        @Test
        void letsAFinerFilmStandInForACoarserCall() {
            assertThat(FilmClass.meetsGrade("D4", "II")).isTrue();
            assertThat(FilmClass.meetsGrade("D2", "III")).isTrue();
        }

        @Test
        void doesNotLetACoarserFilmAnswerAFinerCall() {
            assertThat(FilmClass.meetsGrade("D8", "II")).isFalse();
            assertThat(FilmClass.meetsGrade("D7", "I")).isFalse();
        }

        @Test
        void acceptsAFilmAtExactlyItsOwnClass() {
            assertThat(FilmClass.meetsGrade("D7", "II")).isTrue();
        }

        @Test
        void listsWhatACallAdmits() {
            assertThat(FilmClass.filmsMeeting("I").stream().map(Film::id)).containsExactly("D2", "D3", "D4", "D5");
            assertThat(FilmClass.filmsMeeting("SPECIAL").stream().map(Film::id)).containsExactly("D2", "D3");
        }

        @Test
        void refusesAClassThatDoesNotExist() {
            assertThatThrownBy(() -> FilmClass.meetsGrade("D7", "IV")).hasMessageContaining("no such film class IV");
        }
    }

    @Nested
    class ConvertExposure {

        @Test
        void leavesAnExposureAloneOnTheSameFilm() {
            assertThat(FilmClass.convertExposure(171, "D7", "D7")).isCloseTo(171, digits(9));
        }

        @Test
        void doublesGoingToAFilmHalfTheSpeed() {
            assertThat(FilmClass.convertExposure(171, "D7", "D5")).isCloseTo(342, digits(9));
        }

        @Test
        void halvesGoingToAFilmTwiceTheSpeed() {
            assertThat(FilmClass.convertExposure(171, "D7", "D8")).isCloseTo(85.5, digits(9));
        }

        @Test
        void roundTrips() {
            double there = FilmClass.convertExposure(171, "D7", "D4");
            assertThat(FilmClass.convertExposure(there, "D4", "D7")).isCloseTo(171, digits(9));
        }

        @Test
        void refusesANegativeExposure() {
            assertThatThrownBy(() -> FilmClass.convertExposure(-1, "D7", "D5")).hasMessageContaining("not be negative");
        }
    }

    @Nested
    class FastestMeeting {

        @Test
        void picksTheFastestFilmAClassStillAdmits() {
            assertThat(FilmClass.fastestMeeting("III").id()).isEqualTo("D8");
            assertThat(FilmClass.fastestMeeting("II").id()).isEqualTo("D7");
            assertThat(FilmClass.fastestMeeting("I").id()).isEqualTo("D5");
            assertThat(FilmClass.fastestMeeting("SPECIAL").id()).isEqualTo("D3");
        }

        @Test
        void refusesAClassItDoesNotKnow() {
            assertThatThrownBy(() -> FilmClass.fastestMeeting("IV")).hasMessageContaining("no such film class");
        }
    }
}
