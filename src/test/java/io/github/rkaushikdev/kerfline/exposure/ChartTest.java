package io.github.rkaushikdev.kerfline.exposure;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.util.Num;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChartTest {

    @Nested
    class TheChartsOnFile {

        @Test
        void namesTheReferenceConditionsTheyWereShotAt() {
            assertThat(Chart.chartReference()).isEqualTo(new ChartReference(24, "D7", 2, "LEAD_STANDARD"));
        }

        @Test
        void holdsAChartForTheThreeWorkingSources() {
            assertThat(Chart.chartedIsotopes()).containsExactly("IR192", "SE75", "CO60");
        }

        @Test
        void climbsInBothThicknessAndExposure() {
            for (String id : Chart.chartedIsotopes()) {
                List<ChartPoint> points = Chart.chartPoints(id);
                for (int i = 1; i < points.size(); i++) {
                    assertThat(points.get(i).thicknessIn())
                            .isGreaterThan(points.get(i - 1).thicknessIn());
                    assertThat(points.get(i).curieMinutes())
                            .isGreaterThan(points.get(i - 1).curieMinutes());
                }
            }
        }

        @Test
        void staysInsideTheShopBandForItsSource() {
            assertThat(Chart.chartRange("IR192")).isEqualTo(new ThicknessSpan(0.25, 2.5));
            assertThat(Chart.chartRange("CO60")).isEqualTo(new ThicknessSpan(1.5, 9));
        }

        @Test
        void hasNoChartForTheSoftSources() {
            assertThatThrownBy(() -> Chart.chartPoints("YB169")).hasMessageContaining("no exposure chart");
        }

        @Test
        void answersChartCoversWithoutThrowing() {
            assertThat(Chart.chartCovers("IR192", 1)).isTrue();
            assertThat(Chart.chartCovers("IR192", 5)).isFalse();
            assertThat(Chart.chartCovers("IR192", Double.NaN)).isFalse();
        }
    }

    @Nested
    class ChartExposure {

        @Test
        void readsAChartedPointBackExactly() {
            assertThat(Chart.chartExposure("IR192", 1)).isCloseTo(20, digits(6));
            assertThat(Chart.chartExposure("IR192", 2)).isCloseTo(125, digits(6));
            assertThat(Chart.chartExposure("CO60", 4)).isCloseTo(220, digits(6));
        }

        @Test
        void landsBetweenTheTwoPointsEitherSide() {
            double middle = Chart.chartExposure("IR192", 1.125);
            assertThat(middle).isGreaterThan(20);
            assertThat(middle).isLessThan(32);
        }

        @Test
        void interpolatesOnTheLogScaleRatherThanTheLinearOne() {
            double log = Chart.chartExposure("IR192", 1.125);
            double linear = (20 + 32) / 2.0;
            assertThat(log).isLessThan(linear);
            assertThat(log).isCloseTo(Math.sqrt(20 * 32), digits(6));
        }

        @Test
        void climbsMonotonicallyAcrossTheWholeChart() {
            double previous = 0;
            for (double t = 0.25; t <= 2.5; t += 0.05) {
                double value = Chart.chartExposure("IR192", Num.parse(Num.toFixed(t, 2)));
                assertThat(value).isGreaterThan(previous);
                previous = value;
            }
        }

        @Test
        void refusesAThicknessOffEitherEndRatherThanExtrapolating() {
            assertThatThrownBy(() -> Chart.chartExposure("IR192", 0.2)).hasMessageContaining("off the IR192 chart");
            assertThatThrownBy(() -> Chart.chartExposure("IR192", 3)).hasMessageContaining("off the IR192 chart");
        }

        @Test
        void refusesAThicknessThatIsNotAPositiveNumber() {
            assertThatThrownBy(() -> Chart.chartExposure("IR192", 0)).hasMessageContaining("positive number");
            assertThatThrownBy(() -> Chart.chartExposure("IR192", Double.NaN)).hasMessageContaining("positive number");
        }
    }

    @Nested
    class ChartThickness {

        @Test
        void invertsTheChartAtAChartedPoint() {
            assertThat(Chart.chartThickness("IR192", 20)).isCloseTo(1, digits(3));
            assertThat(Chart.chartThickness("CO60", 220)).isCloseTo(4, digits(3));
        }

        @Test
        void roundTripsThroughChartExposure() {
            for (double thickness : new double[] {0.4, 1.1, 1.9, 2.4}) {
                double exposure = Chart.chartExposure("IR192", thickness);
                assertThat(Chart.chartThickness("IR192", exposure)).isCloseTo(thickness, digits(3));
            }
        }

        @Test
        void refusesAnExposureOffTheChart() {
            assertThatThrownBy(() -> Chart.chartThickness("IR192", 1)).hasMessageContaining("off the IR192 chart");
            assertThatThrownBy(() -> Chart.chartThickness("IR192", 5000)).hasMessageContaining("off the IR192 chart");
        }

        @Test
        void refusesAnExposureThatIsNotPositive() {
            assertThatThrownBy(() -> Chart.chartThickness("IR192", 0)).hasMessageContaining("positive number");
        }
    }

    @Nested
    class ImpliedHalfValueLayerIn {

        @Test
        void landsNearThePublishedNarrowBeamFigureForEachSource() {
            assertThat(Chart.impliedHalfValueLayerIn("IR192")).isGreaterThan(0.3);
            assertThat(Chart.impliedHalfValueLayerIn("IR192")).isLessThan(0.55);
            assertThat(Chart.impliedHalfValueLayerIn("CO60")).isGreaterThan(0.7);
            assertThat(Chart.impliedHalfValueLayerIn("CO60")).isLessThan(1.1);
        }

        @Test
        void isHarderForCobaltThanForIridiumWhichIsTheWholePoint() {
            assertThat(Chart.impliedHalfValueLayerIn("CO60")).isGreaterThan(Chart.impliedHalfValueLayerIn("IR192"));
            assertThat(Chart.impliedHalfValueLayerIn("IR192")).isGreaterThan(Chart.impliedHalfValueLayerIn("SE75"));
        }
    }
}
