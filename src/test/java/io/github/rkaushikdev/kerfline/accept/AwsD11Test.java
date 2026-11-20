package io.github.rkaushikdev.kerfline.accept;

import static io.github.rkaushikdev.kerfline.accept.Indications.at;
import static io.github.rkaushikdev.kerfline.accept.Indications.ind;
import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AwsD11Test {

    private static List<Indication> packedPores(double size) {
        return List.of(
                at("POROSITY", size, 0),
                at("POROSITY", size, 0.3),
                at("POROSITY", size, 0.6),
                at("POROSITY", size, 0.9));
    }

    @Nested
    class TheCode {

        @Test
        void namesItselfAndItsTwoLoadings() {
            assertThat(AwsD11.codeName()).isEqualTo("AWSD1.1");
            assertThat(AwsD11.loadings()).containsExactly("static", "cyclic");
        }

        @Test
        void isTighterThroughoutForCyclicLoading() {
            AwsD11Rules statically = AwsD11.rulesFor("static");
            AwsD11Rules cyclically = AwsD11.rulesFor("cyclic");
            assertThat(cyclically.fraction()).isLessThan(statically.fraction());
            assertThat(cyclically.ceilingIn()).isLessThan(statically.ceilingIn());
            assertThat(cyclically.sumIn()).isLessThan(statically.sumIn());
            assertThat(cyclically.clearanceMultiple()).isGreaterThan(statically.clearanceMultiple());
        }

        @Test
        void refusesALoadingItDoesNotKnow() {
            assertThatThrownBy(() -> AwsD11.rulesFor("impact")).hasMessageContaining("loading must be one of");
        }
    }

    @Nested
    class MaxDiscontinuityIn {

        @Test
        void scalesWithTheWeldThroughTheMiddleOfTheRange() {
            assertThat(AwsD11.maxDiscontinuityIn(1.2)).isCloseTo(0.4, digits(4));
        }

        @Test
        void holdsAFloorOnThinMaterial() {
            assertThat(AwsD11.maxDiscontinuityIn(0.1)).isCloseTo(0.125, digits(4));
            assertThat(AwsD11.maxDiscontinuityIn(0.25)).isCloseTo(0.125, digits(4));
        }

        @Test
        void holdsACeilingOnThickMaterial() {
            assertThat(AwsD11.maxDiscontinuityIn(6)).isCloseTo(0.75, digits(4));
            assertThat(AwsD11.maxDiscontinuityIn(3)).isCloseTo(0.75, digits(4));
        }

        @Test
        void neverGoesDownAsTheWeldThickens() {
            double previous = 0;
            for (double t = 0.1; t <= 4; t += 0.1) {
                double cap = AwsD11.maxDiscontinuityIn(Num.parse(Num.toFixed(t, 1)));
                assertThat(cap).isGreaterThanOrEqualTo(previous - 1e-9);
                previous = cap;
            }
        }

        @Test
        void isAboutHalfAsGenerousForCyclicLoading() {
            assertThat(AwsD11.maxDiscontinuityIn(1.2, "cyclic")).isCloseTo(0.2, digits(4));
        }
    }

    @Nested
    class RequiredClearanceIn {

        @Test
        void isThreeTimesTheLargerForStaticLoading() {
            assertThat(AwsD11.requiredClearanceIn(0.2)).isCloseTo(0.6, digits(4));
        }

        @Test
        void isSixTimesForCyclicLoading() {
            assertThat(AwsD11.requiredClearanceIn(0.2, "cyclic")).isCloseTo(1.2, digits(4));
        }
    }

    @Nested
    class JudgeOne {

        @Test
        void rejectsACrackAtAnySize() {
            assertThat(AwsD11.judgeOne(ind("CRACK", 0.01), 1).disposition()).isEqualTo(Disposition.REJECT);
        }

        @Test
        void rejectsIncompleteFusionAtAnySize() {
            assertThat(AwsD11.judgeOne(ind("IF", 0.01), 1).disposition()).isEqualTo(Disposition.REJECT);
        }

        @Test
        void acceptsADiscontinuityUnderTheCap() {
            assertThat(AwsD11.judgeOne(ind("SLAG", 0.2), 1.2).disposition()).isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void callsARepairOverTheCap() {
            assertThat(AwsD11.judgeOne(ind("SLAG", 0.5), 1.2).disposition()).isEqualTo(Disposition.REPAIR);
        }

        @Test
        void flipsTheVerdictOnTheSameOneUnderCyclicLoading() {
            Indication slag = ind("SLAG", 0.3);
            assertThat(AwsD11.judgeOne(slag, 1.2).disposition()).isEqualTo(Disposition.ACCEPT);
            assertThat(AwsD11.judgeOne(slag, 1.2, "cyclic").disposition()).isEqualTo(Disposition.REPAIR);
        }
    }

    @Nested
    class JudgeSmallSum {

        @Test
        void findsNothingWhenThereIsNothingSmall() {
            assertThat(AwsD11.judgeSmallSum(List.of(at("SLAG", 0.3, 0)))).isEmpty();
        }

        @Test
        void acceptsAFewSmallOnesInsideTheSum() {
            List<Indication> set = List.of(at("POROSITY", 0.1, 0), at("POROSITY", 0.1, 0.5));
            assertThat(AwsD11.judgeSmallSum(set)).isEmpty();
        }

        @Test
        void catchesAScatterOfSmallOnesPackedIntoOneInch() {
            assertThat(AwsD11.judgeSmallSum(packedPores(0.1))).isNotEmpty();
        }

        @Test
        void letsTheSameScatterThroughWhenItIsSpreadOut() {
            List<Indication> set = List.of(
                    at("POROSITY", 0.1, 0), at("POROSITY", 0.1, 3), at("POROSITY", 0.1, 6), at("POROSITY", 0.1, 9));
            assertThat(AwsD11.judgeSmallSum(set)).isEmpty();
        }

        @Test
        void leavesTheLargeOnesOutOfTheSum() {
            List<Indication> set = List.of(at("SLAG", 0.5, 0), at("SLAG", 0.5, 0.6));
            assertThat(AwsD11.judgeSmallSum(set)).isEmpty();
        }

        @Test
        void isTighterUnderCyclicLoading() {
            List<Indication> set = packedPores(0.05);
            assertThat(AwsD11.judgeSmallSum(set)).isEmpty();
            assertThat(AwsD11.judgeSmallSum(set, "cyclic")).isNotEmpty();
        }
    }

    @Nested
    class JudgeClearance {

        @Test
        void acceptsTwoDiscontinuitiesWellApart() {
            List<Indication> set = List.of(at("SLAG", 0.1, 0), at("SLAG", 0.1, 5));
            assertThat(AwsD11.judgeClearance(set)).isEmpty();
        }

        @Test
        void catchesTwoAcceptableOnesSittingTooClose() {
            List<Indication> set = List.of(at("SLAG", 0.2, 0), at("SLAG", 0.2, 0.3));
            assertThat(AwsD11.judgeClearance(set)).isNotEmpty();
        }

        @Test
        void scalesTheClearanceWithTheLargerOfThePair() {
            List<Indication> small = List.of(at("SLAG", 0.05, 0), at("SLAG", 0.05, 0.4));
            List<Indication> large = List.of(at("SLAG", 0.3, 0), at("SLAG", 0.3, 0.4));
            assertThat(AwsD11.judgeClearance(small)).isEmpty();
            assertThat(AwsD11.judgeClearance(large)).isNotEmpty();
        }

        @Test
        void wantsMoreClearanceUnderCyclicLoading() {
            List<Indication> set = List.of(at("SLAG", 0.1, 0), at("SLAG", 0.1, 0.5));
            assertThat(AwsD11.judgeClearance(set)).isEmpty();
            assertThat(AwsD11.judgeClearance(set, "cyclic")).isNotEmpty();
        }

        @Test
        void sortsBeforeComparingSoTheCallerDoesNotHaveTo() {
            List<Indication> set = List.of(at("SLAG", 0.2, 0.3), at("SLAG", 0.2, 0));
            assertThat(AwsD11.judgeClearance(set)).isNotEmpty();
        }

        @Test
        void saysNothingAboutASingleDiscontinuity() {
            assertThat(AwsD11.judgeClearance(List.of(at("SLAG", 0.2, 0)))).isEmpty();
        }
    }

    @Nested
    class JudgeJoint {

        @Test
        void acceptsACleanJoint() {
            assertThat(AwsD11.judgeJoint(List.of(), 1).disposition()).isEqualTo(Disposition.ACCEPT);
        }

        @Test
        void takesAJointOutForAPlanarIndication() {
            JointVerdict result = AwsD11.judgeJoint(List.of(at("CRACK", 0.05, 1)), 1);
            assertThat(result.disposition()).isEqualTo(Disposition.REJECT);
        }

        @Test
        void failsAJointOnTheSumRuleAlone() {
            JointVerdict result = AwsD11.judgeJoint(packedPores(0.1), 2);
            assertThat(result.disposition()).isEqualTo(Disposition.REPAIR);
            assertThat(result.reasons()).anyMatch(reason -> reason.contains("one inch of weld"));
        }

        @Test
        void givesTheSameJointDifferentAnswersUnderTheTwoLoadings() {
            List<Indication> set = List.of(at("SLAG", 0.3, 0));
            assertThat(AwsD11.judgeJoint(set, 1.2).disposition()).isEqualTo(Disposition.ACCEPT);
            assertThat(AwsD11.judgeJoint(set, 1.2, "cyclic").disposition()).isEqualTo(Disposition.REPAIR);
        }

        @Test
        void refusesAThicknessAtZeroOrALoadingItDoesNotKnow() {
            assertThatThrownBy(() -> AwsD11.judgeJoint(List.of(), 0)).hasMessageContaining("weld thickness");
            assertThatThrownBy(() -> AwsD11.judgeJoint(List.of(), 1, "impact")).hasMessageContaining("loading");
        }
    }
}
