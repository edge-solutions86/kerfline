package io.github.rkaushikdev.kerfline.geom;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EllipseTest {

    private static final double OD = 2.375;
    private static final double SOD = 12;

    @Nested
    class ImageSeparationInAndOffsetForSeparation {

        @Test
        void separatesNothingWhenTheSourceSitsOnTheWeldPlane() {
            assertThat(Ellipse.imageSeparationIn(OD, SOD, 0)).isEqualTo(0);
        }

        @Test
        void growsTheSeparationAsTheSourceIsPushedBack() {
            double near = Ellipse.imageSeparationIn(OD, SOD, 1);
            double far = Ellipse.imageSeparationIn(OD, SOD, 3);
            assertThat(far).isCloseTo(near * 3, digits(9));
        }

        @Test
        void isTheOffsetScaledByDiameterOverStandoff() {
            assertThat(Ellipse.imageSeparationIn(OD, SOD, 6)).isCloseTo(1.1875, digits(9));
        }

        @Test
        void inverts() {
            double offset = Ellipse.offsetForSeparation(OD, SOD, 0.75);
            assertThat(Ellipse.imageSeparationIn(OD, SOD, offset)).isCloseTo(0.75, digits(9));
        }

        @Test
        void needsALongerPushOnASmallerPipeForTheSameSeparation() {
            double small = Ellipse.offsetForSeparation(1.05, 12, 0.75);
            double large = Ellipse.offsetForSeparation(3.5, 12, 0.75);
            assertThat(small).isGreaterThan(large);
        }

        @Test
        void refusesANegativeOffsetOrSeparation() {
            assertThatThrownBy(() -> Ellipse.imageSeparationIn(OD, SOD, -1)).hasMessageContaining("not be below 0");
            assertThatThrownBy(() -> Ellipse.offsetForSeparation(OD, SOD, -1)).hasMessageContaining("not be below 0");
        }

        @Test
        void refusesAStandoffAtZero() {
            assertThatThrownBy(() -> Ellipse.imageSeparationIn(2.375, 0, 1)).hasMessageContaining("greater than 0");
        }
    }

    @Nested
    class SeparationWindowIn {

        @Test
        void runsFromOneWeldWidthToThree() {
            assertThat(Ellipse.separationWindowIn(0.25)).isEqualTo(new SeparationWindow(0.25, 0.75));
        }

        @Test
        void refusesAWeldWidthAtZero() {
            assertThatThrownBy(() -> Ellipse.separationWindowIn(0)).hasMessageContaining("greater than 0");
        }
    }

    @Nested
    class PlanEllipse {

        private final EllipseSetup setup = EllipseSetup.builder()
                .odIn(2.375)
                .wallIn(0.218)
                .sourceToObjectIn(12)
                .weldWidthIn(0.25)
                .filmLengthIn(4.5)
                .build();
        private final EllipsePlan plan = Ellipse.planEllipse(setup);

        @Test
        void aimsAtTheMiddleOfTheWindow() {
            assertThat(plan.separationIn()).isCloseTo(0.5, digits(3));
            assertThat(plan.windowIn()).isEqualTo(new SeparationWindow(0.25, 0.75));
        }

        @Test
        void worksOutTheOffsetThatLandsThere() {
            assertThat(plan.offsetIn()).isCloseTo(2.526, digits(2));
        }

        @Test
        void checksTheFilmIsLongEnoughForBothImages() {
            assertThat(plan.filmNeededIn()).isCloseTo(1, digits(3));
            assertThat(plan.fitsOnFilm()).isTrue();
            assertThat(plan.usable()).isTrue();
        }

        @Test
        void givesUpWhenTheFilmCannotHoldBothImages() {
            EllipsePlan tight = Ellipse.planEllipse(
                    setup.toBuilder().weldWidthIn(0.5).filmLengthIn(1.2).build());
            assertThat(tight.fitsOnFilm()).isFalse();
            assertThat(tight.usable()).isFalse();
        }

        @Test
        void isNotUsableOnAPipeThatIsPastSmallBore() {
            EllipsePlan big = Ellipse.planEllipse(EllipseSetup.builder()
                    .odIn(8.625)
                    .wallIn(0.322)
                    .sourceToObjectIn(12)
                    .weldWidthIn(0.25)
                    .filmLengthIn(14)
                    .build());
            assertThat(big.smallBore()).isFalse();
            assertThat(big.usable()).isFalse();
        }

        @Test
        void refusesAFilmLengthAtZero() {
            assertThatThrownBy(() -> Ellipse.planEllipse(
                            setup.toBuilder().filmLengthIn(0).build()))
                    .hasMessageContaining("film length");
        }
    }

    @Nested
    class OffsetIsAcceptable {

        @Test
        void acceptsAnOffsetThatLandsInsideTheWindow() {
            double offset = Ellipse.offsetForSeparation(OD, SOD, 0.5);
            assertThat(Ellipse.offsetIsAcceptable(OD, SOD, offset, 0.25)).isTrue();
        }

        @Test
        void rejectsAnOffsetThatLeavesTheImagesOnTopOfEachOther() {
            assertThat(Ellipse.offsetIsAcceptable(OD, SOD, 0.5, 0.25)).isFalse();
        }

        @Test
        void rejectsAnOffsetThatThrowsTheNearWallTooFar() {
            assertThat(Ellipse.offsetIsAcceptable(OD, SOD, 20, 0.25)).isFalse();
        }

        @Test
        void acceptsBothEdgesOfTheWindow() {
            double low = Ellipse.offsetForSeparation(OD, SOD, 0.25);
            double high = Ellipse.offsetForSeparation(OD, SOD, 0.75);
            assertThat(Ellipse.offsetIsAcceptable(OD, SOD, low, 0.25)).isTrue();
            assertThat(Ellipse.offsetIsAcceptable(OD, SOD, high, 0.25)).isTrue();
        }
    }
}
