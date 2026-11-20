package io.github.rkaushikdev.kerfline.joint;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WpsTest {

    private static Procedure procedure(String id, double couponThicknessIn, double couponOdIn, String testPosition) {
        return Wps.makeProcedure(ProcedureInput.builder()
                .id(id)
                .process("SMAW")
                .couponThicknessIn(couponThicknessIn)
                .couponOdIn(couponOdIn)
                .testPosition(testPosition)
                .build());
    }

    private static final Procedure WPS = procedure("WPS-14", 0.375, 6.625, "6G");

    private static Joint sized(String id, double size, String process) {
        return Spec.makeJoint(JointInput.builder()
                .id(id)
                .nominalSize(size)
                .schedule(40)
                .process(process)
                .build());
    }

    private static Joint measured(String id, double odIn, double wallIn, String process) {
        return Spec.makeJoint(JointInput.builder()
                .id(id)
                .odIn(odIn)
                .wallIn(wallIn)
                .process(process)
                .build());
    }

    private static String reasons(Procedure procedure, Joint joint) {
        return String.join(" ", Wps.coversJoint(procedure, joint).reasons());
    }

    @Nested
    class PositionsCoveredBy {

        @Test
        void namesTheTestPositions() {
            assertThat(Wps.positions()).containsExactly("1G", "2G", "5G", "6G");
        }

        @Test
        void letsA6GTestCoverEverything() {
            assertThat(Wps.positionsCoveredBy("6G")).containsExactly("1G", "2G", "5G", "6G");
        }

        @Test
        void letsA1GTestCoverOnlyItself() {
            assertThat(Wps.positionsCoveredBy("1G")).containsExactly("1G");
        }

        @Test
        void doesNotLetA5GTestCover2G() {
            assertThat(Wps.positionsCoveredBy("5G")).doesNotContain("2G");
        }

        @Test
        void doesNotLetA2GTestCover5G() {
            assertThat(Wps.positionsCoveredBy("2G")).doesNotContain("5G");
        }

        @Test
        void refusesAPositionNobodyTestsIn() {
            assertThatThrownBy(() -> Wps.positionsCoveredBy("3G")).hasMessageContaining("test position must be one of");
        }
    }

    @Nested
    class ThicknessRange {

        @Test
        void runsFromHalfTheCouponToTwiceIt() {
            assertThat(Wps.thicknessRange(0.5)).isEqualTo(new QualifiedRange(0.25, 1));
        }

        @Test
        void isNotCentredOnTheCoupon() {
            QualifiedRange range = Wps.thicknessRange(0.5);
            double middle = (range.minimumIn() + range.maximumIn()) / 2;
            assertThat(middle).isNotCloseTo(0.5, digits(6));
        }

        @Test
        void holdsTheFloorUpOnAVeryThinCoupon() {
            assertThat(Wps.thicknessRange(0.125).minimumIn()).isCloseTo(0.0625, digits(4));
        }

        @Test
        void opensTheCeilingRightUpPastThreeQuarters() {
            assertThat(Wps.thicknessRange(0.75).maximumIn()).isEqualTo(8);
            assertThat(Wps.thicknessRange(1).maximumIn()).isEqualTo(8);
            assertThat(Wps.thicknessRange(0.7).maximumIn()).isCloseTo(1.4, digits(4));
        }

        @Test
        void refusesACouponAtZero() {
            assertThatThrownBy(() -> Wps.thicknessRange(0)).hasMessageContaining("coupon thickness");
        }
    }

    @Nested
    class DiameterRange {

        @Test
        void runsUpwardOnly() {
            assertThat(Wps.diameterRange(6.625).maximumIn()).isEqualTo(Double.POSITIVE_INFINITY);
        }

        @Test
        void letsASmallBoreCouponCoverEverythingAboveOneInch() {
            assertThat(Wps.diameterRange(2).minimumIn()).isEqualTo(1);
        }

        @Test
        void doesNotLetALargeCouponComeBackDownToSmallBore() {
            assertThat(Wps.diameterRange(12.75).minimumIn()).isCloseTo(2.875, digits(4));
        }

        @Test
        void pinsAVerySmallCouponToItsOwnSize() {
            assertThat(Wps.diameterRange(0.84).minimumIn()).isCloseTo(0.84, digits(4));
        }
    }

    @Nested
    class MakeProcedure {

        @Test
        void worksTheThreeBandsOutFromTheOneCoupon() {
            assertThat(WPS.thicknessRange()).isEqualTo(new QualifiedRange(0.1875, 0.75));
            assertThat(WPS.diameterRange().minimumIn()).isCloseTo(2.875, digits(4));
            assertThat(WPS.positionsCovered()).hasSize(4);
        }

        @Test
        void refusesAProcedureWithNoNumber() {
            assertThatThrownBy(() -> Wps.makeProcedure(ProcedureInput.builder()
                            .id(" ")
                            .couponThicknessIn(0.375)
                            .couponOdIn(6.625)
                            .testPosition("6G")
                            .build()))
                    .hasMessageContaining("identified by its number");
        }

        @Test
        void refusesATestPositionItDoesNotKnow() {
            assertThatThrownBy(() -> Wps.makeProcedure(ProcedureInput.builder()
                            .id("W-1")
                            .couponThicknessIn(0.375)
                            .couponOdIn(6.625)
                            .testPosition("4G")
                            .build()))
                    .hasMessageContaining("test position");
        }
    }

    @Nested
    class CoversJoint {

        @Test
        void coversAJointInsideEveryBand() {
            ProcedureCheck check = Wps.coversJoint(WPS, sized("J-1", 6, "SMAW"));
            assertThat(check.covers()).isTrue();
            assertThat(check.reasons()).isEmpty();
        }

        @Test
        void refusesAJointThickerThanTheBand() {
            ProcedureCheck check = Wps.coversJoint(WPS, measured("J-2", 6.625, 0.9, "SMAW"));
            assertThat(check.covers()).isFalse();
            assertThat(String.join(" ", check.reasons())).contains("over the qualified");
        }

        @Test
        void refusesAJointThinnerThanTheBand() {
            assertThat(reasons(WPS, measured("J-3", 6.625, 0.1, "SMAW"))).contains("under the qualified 0.1875");
        }

        @Test
        void refusesASmallBoreJointTheCouponDoesNotReachDownTo() {
            assertThat(reasons(WPS, sized("J-4", 2, "SMAW"))).contains("diameter");
        }

        @Test
        void refusesAPositionTheTestDoesNotCover() {
            Procedure flat = procedure("WPS-1", 0.375, 6.625, "1G");
            Joint joint = Spec.makeJoint(JointInput.builder()
                    .id("J-5")
                    .nominalSize(6)
                    .schedule(40)
                    .position("5G")
                    .build());
            assertThat(reasons(flat, joint)).contains("position 5G is not covered");
        }

        @Test
        void refusesADifferentProcess() {
            assertThat(reasons(WPS, sized("J-6", 6, "GTAW"))).contains("process GTAW");
        }

        @Test
        void reportsEveryReasonAtOnceRatherThanTheFirst() {
            Joint joint = measured("J-7", 2.375, 0.9, "GTAW");
            assertThat(Wps.coversJoint(WPS, joint).reasons().size()).isGreaterThanOrEqualTo(3);
        }

        @Test
        void refusesACheckWithNoJoint() {
            assertThatThrownBy(() -> Wps.coversJoint(WPS, null)).hasMessageContaining("needs a joint");
        }
    }

    @Nested
    class ProceduresForAndChooseProcedure {

        private final Procedure thin = procedure("WPS-9", 0.25, 2.375, "6G");
        private final List<Procedure> procedures = List.of(WPS, thin);

        @Test
        void listsEveryProcedureThatCoversTheJoint() {
            assertThat(Wps.proceduresFor(procedures, sized("J-1", 6, "SMAW")).stream()
                            .map(Procedure::id)
                            .sorted())
                    .containsExactly("WPS-14", "WPS-9");
        }

        @Test
        void putsTheClosestCouponFirst() {
            Joint joint = measured("J-1", 6.625, 0.28, "SMAW");
            assertThat(Wps.chooseProcedure(procedures, joint).id()).isEqualTo("WPS-9");
        }

        @Test
        void picksTheOtherOneWhenTheWallMoves() {
            Joint joint = measured("J-2", 6.625, 0.432, "SMAW");
            assertThat(Wps.chooseProcedure(procedures, joint).id()).isEqualTo("WPS-14");
        }

        @Test
        void findsNothingForAJointOutsideEveryBand() {
            Joint joint = measured("J-3", 24, 2, "SMAW");
            assertThat(Wps.proceduresFor(procedures, joint)).isEmpty();
        }

        @Test
        void refusesToChooseWhenNothingFitsAndSaysWhatWasTried() {
            Joint joint = measured("J-3", 24, 2, "SMAW");
            NotFoundException err =
                    catchThrowableOfType(NotFoundException.class, () -> Wps.chooseProcedure(procedures, joint));
            assertThat(err.getMessage()).contains("no qualified procedure");
            assertThat(err.details().get("tried"))
                    .asInstanceOf(InstanceOfAssertFactories.LIST)
                    .hasSize(2);
        }
    }
}
