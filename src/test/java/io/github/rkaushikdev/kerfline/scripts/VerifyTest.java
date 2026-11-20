package io.github.rkaushikdev.kerfline.scripts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.scripts.Verify.Stage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VerifyTest {

    private static String source() throws IOException {
        return Files.readString(Path.of("scripts", "Verify.java"), StandardCharsets.UTF_8);
    }

    @Nested
    class TheStageList {

        @Test
        void runsTheCheapChecksBeforeTheExpensiveOnes() {
            assertThat(Verify.stageNames()).containsExactly("format", "lint", "test", "coverage", "release", "smoke");
        }

        @Test
        void givesEveryStageACommandAndADescription() {
            for (Stage entry : Verify.stages()) {
                assertThat(entry.command()).isNotEmpty();
                assertThat(entry.args()).isNotNull();
                assertThat(entry.describe().length()).isGreaterThan(10);
            }
        }

        @Test
        void handsBackAPlanACallerCannotEdit() {
            List<Stage> first = Verify.stages();
            assertThatThrownBy(() -> first.get(0).args().add("--nonsense"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(Verify.stages().get(0).args()).doesNotContain("--nonsense");
        }

        @Test
        void findsOneStageByName() {
            assertThat(Verify.stage("lint").args()).contains("compileTestJava");
        }

        @Test
        void refusesAStageNobodyWroteAndListsWhatThereIs() {
            assertThatThrownBy(() -> Verify.stage("typecheck")).hasMessageContaining("no verification stage typecheck");
            assertThatThrownBy(() -> Verify.stage("typecheck")).hasMessageContaining("format, lint, test");
        }

        @Test
        void endsWithTheOneStageThatRunsTheRealTool() {
            List<Stage> all = Verify.stages();
            Stage last = all.get(all.size() - 1);
            assertThat(last.name()).isEqualTo("smoke");
            assertThat(last.command()).isEqualTo("build/install/kerf/bin/kerf");
        }
    }

    @Nested
    class PlanStages {

        @Test
        void plansEverythingWhenNothingIsAskedFor() {
            assertThat(Verify.planStages(List.of()).stream().map(Stage::name)).isEqualTo(Verify.stageNames());
            assertThat(Verify.planStages(null).stream().map(Stage::name)).isEqualTo(Verify.stageNames());
        }

        @Test
        void plansOnlyWhatWasAskedForInTheOrderAsked() {
            assertThat(Verify.planStages(List.of("test", "lint")).stream().map(Stage::name))
                    .containsExactly("test", "lint");
        }

        @Test
        void refusesAPlanNamingAStageThatDoesNotExist() {
            assertThatThrownBy(() -> Verify.planStages(List.of("lint", "typecheck")))
                    .hasMessageContaining("no verification stage");
        }
    }

    @Nested
    class TheFileItself {

        @Test
        void keepsItsActionsBehindItsEntryPointSoLoadingItDoesNothing() throws IOException {
            String text = source();
            assertThat(text).contains("public static void main(String[] args)");
            assertThat(text).doesNotContain("static {");
            assertThat(text.indexOf("new ProcessBuilder"))
                    .isGreaterThan(text.indexOf("public static Outcome runStage"));
        }

        @Test
        void hasNoShebangWhichAWindowsCheckoutWouldBreak() throws IOException {
            assertThat(source()).doesNotStartWith("#!");
        }

        @Test
        void carriesNoCarriageReturns() throws IOException {
            assertThat(source()).doesNotContain("\r");
        }
    }
}
