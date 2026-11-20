package io.github.rkaushikdev.kerfline.report;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SummaryTest {

    private static SummaryJoint joint(String id, String welder, boolean shot, String disposition) {
        return SummaryJoint.of(id, welder, shot, disposition);
    }

    private static SummaryJoint joint(String id, String welder, boolean shot) {
        return SummaryJoint.of(id, welder, shot);
    }

    private static final List<SummaryJoint> JOINTS = List.of(
            joint("J-1", "A17", true, "accept"),
            joint("J-2", "A17", true, "repair"),
            joint("J-3", "A17", true, "accept"),
            joint("J-10", "B04", true, "reject"),
            joint("J-11", "B04", true, null),
            joint("J-12", "C09", false),
            joint("J-13", "C09", false));

    @Nested
    class BuildSummary {

        private final JobSummary summary = Summary.buildSummary("C-4471", JOINTS, "five");

        @Test
        void countsTheJob() {
            assertThat(summary.counts().joints()).isEqualTo(7);
            assertThat(summary.counts().shot()).isEqualTo(5);
            assertThat(summary.counts().judged()).isEqualTo(4);
        }

        @Test
        void splitsTheDispositions() {
            assertThat(summary.counts().accepted()).isEqualTo(2);
            assertThat(summary.counts().repaired()).isEqualTo(1);
            assertThat(summary.counts().rejected()).isEqualTo(1);
        }

        @Test
        void countsWhatHasBeenShotButNotRead() {
            assertThat(summary.outstanding()).isEqualTo(1);
        }

        @Test
        void reportsTheRateWithTheDenominatorItCameFrom() {
            assertThat(summary.rate().shot()).isEqualTo(4);
            assertThat(summary.rate().repaired()).isEqualTo(2);
            assertThat(summary.rate().rate()).isCloseTo(0.5, digits(4));
        }

        @Test
        void checksTheCoverageAgainstWhatWasCalledFor() {
            assertThat(summary.coverage().requiredJoints()).isEqualTo(1);
            assertThat(summary.coverage().compliant()).isTrue();
        }

        @Test
        void namesWeldersWithNothingShotAtAll() {
            assertThat(summary.uncovered()).containsExactly("C09");
        }

        @Test
        void splitsTheRateByWelderWorstFirst() {
            assertThat(summary.welders().get(0).welder()).isEqualTo("B04");
            assertThat(summary.welders().get(0).rate()).isCloseTo(1, digits(4));
        }

        @Test
        void refusesASummaryWithNoJobOnIt() {
            assertThatThrownBy(() -> Summary.buildSummary("  ", JOINTS)).hasMessageContaining("names the job");
        }

        @Test
        void refusesJointsThatAreNotAList() {
            assertThatThrownBy(() -> Summary.buildSummary("C-4471", null)).hasMessageContaining("must be an array");
        }

        @Test
        void handlesAJobThatHasNotStarted() {
            JobSummary empty = Summary.buildSummary("C-9999", List.of());
            assertThat(empty.counts().joints()).isZero();
            assertThat(empty.rate().rate()).isEqualTo(0);
            assertThat(empty.welders()).isEmpty();
        }
    }

    @Nested
    class OutstandingJoints {

        @Test
        void listsEverythingNotYetAccepted() {
            assertThat(Summary.outstandingJoints(JOINTS).stream().map(OutstandingJoint::id))
                    .containsExactly("J-2", "J-10", "J-11", "J-12", "J-13");
        }

        @Test
        void saysWhatEachOneIsWaitingOn() {
            Map<String, String> byId = Summary.outstandingJoints(JOINTS).stream()
                    .collect(Collectors.toMap(OutstandingJoint::id, OutstandingJoint::state));
            assertThat(byId.get("J-12")).isEqualTo("not shot");
            assertThat(byId.get("J-11")).isEqualTo("awaiting interpretation");
            assertThat(byId.get("J-2")).isEqualTo("repair");
            assertThat(byId.get("J-10")).isEqualTo("reject");
        }

        @Test
        void sortsInRegisterOrderRatherThanAsStrings() {
            assertThat(Summary.outstandingJoints(JOINTS).stream().map(OutstandingJoint::id))
                    .containsExactly("J-2", "J-10", "J-11", "J-12", "J-13");
        }

        @Test
        void leavesAcceptedJointsOut() {
            assertThat(Summary.outstandingJoints(JOINTS).stream().map(OutstandingJoint::id))
                    .doesNotContain("J-1");
        }

        @Test
        void isEmptyOnAFinishedJob() {
            assertThat(Summary.outstandingJoints(List.of(joint("J-1", "A17", true, "accept"))))
                    .isEmpty();
        }
    }

    @Nested
    class RenderSummary {

        private final String text = Summary.renderSummary(Summary.buildSummary("C-4471", JOINTS, "five"));

        @Test
        void printsTheHeadingAndTheJob() {
            assertThat(text).contains("RADIOGRAPHY SUMMARY");
            assertThat(text).contains("C-4471");
        }

        @Test
        void printsTheProgressWithBothNumbers() {
            assertThat(text).contains("PROGRESS");
            assertThat(text).contains("5 of 7");
        }

        @Test
        void printsTheRateNextToTheCountItCameFrom() {
            assertThat(text).contains("Repair rate");
            assertThat(text).contains("of 4 judged");
        }

        @Test
        void printsTheWelderTable() {
            assertThat(text).contains("BY WELDER");
            assertThat(text).contains("stencil");
            assertThat(text).contains("A17");
        }

        @Test
        void callsOutWeldersWhoHaveNeverBeenShot() {
            assertThat(text).contains("NOT COVERED");
            assertThat(text).contains("C09");
        }

        @Test
        void leavesTheNotCoveredBlockOutWhenEverybodyIsCovered() {
            List<SummaryJoint> covered = List.of(joint("J-1", "A17", true, "accept"));
            String other = Summary.renderSummary(Summary.buildSummary("C-1", covered));
            assertThat(other).doesNotContain("NOT COVERED");
        }

        @Test
        void saysSoPlainlyWhenNothingHasBeenJudged() {
            String fresh = Summary.renderSummary(Summary.buildSummary("C-1", List.of(joint("J-1", "A17", false))));
            assertThat(fresh).contains("Nothing judged yet.");
        }

        @Test
        void keepsEveryLineInsideThePaperWidth() {
            for (String line : text.split("\n")) {
                assertThat(line.length()).isLessThanOrEqualTo(78);
            }
        }
    }
}
