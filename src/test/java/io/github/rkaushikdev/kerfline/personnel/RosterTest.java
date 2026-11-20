package io.github.rkaushikdev.kerfline.personnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RosterTest {

    private static final PaperScores GOOD_EXAM = new PaperScores(85, 88, 90);

    private static Certification cert(String level, String certifiedOn, String visionOn) {
        return Cert.makeCertification(CertificationInput.builder()
                .person("x")
                .level(level)
                .certifiedOn(certifiedOn)
                .examination(GOOD_EXAM)
                .trainingHours(400)
                .experienceHours(2000)
                .visionOn(visionOn)
                .build());
    }

    private static PersonInput.Builder person(String name, String role) {
        return PersonInput.builder().name(name).role(role);
    }

    private static final Person IBARRA = Roster.makePerson(person("R. Ibarra", "radiographer")
            .certification(cert("II", "2023-05-10", "2025-01-06"))
            .monitored(true)
            .build());

    private static final Person WHYTE =
            Roster.makePerson(person("D. Whyte", "assistant").monitored(true).build());

    private static final Person OSEI = Roster.makePerson(person("K. Osei", "radiographer")
            .certification(cert("III", "2022-08-01", "2025-02-01"))
            .monitored(true)
            .build());

    private static String reasons(CrewCheck check) {
        return String.join(" ", check.reasons());
    }

    @Nested
    class MakePerson {

        @Test
        void namesTheRoles() {
            assertThat(Roster.roles()).containsExactly("radiographer", "assistant", "rso");
            assertThat(Roster.minimumCrew()).isEqualTo(2);
        }

        @Test
        void buildsACrewMember() {
            assertThat(IBARRA.name()).isEqualTo("R. Ibarra");
            assertThat(IBARRA.monitored()).isTrue();
        }

        @Test
        void refusesSomebodyWithNoName() {
            assertThatThrownBy(() -> Roster.makePerson(person(" ", "assistant").build()))
                    .hasMessageContaining("has a name");
        }

        @Test
        void refusesARoleItDoesNotKnow() {
            assertThatThrownBy(() -> Roster.makePerson(person("X", "driver").build()))
                    .hasMessageContaining("a role is one of");
        }

        @Test
        void treatsMonitoringAsFalseUnlessItIsSaid() {
            assertThat(Roster.makePerson(person("X", "assistant").build()).monitored())
                    .isFalse();
        }
    }

    @Nested
    class CanInterpret {

        @Test
        void letsACurrentLevelTwoRead() {
            assertThat(Roster.canInterpret(IBARRA, "2025-03-14").allowed()).isTrue();
        }

        @Test
        void refusesSomebodyWithNoCertificationAtAll() {
            InterpretPermission check = Roster.canInterpret(WHYTE, "2025-03-14");
            assertThat(check.allowed()).isFalse();
            assertThat(check.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("holds no certification"));
        }

        @Test
        void refusesOnceTheVisionTestHasLapsed() {
            InterpretPermission check = Roster.canInterpret(IBARRA, "2026-06-01");
            assertThat(check.allowed()).isFalse();
            assertThat(check.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("vision test"));
        }

        @Test
        void namesThePersonInTheReason() {
            assertThat(Roster.canInterpret(IBARRA, "2026-06-01").reason())
                    .hasValueSatisfying(reason -> assertThat(reason).startsWith("R. Ibarra:"));
        }

        @Test
        void listsEverybodyWhoMayReadOnADay() {
            assertThat(Roster.whoCanInterpret(List.of(IBARRA, WHYTE, OSEI), "2025-03-14").stream()
                            .map(Person::name))
                    .containsExactly("R. Ibarra", "K. Osei");
        }
    }

    @Nested
    class CheckCrew {

        @Test
        void letsAProperCrewWork() {
            CrewCheck check = Roster.checkCrew(List.of(IBARRA, WHYTE), "2025-03-14");
            assertThat(check.mayWork()).isTrue();
            assertThat(check.reasons()).isEmpty();
            assertThat(check.readers()).containsExactly("R. Ibarra");
        }

        @Test
        void refusesACrewOfOneHoweverWellCertified() {
            CrewCheck check = Roster.checkCrew(List.of(IBARRA), "2025-03-14");
            assertThat(check.mayWork()).isFalse();
            assertThat(reasons(check)).contains("at least 2 people");
        }

        @Test
        void refusesACrewWithNobodyWhoCanReadFilm() {
            Person other = Roster.makePerson(
                    person("M. Lund", "assistant").monitored(true).build());
            CrewCheck check = Roster.checkCrew(List.of(WHYTE, other), "2025-03-14");
            assertThat(check.mayWork()).isFalse();
            assertThat(reasons(check)).contains("nobody on the crew may interpret");
        }

        @Test
        void refusesACrewWithNoRadiographerOnIt() {
            Person other = Roster.makePerson(
                    person("M. Lund", "assistant").monitored(true).build());
            assertThat(reasons(Roster.checkCrew(List.of(WHYTE, other), "2025-03-14")))
                    .contains("no radiographer");
        }

        @Test
        void namesAnybodyWhoIsNotAMonitoredRadiationWorker() {
            Person unbadged = Roster.makePerson(person("T. Kerr", "assistant").build());
            CrewCheck check = Roster.checkCrew(List.of(IBARRA, unbadged), "2025-03-14");
            assertThat(check.mayWork()).isFalse();
            assertThat(reasons(check)).contains("T. Kerr");
        }

        @Test
        void givesEveryReasonAtOnce() {
            Person unbadged = Roster.makePerson(person("T. Kerr", "assistant").build());
            assertThat(Roster.checkCrew(List.of(unbadged), "2025-03-14")
                            .reasons()
                            .size())
                    .isGreaterThanOrEqualTo(3);
        }

        @Test
        void turnsAWorkingCrewAwayOnceTheCertificationLapses() {
            assertThat(Roster.checkCrew(List.of(IBARRA, WHYTE), "2025-03-14").mayWork())
                    .isTrue();
            assertThat(Roster.checkCrew(List.of(IBARRA, WHYTE), "2026-06-01").mayWork())
                    .isFalse();
        }
    }

    @Nested
    class Signatory {

        @Test
        void picksTheMostSeniorCurrentReader() {
            assertThat(Roster.signatory(List.of(IBARRA, OSEI, WHYTE), "2025-03-14"))
                    .hasValueSatisfying(person -> assertThat(person.name()).isEqualTo("K. Osei"));
        }

        @Test
        void fallsToTheNextOneWhenTheSeniorReaderHasLapsed() {
            Person lapsed = Roster.makePerson(person("K. Osei", "radiographer")
                    .certification(cert("III", "2016-08-01", "2016-08-01"))
                    .monitored(true)
                    .build());
            assertThat(Roster.signatory(List.of(IBARRA, lapsed), "2025-03-14"))
                    .hasValueSatisfying(person -> assertThat(person.name()).isEqualTo("R. Ibarra"));
        }

        @Test
        void isNobodyWhenNoOneMaySign() {
            assertThat(Roster.signatory(List.of(WHYTE), "2025-03-14")).isEmpty();
        }
    }

    @Nested
    class QualifiedLevels {

        @Test
        void listsEverybodyWhoseLevelPermitsInterpretation() {
            assertThat(Roster.qualifiedLevels(List.of(IBARRA, WHYTE, OSEI)))
                    .containsExactly(new QualifiedLevel("R. Ibarra", "II"), new QualifiedLevel("K. Osei", "III"));
        }

        @Test
        void leavesOutALevelOneWhoDoesNotInterpret() {
            Person trainee = Roster.makePerson(person("S. Pike", "assistant")
                    .certification(cert("I", "2024-05-10", "2025-01-06"))
                    .monitored(true)
                    .build());
            assertThat(Roster.qualifiedLevels(List.of(trainee))).isEmpty();
        }

        @Test
        void leavesOutAnybodyWithNoCertification() {
            assertThat(Roster.qualifiedLevels(List.of(WHYTE))).isEmpty();
        }
    }
}
