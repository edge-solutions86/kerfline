package io.github.rkaushikdev.kerfline.geom;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ViewingTest {

    @Nested
    class TheTechniqueTable {

        @Test
        void listsTheFiveTechniquesTheProcedureAllows() {
            assertThat(Viewing.viewingTechniques())
                    .containsExactly("SWSI_PANORAMIC", "SWSI_OFFSET", "DWSI", "DWDI_ELLIPTICAL", "DWDI_SUPERIMPOSED");
        }

        @Test
        void carriesALabelAndAWallCountForEach() {
            for (String name : Viewing.viewingTechniques()) {
                ViewingTechnique technique = Viewing.viewing(name);
                assertThat(technique.label()).isNotBlank();
                assertThat(technique.walls()).isIn(1, 2);
                assertThat(technique.minimumExposures()).isGreaterThanOrEqualTo(1);
            }
        }

        @Test
        void refusesATechniqueNobodyHasHeardOf() {
            assertThatThrownBy(() -> Viewing.viewing("TWSI")).hasMessageContaining("no viewing technique TWSI");
        }
    }

    @Nested
    class MinimumExposures {

        @Test
        void takesOneShotOnAPanoramic() {
            assertThat(Viewing.minimumExposures("SWSI_PANORAMIC")).isEqualTo(1);
        }

        @Test
        void takesTwoOnAnEllipticalAndThreeWhenTheWallsSuperimpose() {
            assertThat(Viewing.minimumExposures("DWDI_ELLIPTICAL")).isEqualTo(2);
            assertThat(Viewing.minimumExposures("DWDI_SUPERIMPOSED")).isEqualTo(3);
        }

        @Test
        void takesThreeOnADoubleWallSingleImage() {
            assertThat(Viewing.minimumExposures("DWSI")).isEqualTo(3);
        }
    }

    @Nested
    class ExposureSeparationDegrees {

        @Test
        void putsTheTwoEllipticalShotsAtNinetyDegrees() {
            assertThat(Viewing.exposureSeparationDegrees("DWDI_ELLIPTICAL")).isEqualTo(90);
        }

        @Test
        void spreadsThreeShotsEvenlyAtSixtyDegreesOfWeldEach() {
            assertThat(Viewing.exposureSeparationDegrees("DWDI_SUPERIMPOSED")).isEqualTo(120);
            assertThat(Viewing.exposureSeparationDegrees("DWSI")).isEqualTo(120);
        }

        @Test
        void hasNothingToSeparateOnASingleShot() {
            assertThat(Viewing.exposureSeparationDegrees("SWSI_PANORAMIC")).isEqualTo(0);
        }
    }

    @Nested
    class PenetratedThicknessIn {

        @Test
        void crossesOneWallOnASingleWallShot() {
            assertThat(Viewing.penetratedThicknessIn("SWSI_PANORAMIC", 0.28)).isCloseTo(0.28, digits(9));
        }

        @Test
        void crossesTwoWallsOnAnyDoubleWallShotEvenWhenOneIsRead() {
            assertThat(Viewing.penetratedThicknessIn("DWSI", 0.28)).isCloseTo(0.56, digits(9));
            assertThat(Viewing.penetratedThicknessIn("DWDI_ELLIPTICAL", 0.28)).isCloseTo(0.56, digits(9));
        }

        @Test
        void refusesAWallAtOrBelowZero() {
            assertThatThrownBy(() -> Viewing.penetratedThicknessIn("DWSI", 0)).hasMessageContaining("greater than 0");
        }
    }

    @Nested
    class SelectViewing {

        @Test
        void shootsAPanoramicWhenTheSourceCanGetInsideABigPipe() {
            assertThat(Viewing.selectViewing(24, true, true)).isEqualTo(ViewingTechnique.SWSI_PANORAMIC);
        }

        @Test
        void offsetsTheSourceInsideAPipeTooSmallToCentreIt() {
            assertThat(Viewing.selectViewing(3, true, true)).isEqualTo(ViewingTechnique.SWSI_OFFSET);
        }

        @Test
        void shootsDoubleWallSingleImageOnAnythingOverSmallBore() {
            assertThat(Viewing.selectViewing(12.75)).isEqualTo(ViewingTechnique.DWSI);
            assertThat(Viewing.selectViewing(4.5)).isEqualTo(ViewingTechnique.DWSI);
        }

        @Test
        void shootsAnEllipseOnSmallBore() {
            assertThat(Viewing.selectViewing(2.375)).isEqualTo(ViewingTechnique.DWDI_ELLIPTICAL);
            assertThat(Viewing.selectViewing(3.5)).isEqualTo(ViewingTechnique.DWDI_ELLIPTICAL);
        }

        @Test
        void fallsBackToSuperimposedWhenTheImagesWillNotSeparate() {
            assertThat(Viewing.selectViewing(2.375, false, false)).isEqualTo(ViewingTechnique.DWDI_SUPERIMPOSED);
        }

        @Test
        void ignoresTheSeparationQuestionOnceTheSourceIsInside() {
            assertThat(Viewing.selectViewing(24, true, false)).isEqualTo(ViewingTechnique.SWSI_PANORAMIC);
        }

        @Test
        void putsTheSmallBoreBoundaryInTheEllipse() {
            assertThat(Viewing.selectViewing(3.5)).isEqualTo(ViewingTechnique.DWDI_ELLIPTICAL);
            assertThat(Viewing.selectViewing(3.5001)).isEqualTo(ViewingTechnique.DWSI);
        }

        @Test
        void refusesADiameterAtOrBelowZero() {
            assertThatThrownBy(() -> Viewing.selectViewing(0)).hasMessageContaining("greater than 0");
        }
    }

    @Nested
    class ViewingPlans {

        @Test
        void carriesTheChoiceAndEverythingItDecided() {
            assertThat(Viewing.viewingPlan(12.75, 0.375))
                    .isEqualTo(new ViewingPlan(ViewingTechnique.DWSI, "double wall single image", 3, 120, 0.75, false));
        }

        @Test
        void readsBothWallsOnlyOnADoubleWallDoubleImage() {
            assertThat(Viewing.viewingPlan(2.375, 0.218).readsBothWalls()).isTrue();
            assertThat(Viewing.viewingPlan(12.75, 0.375).readsBothWalls()).isFalse();
        }

        @Test
        void halvesThePenetratedThicknessOnceTheSourceGoesInside() {
            ViewingPlan outside = Viewing.viewingPlan(12.75, 0.375);
            ViewingPlan inside = Viewing.viewingPlan(12.75, 0.375, true);
            assertThat(inside.penetratedThicknessIn()).isCloseTo(outside.penetratedThicknessIn() / 2, digits(9));
        }
    }
}
