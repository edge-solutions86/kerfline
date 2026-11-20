package io.github.rkaushikdev.kerfline.joint;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HeatTest {

    private static final List<PipeHeat> HEATS = List.of(
            Heat.makeHeat(HeatInput.builder()
                    .number("H-4471")
                    .materialId("CARBON_STEEL")
                    .receivedOn("2025-02-01")
                    .millReport("MTR-1")
                    .build()),
            Heat.makeHeat(HeatInput.builder()
                    .number("H-4472")
                    .materialId("CARBON_STEEL")
                    .receivedOn("2025-02-01")
                    .millReport("MTR-2")
                    .build()),
            Heat.makeHeat(HeatInput.builder()
                    .number("H-4480")
                    .materialId("STAINLESS")
                    .receivedOn("2025-02-10")
                    .build()));

    private static Joint joint(String id) {
        return Spec.makeJoint(
                JointInput.builder().id(id).nominalSize(6).schedule(40).build());
    }

    private static final Joint BASE = joint("J-101");

    private static HeatInput.Builder heat(String number, String materialId, String receivedOn) {
        return HeatInput.builder().number(number).materialId(materialId).receivedOn(receivedOn);
    }

    @Nested
    class MakeHeat {

        @Test
        void recordsTheMetalAndWhetherItCanBeTraced() {
            assertThat(HEATS.get(0).material()).isEqualTo("carbon steel");
            assertThat(HEATS.get(0).traceable()).isTrue();
            assertThat(HEATS.get(2).traceable()).isFalse();
        }

        @Test
        void refusesAHeatWithNoNumber() {
            assertThatThrownBy(() -> Heat.makeHeat(
                            heat(" ", "CARBON_STEEL", "2025-02-01").build()))
                    .hasMessageContaining("identified by its number");
        }

        @Test
        void refusesAMaterialNobodyStocks() {
            assertThatThrownBy(() ->
                            Heat.makeHeat(heat("H-1", "LEAD", "2025-02-01").build()))
                    .hasMessageContaining("material must be one of");
        }

        @Test
        void refusesADateThatIsNotARealDay() {
            assertThatThrownBy(() -> Heat.makeHeat(
                            heat("H-1", "CARBON_STEEL", "2025-02-30").build()))
                    .hasMessageContaining("not a real calendar day");
        }
    }

    @Nested
    class HeatOf {

        @Test
        void findsAHeat() {
            assertThat(Heat.heatOf(HEATS, "H-4471").materialId()).isEqualTo("CARBON_STEEL");
        }

        @Test
        void refusesOneItDoesNotHaveNamingWhatItDoes() {
            assertThatThrownBy(() -> Heat.heatOf(HEATS, "H-9999")).hasMessageContaining("no heat H-9999 on file");
        }
    }

    @Nested
    class AssignHeats {

        @Test
        void takesOneHeatOnAJointWeldedWithinALength() {
            assertThat(Heat.assignHeats(BASE, "H-4471").heats()).containsExactly("H-4471");
        }

        @Test
        void takesBothOnAJointBetweenTwoLengths() {
            Joint joint = Heat.assignHeats(BASE, "H-4471", "H-4472");
            assertThat(joint.heats()).containsExactly("H-4471", "H-4472");
        }

        @Test
        void leavesTheJointItWasGivenAlone() {
            Heat.assignHeats(BASE, "H-4471");
            assertThat(BASE.heats()).isEmpty();
        }

        @Test
        void refusesAJointWithNoUpstreamHeat() {
            assertThatThrownBy(() -> Heat.assignHeats(BASE, "")).hasMessageContaining("upstream side");
        }
    }

    @Nested
    class TraceJoint {

        @Test
        void tracesAJointBackToItsMetal() {
            Joint joint = Heat.assignHeats(BASE, "H-4471", "H-4472");
            HeatTrace trace = Heat.traceJoint(joint, HEATS);
            assertThat(trace.traceable()).isTrue();
            assertThat(trace.heats()).containsExactly("H-4471", "H-4472");
            assertThat(trace.dissimilar()).isFalse();
        }

        @Test
        void worksTheEquivalentSteelThicknessOutFromTheMetal() {
            Joint joint = Heat.assignHeats(BASE, "H-4471");
            assertThat(Heat.traceJoint(joint, HEATS).equivalentSteelIn().getAsDouble())
                    .isCloseTo(0.28, digits(3));
        }

        @Test
        void takesTheHarderMetalOnADissimilarJoint() {
            Joint joint = Heat.assignHeats(BASE, "H-4471", "H-4480");
            HeatTrace trace = Heat.traceJoint(joint, HEATS);
            assertThat(trace.dissimilar()).isTrue();
            assertThat(trace.governingMaterial()).contains("STAINLESS");
            assertThat(trace.equivalentSteelIn().getAsDouble()).isGreaterThan(0.28);
        }

        @Test
        void isNotTraceableWhenAMillReportIsMissing() {
            Joint joint = Heat.assignHeats(BASE, "H-4480");
            HeatTrace trace = Heat.traceJoint(joint, HEATS);
            assertThat(trace.traceable()).isFalse();
            assertThat(trace.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("no mill report for heat H-4480"));
        }

        @Test
        void isNotTraceableWhenNoHeatWasRecordedAtAll() {
            HeatTrace trace = Heat.traceJoint(BASE, HEATS);
            assertThat(trace.traceable()).isFalse();
            assertThat(trace.reason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("no heat recorded"));
        }

        @Test
        void refusesAHeatThatIsNotOnFile() {
            Joint joint = Heat.assignHeats(BASE, "H-9999");
            assertThatThrownBy(() -> Heat.traceJoint(joint, HEATS)).hasMessageContaining("no heat H-9999");
        }
    }

    @Nested
    class UntraceableHeatsAndUntraceableJoints {

        @Test
        void namesTheHeatsWithNoMillReport() {
            assertThat(Heat.untraceableHeats(HEATS).stream().map(PipeHeat::number))
                    .containsExactly("H-4480");
        }

        @Test
        void namesTheJointsThoseHeatsWentInto() {
            List<Joint> joints =
                    List.of(Heat.assignHeats(joint("J-1"), "H-4471"), Heat.assignHeats(joint("J-2"), "H-4480"));
            assertThat(Heat.untraceableJoints(joints, HEATS).stream().map(HeatTrace::jointId))
                    .containsExactly("J-2");
        }

        @Test
        void findsNothingWhenEveryHeatHasItsReport() {
            List<PipeHeat> good = HEATS.stream().filter(PipeHeat::traceable).toList();
            List<Joint> joints = List.of(Heat.assignHeats(BASE, "H-4471"));
            assertThat(Heat.untraceableJoints(joints, good)).isEmpty();
        }
    }

    @Nested
    class JointsFromHeat {

        private final List<Joint> joints = List.of(
                Heat.assignHeats(joint("J-10"), "H-4471"),
                Heat.assignHeats(joint("J-2"), "H-4471", "H-4472"),
                Heat.assignHeats(joint("J-3"), "H-4472"));

        @Test
        void answersWhatARecallAsksEveryJointThatHeatWentInto() {
            assertThat(Heat.jointsFromHeat(joints, "H-4471")).containsExactly("J-2", "J-10");
        }

        @Test
        void countsAJointWeldedBetweenTwoHeatsUnderBoth() {
            assertThat(Heat.jointsFromHeat(joints, "H-4472")).contains("J-2");
        }

        @Test
        void findsNothingForAHeatThatNeverWentAnywhere() {
            assertThat(Heat.jointsFromHeat(joints, "H-4480")).isEmpty();
        }

        @Test
        void refusesALookupThatIsNotAHeatNumber() {
            assertThatThrownBy(() -> Heat.jointsFromHeat(joints, null)).hasMessageContaining("looked up by its number");
        }
    }
}
