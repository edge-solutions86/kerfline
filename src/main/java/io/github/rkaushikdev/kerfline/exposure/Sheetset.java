package io.github.rkaushikdev.kerfline.exposure;

import static io.github.rkaushikdev.kerfline.report.Technique.buildTechnique;
import static io.github.rkaushikdev.kerfline.report.Technique.summariseTechnique;

import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.report.TechniqueInput;
import io.github.rkaushikdev.kerfline.report.TechniqueSheet;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Ids;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.RootCollation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One technique sheet per group of joints, not one per joint.
 *
 * <p>A spread of three hundred joints is usually four or five sizes. Working out three hundred
 * technique sheets produces three hundred pieces of paper that say five different things, and the
 * crew reads the first one and works from it all week.
 *
 * <p>So joints are grouped by what actually changes the technique, and only that: the outside
 * diameter, the wall, and whether the source can get inside. Two joints with different welders and
 * different spools and the same pipe get one sheet, and the sheet lists which joints it covers, so a
 * joint that later turns out to be different is visible as one that fell out of its group rather
 * than as a missing sheet.
 */
public final class Sheetset {

    private Sheetset() {}

    public static String techniqueKey(Joint joint) {
        return techniqueKey(joint, false);
    }

    /** What makes two joints need different sheets, and nothing else. */
    public static String techniqueKey(Joint joint, boolean sourceCanEnter) {
        return String.join(
                "/",
                Num.text(Num.round(joint.odIn(), 4)),
                Num.text(Num.round(joint.wallIn(), 4)),
                sourceCanEnter ? "inside" : "outside");
    }

    public static List<SheetGroup> groupJoints(List<Joint> joints) {
        return groupJoints(joints, false);
    }

    /**
     * Group a list of joints by the technique they need, biggest group first, each group's joints
     * in register order.
     */
    public static List<SheetGroup> groupJoints(List<Joint> joints, boolean sourceCanEnter) {
        Guard.requireArray(joints, "joints");
        Map<String, List<Joint>> groups = new LinkedHashMap<>();
        for (Joint joint : joints) {
            groups.computeIfAbsent(techniqueKey(joint, sourceCanEnter), key -> new ArrayList<>())
                    .add(joint);
        }
        List<SheetGroup> built = new ArrayList<>();
        groups.forEach((key, members) -> {
            List<Joint> ordered = new ArrayList<>(members);
            ordered.sort(Comparator.comparing(Joint::id, Ids.REGISTER_ORDER));
            built.add(new SheetGroup(key, ordered, members.get(0), members.size()));
        });
        built.sort(Comparator.comparingInt(SheetGroup::count)
                .reversed()
                .thenComparing(SheetGroup::key, RootCollation.ORDER));
        return List.copyOf(built);
    }

    /**
     * Build one sheet per group. The sheet is built from the group's example joint using the shared
     * setup, and carries the list of joints it covers, so the crew can check the joint in front of
     * them is on it. The setup's own job and joint are replaced by the set's.
     */
    public static SheetSet buildSheetSet(String job, List<Joint> joints, TechniqueInput setup) {
        String trimmed = Guard.requireLabel(job, "a sheet set belongs to a job");
        List<SheetGroup> groups = groupJoints(joints, setup.sourceCanEnter());
        List<GroupSheet> sheets = groups.stream()
                .map(group -> {
                    TechniqueSheet technique = buildTechnique(
                            setup.toBuilder().job(job).joint(group.example()).build());
                    List<String> covers = group.joints().stream().map(Joint::id).toList();
                    return new GroupSheet(group.key(), covers, group.count(), technique, technique.usable());
                })
                .toList();
        return new SheetSet(trimmed, sheets, groups.size(), joints.size());
    }

    /** The one line summary of each sheet, for the front of the folder. */
    public static List<SheetIndexLine> sheetIndex(SheetSet set) {
        return set.sheets().stream()
                .map(sheet -> new SheetIndexLine(sheet.key(), sheet.count(), summariseTechnique(sheet.technique())))
                .toList();
    }

    /** Sheets that did not resolve, which is the only thing worth chasing. */
    public static List<GroupSheet> unusableSheets(SheetSet set) {
        return set.sheets().stream().filter(sheet -> !sheet.usable()).toList();
    }

    /** Which sheet covers a joint, or empty when nothing does. */
    public static Optional<GroupSheet> sheetFor(SheetSet set, String jointId) {
        return set.sheets().stream()
                .filter(sheet -> sheet.covers().stream().anyMatch(id -> id.equals(jointId)))
                .findFirst();
    }

    /**
     * How much work the grouping saved. Not vanity: a spread whose grouping saves nothing is a spread
     * where every joint really is different, and that is worth knowing before the crew mobilises with
     * one camera.
     */
    public static Grouping grouping(SheetSet set) {
        int largest = set.sheets().stream().mapToInt(GroupSheet::count).max().orElse(0);
        return new Grouping(set.joints(), set.groups(), largest, set.joints() - set.groups());
    }
}
