package io.github.rkaushikdev.kerfline.exposure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.geom.ViewingTechnique;
import io.github.rkaushikdev.kerfline.joint.Joint;
import io.github.rkaushikdev.kerfline.joint.JointInput;
import io.github.rkaushikdev.kerfline.joint.Spec;
import io.github.rkaushikdev.kerfline.report.TechniqueInput;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SheetsetTest {

    private static Joint six(String id) {
        return six(id, "A17");
    }

    private static Joint six(String id, String welder) {
        return Spec.makeJoint(JointInput.builder()
                .id(id)
                .welder(welder)
                .spool("SP-1")
                .nominalSize(6)
                .schedule(40)
                .build());
    }

    private static Joint two(String id) {
        return Spec.makeJoint(JointInput.builder()
                .id(id)
                .welder("B04")
                .spool("SP-2")
                .nominalSize(2)
                .schedule(40)
                .build());
    }

    private static Joint sized(String id, int size) {
        return Spec.makeJoint(
                JointInput.builder().id(id).nominalSize(size).schedule(40).build());
    }

    private static final List<Joint> JOINTS =
            List.of(six("J-1"), six("J-2", "B04"), six("J-10"), two("J-20"), two("J-21"));

    private static final TechniqueInput SETUP = TechniqueInput.builder()
            .isotopeId("IR192")
            .assayCi(40)
            .sourceToObjectIn(48)
            .objectToFilmIn(6.625)
            .build();

    @Nested
    class TechniqueKey {

        @Test
        void isTheSameForTwoJointsOfTheSamePipe() {
            assertThat(Sheetset.techniqueKey(six("J-1"))).isEqualTo(Sheetset.techniqueKey(six("J-9", "C09")));
        }

        @Test
        void differsWhenThePipeDiffers() {
            assertThat(Sheetset.techniqueKey(six("J-1"))).isNotEqualTo(Sheetset.techniqueKey(two("J-20")));
        }

        @Test
        void differsWhenTheSourceCanGetInside() {
            assertThat(Sheetset.techniqueKey(six("J-1"))).isNotEqualTo(Sheetset.techniqueKey(six("J-1"), true));
        }

        @Test
        void doesNotCareAboutTheWelderOrTheSpool() {
            Joint a = Spec.makeJoint(JointInput.builder()
                    .id("J-1")
                    .welder("A17")
                    .spool("SP-1")
                    .nominalSize(6)
                    .schedule(40)
                    .build());
            Joint b = Spec.makeJoint(JointInput.builder()
                    .id("J-2")
                    .welder("Z99")
                    .spool("SP-9")
                    .nominalSize(6)
                    .schedule(40)
                    .build());
            assertThat(Sheetset.techniqueKey(a)).isEqualTo(Sheetset.techniqueKey(b));
        }
    }

    @Nested
    class GroupJoints {

        private final List<SheetGroup> groups = Sheetset.groupJoints(JOINTS);

        @Test
        void makesOneGroupPerPipe() {
            assertThat(groups).hasSize(2);
        }

        @Test
        void putsTheBiggestGroupFirst() {
            assertThat(groups.get(0).count()).isEqualTo(3);
            assertThat(groups.get(1).count()).isEqualTo(2);
        }

        @Test
        void listsTheJointsInEachGroupInRegisterOrder() {
            assertThat(groups.get(0).joints().stream().map(Joint::id)).containsExactly("J-1", "J-2", "J-10");
        }

        @Test
        void accountsForEveryJointExactlyOnce() {
            int counted = groups.stream().mapToInt(SheetGroup::count).sum();
            assertThat(counted).isEqualTo(JOINTS.size());
        }

        @Test
        void groupsNothingIntoNothing() {
            assertThat(Sheetset.groupJoints(List.of())).isEmpty();
        }

        @Test
        void refusesAnythingThatIsNotAList() {
            assertThatThrownBy(() -> Sheetset.groupJoints(null)).hasMessageContaining("must be an array");
        }
    }

    @Nested
    class BuildSheetSet {

        private final SheetSet set = Sheetset.buildSheetSet("C-4471", JOINTS, SETUP);

        @Test
        void buildsOneSheetPerGroupRatherThanOnePerJoint() {
            assertThat(set.sheets()).hasSize(2);
            assertThat(set.joints()).isEqualTo(5);
            assertThat(set.groups()).isEqualTo(2);
        }

        @Test
        void listsWhichJointsEachSheetCovers() {
            assertThat(set.sheets().get(0).covers()).containsExactly("J-1", "J-2", "J-10");
        }

        @Test
        void buildsARealTechniqueForEachGroup() {
            assertThat(set.sheets().get(0).technique().viewing().technique()).isEqualTo(ViewingTechnique.DWSI);
            assertThat(set.sheets().get(0).usable()).isTrue();
        }

        @Test
        void picksTheRightViewingTechniqueForTheSmallBoreGroup() {
            GroupSheet small = set.sheets().stream()
                    .filter(sheet -> sheet.covers().contains("J-20"))
                    .findFirst()
                    .orElseThrow();
            assertThat(small.technique().viewing().technique()).isEqualTo(ViewingTechnique.DWDI_ELLIPTICAL);
        }

        @Test
        void refusesASetWithNoJob() {
            assertThatThrownBy(() -> Sheetset.buildSheetSet(" ", JOINTS, SETUP))
                    .hasMessageContaining("belongs to a job");
        }

        @Test
        void buildsAnEmptySetForAnEmptyRegister() {
            SheetSet empty = Sheetset.buildSheetSet("C-1", List.of(), SETUP);
            assertThat(empty.sheets()).isEmpty();
            assertThat(empty.groups()).isZero();
        }
    }

    @Nested
    class SheetIndexSheetForAndUnusableSheets {

        private final SheetSet set = Sheetset.buildSheetSet("C-4471", JOINTS, SETUP);

        @Test
        void indexesTheFolderWithALinePerSheet() {
            List<SheetIndexLine> index = Sheetset.sheetIndex(set);
            assertThat(index).hasSize(2);
            assertThat(index.get(0).joints()).isEqualTo(3);
            assertThat(index.get(0).technique()).isEqualTo(ViewingTechnique.DWSI);
        }

        @Test
        void findsTheSheetAJointIsOn() {
            assertThat(Sheetset.sheetFor(set, "J-10").orElseThrow().covers()).contains("J-10");
            assertThat(Sheetset.sheetFor(set, "J-20").orElseThrow().covers()).contains("J-20");
        }

        @Test
        void findsNothingForAJointOnNoSheet() {
            assertThat(Sheetset.sheetFor(set, "J-999")).isEmpty();
        }

        @Test
        void findsNothingUnusableWhenEverythingResolved() {
            assertThat(Sheetset.unusableSheets(set)).isEmpty();
        }

        @Test
        void findsTheSheetThatDidNotResolve() {
            Joint thick = Spec.makeJoint(
                    JointInput.builder().id("J-90").odIn(24).wallIn(3).build());
            List<Joint> joints = new ArrayList<>(JOINTS);
            joints.add(thick);
            SheetSet other = Sheetset.buildSheetSet("C-1", joints, SETUP);
            assertThat(Sheetset.unusableSheets(other)).hasSize(1);
            assertThat(Sheetset.unusableSheets(other).get(0).covers()).containsExactly("J-90");
        }
    }

    @Nested
    class GroupingSaved {

        @Test
        void saysHowMuchPaperTheGroupingSaved() {
            SheetSet set = Sheetset.buildSheetSet("C-4471", JOINTS, SETUP);
            Grouping saved = Sheetset.grouping(set);
            assertThat(saved.joints()).isEqualTo(5);
            assertThat(saved.sheets()).isEqualTo(2);
            assertThat(saved.saved()).isEqualTo(3);
            assertThat(saved.largestGroup()).isEqualTo(3);
        }

        @Test
        void savesNothingWhenEveryJointReallyIsDifferent() {
            List<Joint> varied = List.of(sized("J-1", 2), sized("J-2", 6), sized("J-3", 12));
            SheetSet set = Sheetset.buildSheetSet("C-1", varied, SETUP);
            assertThat(Sheetset.grouping(set).saved()).isZero();
            assertThat(Sheetset.grouping(set).largestGroup()).isEqualTo(1);
        }

        @Test
        void reportsNothingLargestOnAnEmptySet() {
            SheetSet set = Sheetset.buildSheetSet("C-1", List.of(), SETUP);
            assertThat(Sheetset.grouping(set).largestGroup()).isZero();
        }
    }
}
