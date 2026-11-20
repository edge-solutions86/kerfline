package io.github.rkaushikdev.kerfline.report;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.joint.JointInput;
import io.github.rkaushikdev.kerfline.joint.Spec;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CoverageTest {

    // Ninety joints by one welder all shot, ten by another none shot. Ninety
    // percent overall, and one welder never checked.
    private static final List<CoveredJoint> JOINTS = joints();

    private static List<CoveredJoint> joints() {
        List<CoveredJoint> all = new ArrayList<>();
        IntStream.range(0, 90).forEach(i -> all.add(CoveredJoint.of("A-" + i, "A17", "SP-1", true)));
        IntStream.range(0, 10).forEach(i -> all.add(CoveredJoint.of("B-" + i, "B04", "SP-2", false)));
        return List.copyOf(all);
    }

    private static List<CoveredJoint> allShot() {
        return JOINTS.stream().map(joint -> joint.withShot(true)).toList();
    }

    @Nested
    class BuildCoverage {

        private final CoverageReport report = Coverage.buildCoverage("C-4471", JOINTS, "ten");

        @Test
        void clearsTheContractOnTheOverallFigure() {
            assertThat(report.overall().compliant()).isTrue();
            assertThat(report.overall().achievedFraction()).isCloseTo(0.9, digits(3));
        }

        @Test
        void breaksItApartByWelderAndFindsTheGap() {
            GroupCoverage b04 = report.byWelder().stream()
                    .filter(row -> row.key().equals("B04"))
                    .findFirst()
                    .orElseThrow();
            assertThat(b04.compliant()).isFalse();
            assertThat(b04.shot()).isZero();
        }

        @Test
        void namesTheWelderWithNothingShotAtAll() {
            assertThat(report.uncoveredWelders()).containsExactly("B04");
        }

        @Test
        void namesTheSpoolWithNothingShotAtAll() {
            assertThat(report.uncoveredSpools()).containsExactly("SP-2");
        }

        @Test
        void putsTheWorstCoveredGroupFirst() {
            assertThat(report.byWelder().get(0).key()).isEqualTo("B04");
        }

        @Test
        void gathersJointsWithNoWelderOrSpoolUnderOneHeading() {
            CoverageReport anon = Coverage.buildCoverage("C-1", List.of(CoveredJoint.of("J-1", null, null, true)));
            assertThat(anon.byWelder().get(0).key()).isEqualTo("unknown");
            assertThat(anon.bySpool().get(0).key()).isEqualTo("unassigned");
        }

        @Test
        void countsARegisterJointByItsOwnWelderAndSpool() {
            Joint joint = Spec.makeJoint(JointInput.builder()
                    .id("J-1")
                    .welder("A17")
                    .spool("SP-1")
                    .nominalSize("6")
                    .schedule("40")
                    .build());
            CoverageReport fromRegister = Coverage.buildCoverage("C-1", List.of(CoveredJoint.of(joint, false)));
            assertThat(fromRegister.uncoveredWelders()).containsExactly("A17");
            assertThat(fromRegister.uncoveredSpools()).containsExactly("SP-1");
        }

        @Test
        void handlesAJobThatHasNotStarted() {
            CoverageReport fresh = Coverage.buildCoverage("C-1", List.of());
            assertThat(fresh.overall().totalJoints()).isZero();
            assertThat(fresh.byWelder()).isEmpty();
        }

        @Test
        void refusesAReportWithNoJobNamed() {
            assertThatThrownBy(() -> Coverage.buildCoverage(" ", JOINTS)).hasMessageContaining("names the job");
            assertThatThrownBy(() -> Coverage.buildCoverage("C-1", null)).hasMessageContaining("must be an array");
        }
    }

    @Nested
    class Shortfalls {

        @Test
        void findsTheGroupShortEvenWhenTheJobAsAWholeIsNot() {
            List<Shortfall> found = Coverage.shortfalls(Coverage.buildCoverage("C-4471", JOINTS, "ten"));
            assertThat(found).noneMatch(entry -> entry.what().equals("the job"));
            assertThat(found).anyMatch(entry -> entry.what().equals("welder B04"));
        }

        @Test
        void countsHowManyJointsShortEachOneIs() {
            Shortfall b04 = Coverage.shortfalls(Coverage.buildCoverage("C-4471", JOINTS, "ten")).stream()
                    .filter(entry -> entry.what().equals("welder B04"))
                    .findFirst()
                    .orElseThrow();
            assertThat(b04.shortBy()).isEqualTo(1);
        }

        @Test
        void findsNothingOnAFullyCoveredJob() {
            assertThat(Coverage.shortfalls(Coverage.buildCoverage("C-1", allShot(), "ten")))
                    .isEmpty();
        }

        @Test
        void findsTheJobItselfWhenTheOverallFigureIsShort() {
            List<CoveredJoint> barely = IntStream.range(0, JOINTS.size())
                    .mapToObj(i -> JOINTS.get(i).withShot(i < 3))
                    .toList();
            assertThat(Coverage.shortfalls(Coverage.buildCoverage("C-1", barely, "ten")))
                    .anyMatch(entry -> entry.what().equals("the job"));
        }
    }

    @Nested
    class RenderCoverage {

        private final String text = Coverage.renderCoverage(Coverage.buildCoverage("C-4471", JOINTS, "ten"));

        @Test
        void leadsWithTheOverallFigure() {
            assertThat(text).contains("RADIOGRAPHY COVERAGE");
            assertThat(text).contains("OVERALL");
            assertThat(text).contains("90 percent");
        }

        @Test
        void breaksItApartUnderneath() {
            assertThat(text).contains("BY WELDER");
            assertThat(text).contains("BY SPOOL");
            assertThat(text).contains("A17");
            assertThat(text).contains("B04         10     0         1        0%  short");
        }

        @Test
        void callsOutWhatWasNeverCovered() {
            assertThat(text).contains("NOT COVERED AT ALL");
            assertThat(text).contains("B04");
            assertThat(text).contains("SP-2");
        }

        @Test
        void leavesThatBlockOutWhenEverythingIsCovered() {
            String other = Coverage.renderCoverage(Coverage.buildCoverage("C-1", allShot(), "ten"));
            assertThat(other).doesNotContain("NOT COVERED AT ALL");
        }

        @Test
        void saysSoPlainlyWhenThereIsNothingToBreakDown() {
            String fresh = Coverage.renderCoverage(Coverage.buildCoverage("C-1", List.of()));
            assertThat(fresh).contains("Nothing to break down.");
        }

        @Test
        void keepsEveryLineInsideThePaperWidth() {
            for (String line : text.split("\n")) {
                assertThat(line.length()).isLessThanOrEqualTo(78);
            }
        }
    }

    @Nested
    class RequiredFraction {

        @Test
        void readsTheLevelFraction() {
            assertThat(Coverage.requiredFraction("ten")).isCloseTo(0.1, digits(4));
            assertThat(Coverage.requiredFraction("full")).isEqualTo(1);
        }

        @Test
        void refusesALevelNobodySpecified() {
            assertThatThrownBy(() -> Coverage.requiredFraction("most")).hasMessageContaining("no radiography level");
        }
    }
}
