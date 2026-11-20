package io.github.rkaushikdev.kerfline.joint;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RegisterTest {

    private static JointInput input(String id, String spool, String welder, double size) {
        return JointInput.builder()
                .id(id)
                .spool(spool)
                .welder(welder)
                .nominalSize(size)
                .schedule(40)
                .build();
    }

    private static final List<JointInput> INPUTS =
            List.of(input("J-10", "SP-1", "A17", 6), input("J-2", "SP-1", "A17", 6), input("J-3", "SP-2", "B04", 2));

    private static final JointRegister REGISTER = Register.addJoints(Register.makeRegister("C-4471"), INPUTS);

    private static List<String> ids(List<Joint> joints) {
        return joints.stream().map(Joint::id).toList();
    }

    @Nested
    class MakeRegister {

        @Test
        void startsEmptyAndBelongsToAJob() {
            JointRegister empty = Register.makeRegister("C-4471");
            assertThat(empty.job()).isEqualTo("C-4471");
            assertThat(empty.joints()).isEmpty();
        }

        @Test
        void refusesARegisterWithNoJob() {
            assertThatThrownBy(() -> Register.makeRegister("  ")).hasMessageContaining("belongs to a job");
        }
    }

    @Nested
    class AddJoint {

        @Test
        void addsAJoint() {
            assertThat(REGISTER.joints()).hasSize(3);
        }

        @Test
        void leavesTheRegisterItWasGivenAlone() {
            JointRegister before = Register.makeRegister("C-4471");
            Register.addJoint(before, INPUTS.get(0));
            assertThat(before.joints()).isEmpty();
        }

        @Test
        void refusesTheSameJointNumberTwice() {
            assertThatThrownBy(() -> Register.addJoint(REGISTER, INPUTS.get(0)))
                    .hasMessageContaining("already on this register");
        }

        @Test
        void refusesAJointTheSpecWillNotBuild() {
            assertThatThrownBy(() -> Register.addJoint(
                            REGISTER, JointInput.builder().id("J-99").build()))
                    .hasMessageContaining("either a size and schedule");
        }

        @Test
        void refusesSomethingThatIsNotARegister() {
            assertThatThrownBy(() -> Register.addJoint(null, INPUTS.get(0))).hasMessageContaining("not a register");
        }
    }

    @Nested
    class GetJointAndHasJoint {

        @Test
        void findsAJoint() {
            assertThat(Register.getJoint(REGISTER, "J-2").welder()).contains("A17");
        }

        @Test
        void refusesOneThatIsNotThereNamingTheJob() {
            assertThatThrownBy(() -> Register.getJoint(REGISTER, "J-99"))
                    .hasMessageContaining("no joint J-99 on C-4471");
        }

        @Test
        void answersHasJointWithoutThrowing() {
            assertThat(Register.hasJoint(REGISTER, "J-2")).isTrue();
            assertThat(Register.hasJoint(REGISTER, "J-99")).isFalse();
        }
    }

    @Nested
    class Joints {

        @Test
        void readsInRegisterOrderRatherThanEntryOrder() {
            assertThat(ids(Register.joints(REGISTER))).containsExactly("J-2", "J-3", "J-10");
        }

        @Test
        void listsTheIdsTheSameWay() {
            assertThat(Register.jointIds(REGISTER)).containsExactly("J-2", "J-3", "J-10");
        }

        @Test
        void filtersOnAnyPredicate() {
            assertThat(ids(Register.filterJoints(REGISTER, joint -> joint.odIn() > 3)))
                    .containsExactly("J-2", "J-10");
        }

        @Test
        void refusesAPredicateThatIsNotAFunction() {
            assertThatThrownBy(() -> Register.filterJoints(REGISTER, null)).hasMessageContaining("needs a predicate");
        }
    }

    @Nested
    class Grouping {

        @Test
        void findsAWelderWork() {
            assertThat(ids(Register.byWelder(REGISTER, "A17"))).containsExactly("J-2", "J-10");
        }

        @Test
        void findsASpool() {
            assertThat(ids(Register.bySpool(REGISTER, "SP-2"))).containsExactly("J-3");
        }

        @Test
        void listsTheWeldersAndTheSpools() {
            assertThat(Register.welders(REGISTER)).containsExactly("A17", "B04");
            assertThat(Register.spools(REGISTER)).containsExactly("SP-1", "SP-2");
        }

        @Test
        void leavesOutJointsWithNoWelderRecorded() {
            JointRegister anon = Register.addJoint(
                    REGISTER,
                    JointInput.builder().id("J-50").nominalSize(6).schedule(40).build());
            assertThat(Register.welders(anon)).containsExactly("A17", "B04");
        }
    }

    @Nested
    class RegisterTotalsForAJob {

        private final RegisterTotals totals = Register.registerTotals(REGISTER);

        @Test
        void countsTheJob() {
            assertThat(totals.joints()).isEqualTo(3);
            assertThat(totals.welders()).isEqualTo(2);
            assertThat(totals.spools()).isEqualTo(2);
        }

        @Test
        void addsTheWeldLengthUp() {
            assertThat(totals.weldLengthIn()).isCloseTo(20.813 * 2 + 7.461, digits(1));
        }

        @Test
        void countsTheSmallBoreJoints() {
            assertThat(totals.smallBore()).isEqualTo(1);
        }

        @Test
        void countsTheFixedPositionJoints() {
            assertThat(totals.fixedPosition()).isEqualTo(3);
        }

        @Test
        void totalsAnEmptyRegisterToNothing() {
            RegisterTotals empty = Register.registerTotals(Register.makeRegister("C-0"));
            assertThat(empty.joints()).isZero();
            assertThat(empty.weldLengthIn()).isEqualTo(0);
        }
    }

    @Nested
    class SizesOnJob {

        @Test
        void countsTheDistinctSizes() {
            List<SizeCount> sizes = Register.sizesOnJob(REGISTER);
            assertThat(sizes.get(0)).isEqualTo(new SizeCount("6 in sch 40", 2));
            assertThat(sizes.get(1)).isEqualTo(new SizeCount("2 in sch 40", 1));
        }

        @Test
        void putsTheCommonestSizeFirst() {
            List<SizeCount> sizes = Register.sizesOnJob(REGISTER);
            assertThat(sizes.get(0).count()).isGreaterThanOrEqualTo(sizes.get(1).count());
        }

        @Test
        void describesAMeasuredJointByItsMeasurements() {
            JointRegister measured = Register.addJoint(
                    REGISTER,
                    JointInput.builder().id("J-77").odIn(4.5).wallIn(0.237).build());
            assertThat(Register.sizesOnJob(measured)).anyMatch(row -> row.size().contains("OD"));
        }

        @Test
        void isEmptyForAnEmptyRegister() {
            assertThat(Register.sizesOnJob(Register.makeRegister("C-0"))).isEmpty();
        }
    }
}
