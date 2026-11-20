package io.github.rkaushikdev.kerfline.indication;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClusterTest {

    private static Indication pore(double at, double across) {
        return pore(at, across, 0.04);
    }

    private static Indication pore(double at, double across, double size) {
        return Measure.makeIndication(IndicationInput.of("POROSITY", size).at(at))
                .withAcross(across);
    }

    @Nested
    class SeparationIn {

        @Test
        void measuresEdgeToEdgeAndNotCentreToCentre() {
            Indication a = pore(0, 0, 0.1);
            Indication b = pore(0.2, 0, 0.1);
            assertThat(Cluster.separationIn(a, b)).isCloseTo(0.1, digits(4));
        }

        @Test
        void isNothingForTwoIndicationsThatTouchOrOverlap() {
            assertThat(Cluster.separationIn(pore(0, 0, 0.2), pore(0.1, 0, 0.2))).isEqualTo(0);
        }

        @Test
        void measuresAcrossTheWeldAsWellAsAlongIt() {
            double straight = Cluster.separationIn(pore(0, 0), pore(0.3, 0));
            double diagonal = Cluster.separationIn(pore(0, 0), pore(0.3, 0.3));
            assertThat(diagonal).isGreaterThan(straight);
        }

        @Test
        void treatsAMissingAcrossAsOnTheCentreline() {
            Indication a =
                    Measure.makeIndication(IndicationInput.of("POROSITY", 0.04).at(0));
            Indication b =
                    Measure.makeIndication(IndicationInput.of("POROSITY", 0.04).at(0.3));
            assertThat(Cluster.separationIn(a, b)).isCloseTo(0.26, digits(4));
        }
    }

    @Nested
    class ClusterOf {

        @Test
        void usesAnEighthOfAnInchByDefault() {
            assertThat(Cluster.defaultGapIn()).isEqualTo(0.125);
        }

        @Test
        void groupsTwoPoresThatAreClose() {
            List<List<Indication>> groups = Cluster.clusterOf(List.of(pore(0, 0), pore(0.1, 0)));
            assertThat(groups).hasSize(1);
            assertThat(groups.get(0)).hasSize(2);
        }

        @Test
        void leavesTwoDistantPoresApart() {
            assertThat(Cluster.clusterOf(List.of(pore(0, 0), pore(2, 0)))).hasSize(2);
        }

        @Test
        void chainsARunOfPoresIntoOneClusterHoweverLongItGets() {
            List<Indication> run = List.of(pore(0, 0), pore(0.12, 0), pore(0.24, 0), pore(0.36, 0));
            List<List<Indication>> groups = Cluster.clusterOf(run);
            assertThat(groups).hasSize(1);
            assertThat(groups.get(0)).hasSize(4);
        }

        @Test
        void doesNotChainAcrossARealGapInTheRun() {
            List<Indication> run = List.of(pore(0, 0), pore(0.12, 0), pore(3, 0), pore(3.12, 0));
            assertThat(Cluster.clusterOf(run)).hasSize(2);
        }

        @Test
        void opensUpAsTheGapIsWidened() {
            List<Indication> two = List.of(pore(0, 0), pore(0.5, 0));
            assertThat(Cluster.clusterOf(two, 0.125)).hasSize(2);
            assertThat(Cluster.clusterOf(two, 0.6)).hasSize(1);
        }

        @Test
        void sortsGroupsAlongTheWeldAndMembersInsideThem() {
            List<List<Indication>> groups = Cluster.clusterOf(List.of(pore(3, 0), pore(0.1, 0), pore(0, 0)));
            assertThat(groups.get(0).get(0).at()).hasValue(0);
            assertThat(groups.get(0).get(1).at()).hasValue(0.1);
            assertThat(groups.get(1).get(0).at()).hasValue(3);
        }

        @Test
        void leavesOutIndicationsNobodyLocated() {
            Indication unplaced = Measure.makeIndication(IndicationInput.of("POROSITY", 0.04));
            assertThat(Cluster.clusterOf(List.of(unplaced))).isEqualTo(List.of());
        }

        @Test
        void refusesAGapAtZero() {
            assertThatThrownBy(() -> Cluster.clusterOf(List.of(pore(0, 0)), 0))
                    .hasMessageContaining("gap must be greater than 0");
        }
    }

    @Nested
    class ClusterExtentOfAGroup {

        @Test
        void measuresTheOutsideEdgesOfTheGroup() {
            ClusterExtent extent = Cluster.clusterExtent(List.of(pore(0, 0, 0.1), pore(0.5, 0, 0.1)));
            assertThat(extent.alongIn()).isCloseTo(0.6, digits(4));
            assertThat(extent.count()).isEqualTo(2);
        }

        @Test
        void measuresAcrossTheWeldToo() {
            ClusterExtent extent = Cluster.clusterExtent(List.of(pore(0, 0, 0.1), pore(0, 0.4, 0.1)));
            assertThat(extent.acrossIn()).isCloseTo(0.5, digits(4));
        }

        @Test
        void takesTheDiagonalAsTheDiameter() {
            ClusterExtent extent = Cluster.clusterExtent(List.of(pore(0, 0, 0.1), pore(0.3, 0.4, 0.1)));
            assertThat(extent.diameterIn()).isGreaterThan(extent.alongIn());
            assertThat(extent.diameterIn()).isGreaterThan(extent.acrossIn());
        }

        @Test
        void isNothingForAnEmptyGroup() {
            assertThat(Cluster.clusterExtent(List.of())).isEqualTo(new ClusterExtent(0, 0, 0, 0));
        }

        @Test
        void isThePoreItselfForAGroupOfOne() {
            assertThat(Cluster.clusterExtent(List.of(pore(0, 0, 0.1))).alongIn())
                    .isCloseTo(0.1, digits(4));
        }
    }

    @Nested
    class ClustersAndScattered {

        private final List<Indication> set = List.of(pore(0, 0), pore(0.1, 0), pore(3, 0));

        @Test
        void callsAGroupOfTwoOrMoreACluster() {
            assertThat(Cluster.clusters(set)).hasSize(1);
            assertThat(Cluster.clusters(set).get(0)).hasSize(2);
        }

        @Test
        void leavesALonePoreScattered() {
            assertThat(Cluster.scattered(set)).hasSize(1);
            assertThat(Cluster.scattered(set).get(0).at()).hasValue(3);
        }

        @Test
        void accountsForEveryLocatedIndicationOnce() {
            int counted = Cluster.clusters(set).stream().mapToInt(List::size).sum()
                    + Cluster.scattered(set).size();
            assertThat(counted).isEqualTo(3);
        }
    }

    @Nested
    class AsCluster {

        private final List<Indication> group = List.of(pore(0, 0, 0.1), pore(0.1, 0, 0.1));

        @Test
        void becomesOneIndicationACodeCanJudge() {
            Indication merged = Cluster.asCluster(group).orElseThrow();
            assertThat(merged.type()).isEqualTo(IndicationType.CLUSTER);
            assertThat(merged.measure()).isEqualTo(MeasuredBy.DIAMETER);
            assertThat(merged.planar()).isFalse();
        }

        @Test
        void takesItsSizeFromTheExtentOfTheGroup() {
            Indication merged = Cluster.asCluster(group).orElseThrow();
            assertThat(merged.sizeIn()).isCloseTo(Cluster.clusterExtent(group).diameterIn(), digits(4));
        }

        @Test
        void keepsThePoresItWasMadeFrom() {
            assertThat(Cluster.asCluster(group).orElseThrow().members()).hasSize(2);
            assertThat(Cluster.asCluster(group).orElseThrow().note()).hasValue("2 pores");
        }

        @Test
        void startsWhereTheFirstPoreDoes() {
            assertThat(Cluster.asCluster(group).orElseThrow().at()).hasValue(0);
        }

        @Test
        void isNothingForAnEmptyGroup() {
            assertThat(Cluster.asCluster(List.of())).isEmpty();
        }
    }

    @Nested
    class Reportable {

        @Test
        void reportsAClusterAsOneIndicationAndTheRestAsTheyAre() {
            List<Indication> out = Cluster.reportable(List.of(pore(0, 0), pore(0.1, 0), pore(3, 0)));
            assertThat(out).hasSize(2);
            assertThat(out.get(0).type()).isEqualTo(IndicationType.CLUSTER);
            assertThat(out.get(1).type()).isEqualTo(IndicationType.POROSITY);
        }

        @Test
        void leavesASetWithNothingCloseTogetherAlone() {
            List<Indication> out = Cluster.reportable(List.of(pore(0, 0), pore(3, 0)));
            assertThat(out).allMatch(i -> i.type() == IndicationType.POROSITY);
        }

        @Test
        void carriesUnlocatedIndicationsThroughUntouched() {
            Indication unplaced = Measure.makeIndication(IndicationInput.of("SLAG", 0.2));
            List<Indication> out = Cluster.reportable(List.of(pore(0, 0), pore(0.1, 0), unplaced));
            assertThat(out).hasSize(2);
            assertThat(out.get(1).type()).isEqualTo(IndicationType.SLAG);
        }

        @Test
        void isEmptyForNothingAtAll() {
            assertThat(Cluster.reportable(List.of())).isEqualTo(List.of());
        }
    }
}
