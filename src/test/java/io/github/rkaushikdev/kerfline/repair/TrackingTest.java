package io.github.rkaushikdev.kerfline.repair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TrackingTest {

    private static Attempt shot(String label, String day, String disposition) {
        return Tracking.makeAttempt(label, day, disposition);
    }

    private static Attempt shot(String label, String day, String disposition, String at) {
        return Tracking.makeAttempt(label, day, disposition, at);
    }

    @Nested
    class TheRules {

        @Test
        void callsThirtyDegreesTheSameSpotAndAllowsTwoRepairsThere() {
            assertThat(Tracking.sameSpotDegrees()).isEqualTo(30);
            assertThat(Tracking.maximumRepairs()).isEqualTo(2);
        }
    }

    @Nested
    class MakeAttempt {

        @Test
        void buildsAnAttempt() {
            Attempt attempt = shot("original", "2025-03-14", "repair", "3:00");
            assertThat(attempt.label()).isEqualTo("original");
            assertThat(attempt.at()).hasValue("3:00");
        }

        @Test
        void refusesAnAttemptWithNoLabel() {
            assertThatThrownBy(() -> Tracking.makeAttempt("", "2025-03-14", "accept"))
                    .hasMessageContaining("carries a label");
        }

        @Test
        void refusesADispositionItDoesNotKnow() {
            assertThatThrownBy(() -> Tracking.makeAttempt("R1", "2025-03-14", "maybe"))
                    .hasMessageContaining("accept, repair or reject");
        }

        @Test
        void refusesADateThatIsNotARealDay() {
            assertThatThrownBy(() -> Tracking.makeAttempt("R1", "2025-02-30", "accept"))
                    .hasMessageContaining("not a real calendar day");
        }
    }

    @Nested
    class NextLabel {

        @Test
        void startsWithTheOriginalShot() {
            assertThat(Tracking.nextLabel(List.of())).isEqualTo("original");
        }

        @Test
        void countsTheRepairsAfterThat() {
            List<Attempt> attempts = new ArrayList<>(List.of(shot("original", "2025-03-14", "repair", "3:00")));
            assertThat(Tracking.nextLabel(attempts)).isEqualTo("R1");
            attempts.add(shot("R1", "2025-03-15", "repair", "3:00"));
            assertThat(Tracking.nextLabel(attempts)).isEqualTo("R2");
        }
    }

    @Nested
    class RepairsAt {

        private final List<Attempt> attempts = List.of(
                shot("original", "2025-03-14", "repair", "3:00"),
                shot("R1", "2025-03-15", "repair", "3:05"),
                shot("R2", "2025-03-16", "repair", "9:00"));

        @Test
        void countsRepairsWithinThirtyDegrees() {
            assertThat(Tracking.repairsAt(attempts, "3:00")).hasSize(1);
            assertThat(Tracking.repairsAt(attempts, "3:10")).hasSize(1);
        }

        @Test
        void doesNotCountARepairOnTheOtherSideOfThePipe() {
            assertThat(Tracking.repairsAt(attempts, "12:00")).isEmpty();
        }

        @Test
        void doesNotCountTheOriginalShotAsARepair() {
            List<Attempt> first = List.of(shot("original", "2025-03-14", "repair", "3:00"));
            assertThat(Tracking.repairsAt(first, "3:00")).isEmpty();
        }

        @Test
        void countsAcrossTheTopOfThePipeRatherThanSubtracting() {
            List<Attempt> nearTop = List.of(shot("R1", "2025-03-15", "repair", "11:55"));
            assertThat(Tracking.repairsAt(nearTop, "12:05")).hasSize(1);
        }
    }

    @Nested
    class CanRepairAt {

        @Test
        void allowsAFirstRepair() {
            List<Attempt> attempts = List.of(shot("original", "2025-03-14", "repair", "3:00"));
            assertThat(Tracking.canRepairAt(attempts, "3:00").allowed()).isTrue();
        }

        @Test
        void allowsASecondRepairAtTheSameSpot() {
            List<Attempt> attempts = List.of(
                    shot("original", "2025-03-14", "repair", "3:00"), shot("R1", "2025-03-15", "repair", "3:00"));
            assertThat(Tracking.canRepairAt(attempts, "3:00").allowed()).isTrue();
        }

        @Test
        void refusesAThirdRepairAtTheSameSpot() {
            List<Attempt> attempts = List.of(
                    shot("original", "2025-03-14", "repair", "3:00"),
                    shot("R1", "2025-03-15", "repair", "3:00"),
                    shot("R2", "2025-03-16", "repair", "3:02"));
            RepairCheck check = Tracking.canRepairAt(attempts, "3:00");
            assertThat(check.allowed()).isFalse();
            assertThat(check.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("already been made"));
        }

        @Test
        void allowsARepairElsewhereOnTheSameJoint() {
            List<Attempt> attempts = List.of(
                    shot("original", "2025-03-14", "repair", "3:00"),
                    shot("R1", "2025-03-15", "repair", "3:00"),
                    shot("R2", "2025-03-16", "repair", "3:00"));
            assertThat(Tracking.canRepairAt(attempts, "9:00").allowed()).isTrue();
        }

        @Test
        void refusesAnyRepairOnceTheJointHasBeenRejected() {
            List<Attempt> attempts = List.of(shot("original", "2025-03-14", "reject", "3:00"));
            assertThat(Tracking.canRepairAt(attempts, "9:00").allowed()).isFalse();
        }
    }

    @Nested
    class AddAttempt {

        private final Attempt first = shot("original", "2025-03-14", "repair", "3:00");

        @Test
        void appendsAnAttempt() {
            List<Attempt> chain = Tracking.addAttempt(List.of(first), shot("R1", "2025-03-15", "accept", "3:00"));
            assertThat(chain).hasSize(2);
        }

        @Test
        void leavesTheCallerArrayAlone() {
            List<Attempt> attempts = new ArrayList<>(List.of(first));
            Tracking.addAttempt(attempts, shot("R1", "2025-03-15", "accept", "3:00"));
            assertThat(attempts).hasSize(1);
        }

        @Test
        void refusesAnAttemptDatedBeforeTheOneItFollows() {
            assertThatThrownBy(() -> Tracking.addAttempt(List.of(first), shot("R1", "2025-03-13", "accept", "3:00")))
                    .hasMessageContaining("dated before");
        }

        @Test
        void refusesAnotherAttemptOnceTheJointWasAccepted() {
            List<Attempt> closed = List.of(shot("original", "2025-03-14", "accept"));
            assertThatThrownBy(() -> Tracking.addAttempt(closed, shot("R1", "2025-03-15", "repair", "3:00")))
                    .hasMessageContaining("already accepted");
        }

        @Test
        void refusesAThirdRepairAtTheSameSpot() {
            List<Attempt> chain = List.of(
                    first, shot("R1", "2025-03-15", "repair", "3:00"), shot("R2", "2025-03-16", "repair", "3:00"));
            assertThatThrownBy(() -> Tracking.addAttempt(chain, shot("R3", "2025-03-17", "repair", "3:00")))
                    .hasMessageContaining("already been made");
        }
    }

    @Nested
    class ChainStateOf {

        @Test
        void reportsAJointThatHasNotBeenShot() {
            assertThat(Tracking.chainState(List.of()))
                    .isEqualTo(new ChainState(ChainStatus.NOT_SHOT, 0, true, 0, Optional.empty()));
        }

        @Test
        void reportsAJointWaitingOnARepair() {
            ChainState state = Tracking.chainState(List.of(shot("original", "2025-03-14", "repair", "3:00")));
            assertThat(state.status()).isEqualTo(ChainStatus.AWAITING_REPAIR);
            assertThat(state.open()).isTrue();
        }

        @Test
        void reportsAnAcceptedJointAndClosesIt() {
            ChainState state = Tracking.chainState(List.of(
                    shot("original", "2025-03-14", "repair", "3:00"), shot("R1", "2025-03-17", "accept", "3:00")));
            assertThat(state.status()).isEqualTo(ChainStatus.ACCEPTED);
            assertThat(state.open()).isFalse();
            assertThat(state.repairs()).isEqualTo(1);
            assertThat(state.days()).isEqualTo(3);
            assertThat(state.closedOn()).hasValue("2025-03-17");
        }

        @Test
        void reportsAJointThatWasCutOut() {
            ChainState state = Tracking.chainState(List.of(shot("original", "2025-03-14", "reject", "3:00")));
            assertThat(state.status()).isEqualTo(ChainStatus.CUT_OUT);
            assertThat(state.open()).isFalse();
        }
    }

    @Nested
    class Overdue {

        private final List<RepairChain> chains = List.of(
                new RepairChain("J-1", List.of(shot("original", "2025-03-01", "repair", "3:00"))),
                new RepairChain("J-2", List.of(shot("original", "2025-03-14", "repair", "3:00"))),
                new RepairChain("J-3", List.of(shot("original", "2025-03-01", "accept"))));

        @Test
        void findsTheChainsOpenPastTheLimit() {
            assertThat(Tracking.overdue(chains, 7, "2025-03-16").stream().map(RepairChain::id))
                    .containsExactly("J-1");
        }

        @Test
        void leavesClosedChainsOutHoweverOldTheyAre() {
            assertThat(Tracking.overdue(chains, 1, "2026-01-01").stream().map(RepairChain::id))
                    .containsExactly("J-1", "J-2");
        }

        @Test
        void findsNothingWhenNothingIsLate() {
            assertThat(Tracking.overdue(chains, 90, "2025-03-16")).isEqualTo(List.of());
        }

        @Test
        void refusesALimitThatIsNotAWholeNumberOfDays() {
            assertThatThrownBy(() -> Tracking.overdue(chains, 1.5, "2025-03-16"))
                    .hasMessageContaining("whole number");
        }
    }
}
