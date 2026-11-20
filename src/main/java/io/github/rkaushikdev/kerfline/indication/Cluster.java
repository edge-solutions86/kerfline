package io.github.rkaushikdev.kerfline.indication;

import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Grouping rounded indications.
 *
 * <p>Scattered porosity and cluster porosity are different findings with different limits, and the
 * only thing separating them is how close the pores are to each other. So the grouping has to
 * happen before anything is judged, and it has to be done the same way every time, which is why it
 * is here and not in the reader's head.
 *
 * <p>The grouping is single linkage: two pores are in the same cluster if they are within the gap
 * of each other, and a chain of pores each within the gap of the next is one cluster however long
 * the chain gets. That is the right behaviour, because a run of pores along a weld is one defect
 * even when the ends of it are inches apart.
 */
public final class Cluster {

    private static final double DEFAULT_GAP_IN = 0.125;

    /** Along the weld, ties keeping their order. */
    private static final Comparator<Indication> ALONG =
            (a, b) -> sign(positionOf(a).at() - positionOf(b).at());

    private Cluster() {}

    public static double defaultGapIn() {
        return DEFAULT_GAP_IN;
    }

    private record Position(double at, double across) {}

    private static Position positionOf(Indication indication) {
        return new Position(indication.at().orElse(0), indication.across().orElse(0));
    }

    private static int sign(double difference) {
        return difference < 0 ? -1 : difference > 0 ? 1 : 0;
    }

    /** Edge to edge distance between two indications, floored at zero. */
    public static double separationIn(Indication a, Indication b) {
        Position first = positionOf(a);
        Position second = positionOf(b);
        double along = second.at() - first.at();
        double across = second.across() - first.across();
        double centres = Math.sqrt(along * along + across * across);
        double radii = (Measure.governingSizeIn(a) + Measure.governingSizeIn(b)) / 2;
        return Num.round(Math.max(0, centres - radii), 4);
    }

    public static List<List<Indication>> clusterOf(List<Indication> indications) {
        return clusterOf(indications, DEFAULT_GAP_IN);
    }

    /**
     * Group by single linkage, groups and their members sorted along the weld. Indications with no
     * position are left out: a pore nobody located cannot be grouped with anything, and guessing it
     * into a cluster would invent a defect.
     */
    public static List<List<Indication>> clusterOf(List<Indication> indications, double gapIn) {
        Guard.requireArray(indications, "indications");
        Guard.requireAbove(gapIn, 0, "gap");
        List<Indication> placed =
                indications.stream().filter(i -> i.at().isPresent()).toList();
        List<List<Indication>> groups = new ArrayList<>();
        Set<Integer> taken = new HashSet<>();

        for (int i = 0; i < placed.size(); i++) {
            if (taken.contains(i)) {
                continue;
            }
            List<Integer> group = new ArrayList<>(List.of(i));
            taken.add(i);
            // Walk the chain outward until nothing new comes within the gap.
            for (int cursor = 0; cursor < group.size(); cursor++) {
                for (int j = 0; j < placed.size(); j++) {
                    if (taken.contains(j)) {
                        continue;
                    }
                    if (separationIn(placed.get(group.get(cursor)), placed.get(j)) <= gapIn) {
                        group.add(j);
                        taken.add(j);
                    }
                }
            }
            List<Indication> members =
                    new ArrayList<>(group.stream().map(placed::get).toList());
            members.sort(ALONG);
            groups.add(List.copyOf(members));
        }
        groups.sort((a, b) -> ALONG.compare(a.get(0), b.get(0)));
        return List.copyOf(groups);
    }

    /** The extent of a group, along the weld and across it, edge to edge, and its diagonal. */
    public static ClusterExtent clusterExtent(List<Indication> group) {
        Guard.requireArray(group, "group");
        if (group.isEmpty()) {
            return new ClusterExtent(0, 0, 0, 0);
        }
        List<Double> lows = new ArrayList<>();
        List<Double> highs = new ArrayList<>();
        List<Double> nears = new ArrayList<>();
        List<Double> fars = new ArrayList<>();
        for (Indication indication : group) {
            Position position = positionOf(indication);
            double half = Measure.governingSizeIn(indication) / 2;
            lows.add(position.at() - half);
            highs.add(position.at() + half);
            nears.add(position.across() - half);
            fars.add(position.across() + half);
        }
        double alongIn = Num.round(Num.maxOf(highs) - Num.minOf(lows), 4);
        double acrossIn = Num.round(Num.maxOf(fars) - Num.minOf(nears), 4);
        return new ClusterExtent(
                alongIn, acrossIn, Num.round(Math.sqrt(alongIn * alongIn + acrossIn * acrossIn), 4), group.size());
    }

    public static List<List<Indication>> clusters(List<Indication> indications) {
        return clusters(indications, DEFAULT_GAP_IN);
    }

    /** A group of two or more is a cluster. */
    public static List<List<Indication>> clusters(List<Indication> indications, double gapIn) {
        return clusterOf(indications, gapIn).stream()
                .filter(group -> group.size() > 1)
                .toList();
    }

    public static List<Indication> scattered(List<Indication> indications) {
        return scattered(indications, DEFAULT_GAP_IN);
    }

    /** The located indications that stand alone. */
    public static List<Indication> scattered(List<Indication> indications, double gapIn) {
        return clusterOf(indications, gapIn).stream()
                .filter(group -> group.size() == 1)
                .map(group -> group.get(0))
                .toList();
    }

    /**
     * Turn a group into the single indication a code paragraph will judge, sized by the extent of
     * the group and starting where its first pore does. The members are kept on it, because a
     * reader asked to justify a cluster call has to be able to point at the pores it was made from.
     * Empty for an empty group.
     */
    public static Optional<Indication> asCluster(List<Indication> group) {
        Guard.requireArray(group, "group");
        if (group.isEmpty()) {
            return Optional.empty();
        }
        ClusterExtent extent = clusterExtent(group);
        Indication first = group.get(0);
        return Optional.of(new Indication(
                IndicationType.CLUSTER,
                IndicationType.CLUSTER.label(),
                false,
                MeasuredBy.DIAMETER,
                extent.diameterIn(),
                first.magnification(),
                extent.diameterIn(),
                OptionalDouble.of(extent.acrossIn()),
                first.at(),
                first.film(),
                Optional.of(group.size() + " pores"),
                OptionalDouble.empty(),
                group));
    }

    public static List<Indication> reportable(List<Indication> indications) {
        return reportable(indications, DEFAULT_GAP_IN);
    }

    /**
     * Reduce a set of indications to what gets reported: clusters as single cluster indications,
     * everything else as it stands, the unlocated ones last.
     */
    public static List<Indication> reportable(List<Indication> indications, double gapIn) {
        List<Indication> out = new ArrayList<>();
        for (List<Indication> group : clusterOf(indications, gapIn)) {
            out.add(group.size() > 1 ? asCluster(group).orElseThrow() : group.get(0));
        }
        indications.stream().filter(i -> i.at().isEmpty()).forEach(out::add);
        return List.copyOf(out);
    }
}
