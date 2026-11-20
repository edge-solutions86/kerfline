package io.github.rkaushikdev.kerfline.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.repair.Attempt;
import io.github.rkaushikdev.kerfline.repair.ChainStatus;
import io.github.rkaushikdev.kerfline.store.Entry;
import io.github.rkaushikdev.kerfline.store.EntryDraft;
import io.github.rkaushikdev.kerfline.store.Journal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CloseTest {

    private static EntryDraft entry(String kind, String day, String subject) {
        return EntryDraft.of(kind, day, subject);
    }

    private static EntryDraft entry(String kind, String day, String subject, Map<String, ?> payload) {
        return EntryDraft.of(kind, day, subject, payload);
    }

    private static final Journal SHOT_ONLY =
            Journal.appendAll(Journal.makeJournal("C-4471"), List.of(entry("shot", "2025-03-10", "J-118")));

    private static final Journal REPAIRING = Journal.appendAll(
            SHOT_ONLY, List.of(entry("verdict", "2025-03-11", "J-118", Map.of("disposition", "repair", "at", "3:00"))));

    private static final Journal ACCEPTED = Journal.appendAll(
            REPAIRING,
            List.of(
                    entry("repair", "2025-03-13", "J-118", Map.of("at", "3:00")),
                    entry("shot", "2025-03-14", "J-118"),
                    entry("verdict", "2025-03-14", "J-118", Map.of("disposition", "accept"))));

    private static Entry last(Journal journal) {
        return journal.entries().get(journal.entries().size() - 1);
    }

    private static Journal repairedTwiceAtThree() {
        return Journal.appendAll(
                REPAIRING,
                List.of(
                        entry("verdict", "2025-03-14", "J-118", Map.of("disposition", "repair", "at", "3:00")),
                        entry("verdict", "2025-03-15", "J-118", Map.of("disposition", "repair", "at", "3:02"))));
    }

    @Nested
    class AttemptsFor {

        @Test
        void rebuildsTheChainFromTheVerdicts() {
            assertThat(Close.attemptsFor(ACCEPTED, "J-118").stream().map(Attempt::label))
                    .containsExactly("original", "R1");
        }

        @Test
        void isEmptyForAJointWithNoVerdictYet() {
            assertThat(Close.attemptsFor(SHOT_ONLY, "J-118")).isEmpty();
        }

        @Test
        void carriesTheLocationARepairWasCalledAt() {
            assertThat(Close.attemptsFor(REPAIRING, "J-118").get(0).at()).contains("3:00");
        }
    }

    @Nested
    class JointStateOf {

        @Test
        void reportsAJointShotButNotJudged() {
            JointState state = Close.jointState(SHOT_ONLY, "J-118");
            assertThat(state.everShot()).isTrue();
            assertThat(state.status()).isEqualTo(ChainStatus.NOT_SHOT);
            assertThat(state.open()).isTrue();
        }

        @Test
        void reportsAJointWaitingOnARepair() {
            JointState state = Close.jointState(REPAIRING, "J-118");
            assertThat(state.status()).isEqualTo(ChainStatus.AWAITING_REPAIR);
            assertThat(state.open()).isTrue();
        }

        @Test
        void reportsAnAcceptedJointAsClosed() {
            JointState state = Close.jointState(ACCEPTED, "J-118");
            assertThat(state.status()).isEqualTo(ChainStatus.ACCEPTED);
            assertThat(state.open()).isFalse();
            assertThat(state.repairs()).isEqualTo(1);
        }

        @Test
        void reportsAJointThatWasNeverShot() {
            JointState state = Close.jointState(Journal.makeJournal("C-0"), "J-999");
            assertThat(state.everShot()).isFalse();
        }
    }

    @Nested
    class RecordRepair {

        @Test
        void writesARepairEntry() {
            Journal journal = Close.recordRepair(
                    REPAIRING, RepairWork.of("J-118", "2025-03-13").at("3:00"));
            assertThat(last(journal).kind()).isEqualTo("repair");
            assertThat(Journal.verify(journal).intact()).isTrue();
        }

        @Test
        void carriesTheWelderAndTheNote() {
            Journal journal = Close.recordRepair(
                    REPAIRING,
                    RepairWork.of("J-118", "2025-03-13")
                            .at("3:00")
                            .welder("A17")
                            .note("ground to sound metal"));
            Map<String, Object> payload = last(journal).payload();
            assertThat(payload.get("welder")).isEqualTo("A17");
            assertThat(payload.get("note")).isEqualTo("ground to sound metal");
        }

        @Test
        void refusesAThirdRepairAtTheSameSpot() {
            Journal twice = repairedTwiceAtThree();
            assertThatThrownBy(() -> Close.recordRepair(
                            twice, RepairWork.of("J-118", "2025-03-16").at("3:00")))
                    .hasMessageContaining("already been made");
        }

        @Test
        void allowsARepairElsewhereOnTheSameJoint() {
            Journal twice = repairedTwiceAtThree();
            assertThatCode(() -> Close.recordRepair(
                            twice, RepairWork.of("J-118", "2025-03-16").at("9:00")))
                    .doesNotThrowAnyException();
        }

        @Test
        void refusesARepairWithNoJointNamed() {
            assertThatThrownBy(() -> Close.recordRepair(REPAIRING, RepairWork.of("", "2025-03-13")))
                    .hasMessageContaining("names the joint");
        }
    }

    @Nested
    class CloseJoint {

        @Test
        void closesAnOpenJointThatIsNotWaitingOnARepair() {
            Journal closed = Close.closeJoint(
                    SHOT_ONLY, Closing.of("J-118", "2025-03-15", "accept").by("D. Whyte"));
            Entry closing = last(closed);
            assertThat(closing.payload().get("closing")).isEqualTo(true);
            assertThat(closing.payload().get("closedBy")).isEqualTo("D. Whyte");
            assertThat(Close.jointState(closed, "J-118").status()).isEqualTo(ChainStatus.ACCEPTED);
        }

        @Test
        void refusesToCloseAJointThatWasNeverShot() {
            assertThatThrownBy(() ->
                            Close.closeJoint(Journal.makeJournal("C-0"), Closing.of("J-9", "2025-03-15", "accept")))
                    .hasMessageContaining("never shot");
        }

        @Test
        void refusesToAcceptAJointStillWaitingOnItsRepair() {
            assertThatThrownBy(() -> Close.closeJoint(REPAIRING, Closing.of("J-118", "2025-03-15", "accept")))
                    .hasMessageContaining("has not been shot again");
        }

        @Test
        void letsAJointWaitingOnARepairBeCutOutInstead() {
            Journal cut = Close.closeJoint(REPAIRING, Closing.of("J-118", "2025-03-15", "reject"));
            assertThat(Close.jointState(cut, "J-118").status()).isEqualTo(ChainStatus.CUT_OUT);
        }

        @Test
        void refusesToCloseAJointItsOwnVerdictAlreadyAccepted() {
            // The accepting verdict is what closes a joint. There is no second step,
            // and offering one would let a joint be answered twice.
            assertThat(Close.jointState(ACCEPTED, "J-118").open()).isFalse();
            assertThatThrownBy(() -> Close.closeJoint(ACCEPTED, Closing.of("J-118", "2025-03-16", "accept")))
                    .hasMessageContaining("already accepted");
        }

        @Test
        void refusesToReopenAJointThatWasCutOut() {
            Journal cut = Close.closeJoint(REPAIRING, Closing.of("J-118", "2025-03-15", "reject"));
            assertThatThrownBy(() -> Close.closeJoint(cut, Closing.of("J-118", "2025-03-16", "accept")))
                    .hasMessageContaining("already cut out");
        }

        @Test
        void refusesADispositionAJointCannotCloseAs() {
            assertThatThrownBy(() -> Close.closeJoint(ACCEPTED, Closing.of("J-118", "2025-03-15", "repair")))
                    .hasMessageContaining("accepted or cut out");
        }

        @Test
        void keepsTheChainIntact() {
            Journal closed = Close.closeJoint(REPAIRING, Closing.of("J-118", "2025-03-15", "reject"));
            assertThat(Journal.verify(closed).intact()).isTrue();
        }
    }

    @Nested
    class OpenJoints {

        @Test
        void listsTheJointsStillOwingSomething() {
            Journal many = Journal.appendAll(
                    REPAIRING,
                    List.of(
                            entry("shot", "2025-03-12", "J-200"),
                            entry("verdict", "2025-03-12", "J-200", Map.of("disposition", "accept"))));
            assertThat(Close.openJoints(many).stream().map(JointState::jointId)).containsExactly("J-118");
        }

        @Test
        void leavesOutAJointThatWasNeverShot() {
            Journal planned =
                    Journal.appendAll(Journal.makeJournal("C-1"), List.of(entry("joint", "2025-03-01", "J-1")));
            assertThat(Close.openJoints(planned)).isEmpty();
        }

        @Test
        void isEmptyOnceEverythingHasClosed() {
            assertThat(Close.openJoints(ACCEPTED)).isEmpty();
        }
    }
}
