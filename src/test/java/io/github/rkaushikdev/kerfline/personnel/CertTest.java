package io.github.rkaushikdev.kerfline.personnel;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CertTest {

    private static final PaperScores GOOD_EXAM = new PaperScores(85, 88, 90);

    private static final CertificationInput IBARRA = CertificationInput.builder()
            .person("R. Ibarra")
            .level("II")
            .certifiedOn("2023-05-10")
            .examination(GOOD_EXAM)
            .trainingHours(80)
            .experienceHours(900)
            .visionOn("2025-01-06")
            .build();

    private static final Certification CERTIFICATION = Cert.makeCertification(IBARRA);

    @Nested
    class TheLevels {

        @Test
        void runsOneToThree() {
            assertThat(Cert.levels()).containsExactly("I", "II", "III");
        }

        @Test
        void asksMoreExperienceAtEachLevel() {
            assertThat(Cert.requirementsFor("II").experienceHours())
                    .isGreaterThan(Cert.requirementsFor("I").experienceHours());
            assertThat(Cert.requirementsFor("III").experienceHours())
                    .isGreaterThan(Cert.requirementsFor("II").experienceHours());
        }

        @Test
        void doesNotLetALevelOneInterpretFilm() {
            assertThat(Cert.requirementsFor("I").mayInterpret()).isFalse();
            assertThat(Cert.requirementsFor("II").mayInterpret()).isTrue();
        }

        @Test
        void refusesALevelThatDoesNotExist() {
            assertThatThrownBy(() -> Cert.requirementsFor("IV")).hasMessageContaining("no certification level IV");
        }
    }

    @Nested
    class GradeExamination {

        @Test
        void namesTheThreePapersAndTheTwoMarks() {
            assertThat(Cert.papers()).containsExactly("general", "specific", "practical");
            assertThat(Cert.passMarks()).isEqualTo(new PassMarks(80, 70));
        }

        @Test
        void passesACandidateWhoClearsBothConditions() {
            ExaminationResult result = Cert.gradeExamination(GOOD_EXAM);
            assertThat(result.composite()).isCloseTo(87.67, digits(2));
            assertThat(result.pass()).isTrue();
        }

        @Test
        void failsAHighAverageWithOnePaperUnderSeventy() {
            ExaminationResult result = Cert.gradeExamination(new PaperScores(95, 95, 68));
            assertThat(result.composite()).isEqualTo(86);
            assertThat(result.compositeMet()).isTrue();
            assertThat(result.everyPaperMet()).isFalse();
            assertThat(result.pass()).isFalse();
        }

        @Test
        void failsEveryPaperPassingButTheAverageShort() {
            ExaminationResult result = Cert.gradeExamination(new PaperScores(75, 75, 75));
            assertThat(result.everyPaperMet()).isTrue();
            assertThat(result.compositeMet()).isFalse();
            assertThat(result.pass()).isFalse();
        }

        @Test
        void passesExactlyOnBothMarks() {
            ExaminationResult result = Cert.gradeExamination(new PaperScores(70, 85, 85));
            assertThat(result.composite()).isEqualTo(80);
            assertThat(result.lowestPaper()).isEqualTo(70);
            assertThat(result.pass()).isTrue();
        }

        @Test
        void refusesAScoreOutsideNoughtToAHundred() {
            assertThatThrownBy(() -> Cert.gradeExamination(new PaperScores(101, 88, 90)))
                    .hasMessageContaining("above 100");
            assertThatThrownBy(() -> Cert.gradeExamination(new PaperScores(-1, 88, 90)))
                    .hasMessageContaining("not be below 0");
        }

        @Test
        void refusesAMissingPaper() {
            assertThatThrownBy(() -> Cert.gradeExamination(new PaperScores(85, 88, Double.NaN)))
                    .hasMessageContaining("practical paper");
        }
    }

    @Nested
    class HoursMet {

        @Test
        void meetsALevelWithEnoughOfBoth() {
            HoursCheck met = Cert.hoursMet("II", 80, 900);
            assertThat(met.met()).isTrue();
            assertThat(met.trainingShort()).isEqualTo(0);
        }

        @Test
        void saysHowShortTheExperienceIs() {
            HoursCheck met = Cert.hoursMet("II", 80, 400);
            assertThat(met.met()).isFalse();
            assertThat(met.experienceShort()).isEqualTo(230);
        }

        @Test
        void needsBothAndNotEither() {
            HoursCheck noTraining = Cert.hoursMet("II", 10, 900);
            assertThat(noTraining.experienceMet()).isTrue();
            assertThat(noTraining.met()).isFalse();
        }
    }

    @Nested
    class MakeCertification {

        @Test
        void setsTheTwoClocksIndependently() {
            assertThat(CERTIFICATION.expiresOn()).isEqualTo("2028-05-10");
            assertThat(CERTIFICATION.visionExpiresOn()).isEqualTo("2026-01-06");
        }

        @Test
        void isValidWhenTheExamAndTheHoursAreBothGood() {
            assertThat(CERTIFICATION.valid()).isTrue();
        }

        @Test
        void isNotValidWhenTheExamFailedHoweverGoodTheHoursAre() {
            Certification failed = Cert.makeCertification(IBARRA.toBuilder()
                    .person("D. Whyte")
                    .examination(new PaperScores(95, 95, 68))
                    .trainingHours(400)
                    .experienceHours(5000)
                    .build());
            assertThat(failed.valid()).isFalse();
        }

        @Test
        void refusesACertificationWithNobodyOnIt() {
            assertThatThrownBy(() ->
                            Cert.makeCertification(IBARRA.toBuilder().person("").build()))
                    .hasMessageContaining("names the person");
        }
    }

    @Nested
    class MayInterpret {

        @Test
        void letsACurrentLevelTwoReadFilm() {
            assertThat(Cert.mayInterpret(CERTIFICATION, "2025-03-14").allowed()).isTrue();
        }

        @Test
        void willNotLetALevelOneReadFilmHoweverCurrent() {
            Certification levelOne = Cert.makeCertification(IBARRA.toBuilder()
                    .person("K. Osei")
                    .level("I")
                    .certifiedOn("2024-05-10")
                    .experienceHours(300)
                    .build());
            InterpretPermission check = Cert.mayInterpret(levelOne, "2025-03-14");
            assertThat(check.allowed()).isFalse();
            assertThat(check.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("does not interpret film"));
        }

        @Test
        void stopsOnceTheCertificationExpires() {
            InterpretPermission check = Cert.mayInterpret(CERTIFICATION, "2028-05-11");
            assertThat(check.allowed()).isFalse();
            assertThat(check.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("certification has expired"));
        }

        @Test
        void stopsOnceTheVisionTestLapsesEvenWithYearsOfCertificationLeft() {
            InterpretPermission check = Cert.mayInterpret(CERTIFICATION, "2026-01-07");
            assertThat(check.allowed()).isFalse();
            assertThat(check.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("vision test has lapsed"));
        }

        @Test
        void doesNotAllowReadingBeforeTheCertificateWasIssued() {
            assertThat(Cert.mayInterpret(CERTIFICATION, "2023-05-09").allowed()).isFalse();
        }

        @Test
        void neverAllowsReadingOnAnInvalidCertification() {
            Certification failed = Cert.makeCertification(IBARRA.toBuilder()
                    .person("D. Whyte")
                    .examination(new PaperScores(60, 60, 60))
                    .build());
            assertThat(Cert.mayInterpret(failed, "2025-03-14").allowed()).isFalse();
        }
    }

    @Nested
    class NextDue {

        @Test
        void putsTheSoonerOfTheTwoClocksFirst() {
            List<DueItem> due = Cert.nextDue(CERTIFICATION, "2025-03-14");
            assertThat(due.get(0).what()).isEqualTo("vision test");
            assertThat(due.get(1).what()).isEqualTo("certification");
        }

        @Test
        void countsTheDaysLeft() {
            List<DueItem> due = Cert.nextDue(CERTIFICATION, "2026-01-01");
            assertThat(due.get(0).daysLeft()).isEqualTo(5);
        }

        @Test
        void goesNegativeOnceSomethingHasLapsed() {
            assertThat(Cert.nextDue(CERTIFICATION, "2026-02-01").get(0).daysLeft())
                    .isNegative();
        }
    }

    @Nested
    class LapsingWithin {

        @Test
        void findsNobodyWhenNothingIsDue() {
            assertThat(Cert.lapsingWithin(List.of(CERTIFICATION), "2025-03-14", 30))
                    .isEmpty();
        }

        @Test
        void findsSomebodyWhoseVisionTestIsComingUp() {
            List<Lapsing> lapsing = Cert.lapsingWithin(List.of(CERTIFICATION), "2025-12-20", 30);
            assertThat(lapsing).hasSize(1);
            assertThat(lapsing.get(0).person()).isEqualTo("R. Ibarra");
            assertThat(lapsing.get(0).due().get(0).what()).isEqualTo("vision test");
        }

        @Test
        void refusesANegativeWindow() {
            assertThatThrownBy(() -> Cert.lapsingWithin(List.of(CERTIFICATION), "2025-03-14", -1))
                    .hasMessageContaining("not be below 0");
        }
    }
}
