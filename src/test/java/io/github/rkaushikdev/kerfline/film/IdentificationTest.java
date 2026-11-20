package io.github.rkaushikdev.kerfline.film;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IdentificationTest {

    @Nested
    class CheckIdentification {

        private final FilmIdentification complete = FilmIdentification.builder()
                .job("C-4471")
                .day("2025-03-14")
                .jointId("J-118")
                .position("3")
                .build();

        @Test
        void passesAFilmCarryingEverything() {
            IdentificationCheck check = Identification.checkIdentification(complete);
            assertThat(check.complete()).isTrue();
            assertThat(check.missing()).isEmpty();
        }

        @Test
        void namesTheRequiredFields() {
            assertThat(Identification.identificationFields()).containsExactly("job", "day", "jointId", "position");
        }

        @Test
        void listsEverythingMissingRatherThanStoppingAtTheFirst() {
            IdentificationCheck check = Identification.checkIdentification(
                    FilmIdentification.builder().job("C-4471").build());
            assertThat(check.missing()).containsExactly("day", "jointId", "position");
            assertThat(check.complete()).isFalse();
        }

        @Test
        void countsABlankFieldAsMissing() {
            assertThat(Identification.checkIdentification(
                                    complete.toBuilder().jointId("   ").build())
                            .missing())
                    .containsExactly("jointId");
        }

        @Test
        void catchesADateThatIsNotARealDay() {
            IdentificationCheck check = Identification.checkIdentification(
                    complete.toBuilder().day("14/03/2025").build());
            assertThat(check.dayIsReadable()).isFalse();
            assertThat(check.complete()).isFalse();
        }

        @Test
        void refusesSomethingThatIsNotARecordAtAll() {
            assertThatThrownBy(() -> Identification.checkIdentification(null))
                    .hasMessageContaining("must be an object");
        }
    }

    @Nested
    class BackscatterVerdicts {

        @Test
        void rejectsALightBOnADarkerBackground() {
            BackscatterVerdict verdict = Identification.backscatterVerdict(new BackscatterReading(1.8, 2.6));
            assertThat(verdict.appearance()).isEqualTo(BackscatterAppearance.LIGHT);
            assertThat(verdict.appearance().label()).isEqualTo("light");
            assertThat(verdict.pass()).isFalse();
        }

        @Test
        void acceptsADarkBOnALighterBackground() {
            BackscatterVerdict verdict = Identification.backscatterVerdict(new BackscatterReading(3.1, 2.6));
            assertThat(verdict.appearance()).isEqualTo(BackscatterAppearance.DARK);
            assertThat(verdict.pass()).isTrue();
        }

        @Test
        void acceptsABThatDidNotImageAtAll() {
            BackscatterVerdict verdict = Identification.backscatterVerdict(new BackscatterReading(2.6, 2.6));
            assertThat(verdict.appearance().label()).isEqualTo("not visible");
            assertThat(verdict.pass()).isTrue();
        }

        @Test
        void turnsEntirelyOnWhichWayRoundTheContrastWent() {
            BackscatterVerdict light = Identification.backscatterVerdict(new BackscatterReading(2, 3));
            BackscatterVerdict dark = Identification.backscatterVerdict(new BackscatterReading(3, 2));
            assertThat(light.pass()).isFalse();
            assertThat(dark.pass()).isTrue();
        }

        @Test
        void refusesADensityAtOrBelowZero() {
            assertThatThrownBy(() -> Identification.backscatterVerdict(new BackscatterReading(0, 2)))
                    .hasMessageContaining("greater than 0");
        }
    }

    @Nested
    class LocationMarkerPlan {

        @Test
        void takesOneFilmWhenTheWeldFitsOnIt() {
            MarkerPlan plan = Identification.locationMarkerPlan(10, 14);
            assertThat(plan.films()).isEqualTo(1);
            assertThat(plan.coversWholeWeld()).isTrue();
        }

        @Test
        void advancesByTheFilmLengthLessTheOverlap() {
            MarkerPlan plan = Identification.locationMarkerPlan(40, 14, 1);
            assertThat(plan.advanceIn()).isEqualTo(13);
            assertThat(plan.films()).isEqualTo(3);
        }

        @Test
        void numbersTheMarkersFromOne() {
            MarkerPlan plan = Identification.locationMarkerPlan(40, 14);
            assertThat(plan.markers().stream().map(LocationMarker::marker)).containsExactly("1", "2", "3");
        }

        @Test
        void neverLeavesAGapBetweenFilms() {
            MarkerPlan plan = Identification.locationMarkerPlan(40, 14, 1);
            List<LocationMarker> markers = plan.markers();
            for (int i = 1; i < markers.size(); i++) {
                assertThat(markers.get(i).startIn())
                        .isLessThan(markers.get(i - 1).endIn());
            }
        }

        @Test
        void coversTheWholeWeld() {
            for (double length : new double[] {10, 25, 40, 63.5}) {
                MarkerPlan plan = Identification.locationMarkerPlan(length, 14, 1);
                assertThat(plan.coversWholeWeld()).isTrue();
            }
        }

        @Test
        void needsMoreFilmsAsTheOverlapGrows() {
            MarkerPlan tight = Identification.locationMarkerPlan(40, 14, 1);
            MarkerPlan wide = Identification.locationMarkerPlan(40, 14, 6);
            assertThat(wide.films()).isGreaterThan(tight.films());
        }

        @Test
        void refusesAnOverlapAsLongAsTheFilm() {
            assertThatThrownBy(() -> Identification.locationMarkerPlan(40, 14, 14))
                    .hasMessageContaining("no film covers new weld");
        }

        @Test
        void refusesAWeldOrFilmLengthAtZero() {
            assertThatThrownBy(() -> Identification.locationMarkerPlan(0, 14)).hasMessageContaining("greater than 0");
        }
    }

    @Nested
    class MarksForShot {

        @Test
        void alwaysCarriesTheIdentificationAndTheLocation() {
            assertThat(Identification.marksForShot("3")).containsExactly("identification", "location 3");
        }

        @Test
        void addsTheFOnAFilmSideIndicator() {
            assertThat(Identification.marksForShot("3", "film", false)).contains("F");
        }

        @Test
        void addsTheBWhenTheBackscatterCheckIsCalledFor() {
            assertThat(Identification.marksForShot("3", "source", true)).contains("B");
        }

        @Test
        void carriesBothWhenBothApply() {
            assertThat(Identification.marksForShot("1", "film", true))
                    .containsExactly("identification", "location 1", "F", "B");
        }

        @Test
        void refusesAShotWithNoLocationMarker() {
            assertThatThrownBy(() -> Identification.marksForShot("")).hasMessageContaining("carries a location marker");
            assertThatThrownBy(() -> Identification.marksForShot(null))
                    .hasMessageContaining("carries a location marker");
        }
    }
}
