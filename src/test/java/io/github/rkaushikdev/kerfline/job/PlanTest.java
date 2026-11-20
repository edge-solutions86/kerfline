package io.github.rkaushikdev.kerfline.job;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.joint.JointInput;
import io.github.rkaushikdev.kerfline.joint.Spec;
import io.github.rkaushikdev.kerfline.safety.SealedSource;
import io.github.rkaushikdev.kerfline.safety.SourceLog;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlanTest {

    private static final SealedSource SOURCE = SourceLog.makeSource("IR-7741", "IR192", 80, "2025-03-01");

    private static final List<Joint> JOINTS = List.of(joint("J-101"), joint("J-102"), joint("J-103"));

    private static final PlanOptions OPTIONS =
            PlanOptions.builder().sourceToObjectIn(48).build();

    private static Joint joint(String id) {
        return Spec.makeJoint(
                JointInput.builder().id(id).nominalSize(6).schedule(40).build());
    }

    private static PlanOptions hours(double available) {
        return OPTIONS.toBuilder().hoursAvailable(available).build();
    }

    @Nested
    class TheStandingTimes {

        @Test
        void carriesASetupAndAMoveTime() {
            assertThat(Plan.setupMinutes()).isGreaterThan(0);
            assertThat(Plan.moveMinutes()).isGreaterThan(0);
        }
    }

    @Nested
    class JointMinutes {

        private final JointCost cost = Plan.jointMinutes(JOINTS.get(0), SOURCE, "2025-03-14", OPTIONS);

        @Test
        void countsASetupPerShotRatherThanPerJoint() {
            assertThat(cost.shots()).isGreaterThan(1);
            assertThat(cost.setupMinutes()).isCloseTo(Plan.setupMinutes() * cost.shots(), digits(2));
        }

        @Test
        void addsTheSetupsToTheExposures() {
            assertThat(cost.totalMinutes()).isCloseTo(cost.exposureMinutes() + cost.setupMinutes(), digits(2));
        }

        @Test
        void isUsuallyMoreSetupThanExposureOnAShortShot() {
            assertThat(cost.setupMinutes()).isGreaterThan(cost.exposureMinutes());
        }

        @Test
        void takesLongerAsTheSourceDecays() {
            JointCost march = Plan.jointMinutes(JOINTS.get(0), SOURCE, "2025-03-14", OPTIONS);
            JointCost july = Plan.jointMinutes(JOINTS.get(0), SOURCE, "2025-07-14", OPTIONS);
            assertThat(july.exposureMinutes()).isGreaterThan(march.exposureMinutes());
            assertThat(july.activityCi()).isLessThan(march.activityCi());
        }

        @Test
        void leavesTheSetupTimeAloneAsTheSourceDecays() {
            JointCost march = Plan.jointMinutes(JOINTS.get(0), SOURCE, "2025-03-14", OPTIONS);
            JointCost july = Plan.jointMinutes(JOINTS.get(0), SOURCE, "2025-07-14", OPTIONS);
            assertThat(july.setupMinutes()).isCloseTo(march.setupMinutes(), digits(2));
        }

        @Test
        void refusesADistanceAtZero() {
            PlanOptions none = PlanOptions.builder().sourceToObjectIn(0).build();
            assertThatThrownBy(() -> Plan.jointMinutes(JOINTS.get(0), SOURCE, "2025-03-14", none))
                    .hasMessageContaining("source to object distance");
        }
    }

    @Nested
    class PlanDay {

        @Test
        void fitsTheWholeListIntoALongDay() {
            DayPlan plan = Plan.planDay(JOINTS, SOURCE, "2025-03-14", hours(10));
            assertThat(plan.fitsAll()).isTrue();
            assertThat(plan.scheduled()).hasSize(3);
            assertThat(plan.spilled()).isEmpty();
        }

        @Test
        void spillsWhatDoesNotFitIntoAShortOne() {
            DayPlan plan = Plan.planDay(JOINTS, SOURCE, "2025-03-14", hours(1.5));
            assertThat(plan.fitsAll()).isFalse();
            assertThat(plan.scheduled().size() + plan.spilled().size()).isEqualTo(3);
        }

        @Test
        void keepsTheOrderTheCrewWalksTheLineIn() {
            DayPlan plan = Plan.planDay(JOINTS, SOURCE, "2025-03-14", hours(10));
            assertThat(plan.scheduled().stream().map(JointCost::jointId)).containsExactly("J-101", "J-102", "J-103");
        }

        @Test
        void takesTheMoveTimeOutOfTheBudgetBeforeAnythingIsScheduled() {
            DayPlan plan = Plan.planDay(JOINTS, SOURCE, "2025-03-14", hours(10));
            assertThat(plan.budgetMinutes()).isCloseTo(10 * 60 - Plan.moveMinutes(), digits(2));
        }

        @Test
        void accountsForEveryMinuteItSpent() {
            DayPlan plan = Plan.planDay(JOINTS, SOURCE, "2025-03-14", hours(10));
            double spent = 0;
            for (JointCost entry : plan.scheduled()) {
                spent += entry.totalMinutes();
            }
            assertThat(plan.usedMinutes()).isCloseTo(spent, digits(2));
            assertThat(plan.freeMinutes()).isCloseTo(plan.budgetMinutes() - plan.usedMinutes(), digits(2));
        }

        @Test
        void recordsTheActivityThePlanWasMadeOn() {
            DayPlan plan = Plan.planDay(JOINTS, SOURCE, "2025-03-14", hours(10));
            assertThat(plan.activityCi()).isLessThan(80);
            assertThat(plan.source()).isEqualTo("IR-7741");
        }

        @Test
        void refusesADayWithNoHoursInIt() {
            assertThatThrownBy(() -> Plan.planDay(JOINTS, SOURCE, "2025-03-14", hours(0)))
                    .hasMessageContaining("hours available");
        }

        @Test
        void plansAnEmptyListWithoutComplaining() {
            DayPlan plan = Plan.planDay(List.of(), SOURCE, "2025-03-14", OPTIONS);
            assertThat(plan.fitsAll()).isTrue();
            assertThat(plan.usedMinutes()).isEqualTo(0);
        }
    }

    @Nested
    class CostOverTime {

        private final List<String> days = List.of("2025-03-14", "2025-06-14", "2025-09-14");

        @Test
        void showsTheSameListCostingMoreAsTheMonthsPass() {
            List<DayCost> costs = Plan.costOverTime(JOINTS, SOURCE, days, hours(10));
            assertThat(costs.get(1).totalMinutes()).isGreaterThan(costs.get(0).totalMinutes());
            assertThat(costs.get(2).totalMinutes()).isGreaterThan(costs.get(1).totalMinutes());
        }

        @Test
        void showsTheActivityFallingAlongsideIt() {
            List<DayCost> costs = Plan.costOverTime(JOINTS, SOURCE, days, hours(10));
            assertThat(costs.get(2).activityCi()).isLessThan(costs.get(0).activityCi());
        }

        @Test
        void saysOnWhichDaysTheListStillFits() {
            List<DayCost> costs = Plan.costOverTime(JOINTS, SOURCE, days, hours(2));
            assertThat(costs.stream().map(DayCost::fitsAll)).contains(false);
        }
    }

    @Nested
    class LastDayThatFits {

        private final List<String> days = List.of("2025-03-14", "2025-06-14", "2025-09-14", "2025-12-14");

        @Test
        void findsTheLastDayTheWholeListStillFits() {
            String day = Plan.lastDayThatFits(JOINTS, SOURCE, days, hours(10));
            assertThat(days).contains(day);
        }

        @Test
        void neverFitsForLongerOnAShorterDay() {
            String tight = Plan.lastDayThatFits(JOINTS, SOURCE, days, hours(3.5));
            String roomy = Plan.lastDayThatFits(JOINTS, SOURCE, days, hours(10));
            assertThat(days.indexOf(roomy)).isGreaterThanOrEqualTo(days.indexOf(tight));
        }

        @Test
        void refusesWhenTheListFitsOnNoneOfThem() {
            assertThatThrownBy(() -> Plan.lastDayThatFits(JOINTS, SOURCE, days, hours(0.4)))
                    .hasMessageContaining("does not fit on any of those days");
        }
    }
}
