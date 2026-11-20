package io.github.rkaushikdev.kerfline.safety;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CollimatorTest {

    private static final Camera CAMERA = new Camera("IR192", 40);

    @Nested
    class TheSleeves {

        @Test
        void listsWhatTheShopOwns() {
            assertThat(Collimator.collimatorTypes()).containsExactly("NONE", "PARTIAL", "DIRECTIONAL");
        }

        @Test
        void getsNarrowerAndHeavierAsItGetsBetter() {
            List<Sleeve> sleeves = Collimator.collimatorTypes().stream()
                    .map(Collimator::collimator)
                    .toList();
            for (int i = 1; i < sleeves.size(); i++) {
                assertThat(sleeves.get(i).beamDegrees())
                        .isLessThan(sleeves.get(i - 1).beamDegrees());
                assertThat(sleeves.get(i).outsideFactor())
                        .isLessThan(sleeves.get(i - 1).outsideFactor());
                assertThat(sleeves.get(i).weightLb())
                        .isGreaterThan(sleeves.get(i - 1).weightLb());
            }
        }

        @Test
        void refusesASleeveNobodyOwns() {
            assertThatThrownBy(() -> Collimator.collimator("PINHOLE"))
                    .hasMessageContaining("no collimator called PINHOLE");
        }
    }

    @Nested
    class InBeam {

        @Test
        void isInTheBeamStraightAhead() {
            assertThat(Collimator.inBeam("DIRECTIONAL", 0)).isTrue();
        }

        @Test
        void isInTheBeamAtTheEdgeOfTheCone() {
            assertThat(Collimator.inBeam("DIRECTIONAL", 30)).isTrue();
            assertThat(Collimator.inBeam("DIRECTIONAL", 31)).isFalse();
        }

        @Test
        void doesNotCareWhichSideOfTheAxis() {
            assertThat(Collimator.inBeam("DIRECTIONAL", -30)).isTrue();
            assertThat(Collimator.inBeam("DIRECTIONAL", -31)).isFalse();
        }

        @Test
        void isBehindTheCameraAtAHundredAndEighty() {
            assertThat(Collimator.inBeam("DIRECTIONAL", 180)).isFalse();
            assertThat(Collimator.inBeam("PARTIAL", 180)).isFalse();
        }

        @Test
        void wrapsABearingPastAFullTurn() {
            assertThat(Collimator.inBeam("DIRECTIONAL", 360)).isTrue();
            // 390 is 30 off the axis, which is still the edge of the cone.
            assertThat(Collimator.inBeam("DIRECTIONAL", 390)).isTrue();
            assertThat(Collimator.inBeam("DIRECTIONAL", 400)).isFalse();
        }

        @Test
        void isAlwaysInTheBeamWithNoSleeveOn() {
            for (double bearing : new double[] {0, 90, 180, 270}) {
                assertThat(Collimator.inBeam("NONE", bearing)).isTrue();
            }
        }
    }

    @Nested
    class FactorAt {

        @Test
        void doesNothingInTheBeam() {
            assertThat(Collimator.factorAt("DIRECTIONAL", 0)).isEqualTo(1);
        }

        @Test
        void cutsTheRateHardOutsideIt() {
            assertThat(Collimator.factorAt("DIRECTIONAL", 180)).isCloseTo(0.03, digits(4));
            assertThat(Collimator.factorAt("PARTIAL", 180)).isCloseTo(0.1, digits(4));
        }

        @Test
        void doesNothingAnywhereWithABareSource() {
            assertThat(Collimator.factorAt("NONE", 180)).isEqualTo(1);
        }
    }

    @Nested
    class CollimatedRateAt {

        @Test
        void matchesTheBareRateInTheBeam() {
            double bare = DoseRate.doseRateAt(CAMERA, 50);
            double inside = Collimator.collimatedRateAt(CAMERA, 50, "DIRECTIONAL", 0);
            assertThat(inside).isCloseTo(bare, digits(2));
        }

        @Test
        void isFarLowerBehindTheCamera() {
            double behind = Collimator.collimatedRateAt(CAMERA, 50, "DIRECTIONAL", 180);
            double bare = DoseRate.doseRateAt(CAMERA, 50);
            assertThat(behind).isLessThan(bare / 20);
        }
    }

    @Nested
    class CollimatedBoundaryFt {

        @Test
        void leavesTheBeamSideBoundaryExactlyWhereItWas() {
            double bare = Boundary.barricadeDistanceFt(CAMERA);
            CollimatedBoundary bounds = Collimator.collimatedBoundaryFt(CAMERA, "DIRECTIONAL");
            assertThat(bounds.inBeamFt()).isCloseTo(bare, digits(1));
        }

        @Test
        void pullsTheBoundaryInBehindTheSleeve() {
            CollimatedBoundary bounds = Collimator.collimatedBoundaryFt(CAMERA, "DIRECTIONAL");
            assertThat(bounds.outsideBeamFt()).isLessThan(bounds.inBeamFt());
        }

        @Test
        void pullsItInByTheSquareRootOfTheAttenuation() {
            CollimatedBoundary bounds = Collimator.collimatedBoundaryFt(CAMERA, "PARTIAL");
            assertThat(bounds.outsideBeamFt()).isCloseTo(bounds.inBeamFt() * Math.sqrt(0.1), digits(0));
        }

        @Test
        void changesNothingAtAllWithNoSleeveOn() {
            CollimatedBoundary bounds = Collimator.collimatedBoundaryFt(CAMERA, "NONE");
            assertThat(bounds.outsideBeamFt()).isCloseTo(bounds.inBeamFt(), digits(1));
        }
    }

    @Nested
    class SmallestFitting {

        @Test
        void takesTheLightestSleeveThatWillDo() {
            Optional<FittedCollimator> fitted = Collimator.smallestFitting(CAMERA, 130);
            assertThat(fitted).isPresent();
            assertThat(fitted.get().collimator()).isEqualTo("PARTIAL");
            assertThat(fitted.get().outsideBeamFt()).isLessThanOrEqualTo(130);
        }

        @Test
        void reachesForTheHeavyOneWhenTheLighterSleeveIsTenFeetShort() {
            // The partial sleeve lands just past 120 ft on this source, so a bay of
            // 120 is the case where a crew has to carry the heavy one.
            assertThat(Collimator.smallestFitting(CAMERA, 120).orElseThrow().collimator())
                    .isEqualTo("DIRECTIONAL");
            assertThat(Collimator.smallestFitting(CAMERA, 70).orElseThrow().collimator())
                    .isEqualTo("DIRECTIONAL");
        }

        @Test
        void needsNothingAtAllInTheOpen() {
            assertThat(Collimator.smallestFitting(CAMERA, 1000).orElseThrow().collimator())
                    .isEqualTo("NONE");
        }

        @Test
        void givesUpWhenNoSleeveIsEnough() {
            assertThat(Collimator.smallestFitting(CAMERA, 10)).isEmpty();
        }

        @Test
        void reportsTheWeightTheCrewHasToCarry() {
            assertThat(Collimator.smallestFitting(CAMERA, 70).orElseThrow().weightLb())
                    .isGreaterThan(0);
        }

        @Test
        void refusesASpaceAtZero() {
            assertThatThrownBy(() -> Collimator.smallestFitting(CAMERA, 0)).hasMessageContaining("available space");
        }
    }

    @Nested
    class QuietFraction {

        @Test
        void saysHowMuchOfTheSphereASleeveQuietens() {
            assertThat(Collimator.quietFraction("DIRECTIONAL")).isCloseTo(1 - 60.0 / 360, digits(4));
            assertThat(Collimator.quietFraction("PARTIAL")).isCloseTo(1 - 120.0 / 360, digits(4));
        }

        @Test
        void isNothingWithNoSleeveOn() {
            assertThat(Collimator.quietFraction("NONE")).isEqualTo(0);
        }
    }
}
