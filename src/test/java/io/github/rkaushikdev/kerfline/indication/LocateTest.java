package io.github.rkaushikdev.kerfline.indication;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.geom.Pipe;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LocateTest {

    private static final double OD = 6.625;
    private static final double AROUND = Pipe.circumference(OD);

    @Nested
    class WeldPositionOf {

        @Test
        void putsTheMarkerItselfAtTheMarker() {
            WeldPosition placed = Locate.weldPositionOf(0, 0, OD);
            assertThat(placed.alongWeldIn()).isEqualTo(0);
            assertThat(placed.clock()).isEqualTo("12:00");
        }

        @Test
        void walksForwardAlongTheWeld() {
            assertThat(Locate.weldPositionOf(0, AROUND / 4, OD).clock()).isEqualTo("3:00");
        }

        @Test
        void dividesTheFilmDistanceByTheMagnification() {
            WeldPosition contact = Locate.weldPositionOf(0, 2, OD);
            WeldPosition magnified = Locate.weldPositionOf(0, 2, 2, OD);
            assertThat(magnified.alongWeldIn()).isCloseTo(contact.alongWeldIn() / 2, digits(4));
        }

        @Test
        void startsFromWhereTheMarkerWasLaid() {
            assertThat(Locate.weldPositionOf(AROUND / 4, 0, OD).clock()).isEqualTo("3:00");
        }

        @Test
        void wrapsRoundTheTopRatherThanRunningOff() {
            WeldPosition placed = Locate.weldPositionOf(AROUND - 1, 2, OD);
            assertThat(placed.alongWeldIn()).isCloseTo(1, digits(4));
            assertThat(placed.wrapped()).isTrue();
        }

        @Test
        void readsJustShortOfTheTopAsALateClockPosition() {
            WeldPosition placed = Locate.weldPositionOf(AROUND - 0.2, 0, OD);
            assertThat(placed.clock()).startsWith("11:");
        }

        @Test
        void refusesAMagnificationBelowOne() {
            assertThatThrownBy(() -> Locate.weldPositionOf(0, 1, 0.5, OD)).hasMessageContaining("not be below 1");
        }

        @Test
        void refusesANegativeDistance() {
            assertThatThrownBy(() -> Locate.weldPositionOf(0, -1, OD)).hasMessageContaining("not be below 0");
        }
    }

    @Nested
    class FilmPositionOf {

        @Test
        void invertsWeldPositionOf() {
            double alongFilmIn = 3;
            WeldPosition placed = Locate.weldPositionOf(1, alongFilmIn, 1.25, OD);
            double back = Locate.filmPositionOf(1, placed.alongWeldIn(), 1.25, OD);
            assertThat(back).isCloseTo(alongFilmIn, digits(3));
        }

        @Test
        void isNothingAtTheMarker() {
            assertThat(Locate.filmPositionOf(2, 2, OD)).isEqualTo(0);
        }

        @Test
        void multipliesByTheMagnification() {
            double contact = Locate.filmPositionOf(0, 2, OD);
            double magnified = Locate.filmPositionOf(0, 2, 2, OD);
            assertThat(magnified).isCloseTo(contact * 2, digits(4));
        }
    }

    @Nested
    class MarkerFromClock {

        @Test
        void turnsAClockReadingIntoInchesAlongTheWeld() {
            assertThat(Locate.markerFromClock("3:00", OD)).isCloseTo(AROUND / 4, digits(3));
            assertThat(Locate.markerFromClock("12:00", OD)).isEqualTo(0);
        }

        @Test
        void refusesAClockPositionItCannotRead() {
            assertThatThrownBy(() -> Locate.markerFromClock("quarter past", OD)).hasMessageContaining("cannot read");
        }
    }

    @Nested
    class LocateAll {

        private final MarkerContext context = new MarkerContext(0, OD);

        @Test
        void placesEveryIndicationOnTheWeld() {
            List<Indication> set = List.of(
                    Measure.makeIndication(IndicationInput.of("SLAG", 0.2).at(2)),
                    Measure.makeIndication(IndicationInput.of("POROSITY", 0.05).at(5)));
            List<LocatedIndication> placed = Locate.locateAll(set, context);
            assertThat(placed).hasSize(2);
            assertThat(placed.get(0).clock()).isNotNull();
            assertThat(placed.get(1).degrees()).isGreaterThan(placed.get(0).degrees());
        }

        @Test
        void keepsEverythingTheIndicationAlreadyCarried() {
            List<Indication> set = List.of(
                    Measure.makeIndication(IndicationInput.of("SLAG", 0.2).at(2).film("1")));
            Indication placed = Locate.locateAll(set, context).get(0).indication();
            assertThat(placed.type()).isEqualTo(IndicationType.SLAG);
            assertThat(placed.film()).hasValue("1");
            assertThat(placed.sizeIn()).isEqualTo(0.2);
        }

        @Test
        void takesTheMagnificationOffTheIndicationWhenItCarriesOne() {
            List<Indication> set = List.of(
                    Measure.makeIndication(IndicationInput.of("SLAG", 0.2).at(4).magnification(2)));
            Indication placed = Locate.locateAll(set, context).get(0).indication();
            assertThat(placed.at().getAsDouble()).isCloseTo(2, digits(3));
        }

        @Test
        void refusesAnIndicationWithNoPositionOnTheFilm() {
            List<Indication> set = List.of(Measure.makeIndication(IndicationInput.of("SLAG", 0.2)));
            assertThatThrownBy(() -> Locate.locateAll(set, context))
                    .hasMessageContaining("position measured on the film");
        }
    }

    @Nested
    class SamePlace {

        @Test
        void matchesTwoPositionsWithinTheTolerance() {
            assertThat(Locate.samePlace(5, 5.2, OD)).isTrue();
        }

        @Test
        void doesNotMatchTwoPositionsApart() {
            assertThat(Locate.samePlace(5, 9, OD)).isFalse();
        }

        @Test
        void matchesAcrossTheTopOfThePipe() {
            assertThat(Locate.samePlace(AROUND - 0.2, 0.2, OD)).isTrue();
        }

        @Test
        void takesAWiderTolerance() {
            assertThat(Locate.samePlace(5, 6, OD, 2)).isTrue();
        }
    }

    @Nested
    class SpanOf {

        @Test
        void givesTheTwoClockPositionsACrewCanChalk() {
            WeldSpan span = Locate.spanOf(0, AROUND / 4, OD);
            assertThat(span.from()).isEqualTo("12:00");
            assertThat(span.to()).isEqualTo("3:00");
            assertThat(span.crossesTop()).isFalse();
        }

        @Test
        void wrapsASpanThatCrossesTheTop() {
            WeldSpan span = Locate.spanOf(AROUND - 1, 2, OD);
            assertThat(span.crossesTop()).isTrue();
            assertThat(span.toIn()).isCloseTo(1, digits(3));
        }

        @Test
        void isAPointForASpanOfNoLength() {
            WeldSpan span = Locate.spanOf(2, 0, OD);
            assertThat(span.fromIn()).isEqualTo(span.toIn());
        }

        @Test
        void refusesANegativeLength() {
            assertThatThrownBy(() -> Locate.spanOf(0, -1, OD)).hasMessageContaining("not be below 0");
        }
    }
}
