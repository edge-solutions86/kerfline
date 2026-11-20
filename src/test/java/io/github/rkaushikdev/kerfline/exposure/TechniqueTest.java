package io.github.rkaushikdev.kerfline.exposure;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TechniqueTest {

    private static final TechniqueSetup SETUP = TechniqueSetup.builder()
            .isotopeId("IR192")
            .thicknessIn(1)
            .distanceIn(24)
            .filmId("D7")
            .screens("LEAD_STANDARD")
            .targetDensity(2)
            .assayCi(40)
            .daysSinceAssay(0)
            .build();

    @Nested
    class ExposureFactorsOfASetup {

        @Test
        void isTheBareChartReadingAtTheReferenceConditions() {
            ExposureFactors factors = Technique.exposureFactors(SETUP);
            assertThat(factors.chartCurieMinutes()).isCloseTo(Chart.chartExposure("IR192", 1), digits(3));
            assertThat(factors.distanceFactor()).isEqualTo(1);
            assertThat(factors.filmFactor()).isEqualTo(1);
            assertThat(factors.screenFactor()).isEqualTo(1);
            assertThat(factors.densityFactor()).isEqualTo(1);
            assertThat(factors.curieMinutes()).isCloseTo(20, digits(2));
        }

        @Test
        void squaresTheDistanceFactor() {
            ExposureFactors factors =
                    Technique.exposureFactors(SETUP.toBuilder().distanceIn(48).build());
            assertThat(factors.distanceFactor()).isEqualTo(4);
            assertThat(factors.curieMinutes()).isCloseTo(80, digits(2));
        }

        @Test
        void doublesForAFilmHalfTheSpeed() {
            ExposureFactors factors =
                    Technique.exposureFactors(SETUP.toBuilder().filmId("D5").build());
            assertThat(factors.filmFactor()).isCloseTo(2, digits(4));
        }

        @Test
        void lengthensTheShotWhenTheLeadComesOff() {
            ExposureFactors factors =
                    Technique.exposureFactors(SETUP.toBuilder().screens("NONE").build());
            assertThat(factors.screenFactor()).isGreaterThan(1);
        }

        @Test
        void shortensItSlightlyWithHeavierLead() {
            ExposureFactors factors = Technique.exposureFactors(
                    SETUP.toBuilder().screens("LEAD_HEAVY").build());
            assertThat(factors.screenFactor()).isLessThan(1);
        }

        @Test
        void liftsTheExposureForAHigherWantedDensity() {
            ExposureFactors factors = Technique.exposureFactors(
                    SETUP.toBuilder().targetDensity(2.5).build());
            assertThat(factors.densityFactor()).isGreaterThan(1);
        }

        @Test
        void multipliesTheFactorsOutToTheFinalExposure() {
            ExposureFactors factors = Technique.exposureFactors(SETUP.toBuilder()
                    .distanceIn(36)
                    .filmId("D5")
                    .screens("NONE")
                    .targetDensity(2.5)
                    .build());
            double product = factors.chartCurieMinutes()
                    * factors.distanceFactor()
                    * factors.filmFactor()
                    * factors.screenFactor()
                    * factors.densityFactor();
            assertThat(factors.curieMinutes()).isCloseTo(product, digits(1));
        }

        @Test
        void refusesAThicknessTheChartDoesNotCover() {
            assertThatThrownBy(() -> Technique.exposureFactors(
                            SETUP.toBuilder().thicknessIn(4).build()))
                    .hasMessageContaining("off the IR192 chart");
        }
    }

    @Nested
    class PlanExposure {

        @Test
        void turnsTheExposureIntoATimeOnTodayActivity() {
            ExposurePlan plan = Technique.planExposure(SETUP);
            assertThat(plan.activityCi()).isEqualTo(40);
            assertThat(plan.minutes()).isCloseTo(20.0 / 40, digits(2));
            assertThat(plan.seconds()).isEqualTo(Math.round(plan.minutes() * 60));
        }

        @Test
        void asksForLongerAsTheSourceDecays() {
            ExposurePlan fresh = Technique.planExposure(SETUP);
            ExposurePlan aged = Technique.planExposure(
                    SETUP.toBuilder().daysSinceAssay(73.83).build());
            assertThat(aged.activityCi()).isCloseTo(20, digits(1));
            assertThat(aged.minutes()).isCloseTo(fresh.minutes() * 2, digits(2));
        }

        @Test
        void carriesTheSetupThroughToTheSheet() {
            ExposurePlan plan = Technique.planExposure(
                    SETUP.toBuilder().filmId("D5").screens("LEAD_HEAVY").build());
            assertThat(plan.isotope()).isEqualTo("IR192");
            assertThat(plan.film()).isEqualTo("D5");
            assertThat(plan.screens()).isEqualTo("LEAD_HEAVY");
            assertThat(plan.thicknessIn()).isEqualTo(1);
        }

        @Test
        void fallsBackToTheShopDefaults() {
            ExposurePlan plan = Technique.planExposure(TechniqueSetup.builder()
                    .isotopeId("IR192")
                    .thicknessIn(1)
                    .distanceIn(24)
                    .assayCi(40)
                    .build());
            assertThat(plan.film()).isEqualTo("D7");
            assertThat(plan.screens()).isEqualTo("LEAD_STANDARD");
            assertThat(plan.targetDensity()).isEqualTo(2);
        }

        @Test
        void takesALabelRatherThanOnlyAnIdForTheSource() {
            assertThat(Technique.planExposure(
                                    SETUP.toBuilder().isotopeId("ir-192").build())
                            .isotope())
                    .isEqualTo("IR192");
        }

        @Test
        void refusesAnAssayAtOrBelowZero() {
            assertThatThrownBy(() ->
                            Technique.planExposure(SETUP.toBuilder().assayCi(0).build()))
                    .hasMessageContaining("greater than 0");
        }

        @Test
        void refusesADayCountThatIsNotANumber() {
            assertThatThrownBy(() -> Technique.planExposure(
                            SETUP.toBuilder().daysSinceAssay(Double.NaN).build()))
                    .hasMessageContaining("finite number");
        }
    }

    @Nested
    class TimerSetting {

        @Test
        void roundsUpToAWholeSecondWithTheWorkingMarginOn() {
            assertThat(Technique.timerSetting(1)).isEqualTo(66);
        }

        @Test
        void neverRoundsDown() {
            assertThat(Technique.timerSetting(0.5, 0)).isEqualTo(30);
            assertThat(Technique.timerSetting(0.5001, 0)).isEqualTo(31);
        }

        @Test
        void takesADifferentMargin() {
            assertThat(Technique.timerSetting(1, 0.25)).isEqualTo(75);
        }

        @Test
        void refusesATimeAtZeroOrANegativeMargin() {
            assertThatThrownBy(() -> Technique.timerSetting(0)).hasMessageContaining("greater than 0");
            assertThatThrownBy(() -> Technique.timerSetting(1, -0.1)).hasMessageContaining("not be below 0");
        }
    }
}
