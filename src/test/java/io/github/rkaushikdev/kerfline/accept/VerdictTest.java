package io.github.rkaushikdev.kerfline.accept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VerdictTest {

    private static final ClauseVerdict OK = Verdict.accept("API1104", "9.3");
    private static final ClauseVerdict REPAIR =
            Verdict.makeVerdict(Disposition.REPAIR, "API1104", "9.3.6", "slag longer than allowed");
    private static final ClauseVerdict REJECT = Verdict.makeVerdict(Disposition.REJECT, "API1104", "9.3.9", "crack");

    @Nested
    class TheDispositions {

        @Test
        void runsFromAcceptToReject() {
            assertThat(Verdict.dispositions().stream().map(Disposition::label))
                    .containsExactly("accept", "repair", "reject");
        }

        @Test
        void ordersThemBySeverity() {
            assertThat(Verdict.severityOf(Disposition.ACCEPT)).isLessThan(Verdict.severityOf(Disposition.REPAIR));
            assertThat(Verdict.severityOf(Disposition.REPAIR)).isLessThan(Verdict.severityOf(Disposition.REJECT));
        }

        @Test
        void refusesADispositionItDoesNotKnow() {
            assertThatThrownBy(() -> Disposition.of("maybe")).hasMessageContaining("disposition must be one of");
            assertThatThrownBy(() -> Verdict.severityOf(null)).hasMessageContaining("disposition must be one of");
        }
    }

    @Nested
    class MakeVerdict {

        @Test
        void carriesTheCodeAndClause() {
            ClauseVerdict verdict =
                    Verdict.makeVerdict(Disposition.REPAIR, "API1104", "9.3.6", "slag longer than allowed");
            assertThat(verdict.code()).isEqualTo("API1104");
            assertThat(verdict.clause()).isEqualTo("9.3.6");
            assertThat(verdict.reason()).contains("slag longer than allowed");
        }

        @Test
        void freezesSoAReportCannotRewriteAFinding() {
            JointVerdict rolled = Verdict.combine(List.of(REPAIR));
            assertThatThrownBy(() -> rolled.findings().set(0, OK)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> rolled.reasons().clear()).isInstanceOf(UnsupportedOperationException.class);
            assertThat(rolled.findings().get(0).disposition()).isEqualTo(Disposition.REPAIR);
        }

        @Test
        void letsAnAcceptanceCarryNoReason() {
            assertThat(Verdict.accept("API1104", "9.3").reason()).isEmpty();
        }

        @Test
        void insistsAFindingCarriesAReason() {
            assertThatThrownBy(() -> Verdict.makeVerdict(Disposition.REPAIR, "API1104", "9.3.6", null))
                    .hasMessageContaining("carries a reason");
        }

        @Test
        void insistsOnACodeAndAClause() {
            assertThatThrownBy(() -> Verdict.makeVerdict(Disposition.ACCEPT, "", "9.3", null))
                    .hasMessageContaining("names the code");
            assertThatThrownBy(() -> Verdict.makeVerdict(Disposition.ACCEPT, "API1104", "  ", null))
                    .hasMessageContaining("names the clause");
        }
    }

    @Nested
    class WorstOf {

        @Test
        void picksTheMostSevere() {
            assertThat(Verdict.worstOf(List.of(OK, REPAIR, REJECT)).disposition())
                    .isEqualTo(Disposition.REJECT);
            assertThat(Verdict.worstOf(List.of(OK, REPAIR)).disposition()).isEqualTo(Disposition.REPAIR);
            assertThat(Verdict.worstOf(List.of(OK, OK)).disposition()).isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void doesNotCareWhatOrderTheyArriveIn() {
            assertThat(Verdict.worstOf(List.of(REJECT, OK, REPAIR)).disposition())
                    .isEqualTo(Disposition.REJECT);
        }

        @Test
        void refusesAnEmptySet() {
            assertThatThrownBy(() -> Verdict.worstOf(List.of())).hasMessageContaining("no worst of an empty set");
            assertThatThrownBy(() -> Verdict.worstOf(null)).hasMessageContaining("must be an array");
        }
    }

    @Nested
    class Combine {

        @Test
        void acceptsAJointWithNothingOnIt() {
            JointVerdict rolled = Verdict.combine(List.of());
            assertThat(rolled.disposition()).isEqualTo(Disposition.ACCEPT);
            assertThat(rolled.findings()).isEmpty();
        }

        @Test
        void acceptsAJointWhoseEveryVerdictWasAnAcceptance() {
            JointVerdict rolled = Verdict.combine(List.of(OK, OK));
            assertThat(rolled.disposition()).isEqualTo(Disposition.ACCEPT);
            assertThat(rolled.findings()).isEmpty();
        }

        @Test
        void takesTheGoverningDispositionFromTheWorstFinding() {
            assertThat(Verdict.combine(List.of(OK, REPAIR, REJECT)).disposition())
                    .isEqualTo(Disposition.REJECT);
        }

        @Test
        void keepsEveryFindingAndNotOnlyTheGoverningOne() {
            JointVerdict rolled = Verdict.combine(List.of(OK, REPAIR, REJECT));
            assertThat(rolled.findings()).hasSize(2);
            assertThat(rolled.reasons()).containsExactly("slag longer than allowed", "crack");
        }

        @Test
        void namesTheClauseOfTheGoverningFinding() {
            assertThat(Verdict.combine(List.of(OK, REPAIR, REJECT)).clause()).contains("9.3.9");
        }

        @Test
        void takesAnOverridingCodeLabel() {
            assertThat(Verdict.combine(List.of(REPAIR), "API 1104 2021").code()).contains("API 1104 2021");
        }
    }

    @Nested
    class IsAcceptableAndRequiresRemoval {

        @Test
        void readsASingleVerdict() {
            assertThat(Verdict.isAcceptable(Verdict.accept("API1104", "9.3"))).isTrue();
            assertThat(Verdict.isAcceptable(REJECT)).isFalse();
            assertThat(Verdict.requiresRemoval(REJECT)).isTrue();
        }

        @Test
        void readsACombinedResult() {
            JointVerdict rolled = Verdict.combine(List.of(Verdict.accept("API1104", "9.3")));
            assertThat(Verdict.isAcceptable(rolled)).isTrue();
            assertThat(Verdict.requiresRemoval(rolled)).isFalse();
        }

        @Test
        void doesNotCallARepairARemoval() {
            ClauseVerdict repair = Verdict.makeVerdict(Disposition.REPAIR, "API1104", "9.3.6", "slag");
            assertThat(Verdict.requiresRemoval(repair)).isFalse();
            assertThat(Verdict.isAcceptable(repair)).isFalse();
        }
    }
}
