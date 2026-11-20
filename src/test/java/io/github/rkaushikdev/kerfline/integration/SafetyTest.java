package io.github.rkaushikdev.kerfline.integration;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.personnel.Cert;
import io.github.rkaushikdev.kerfline.personnel.Certification;
import io.github.rkaushikdev.kerfline.personnel.CertificationInput;
import io.github.rkaushikdev.kerfline.personnel.PaperScores;
import io.github.rkaushikdev.kerfline.personnel.Person;
import io.github.rkaushikdev.kerfline.personnel.PersonInput;
import io.github.rkaushikdev.kerfline.personnel.Roster;
import io.github.rkaushikdev.kerfline.personnel.Training;
import io.github.rkaushikdev.kerfline.personnel.TrainingInput;
import io.github.rkaushikdev.kerfline.personnel.TrainingRecord;
import io.github.rkaushikdev.kerfline.safety.AnnualStanding;
import io.github.rkaushikdev.kerfline.safety.BadgeRecord;
import io.github.rkaushikdev.kerfline.safety.Booking;
import io.github.rkaushikdev.kerfline.safety.Boundary;
import io.github.rkaushikdev.kerfline.safety.Camera;
import io.github.rkaushikdev.kerfline.safety.Classification;
import io.github.rkaushikdev.kerfline.safety.CollimatedBoundary;
import io.github.rkaushikdev.kerfline.safety.Collimator;
import io.github.rkaushikdev.kerfline.safety.DoseRate;
import io.github.rkaushikdev.kerfline.safety.DoseStanding;
import io.github.rkaushikdev.kerfline.safety.Dosimetry;
import io.github.rkaushikdev.kerfline.safety.FittedCollimator;
import io.github.rkaushikdev.kerfline.safety.Holder;
import io.github.rkaushikdev.kerfline.safety.Incident;
import io.github.rkaushikdev.kerfline.safety.IncidentRecord;
import io.github.rkaushikdev.kerfline.safety.LeakTest;
import io.github.rkaushikdev.kerfline.safety.NewIncident;
import io.github.rkaushikdev.kerfline.safety.Reading;
import io.github.rkaushikdev.kerfline.safety.ReportingWindow;
import io.github.rkaushikdev.kerfline.safety.RetractionVerdict;
import io.github.rkaushikdev.kerfline.safety.SealedSource;
import io.github.rkaushikdev.kerfline.safety.SourceLog;
import io.github.rkaushikdev.kerfline.safety.SourceUse;
import io.github.rkaushikdev.kerfline.safety.Survey;
import io.github.rkaushikdev.kerfline.safety.SurveyRecord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A day of radiography, from the store to the dose record.
 *
 * <p>The safety classes are the ones where a seam failing quietly matters most, because every one
 * of them answers a question whose wrong answer looks exactly like the right one. This walks the
 * day in the order it happens and checks the handoffs.
 */
class SafetyTest {

    private static final String DAY = "2025-03-14";

    private static final SealedSource SOURCE = SourceLog.makeSource("IR-7741", "IR192", 80, "2025-03-01");

    private static final LeakTest CURRENT_TEST = SourceLog.makeLeakTest("IR-7741", "2025-03-01", 0.0001);

    private static final Certification CERTIFICATION = Cert.makeCertification(CertificationInput.builder()
            .person("R. Ibarra")
            .level("II")
            .certifiedOn("2023-05-10")
            .examination(new PaperScores(85, 88, 90))
            .trainingHours(400)
            .experienceHours(2000)
            .visionOn("2025-01-06")
            .build());

    private static final List<Person> CREW = List.of(
            Roster.makePerson(PersonInput.builder()
                    .name("R. Ibarra")
                    .role("radiographer")
                    .certification(CERTIFICATION)
                    .monitored(true)
                    .build()),
            Roster.makePerson(PersonInput.builder()
                    .name("D. Whyte")
                    .role("assistant")
                    .monitored(true)
                    .build()));

    private static final List<TrainingRecord> TRAINING = List.of(Training.makeTraining(TrainingInput.builder()
            .person("R. Ibarra")
            .method("SAFETY")
            .course("radiation safety refresher")
            .hours(8)
            .day("2025-01-06")
            .build()));

    private static Camera camera() {
        Booking out = SourceLog.checkOut(SOURCE, List.of(CURRENT_TEST), DAY);
        return new Camera("IR192", out.activityCi());
    }

    @Nested
    class BeforeTheSourceLeavesTheStore {

        @Test
        void theCrewIsAllowedToWork() {
            assertThat(Roster.checkCrew(CREW, DAY).mayWork()).isTrue();
        }

        @Test
        void theRadiographerIsCurrentOnSafetyTraining() {
            assertThat(Training.currentToWork(TRAINING, DAY).current()).isTrue();
        }

        @Test
        void theSourceBooksOutWithTheActivityItHasToday() {
            Booking out = SourceLog.checkOut(SOURCE, List.of(CURRENT_TEST), DAY);
            assertThat(out.activityCi()).isLessThan(80);
            assertThat(out.activityCi()).isGreaterThan(70);
        }

        @Test
        void aSourceWhoseLeakTestHasRunOutDoesNotLeaveAtAll() {
            assertThatThrownBy(() -> SourceLog.checkOut(SOURCE, List.of(CURRENT_TEST), "2025-10-01"))
                    .hasMessageContaining("run out");
        }
    }

    @Nested
    class SettingTheBoundary {

        private final Camera camera = camera();

        @Test
        void theRopeGoesALongWayOutOnABareSource() {
            assertThat(Boundary.barricadeDistanceFt(camera)).isGreaterThan(250);
        }

        @Test
        void theJobDoesNotFitInATightBayBare() {
            assertThat(Boundary.fitsInSpace(camera, 100).fits()).isFalse();
        }

        @Test
        void aCollimatorBringsTheBoundaryInBehindTheCameraButNotInFront() {
            CollimatedBoundary bounds = Collimator.collimatedBoundaryFt(camera, "DIRECTIONAL");
            assertThat(bounds.outsideBeamFt()).isLessThan(bounds.inBeamFt());
            assertThat(bounds.inBeamFt()).isCloseTo(Boundary.barricadeDistanceFt(camera), digits(1));
        }

        @Test
        void theToolPicksTheLightestSleeveThatFitsTheBay() {
            Optional<FittedCollimator> fitted = Collimator.smallestFitting(camera, 100);
            assertThat(fitted).isPresent();
            assertThat(fitted.orElseThrow().outsideBeamFt()).isLessThanOrEqualTo(100);
        }

        @Test
        void aSurveyAtTheRopeBacksTheCalculationUp() {
            double boundaryFt = Boundary.barricadeDistanceFt(camera);
            SurveyRecord survey = Survey.makeSurvey(
                    "boundary",
                    DAY,
                    "Ludlum 3",
                    List.of(new Reading(boundaryFt, DoseRate.doseRateAt(camera, boundaryFt))));
            assertThat(Survey.judgeBoundary(survey, boundaryFt).pass()).isTrue();
        }
    }

    @Nested
    class AfterTheShot {

        @Test
        void aCameraReadingAtBackgroundLetsTheCrewWalkBackIn() {
            SurveyRecord survey = Survey.makeSurvey("retraction", DAY, "Ludlum 3", List.of(new Reading(1, 0.03)));
            assertThat(Survey.judgeRetraction(survey).sourceIsHome()).isTrue();
        }

        @Test
        void aCameraStillReadingHotStopsEverybodyWhereTheyAre() {
            SurveyRecord survey = Survey.makeSurvey("retraction", DAY, "Ludlum 3", List.of(new Reading(1, 480)));
            RetractionVerdict verdict = Survey.judgeRetraction(survey);
            assertThat(verdict.sourceIsHome()).isFalse();
            assertThat(verdict.action()).contains("do not approach");
        }

        @Test
        void theDayIsNotFinishedUntilAllThreeSurveysAreOnFile() {
            SurveyRecord boundary = Survey.makeSurvey("boundary", DAY, "Ludlum 3", List.of(new Reading(300, 1)));
            assertThat(Survey.missingSurveys(List.of(boundary))).containsExactly("perimeter", "retraction");
        }
    }

    @Nested
    class TheDoseThatCameOfIt {

        private final Camera camera = camera();

        @Test
        void standingAtTheRopeForAnHourIsAVerySmallDose() {
            double atRope = DoseRate.doseForExposure(camera, Boundary.barricadeDistanceFt(camera), 60);
            assertThat(atRope).isLessThan(3);
        }

        @Test
        void standingCloseIsAVeryShortTimeBeforeALimit() {
            double close = DoseRate.minutesToDose(camera, 5, 100);
            double far = DoseRate.minutesToDose(camera, 300, 100);
            assertThat(close).isLessThan(1);
            assertThat(far).isGreaterThan(close * 100);
        }

        @Test
        void aQuietQuarterLeavesTheAnnualRecordWellInsideTheLimits() {
            BadgeRecord badge = Dosimetry.makeBadgeRecord("R. Ibarra", "2025-01-01", "2025-03-31", 120);
            AnnualStanding standing = Dosimetry.judgeAnnual(List.of(badge), "2025-06-30");
            assertThat(standing.standing()).isEqualTo(DoseStanding.WITHIN_LIMITS);
            assertThat(standing.standing().label()).isEqualTo("within limits");
            assertThat(standing.period().complete()).isTrue();
        }

        @Test
        void aBadgeThatNeverCameBackStopsTheRecordReadingAsSafe() {
            BadgeRecord missing = Dosimetry.makeBadgeRecord("R. Ibarra", "2025-04-01", "2025-06-30", null, false);
            assertThat(Dosimetry.judgeAnnual(List.of(missing), "2025-06-30")
                            .standing()
                            .label())
                    .isEqualTo("incomplete record");
        }
    }

    @Nested
    class WhenSomethingGoesWrong {

        @Test
        void aSourceThatFailedToRetractIsReportableInsideADayExposedOrNot() {
            Classification clock = Incident.classify("disconnect", 0);
            assertThat(clock.window()).isEqualTo(ReportingWindow.ONE_DAY);
            assertThat(clock.window().label()).isEqualTo("oneDay");
        }

        @Test
        void theIncidentCarriesTheDateItHasToBeReportedBy() {
            IncidentRecord incident = Incident.makeIncident(NewIncident.builder()
                    .reference("INC-1")
                    .kind("disconnect")
                    .day(DAY)
                    .detail("source hung up in the guide tube, recovered with the crank")
                    .build());
            assertThat(incident.reportBy()).isEqualTo("2025-03-15");
            IncidentRecord reported = Incident.markReported(incident, "2025-03-15");
            assertThat(reported.filing().orElseThrow().onTime()).isTrue();
        }

        @Test
        void theLogCanSayWhoHadTheSourceThatDay() {
            List<SourceUse> uses = List.of(SourceLog.makeUse("IR-7741", DAY, "R. Ibarra", "Station 6", 45, 12));
            List<Holder> holders = SourceLog.whoHadIt(uses, "IR-7741", DAY);
            assertThat(holders).hasSize(1);
            assertThat(holders.get(0).user()).isEqualTo("R. Ibarra");
        }
    }
}
