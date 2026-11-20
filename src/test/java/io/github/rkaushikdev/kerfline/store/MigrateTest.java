package io.github.rkaushikdev.kerfline.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MigrateTest {

    private static Entry old(String kind, Map<String, Object> payload, int version) {
        return new Entry(1, kind, "2023-05-10", "J-118", payload, "a1b2c3d4", "abcd1234", version);
    }

    private static Entry old(String kind, Map<String, Object> payload) {
        return old(kind, payload, 1);
    }

    @Nested
    class TheVersionTable {

        @Test
        void knowsEveryEntryKind() {
            assertThat(Migrate.knownKinds()).contains("joint", "verdict");
        }

        @Test
        void reportsTheCurrentVersionOfEach() {
            assertThat(Migrate.currentVersion("joint")).isEqualTo(2);
            assertThat(Migrate.currentVersion("note")).isEqualTo(1);
        }

        @Test
        void refusesAKindItDoesNotCarry() {
            assertThatThrownBy(() -> Migrate.currentVersion("invoice")).hasMessageContaining("no record kind invoice");
        }

        @Test
        void spotsAnEntryThatNeedsBringingForward() {
            assertThat(Migrate.needsMigration(old("joint", Map.of()))).isTrue();
            assertThat(Migrate.needsMigration(old("joint", Map.of(), 2))).isFalse();
            assertThat(Migrate.needsMigration(old("note", Map.of()))).isFalse();
        }

        @Test
        void treatsAnEntryWithNoVersionAsVersionOne() {
            Entry written = Journal.append(Journal.makeJournal("C-4471"), EntryDraft.of("joint", "2025-03-10", "J-118"))
                    .entries()
                    .get(0);
            assertThat(written.version()).isEqualTo(1);
            assertThat(Migrate.needsMigration(written)).isTrue();
        }
    }

    @Nested
    class MigrateEntry {

        @Test
        void renamesTheOldThicknessFieldOnAJoint() {
            MigratedEntry migrated = Migrate.migrateEntry(old("joint", Map.of("thickness", 0.28, "id", "J-118")));
            assertThat(migrated.payload().get("wallIn")).isEqualTo(0.28);
            assertThat(migrated.payload()).doesNotContainKey("thickness");
            assertThat(migrated.payload().get("id")).isEqualTo("J-118");
        }

        @Test
        void leavesAJointThatNeverHadTheOldFieldAlone() {
            MigratedEntry migrated = Migrate.migrateEntry(old("joint", Map.of("wallIn", 0.28)));
            assertThat(migrated.payload().get("wallIn")).isEqualTo(0.28);
        }

        @Test
        void fillsInTheMissingReadyFlagOnAnOldShot() {
            assertThat(Migrate.migrateEntry(old("shot", Map.of("films", 3)))
                            .payload()
                            .get("ready"))
                    .isEqualTo(true);
        }

        @Test
        void doesNotOverwriteAFieldTheRecordAlreadyHad() {
            assertThat(Migrate.migrateEntry(old("shot", Map.of("films", 3, "ready", false)))
                            .payload()
                            .get("ready"))
                    .isEqualTo(false);
        }

        @Test
        void marksAVerdictThatNeverRecordedAClauseRatherThanInventingOne() {
            MigratedEntry migrated = Migrate.migrateEntry(old("verdict", Map.of("disposition", "accept")));
            assertThat(migrated.payload().get("clause")).isEqualTo("not recorded");
            assertThat(migrated.payload().get("reasons")).isEqualTo(List.of());
        }

        @Test
        void landsOnTheCurrentVersionAndSaysWhereItCameFrom() {
            MigratedEntry migrated = Migrate.migrateEntry(old("joint", Map.of("thickness", 0.28)));
            assertThat(migrated.version()).isEqualTo(2);
            assertThat(migrated.migratedFrom()).containsExactly(1);
        }

        @Test
        void leavesACurrentEntryUntouchedAndSaysSo() {
            Entry entry = old("joint", Map.of("wallIn", 0.28), 2);
            MigratedEntry migrated = Migrate.migrateEntry(entry);
            assertThat(migrated.payload()).isEqualTo(Map.of("wallIn", 0.28));
            assertThat(migrated.migratedFrom()).isEmpty();
            assertThat(migrated.migrated()).isFalse();
        }

        @Test
        void neverChangesTheChecksumWhichAttestsToWhatWasWritten() {
            Entry entry = old("joint", Map.of("thickness", 0.28));
            assertThat(Migrate.migrateEntry(entry).checksum()).isEqualTo(entry.checksum());
        }

        @Test
        void leavesTheEntryItWasGivenAlone() {
            Entry entry = old("joint", Map.of("thickness", 0.28));
            Migrate.migrateEntry(entry);
            assertThat(entry.payload().get("thickness")).isEqualTo(0.28);
            assertThat(entry.version()).isEqualTo(1);
        }

        @Test
        void refusesARecordFromANewerVersionThanTheToolKnows() {
            assertThatThrownBy(() -> Migrate.migrateEntry(old("joint", Map.of(), 5)))
                    .hasMessageContaining("version 5 and the tool knows 2");
        }

        @Test
        void refusesWhenNoMigrationExistsForAStep() {
            assertThatThrownBy(() -> Migrate.migrateEntry(old("interpretation", Map.of(), 0)))
                    .hasMessageContaining("no migration from version");
        }

        @Test
        void refusesSomethingThatIsNotARecord() {
            assertThatThrownBy(() -> Migrate.migrateEntry(null)).hasMessageContaining("must be an object");
        }
    }

    @Nested
    class MigrateJournal {

        private final Journal journal = new Journal(
                "C-4471",
                List.of(
                        old("joint", Map.of("thickness", 0.28)),
                        old("verdict", Map.of("disposition", "accept")),
                        old("note", Map.of("text", "reshot"))));

        @Test
        void bringsEveryEntryForward() {
            MigratedJournal migrated = Migrate.migrateJournal(journal);
            assertThat(migrated.entries().get(0).payload().get("wallIn")).isEqualTo(0.28);
            assertThat(migrated.entries().get(1).payload().get("clause")).isEqualTo("not recorded");
        }

        @Test
        void countsWhatItMoved() {
            assertThat(Migrate.migrateJournal(journal).migrated()).isEqualTo(2);
        }

        @Test
        void leavesAnAlreadyCurrentJournalAlone() {
            Journal current = new Journal("C-1", List.of(old("note", Map.of("text", "x"))));
            assertThat(Migrate.migrateJournal(current).migrated()).isZero();
        }

        @Test
        void keepsTheJournalName() {
            assertThat(Migrate.migrateJournal(journal).name()).isEqualTo("C-4471");
        }

        @Test
        void refusesSomethingThatIsNotAJournal() {
            assertThatThrownBy(() -> Migrate.migrateJournal(null)).hasMessageContaining("not a journal");
        }
    }

    @Nested
    class SurveyJournal {

        @Test
        void countsTheEntriesByKindAndVersionWithoutChangingAnything() {
            Journal journal = new Journal(
                    "C-4471", List.of(old("joint", Map.of()), old("joint", Map.of(), 2), old("note", Map.of())));
            JournalSurvey survey = Migrate.surveyJournal(journal);
            assertThat(survey.entries()).isEqualTo(3);
            assertThat(survey.byVersion().get("joint v1")).isEqualTo(1);
            assertThat(survey.byVersion().get("joint v2")).isEqualTo(1);
            assertThat(survey.byVersion().get("note v1")).isEqualTo(1);
        }

        @Test
        void countsHowManyNeedBringingForward() {
            Journal journal = new Journal("C-1", List.of(old("joint", Map.of()), old("note", Map.of())));
            assertThat(Migrate.surveyJournal(journal).needing()).isEqualTo(1);
        }

        @Test
        void surveysAnEmptyJournal() {
            assertThat(Migrate.surveyJournal(new Journal("C-0", List.of())))
                    .isEqualTo(new JournalSurvey(0, Map.of(), 0));
        }
    }
}
