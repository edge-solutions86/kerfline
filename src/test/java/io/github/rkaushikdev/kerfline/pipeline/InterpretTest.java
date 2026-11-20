package io.github.rkaushikdev.kerfline.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.density.AreaReading;
import io.github.rkaushikdev.kerfline.film.FilmIdentification;
import io.github.rkaushikdev.kerfline.indication.IndicationInput;
import io.github.rkaushikdev.kerfline.indication.Measure;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.joint.JointInput;
import io.github.rkaushikdev.kerfline.joint.Spec;
import io.github.rkaushikdev.kerfline.personnel.Cert;
import io.github.rkaushikdev.kerfline.personnel.Certification;
import io.github.rkaushikdev.kerfline.personnel.CertificationInput;
import io.github.rkaushikdev.kerfline.personnel.PaperScores;
import io.github.rkaushikdev.kerfline.store.Entry;
import io.github.rkaushikdev.kerfline.store.Journal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InterpretTest {

    private static final Joint JOINT = Spec.makeJoint(
            JointInput.builder().id("J-118").nominalSize(6).schedule(40).build());

    private static FilmReading goodFilm() {
        return FilmReading.builder()
                .film("1")
                .identification(FilmIdentification.builder()
                        .job("C-4471")
                        .day("2025-03-14")
                        .jointId("J-118")
                        .position("1")
                        .build())
                .sourceKind("gamma")
                .indicatorDensity(2.4)
                .areaDensities(AreaReading.unlocated(List.of(2.3, 2.5, 2.4)))
                .indicator(20, "2T")
                .penetratedThicknessIn(0.56)
                .build();
    }

    private static boolean failsOn(FilmGrade graded, String check) {
        return graded.failures().stream().anyMatch(failure -> failure.check().equals(check));
    }

    private static final InterpretationRequest BASE = InterpretationRequest.builder()
            .joint(JOINT)
            .day("2025-03-14")
            .reader("R. Ibarra")
            .code("API1104")
            .build();

    private static Certification certifiedOn(String certifiedOn, String visionOn) {
        return Cert.makeCertification(CertificationInput.builder()
                .person("R. Ibarra")
                .level("II")
                .certifiedOn(certifiedOn)
                .examination(new PaperScores(85, 88, 90))
                .trainingHours(80)
                .experienceHours(900)
                .visionOn(visionOn)
                .build());
    }

    @Nested
    class GradeFilm {

        @Test
        void namesTheChecksItRuns() {
            assertThat(Interpret.qualityChecks())
                    .containsExactly("identification", "density", "variation", "sensitivity", "backscatter");
        }

        @Test
        void passesAGoodFilm() {
            FilmGrade graded = Interpret.gradeFilm(goodFilm());
            assertThat(graded.acceptable()).isTrue();
            assertThat(graded.failures()).isEmpty();
        }

        @Test
        void failsAFilmThatIsTooLight() {
            FilmGrade graded = Interpret.gradeFilm(goodFilm().toBuilder()
                    .indicatorDensity(1.6)
                    .areaDensities(AreaReading.unlocated(List.of(1.6)))
                    .build());
            assertThat(graded.acceptable()).isFalse();
            assertThat(failsOn(graded, "density")).isTrue();
        }

        @Test
        void acceptsTheSameDensityUnderAnXraySource() {
            FilmGrade graded = Interpret.gradeFilm(goodFilm().toBuilder()
                    .sourceKind("xray")
                    .indicatorDensity(1.9)
                    .areaDensities(AreaReading.unlocated(List.of(1.9)))
                    .build());
            assertThat(graded.acceptable()).isTrue();
        }

        @Test
        void failsAFilmWhoseAreaOfInterestWandersOutsideTheBand() {
            FilmGrade graded = Interpret.gradeFilm(goodFilm().toBuilder()
                    .areaDensities(AreaReading.unlocated(List.of(2.4, 1.5)))
                    .build());
            assertThat(failsOn(graded, "variation")).isTrue();
        }

        @Test
        void failsAFilmWithNoIndicatorOnIt() {
            FilmGrade graded =
                    Interpret.gradeFilm(goodFilm().toBuilder().noIndicator().build());
            assertThat(failsOn(graded, "sensitivity")).isTrue();
        }

        @Test
        void failsAFilmShowingAThickerPlaqueThanTheTableCallsFor() {
            FilmGrade graded = Interpret.gradeFilm(
                    goodFilm().toBuilder().indicator(60, "2T").build());
            assertThat(failsOn(graded, "sensitivity")).isTrue();
        }

        @Test
        void failsAFilmShowingOnlyThe4tHole() {
            FilmGrade graded = Interpret.gradeFilm(
                    goodFilm().toBuilder().indicator(20, "4T").build());
            assertThat(failsOn(graded, "sensitivity")).isTrue();
        }

        @Test
        void passesAFilmShowingAThinnerPlaqueWhichIsTheHarderJob() {
            FilmGrade graded = Interpret.gradeFilm(
                    goodFilm().toBuilder().indicator(15, "2T").build());
            assertThat(graded.acceptable()).isTrue();
        }

        @Test
        void passesAFilmShowingThe1tHoleOnThePlaqueCalledFor() {
            FilmGrade graded = Interpret.gradeFilm(
                    goodFilm().toBuilder().indicator(20, "1T").build());
            assertThat(graded.acceptable()).isTrue();
        }

        @Test
        void carriesTheEquivalentPercentageForTheRecordWithoutJudgingOnIt() {
            FilmGrade graded = Interpret.gradeFilm(goodFilm());
            SensitivityCheck sensitivity = graded.sensitivity().orElseThrow();
            assertThat(sensitivity.equivalentPercent()).isGreaterThan(0);
            assertThat(sensitivity.required()).isEqualTo(new HoleIndicator(20, "2T"));
        }

        @Test
        void failsAFilmMissingItsIdentification() {
            FilmGrade graded = Interpret.gradeFilm(goodFilm().toBuilder()
                    .identification(FilmIdentification.builder().job("C-4471").build())
                    .build());
            assertThat(failsOn(graded, "identification")).isTrue();
        }

        @Test
        void failsAFilmOnALightBackscatterLetter() {
            FilmGrade graded = Interpret.gradeFilm(
                    goodFilm().toBuilder().backscatter(1.8, 2.4).build());
            assertThat(failsOn(graded, "backscatter")).isTrue();
        }

        @Test
        void passesAFilmOnADarkBackscatterLetter() {
            FilmGrade graded = Interpret.gradeFilm(
                    goodFilm().toBuilder().backscatter(3, 2.4).build());
            assertThat(graded.acceptable()).isTrue();
        }

        @Test
        void runsEveryCheckRatherThanStoppingAtTheFirstFailure() {
            FilmGrade graded = Interpret.gradeFilm(goodFilm().toBuilder()
                    .identification(FilmIdentification.builder().build())
                    .indicatorDensity(1.2)
                    .noIndicator()
                    .build());
            Set<String> checks =
                    graded.failures().stream().map(QualityFailure::check).collect(Collectors.toSet());
            assertThat(checks.size()).isGreaterThanOrEqualTo(3);
        }

        @Test
        void refusesSomethingThatIsNotAnObject() {
            assertThatThrownBy(() -> Interpret.gradeFilm(null)).hasMessageContaining("graded from an object");
        }

        @Test
        void failsAFilmWithNoDensityReadAtTheIndicatorAndSkipsTheAreaCheck() {
            FilmGrade graded = Interpret.gradeFilm(
                    goodFilm().toBuilder().noIndicatorDensity().build());
            assertThat(graded.failures())
                    .containsExactly(new QualityFailure("density", "no density was read at the indicator"));
            assertThat(graded.density()).isEmpty();
            assertThat(graded.area()).isEmpty();
        }

        @Test
        void writesEachFailureTheWayTheSheetPrintsIt() {
            FilmGrade graded = Interpret.gradeFilm(goodFilm().toBuilder()
                    .identification(FilmIdentification.builder()
                            .job("C-4471")
                            .day("2025-02-30")
                            .jointId("J-118")
                            .position("1")
                            .build())
                    .indicatorDensity(1.6)
                    .areaDensities(List.of())
                    .indicator(60, "2T")
                    .build());
            assertThat(graded.failures())
                    .containsExactly(
                            new QualityFailure("identification", "the film is missing an unreadable date"),
                            new QualityFailure("density", "the film reads light at 1.6"),
                            new QualityFailure("sensitivity", "the film shows 60 2T where 20 2T is required"));
            assertThat(graded.backscatter()).isEmpty();
        }
    }

    @Nested
    class InterpretJoint {

        @Test
        void judgesTheWeldOnceEveryFilmIsGood() {
            Interpretation result = Interpret.interpretJoint(BASE.toBuilder()
                    .films(List.of(goodFilm()))
                    .indications(List.of(Measure.makeIndication(
                            IndicationInput.of("SLAG", 0.5).at(3))))
                    .build());
            assertThat(result.outcome()).isEqualTo(Outcome.ACCEPT);
            assertThat(result.verdict()).isPresent();
        }

        @Test
        void callsARepairWhenTheWeldFailsTheCode() {
            Interpretation result = Interpret.interpretJoint(BASE.toBuilder()
                    .films(List.of(goodFilm()))
                    .indications(List.of(
                            Measure.makeIndication(IndicationInput.of("SLAG", 3).at(3))))
                    .build());
            assertThat(result.outcome()).isEqualTo(Outcome.REPAIR);
        }

        @Test
        void callsAReshootAndGivesNoVerdictWhenAFilmFailsQuality() {
            Interpretation result = Interpret.interpretJoint(BASE.toBuilder()
                    .films(List.of(goodFilm().toBuilder()
                            .indicatorDensity(1.5)
                            .areaDensities(AreaReading.unlocated(List.of(1.5)))
                            .build()))
                    .indications(List.of(Measure.makeIndication(
                            IndicationInput.of("CRACK", 0.5).at(3))))
                    .build());
            assertThat(result.outcome()).isEqualTo(Outcome.RESHOOT);
            assertThat(result.verdict()).isEmpty();
        }

        @Test
        void doesNotJudgeTheWeldAtAllOnABadFilmEvenACleanOne() {
            Interpretation result = Interpret.interpretJoint(BASE.toBuilder()
                    .films(List.of(goodFilm().toBuilder().noIndicator().build()))
                    .indications(List.of())
                    .build());
            assertThat(result.outcome()).isEqualTo(Outcome.RESHOOT);
            assertThat(result.verdict()).isEmpty();
        }

        @Test
        void saysWhichFilmFailedAndWhy() {
            Interpretation result = Interpret.interpretJoint(BASE.toBuilder()
                    .films(List.of(
                            goodFilm(),
                            goodFilm().toBuilder()
                                    .film("2")
                                    .indicatorDensity(1.5)
                                    .areaDensities(AreaReading.unlocated(List.of(1.5)))
                                    .build()))
                    .indications(List.of())
                    .build());
            assertThat(String.join(" ", result.reasons())).containsPattern("^2:|\\s2:");
        }

        @Test
        void callsAReshootWhenNoFilmsWerePresentedAtAll() {
            Interpretation result = Interpret.interpretJoint(
                    BASE.toBuilder().films(List.of()).indications(List.of()).build());
            assertThat(result.outcome()).isEqualTo(Outcome.RESHOOT);
            assertThat(result.reasons()).containsExactly("no films were presented");
        }

        @Test
        void refusesAReaderWhoseCertificationHasLapsed() {
            Certification lapsed = certifiedOn("2018-05-10", "2018-05-10");
            assertThatThrownBy(() -> Interpret.interpretJoint(BASE.toBuilder()
                            .certification(lapsed)
                            .films(List.of(goodFilm()))
                            .indications(List.of())
                            .build()))
                    .hasMessageContaining("may not interpret film");
        }

        @Test
        void allowsAReaderWhoseCertificationIsCurrent() {
            Certification current = certifiedOn("2023-05-10", "2025-01-06");
            Interpretation result = Interpret.interpretJoint(BASE.toBuilder()
                    .certification(current)
                    .films(List.of(goodFilm()))
                    .indications(List.of())
                    .build());
            assertThat(result.outcome()).isEqualTo(Outcome.ACCEPT);
        }

        @Test
        void refusesAnInterpretationWithNoJoint() {
            assertThatThrownBy(() -> Interpret.interpretJoint(
                            BASE.toBuilder().joint(null).films(List.of()).build()))
                    .hasMessageContaining("needs a joint");
        }
    }

    @Nested
    class RecordInterpretation {

        @Test
        void writesAnInterpretationAndAVerdictWhenTheWeldWasJudged() {
            Interpretation result = Interpret.interpretJoint(BASE.toBuilder()
                    .films(List.of(goodFilm()))
                    .indications(List.of())
                    .build());
            Journal journal = Interpret.recordInterpretation(Journal.makeJournal("C-4471"), result);
            assertThat(journal.entries().stream().map(Entry::kind)).containsExactly("interpretation", "verdict");
            assertThat(Journal.verify(journal).intact()).isTrue();
        }

        @Test
        void writesOnlyTheInterpretationOnAReshoot() {
            Interpretation result = Interpret.interpretJoint(
                    BASE.toBuilder().films(List.of()).indications(List.of()).build());
            Journal journal = Interpret.recordInterpretation(Journal.makeJournal("C-4471"), result);
            assertThat(journal.entries().stream().map(Entry::kind)).containsExactly("interpretation");
        }

        @Test
        void recordsHowManyFilmsWereTurnedBack() {
            Interpretation result = Interpret.interpretJoint(BASE.toBuilder()
                    .films(List.of(
                            goodFilm(),
                            goodFilm().toBuilder().film("2").noIndicator().build()))
                    .indications(List.of())
                    .build());
            Journal journal = Interpret.recordInterpretation(Journal.makeJournal("C-4471"), result);
            assertThat(journal.entries().get(0).payload().get("filmsRejected")).isEqualTo(1);
        }

        @Test
        void refusesAResultWithNoJointOnIt() {
            assertThatThrownBy(() -> Interpret.recordInterpretation(Journal.makeJournal("C-4471"), null))
                    .hasMessageContaining("needs a joint");
        }

        @Test
        void writesTheVerdictInTheWordsTheJournalCarries() {
            Interpretation result = Interpret.interpretJoint(BASE.toBuilder()
                    .films(List.of(goodFilm()))
                    .indications(List.of())
                    .build());
            Journal journal = Interpret.recordInterpretation(Journal.makeJournal("C-4471"), result);
            assertThat(journal.entries().get(0).payload())
                    .containsEntry("reader", "R. Ibarra")
                    .containsEntry("films", 1)
                    .containsEntry("filmsRejected", 0)
                    .containsEntry("outcome", "accept");
            assertThat(journal.entries().get(1).payload())
                    .containsEntry("disposition", "accept")
                    .containsEntry("code", "API1104");
        }
    }

    @Nested
    class InterpretInOneStep {

        @Test
        void interpretsAndRecordsInOneStep() {
            RecordedInterpretation recorded = Interpret.interpret(
                    Journal.makeJournal("C-4471"),
                    InterpretationRequest.builder()
                            .joint(JOINT)
                            .day("2025-03-14")
                            .reader("R. Ibarra")
                            .films(List.of(goodFilm()))
                            .indications(List.of())
                            .build());
            assertThat(recorded.result().outcome()).isEqualTo(Outcome.ACCEPT);
            assertThat(recorded.journal().entries()).hasSize(2);
            assertThat(Journal.verify(recorded.journal()).intact()).isTrue();
        }
    }
}
