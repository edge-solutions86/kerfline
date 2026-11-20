package io.github.rkaushikdev.kerfline.joint;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SpecTest {

    private static JointInput.Builder sized(String id, double size, double schedule) {
        return JointInput.builder().id(id).nominalSize(size).schedule(schedule);
    }

    private static JointInput.Builder measured(String id, double odIn, double wallIn) {
        return JointInput.builder().id(id).odIn(odIn).wallIn(wallIn);
    }

    @Nested
    class TheVocabularies {

        @Test
        void namesThePositionsProcessesAndJointTypes() {
            assertThat(Spec.weldPositions()).contains("5G");
            assertThat(Spec.weldProcesses()).contains("SMAW");
            assertThat(Spec.jointTypes()).contains("butt");
        }

        @Test
        void callsAPipeThatCannotBeTurnedAFixedPosition() {
            assertThat(Spec.isFixedPosition("5G")).isTrue();
            assertThat(Spec.isFixedPosition("6G")).isTrue();
            assertThat(Spec.isFixedPosition("1G")).isFalse();
        }

        @Test
        void refusesAPositionNobodyWeldsIn() {
            assertThatThrownBy(() -> Spec.isFixedPosition("9G")).hasMessageContaining("weld position must be one of");
        }
    }

    @Nested
    class MakeJointFromASizeAndSchedule {

        private final Joint joint =
                Spec.makeJoint(sized("J-118", 6, 40).welder("A17").build());

        @Test
        void fillsTheGeometryFromTheTable() {
            assertThat(joint.odIn()).isCloseTo(6.625, digits(4));
            assertThat(joint.wallIn()).isCloseTo(0.28, digits(4));
            assertThat(joint.fromTable()).isTrue();
        }

        @Test
        void keepsTheIdentifyingFields() {
            assertThat(joint.id()).isEqualTo("J-118");
            assertThat(joint.welder()).contains("A17");
            assertThat(joint.nominalSize()).contains("6");
            assertThat(joint.schedule()).contains("40");
        }

        @Test
        void takesTheShopDefaultsForPositionAndProcess() {
            assertThat(joint.position()).isEqualTo("5G");
            assertThat(joint.process()).isEqualTo("SMAW");
            assertThat(joint.type()).isEqualTo("butt");
            assertThat(joint.fixedPosition()).isTrue();
        }

        @Test
        void letsAMeasuredWallOverrideTheTableWhileKeepingTheSize() {
            Joint worn =
                    Spec.makeJoint(sized("J-9", 6, 40).odIn(6.625).wallIn(0.265).build());
            assertThat(worn.wallIn()).isCloseTo(0.265, digits(4));
            assertThat(worn.nominalSize()).contains("6");
        }

        @Test
        void refusesAMeasuredDiameterThatContradictsTheSizeGiven() {
            assertThatThrownBy(() -> Spec.makeJoint(
                            sized("J-9", 6, 40).odIn(8.625).wallIn(0.28).build()))
                    .hasMessageContaining("does not match the size given");
        }
    }

    @Nested
    class MakeJointFromACaliper {

        private final Joint joint = Spec.makeJoint(measured("J-201", 4.5, 0.237).build());

        @Test
        void takesTheMeasurementsAsTheyAre() {
            assertThat(joint.odIn()).isEqualTo(4.5);
            assertThat(joint.wallIn()).isEqualTo(0.237);
            assertThat(joint.fromTable()).isFalse();
            assertThat(joint.nominalSize()).isEmpty();
        }

        @Test
        void refusesAWallThatSwallowsTheBore() {
            assertThatThrownBy(() -> Spec.makeJoint(measured("J-1", 1, 0.5).build()))
                    .hasMessageContaining("two walls do not fit");
        }

        @Test
        void refusesAMeasurementAtOrBelowZero() {
            assertThatThrownBy(() -> Spec.makeJoint(measured("J-1", 0, 0.2).build()))
                    .hasMessageContaining("both be positive");
        }

        @Test
        void refusesAJointWithNoGeometryAtAll() {
            assertThatThrownBy(
                            () -> Spec.makeJoint(JointInput.builder().id("J-1").build()))
                    .hasMessageContaining("either a size and schedule");
        }
    }

    @Nested
    class MakeJointValidation {

        @Test
        void refusesAJointWithNoId() {
            assertThatThrownBy(() -> Spec.makeJoint(measured(null, 4.5, 0.237).build()))
                    .hasMessageContaining("needs an id");
            assertThatThrownBy(() -> Spec.makeJoint(measured("  ", 4.5, 0.237).build()))
                    .hasMessageContaining("needs an id");
        }

        @Test
        void refusesAProcessOrTypeItDoesNotKnow() {
            JointInput base = measured("J-1", 4.5, 0.237).build();
            assertThatThrownBy(
                            () -> Spec.makeJoint(base.toBuilder().process("TIG").build()))
                    .hasMessageContaining("welding process");
            assertThatThrownBy(() -> Spec.makeJoint(base.toBuilder().type("lap").build()))
                    .hasMessageContaining("joint type");
        }

        @Test
        void refusesAWelderStencilThatCannotBeStamped() {
            assertThatThrownBy(() -> Spec.makeJoint(
                            measured("J-1", 4.5, 0.237).welder("BW-9").build()))
                    .hasMessageContaining("one to six letters or digits");
        }

        @Test
        void refusesAWeldDateThatIsNotARealDay() {
            assertThatThrownBy(() -> Spec.makeJoint(
                            measured("J-1", 4.5, 0.237).weldedOn("2025-02-30").build()))
                    .hasMessageContaining("not a real calendar day");
        }

        @Test
        void leavesTheOptionalFieldsEmpty() {
            Joint joint = Spec.makeJoint(measured("J-1", 4.5, 0.237).build());
            assertThat(joint.spool()).isEmpty();
            assertThat(joint.welder()).isEmpty();
            assertThat(joint.weldedOn()).isEmpty();
        }

        @Test
        void refusesSomethingThatIsNotAnObject() {
            assertThatThrownBy(() -> Spec.makeJoint(null)).hasMessageContaining("must be described by an object");
        }
    }

    @Nested
    class TheDerivedMeasures {

        @Test
        void measuresTheWeldRoundTheJoint() {
            Joint joint = Spec.makeJoint(sized("J-1", 6, 40).build());
            assertThat(Spec.weldLengthIn(joint)).isCloseTo(20.813, digits(3));
        }

        @Test
        void spotsASmallBoreJoint() {
            assertThat(Spec.isSmallBoreJoint(Spec.makeJoint(sized("J-1", 2, 40).build())))
                    .isTrue();
            assertThat(Spec.isSmallBoreJoint(Spec.makeJoint(sized("J-2", 6, 40).build())))
                    .isFalse();
        }

        @Test
        void describesAJointFromTheTableForARegisterRow() {
            Joint joint = Spec.makeJoint(sized("J-118", 6, 40).welder("A17").build());
            assertThat(Spec.describeJoint(joint)).isEqualTo("J-118 6 in sch 40 5G SMAW welder A17");
        }

        @Test
        void describesAMeasuredJointByItsMeasurements() {
            Joint joint = Spec.makeJoint(measured("J-201", 4.5, 0.237).build());
            assertThat(Spec.describeJoint(joint)).isEqualTo("J-201 4.5 in OD x 0.237 wall 5G SMAW");
        }

        @Test
        void looksADiameterUpWithoutBuildingAJoint() {
            assertThat(Spec.diameterForSize("12")).isCloseTo(12.75, digits(4));
        }
    }
}
