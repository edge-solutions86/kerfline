package io.github.rkaushikdev.kerfline.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.geom.ViewingTechnique;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.joint.JointInput;
import io.github.rkaushikdev.kerfline.joint.Spec;
import io.github.rkaushikdev.kerfline.safety.LeakTest;
import io.github.rkaushikdev.kerfline.safety.SealedSource;
import io.github.rkaushikdev.kerfline.safety.SourceLog;
import io.github.rkaushikdev.kerfline.store.Entry;
import io.github.rkaushikdev.kerfline.store.Journal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ShootTest {

    private static final Joint JOINT = Spec.makeJoint(
            JointInput.builder().id("J-118").nominalSize(6).schedule(40).build());

    private static final Joint THICK =
            Spec.makeJoint(JointInput.builder().id("J-9").odIn(24).wallIn(3).build());

    private static final SealedSource SOURCE = SourceLog.makeSource("IR-7741", "IR192", 80, "2025-03-01");

    private static final List<LeakTest> LEAK_TESTS = List.of(SourceLog.makeLeakTest("IR-7741", "2025-03-01", 0.0001));

    private static final ShotRequest BASE = ShotRequest.builder()
            .job("C-4471")
            .joint(JOINT)
            .day("2025-03-14")
            .source(SOURCE)
            .leakTests(LEAK_TESTS)
            .sourceToObjectIn(48)
            .objectToFilmIn(6.625)
            .build();

    @Nested
    class PlanShot {

        private final ShotPlan plan = Shoot.planShot(BASE);

        @Test
        void booksTheSourceOutAndDecaysItToTheDay() {
            assertThat(plan.source().serial()).isEqualTo("IR-7741");
            assertThat(plan.source().activityCi()).isLessThan(80);
            assertThat(plan.source().activityCi()).isGreaterThan(70);
        }

        @Test
        void buildsTheTechniqueOffTheSourceItBooked() {
            assertThat(plan.technique().exposure().orElseThrow().isotope()).isEqualTo("IR192");
            assertThat(plan.technique().viewing().technique()).isEqualTo(ViewingTechnique.DWSI);
        }

        @Test
        void plansAFilmForEachShotRoundTheJoint() {
            assertThat(plan.shots().size()).isGreaterThan(1);
            assertThat(plan.shots().get(0).clock()).isEqualTo("12:00");
        }

        @Test
        void putsTheLeadMarksOnEveryShot() {
            for (MarkedShot shot : plan.shots()) {
                assertThat(shot.marks()).contains("identification");
                assertThat(String.join(" ", shot.marks())).containsPattern("location \\d+");
            }
        }

        @Test
        void callsForTheBackscatterLetterOnlyWhenNothingIsBehindTheFilm() {
            ShotPlan leaded = Shoot.planShot(BASE);
            assertThat(leaded.shots().get(0).marks()).doesNotContain("B");
            ShotPlan bare = Shoot.planShot(BASE.toBuilder().screens("NONE").build());
            assertThat(bare.shots().get(0).marks()).contains("B");
        }

        @Test
        void marksTheFilmSideIndicatorWhenTheShotCallsForIt() {
            ShotPlan filmSide = Shoot.planShot(BASE.toBuilder().side("film").build());
            assertThat(filmSide.shots().get(0).marks()).contains("F");
        }

        @Test
        void isReadyWhenEverythingResolved() {
            assertThat(plan.ready()).isTrue();
            assertThat(plan.problems()).isEmpty();
        }

        @Test
        void refusesASourceWhoseLeakTestHasRunOut() {
            assertThatThrownBy(() ->
                            Shoot.planShot(BASE.toBuilder().day("2025-10-01").build()))
                    .hasMessageContaining("run out");
        }

        @Test
        void refusesASourceThatHasNeverBeenLeakTested() {
            assertThatThrownBy(() ->
                            Shoot.planShot(BASE.toBuilder().leakTests(List.of()).build()))
                    .hasMessageContaining("no leak test");
        }

        @Test
        void refusesAShotWithNoSource() {
            assertThatThrownBy(
                            () -> Shoot.planShot(BASE.toBuilder().source(null).build()))
                    .hasMessageContaining("needs a source");
        }

        @Test
        void refusesADateThatIsNotARealDay() {
            assertThatThrownBy(() ->
                            Shoot.planShot(BASE.toBuilder().day("2025-02-30").build()))
                    .hasMessageContaining("not a real calendar day");
        }

        @Test
        void isNotReadyWhenTheTechniqueCouldNotBeWorkedOut() {
            ShotPlan thick = Shoot.planShot(BASE.toBuilder().joint(THICK).build());
            assertThat(thick.ready()).isFalse();
            assertThat(thick.problems()).isNotEmpty();
        }
    }

    @Nested
    class RecordShot {

        @Test
        void writesAnEntryAgainstTheJoint() {
            Journal journal = Shoot.recordShot(Journal.makeJournal("C-4471"), Shoot.planShot(BASE));
            assertThat(journal.entries()).hasSize(1);
            assertThat(journal.entries().get(0).kind()).isEqualTo("shot");
            assertThat(journal.entries().get(0).subject()).isEqualTo("J-118");
        }

        @Test
        void recordsWhatWasActuallyUsed() {
            Journal journal = Shoot.recordShot(Journal.makeJournal("C-4471"), Shoot.planShot(BASE));
            Map<String, Object> payload = journal.entries().get(0).payload();
            assertThat(payload.get("source")).isEqualTo("IR-7741");
            assertThat(payload.get("technique")).isEqualTo("DWSI");
            assertThat((Integer) payload.get("films")).isGreaterThan(1);
            assertThat(payload.get("ready")).isEqualTo(true);
        }

        @Test
        void recordsAPlanThatWasNotReadyWithItsProblems() {
            Journal journal = Shoot.recordShot(
                    Journal.makeJournal("C-4471"),
                    Shoot.planShot(BASE.toBuilder().joint(THICK).build()));
            Entry entry = journal.entries().get(0);
            assertThat(entry.payload().get("ready")).isEqualTo(false);
            assertThat(entry.payload().get("problems"))
                    .asInstanceOf(InstanceOfAssertFactories.LIST)
                    .isNotEmpty();
        }

        @Test
        void keepsTheJournalChainIntact() {
            Journal journal = Shoot.recordShot(Journal.makeJournal("C-4471"), Shoot.planShot(BASE));
            assertThat(Journal.verify(journal).intact()).isTrue();
        }

        @Test
        void refusesAPlanWithNoJointOnIt() {
            assertThatThrownBy(() -> Shoot.recordShot(Journal.makeJournal("C-4471"), null))
                    .hasMessageContaining("needs a plan with a joint");
        }

        @Test
        void writesThePayloadTheOriginalJournalCarried() {
            ShotPlan plan = Shoot.planShot(BASE);
            Map<String, Object> payload = Shoot.recordShot(Journal.makeJournal("C-4471"), plan)
                    .entries()
                    .get(0)
                    .payload();
            assertThat(payload.keySet())
                    .containsExactly(
                            "source",
                            "isotope",
                            "activityCi",
                            "technique",
                            "exposureMinutes",
                            "films",
                            "ready",
                            "problems");
            assertThat(payload.get("isotope")).isEqualTo("IR192");
            assertThat(payload.get("activityCi")).isEqualTo(plan.source().activityCi());
            assertThat(payload.get("exposureMinutes"))
                    .isEqualTo(plan.technique().exposure().orElseThrow().minutes());
        }
    }

    @Nested
    class ShootInOneStep {

        @Test
        void plansAndRecordsInOneStep() {
            RecordedShot recorded = Shoot.shoot(Journal.makeJournal("C-4471"), BASE);
            assertThat(recorded.plan().ready()).isTrue();
            assertThat(recorded.journal().entries()).hasSize(1);
        }

        @Test
        void chainsOntoAnExistingJournal() {
            RecordedShot first = Shoot.shoot(Journal.makeJournal("C-4471"), BASE);
            RecordedShot second = Shoot.shoot(
                    first.journal(), BASE.toBuilder().day("2025-03-15").build());
            assertThat(second.journal().entries()).hasSize(2);
            assertThat(Journal.verify(second.journal()).intact()).isTrue();
        }
    }

    @Nested
    class FilmLabels {

        @Test
        void labelsAJacketPerShot() {
            ShotPlan plan = Shoot.planShot(BASE);
            List<String> labels = Shoot.filmLabels(plan);
            assertThat(labels).hasSize(plan.shots().size());
            assertThat(labels.get(0)).isEqualTo("J-118-1");
        }

        @Test
        void givesEveryFilmADifferentLabel() {
            List<String> labels = Shoot.filmLabels(Shoot.planShot(BASE));
            assertThat(new HashSet<>(labels)).hasSameSizeAs(labels);
        }
    }
}
