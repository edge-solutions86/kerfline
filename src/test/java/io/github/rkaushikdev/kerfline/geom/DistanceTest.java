package io.github.rkaushikdev.kerfline.geom;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DistanceTest {

    @Nested
    class TheThreeDistances {

        @Test
        void addsUp() {
            assertThat(Distance.sourceToFilm(24, 6)).isEqualTo(30);
            assertThat(Distance.sourceToObject(30, 6)).isEqualTo(24);
            assertThat(Distance.objectToFilm(30, 24)).isEqualTo(6);
        }

        @Test
        void allowsTheObjectToLieOnTheFilm() {
            assertThat(Distance.sourceToFilm(24, 0)).isEqualTo(24);
            assertThat(Distance.objectToFilm(24, 24)).isEqualTo(0);
        }

        @Test
        void refusesAnObjectAtOrBehindTheFilm() {
            assertThatThrownBy(() -> Distance.sourceToObject(24, 24)).hasMessageContaining("at or behind the film");
            assertThatThrownBy(() -> Distance.objectToFilm(24, 30)).hasMessageContaining("behind the film");
        }

        @Test
        void refusesADistanceAtOrBelowZero() {
            assertThatThrownBy(() -> Distance.sourceToFilm(0, 6)).hasMessageContaining("greater than 0");
            assertThatThrownBy(() -> Distance.sourceToFilm(24, -1)).hasMessageContaining("not be below 0");
        }

        @Test
        void refusesADistanceThatIsNotANumber() {
            assertThatThrownBy(() -> Distance.sourceToFilm(Double.NaN, 6)).hasMessageContaining("finite number");
        }
    }

    @Nested
    class Magnification {

        @Test
        void isOneWhenTheObjectLiesOnTheFilm() {
            assertThat(Distance.magnification(24, 0)).isEqualTo(1);
        }

        @Test
        void growsWithTheStandoff() {
            assertThat(Distance.magnification(24, 6)).isCloseTo(1.25, digits(9));
            assertThat(Distance.magnification(24, 12)).isCloseTo(1.5, digits(9));
        }

        @Test
        void fallsBackTowardsOneAsTheSourceIsCrankedOut() {
            double near = Distance.magnification(12, 6);
            double far = Distance.magnification(120, 6);
            assertThat(far).isLessThan(near);
            assertThat(far).isGreaterThan(1);
        }
    }

    @Nested
    class ProjectedLengthAndActualLength {

        @Test
        void invertEachOther() {
            double factor = Distance.magnification(24, 6);
            assertThat(Distance.actualLength(Distance.projectedLength(0.25, factor), factor))
                    .isCloseTo(0.25, digits(12));
        }

        @Test
        void leavesALengthAloneAtMagnificationOne() {
            assertThat(Distance.projectedLength(0.25, 1)).isEqualTo(0.25);
            assertThat(Distance.actualLength(0.25, 1)).isEqualTo(0.25);
        }

        @Test
        void makesAnIndicationReadLongerThanItIs() {
            assertThat(Distance.projectedLength(0.25, 1.25)).isCloseTo(0.3125, digits(9));
            assertThat(Distance.actualLength(0.3125, 1.25)).isCloseTo(0.25, digits(9));
        }

        @Test
        void refusesAMagnificationBelowOne() {
            assertThatThrownBy(() -> Distance.projectedLength(0.25, 0.9)).hasMessageContaining("not be below 1");
            assertThatThrownBy(() -> Distance.actualLength(0.25, 0)).hasMessageContaining("not be below 1");
        }

        @Test
        void refusesANegativeLength() {
            assertThatThrownBy(() -> Distance.projectedLength(-0.25, 1)).hasMessageContaining("not be below 0");
        }
    }

    @Nested
    class ResolveDistances {

        private final Distances expected = new Distances(30, 24, 6, 1.25);

        @Test
        void completesThePictureFromSourceToObjectAndStandoff() {
            assertThat(Distance.resolveDistances(new GivenDistances(null, 24.0, 6.0)))
                    .isEqualTo(expected);
        }

        @Test
        void completesItFromSourceToFilmAndStandoff() {
            assertThat(Distance.resolveDistances(new GivenDistances(30.0, null, 6.0)))
                    .isEqualTo(expected);
        }

        @Test
        void completesItFromTheTwoSourceDistances() {
            assertThat(Distance.resolveDistances(new GivenDistances(30.0, 24.0, null)))
                    .isEqualTo(expected);
        }

        @Test
        void acceptsAllThreeWhenTheyAgree() {
            Distances out = Distance.resolveDistances(new GivenDistances(30.0, 24.0, 6.0));
            assertThat(out.magnification()).isEqualTo(1.25);
        }

        @Test
        void refusesAllThreeWhenTheyDoNotAgree() {
            assertThatThrownBy(() -> Distance.resolveDistances(new GivenDistances(30.0, 24.0, 8.0)))
                    .hasMessageContaining("do not agree");
        }

        @Test
        void refusesFewerThanTwo() {
            assertThatThrownBy(() -> Distance.resolveDistances(new GivenDistances(30.0, null, null)))
                    .hasMessageContaining("two of the three");
            assertThatThrownBy(() -> Distance.resolveDistances(new GivenDistances(null, null, null)))
                    .hasMessageContaining("two of the three");
        }

        @Test
        void treatsANullTheSameAsAMissingValue() {
            assertThatThrownBy(() -> Distance.resolveDistances(null)).hasMessageContaining("two of the three");
            assertThatThrownBy(() -> Distance.resolveDistances(new GivenDistances(30.0, null, null)))
                    .hasMessageContaining("two of the three");
        }
    }
}
