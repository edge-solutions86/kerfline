package io.github.rkaushikdev.kerfline.safety;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BoundaryTest {

    private static final Camera CAMERA = new Camera("IR192", 40);

    @Nested
    class TheTwoRates {

        @Test
        void areTheRegulatoryNumbers() {
            assertThat(Boundary.highRadiationRate()).isEqualTo(100);
            assertThat(Boundary.restrictedAreaRate()).isEqualTo(2);
        }
    }

    @Nested
    class BoundaryDistancesFt {

        private final BoundaryDistances bounds = Boundary.boundaryDistancesFt(CAMERA);

        @Test
        void putsTheRestrictedBoundaryMuchFurtherOutThanTheHighOne() {
            assertThat(bounds.restrictedFt()).isGreaterThan(bounds.highRadiationFt());
        }

        @Test
        void landsWhereTheRateIsWhatItSays() {
            assertThat(DoseRate.doseRateAt(CAMERA, bounds.restrictedFt())).isCloseTo(2, digits(1));
            assertThat(DoseRate.doseRateAt(CAMERA, bounds.highRadiationFt())).isCloseTo(100, digits(0));
        }

        @Test
        void putsAFortyCurieIridiumCameraBoundaryALongWayOut() {
            assertThat(bounds.restrictedFt()).isGreaterThan(200);
            assertThat(bounds.restrictedFt()).isLessThan(400);
        }

        @Test
        void growsWithTheSquareRootOfTheActivity() {
            BoundaryDistances small = Boundary.boundaryDistancesFt(new Camera("IR192", 10));
            BoundaryDistances large = Boundary.boundaryDistancesFt(new Camera("IR192", 40));
            assertThat(large.restrictedFt()).isCloseTo(small.restrictedFt() * 2, digits(0));
        }
    }

    @Nested
    class BarricadeDistanceFt {

        @Test
        void isFurtherOutThanTheBareCalculation() {
            double bare = Boundary.boundaryDistancesFt(CAMERA).restrictedFt();
            assertThat(Boundary.barricadeDistanceFt(CAMERA)).isGreaterThan(bare);
        }

        @Test
        void takesAMargin() {
            double bare = Boundary.boundaryDistancesFt(CAMERA).restrictedFt();
            assertThat(Boundary.barricadeDistanceFt(CAMERA, 1)).isCloseTo(bare, digits(0));
            assertThat(Boundary.barricadeDistanceFt(CAMERA, 2)).isCloseTo(bare * 2, digits(0));
        }

        @Test
        void refusesAMarginBelowOne() {
            assertThatThrownBy(() -> Boundary.barricadeDistanceFt(CAMERA, 0.9)).hasMessageContaining("not be below 1");
        }
    }

    @Nested
    class IsInsideBoundary {

        @Test
        void isInsideCloseToTheSourceAndOutsideFarFromIt() {
            assertThat(Boundary.isInsideBoundary(CAMERA, 10)).isTrue();
            assertThat(Boundary.isInsideBoundary(CAMERA, 5000)).isFalse();
        }

        @Test
        void readsTheHighRadiationBoundarySeparately() {
            BoundaryDistances bounds = Boundary.boundaryDistancesFt(CAMERA);
            double between = (bounds.highRadiationFt() + bounds.restrictedFt()) / 2;
            assertThat(Boundary.isInsideBoundary(CAMERA, between, BoundaryLine.RESTRICTED))
                    .isTrue();
            assertThat(Boundary.isInsideBoundary(CAMERA, between, BoundaryLine.HIGH_RADIATION))
                    .isFalse();
        }
    }

    @Nested
    class FitsInSpace {

        @Test
        void fitsWhenThereIsRoom() {
            SpaceCheck check = Boundary.fitsInSpace(CAMERA, 1000);
            assertThat(check.fits()).isTrue();
            assertThat(check.shortFt()).isEqualTo(0);
        }

        @Test
        void doesNotFitInATightBayAndSaysByHowMuch() {
            SpaceCheck check = Boundary.fitsInSpace(CAMERA, 100);
            assertThat(check.fits()).isFalse();
            assertThat(check.shortFt()).isGreaterThan(0);
            assertThat(check.neededFt()).isCloseTo(check.availableFt() + check.shortFt(), digits(1));
        }

        @Test
        void fitsASmallerSourceWhereABigOneWillNot() {
            SpaceCheck big = Boundary.fitsInSpace(new Camera("IR192", 100), 200);
            SpaceCheck small = Boundary.fitsInSpace(new Camera("IR192", 2), 200);
            assertThat(big.fits()).isFalse();
            assertThat(small.fits()).isTrue();
        }

        @Test
        void refusesASpaceAtZero() {
            assertThatThrownBy(() -> Boundary.fitsInSpace(CAMERA, 0)).hasMessageContaining("available space");
        }
    }

    @Nested
    class LargestActivityFor {

        @Test
        void findsAnActivityThatFitsTheSpace() {
            double activityCi = Boundary.largestActivityFor("IR192", 200);
            SpaceCheck check = Boundary.fitsInSpace(new Camera("IR192", activityCi), 200);
            assertThat(check.fits()).isTrue();
            assertThat(check.neededFt()).isCloseTo(200, digits(0));
        }

        @Test
        void allowsMoreActivityInMoreSpaceAsTheSquare() {
            double near = Boundary.largestActivityFor("IR192", 100);
            double far = Boundary.largestActivityFor("IR192", 200);
            assertThat(far).isCloseTo(near * 4, digits(0));
        }

        @Test
        void allowsMoreOfASoftSourceThanAHardOne() {
            double selenium = Boundary.largestActivityFor("SE75", 200);
            double cobalt = Boundary.largestActivityFor("CO60", 200);
            assertThat(selenium).isGreaterThan(cobalt);
        }
    }

    @Nested
    class CombinedBoundaryFt {

        @Test
        void isFurtherOutThanEitherSourceOnItsOwn() {
            double one = Boundary.barricadeDistanceFt(new Camera("IR192", 40), 1);
            double both = Boundary.combinedBoundaryFt(List.of(new Camera("IR192", 40), new Camera("IR192", 40)), 1);
            assertThat(both).isGreaterThan(one);
        }

        @Test
        void landsWhereTheRatesAddToTheRestrictedLimit() {
            List<Camera> sources = List.of(new Camera("IR192", 40), new Camera("CO60", 10));
            double distanceFt = Boundary.combinedBoundaryFt(sources, 1);
            double total = 0;
            for (Camera source : sources) {
                total += DoseRate.doseRateAt(source, distanceFt);
            }
            assertThat(total).isCloseTo(2, digits(1));
        }

        @Test
        void matchesTheSingleSourceBoundaryWhenThereIsOnlyOne() {
            double single = Boundary.barricadeDistanceFt(new Camera("IR192", 40), 1);
            double combined = Boundary.combinedBoundaryFt(List.of(new Camera("IR192", 40)), 1);
            assertThat(combined).isCloseTo(single, digits(0));
        }

        @Test
        void refusesAnEmptyList() {
            assertThatThrownBy(() -> Boundary.combinedBoundaryFt(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one source");
        }
    }
}
