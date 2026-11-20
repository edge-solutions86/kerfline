package io.github.rkaushikdev.kerfline.store;

import static io.github.rkaushikdev.kerfline.store.JournalTest.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SnapshotTest {

    private static final Journal JOURNAL = Journal.appendAll(
            Journal.makeJournal("C-4471"),
            List.of(
                    entry("shot", "2025-03-10", "J-2"),
                    entry("verdict", "2025-03-11", "J-2", Map.of("disposition", "repair")),
                    entry("shot", "2025-03-12", "J-10"),
                    entry("verdict", "2025-03-14", "J-2", Map.of("disposition", "accept")),
                    entry("verdict", "2025-03-14", "J-10", Map.of("disposition", "accept")),
                    entry("verdict", "2025-03-20", "J-2", Map.of("disposition", "repair"))));

    @Nested
    class SnapshotAt {

        @Test
        void dropsTheEntriesWrittenLater() {
            Snapshot snapshot = Snapshot.snapshotAt(JOURNAL, "2025-03-12");
            assertThat(snapshot.entries()).hasSize(3);
            assertThat(snapshot.dropped()).isEqualTo(3);
        }

        @Test
        void keepsEntriesWrittenOnTheDayItself() {
            assertThat(Snapshot.snapshotAt(JOURNAL, "2025-03-14").entries()).hasSize(5);
        }

        @Test
        void isTheWholeJournalAtTheEnd() {
            Snapshot snapshot = Snapshot.snapshotAt(JOURNAL, "2025-12-31");
            assertThat(snapshot.entries()).hasSameSizeAs(JOURNAL.entries());
            assertThat(snapshot.dropped()).isZero();
        }

        @Test
        void isEmptyBeforeAnythingHappened() {
            assertThat(Snapshot.snapshotAt(JOURNAL, "2025-01-01").entries()).isEmpty();
        }

        @Test
        void keepsTheJobNameAndRecordsWhatItIsAsOf() {
            Snapshot snapshot = Snapshot.snapshotAt(JOURNAL, "2025-03-12");
            assertThat(snapshot.name()).isEqualTo("C-4471");
            assertThat(snapshot.asOf()).isEqualTo("2025-03-12");
        }

        @Test
        void verifiesAgainstTheSameChainTheJournalDid() {
            assertThat(Snapshot.verifySnapshot(Snapshot.snapshotAt(JOURNAL, "2025-03-14"))
                            .intact())
                    .isTrue();
            assertThat(Snapshot.verifySnapshot(Snapshot.snapshotAt(JOURNAL, "2025-01-01"))
                            .intact())
                    .isTrue();
        }

        @Test
        void refusesSomethingThatIsNotAJournal() {
            assertThatThrownBy(() -> Snapshot.snapshotAt(null, "2025-03-14")).hasMessageContaining("not a journal");
        }
    }

    @Nested
    class StateAt {

        @Test
        void readsTheRecordAsItStood() {
            assertThat(Snapshot.stateAt(JOURNAL, "2025-03-11")).isEqualTo(Map.of("J-2", "repair"));
        }

        @Test
        void movesAsTheRecordMoves() {
            assertThat(Snapshot.stateAt(JOURNAL, "2025-03-14")).isEqualTo(Map.of("J-2", "accept", "J-10", "accept"));
        }

        @Test
        void showsAJointReopenedLater() {
            assertThat(Snapshot.stateAt(JOURNAL, "2025-03-20").get("J-2")).isEqualTo("repair");
        }

        @Test
        void isEmptyBeforeAnythingWasJudged() {
            assertThat(Snapshot.stateAt(JOURNAL, "2025-03-10")).isEmpty();
        }

        @Test
        void readsInRegisterOrder() {
            assertThat(Snapshot.stateAt(JOURNAL, "2025-03-14").keySet()).containsExactly("J-2", "J-10");
        }
    }

    @Nested
    class DiffStates {

        @Test
        void findsNothingBetweenTwoIdenticalStates() {
            StateDiff diff = Snapshot.diffStates(Map.of("J-1", "accept"), Map.of("J-1", "accept"));
            assertThat(diff.quiet()).isTrue();
            assertThat(diff.changed()).isEmpty();
        }

        @Test
        void findsASubjectThatAppeared() {
            StateDiff diff = Snapshot.diffStates(Map.of(), Map.of("J-1", "accept"));
            assertThat(diff.added()).containsExactly(new SubjectDisposition("J-1", "accept"));
            assertThat(diff.quiet()).isFalse();
        }

        @Test
        void findsADispositionThatMoved() {
            StateDiff diff = Snapshot.diffStates(Map.of("J-1", "repair"), Map.of("J-1", "accept"));
            assertThat(diff.changed()).containsExactly(new DispositionChange("J-1", "repair", "accept"));
        }

        @Test
        void callsOrdinaryProgressProgressAndNotAReopening() {
            assertThat(Snapshot.diffStates(Map.of("J-1", "repair"), Map.of("J-1", "accept"))
                            .reopened())
                    .isEmpty();
        }

        @Test
        void callsAJointGoingBackFromAcceptedAReopening() {
            StateDiff diff = Snapshot.diffStates(Map.of("J-1", "accept"), Map.of("J-1", "repair"));
            assertThat(diff.reopened()).hasSize(1);
        }

        @Test
        void findsASubjectThatVanished() {
            StateDiff diff = Snapshot.diffStates(Map.of("J-1", "accept"), Map.of());
            assertThat(diff.removed()).containsExactly(new SubjectDisposition("J-1", "accept"));
        }

        @Test
        void refusesToCompareSomethingThatIsNotAState() {
            assertThatThrownBy(() -> Snapshot.diffStates(null, Map.of())).hasMessageContaining("two states are needed");
        }
    }

    @Nested
    class ChangedBetween {

        @Test
        void reportsWhatMovedOverAPeriod() {
            StateDiff diff = Snapshot.changedBetween(JOURNAL, "2025-03-11", "2025-03-14");
            assertThat(diff.changed()).containsExactly(new DispositionChange("J-2", "repair", "accept"));
            assertThat(diff.added()).containsExactly(new SubjectDisposition("J-10", "accept"));
        }

        @Test
        void isQuietOverAPeriodWhenNothingMoved() {
            assertThat(Snapshot.changedBetween(JOURNAL, "2025-03-14", "2025-03-15")
                            .quiet())
                    .isTrue();
        }

        @Test
        void refusesAPeriodThatRunsBackwards() {
            assertThatThrownBy(() -> Snapshot.changedBetween(JOURNAL, "2025-03-14", "2025-03-11"))
                    .hasMessageContaining("runs backwards");
        }
    }

    @Nested
    class ActiveDaysAndHistory {

        @Test
        void listsOnlyTheDaysSomethingHappened() {
            assertThat(Snapshot.activeDays(JOURNAL))
                    .containsExactly("2025-03-10", "2025-03-11", "2025-03-12", "2025-03-14", "2025-03-20");
        }

        @Test
        void walksTheRecordDayByDay() {
            List<DayState> walked = Snapshot.history(JOURNAL);
            assertThat(walked).hasSameSizeAs(Snapshot.activeDays(JOURNAL));
            assertThat(walked.get(0).state()).isEmpty();
            assertThat(walked.get(walked.size() - 1).state().get("J-2")).isEqualTo("repair");
        }

        @Test
        void hasNothingToWalkOnAnEmptyJournal() {
            assertThat(Snapshot.history(Journal.makeJournal("C-0"))).isEmpty();
        }
    }

    @Nested
    class Reopenings {

        @Test
        void findsTheJointThatWentBackAfterBeingAccepted() {
            List<Reopening> found = Snapshot.reopenings(JOURNAL);
            assertThat(found).hasSize(1);
            assertThat(found.get(0).subject()).isEqualTo("J-2");
            assertThat(found.get(0).from()).isEqualTo("accept");
            assertThat(found.get(0).to()).isEqualTo("repair");
            assertThat(found.get(0).day()).isEqualTo("2025-03-20");
        }

        @Test
        void findsNothingOnAJobThatOnlyWentForwards() {
            Journal clean = Journal.appendAll(
                    Journal.makeJournal("C-1"),
                    List.of(
                            entry("verdict", "2025-03-11", "J-1", Map.of("disposition", "repair")),
                            entry("verdict", "2025-03-14", "J-1", Map.of("disposition", "accept"))));
            assertThat(Snapshot.reopenings(clean)).isEmpty();
        }
    }
}
