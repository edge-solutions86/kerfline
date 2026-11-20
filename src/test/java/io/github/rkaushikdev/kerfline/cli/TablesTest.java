package io.github.rkaushikdev.kerfline.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.geom.Unsharpness;
import io.github.rkaushikdev.kerfline.joint.Schedule;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TablesTest {

    @Nested
    class TheTableList {

        @Test
        void offersTheTablesATechnicianAsksFor() {
            assertThat(Tables.tableNames()).containsExactly("unsharpness", "iqi", "density", "wire", "schedule");
        }

        @Test
        void describesEachOne() {
            for (String name : Tables.tableNames()) {
                assertThat(Tables.tableDescribe(name).length()).isGreaterThan(10);
            }
        }

        @Test
        void refusesATableNobodyPrinted() {
            assertThatThrownBy(() -> Tables.tableDescribe("exposure")).hasMessageContaining("no table called exposure");
            assertThatThrownBy(() -> Tables.renderReferenceTable("exposure"))
                    .hasMessageContaining("no table called exposure");
        }
    }

    @Nested
    class ThePrintedTablesAgreeWithTheCodeThatAppliesThem {

        @Test
        void printsTheUnsharpnessLimitsTheCheckerUses() {
            String text = Tables.renderReferenceTable("unsharpness");
            assertThat(text).contains("Ug limit");
            assertThat(text).contains("0.020");
            assertThat(text).contains("0.070");
            assertThat(Unsharpness.unsharpnessLimit(0.5)).isEqualTo(0.02);
        }

        @Test
        void printsTheIndicatorTableTheSelectorReads() {
            String text = Tables.renderReferenceTable("iqi");
            assertThat(text).contains("src hole");
            assertThat(text).contains("film wire");
            assertThat(text).contains("The essential hole is 2T");
        }

        @Test
        void printsBothDensityFloors() {
            String text = Tables.renderReferenceTable("density");
            assertThat(text).contains("gamma");
            assertThat(text).contains("xray");
            assertThat(text).contains("2.0");
            assertThat(text).contains("1.8");
            assertThat(text).contains("composite");
        }

        @Test
        void printsEveryWireWithTheSetItComesFrom() {
            String text = Tables.renderReferenceTable("wire");
            assertThat(text.split("\n")).hasSize(23);
            assertThat(text).contains("0.0032");
            assertThat(text).contains("0.3200");
        }

        @Test
        void printsTheWallsTheScheduleTableCarries() {
            String text = Tables.renderReferenceTable("schedule");
            assertThat(text).contains("NPS");
            assertThat(text).contains("6.625");
            assertThat(text).contains(Num.toFixed(Schedule.wallThicknessIn("6", "40"), 3));
        }

        @Test
        void printsARowForEverySizeAndSchedulePairing() {
            String[] lines = Tables.renderReferenceTable("schedule").split("\n");
            assertThat(lines.length).isGreaterThan(60);
        }
    }

    @Nested
    class TheTablesCommand {

        @Test
        void isRegisteredWithTheOthers() {
            assertThat(Commands.commandNames()).contains("tables");
        }

        @Test
        void listsTheTablesWhenNoneIsNamed() {
            CommandResult result = Commands.runCommand("tables", List.of());
            assertThat(result.code()).isZero();
            for (String name : Tables.tableNames()) {
                assertThat(result.text()).contains(name);
            }
        }

        @Test
        void printsOneTableWhenItIsNamed() {
            CommandResult result = Commands.runCommand("tables", List.of("--table", "unsharpness"));
            assertThat(result.text()).contains("Ug limit");
            assertThat(result.code()).isZero();
        }

        @Test
        void refusesATableNobodyPrinted() {
            assertThatThrownBy(() -> Commands.runCommand("tables", List.of("--table", "exposure")))
                    .hasMessageContaining("no table called");
        }

        @Test
        void describesItselfForTheUsageScreen() {
            assertThat(Tables.tablesCommand().describe()).contains("reference table");
            assertThat(Tables.tablesCommand().options()).hasSize(1);
        }
    }
}
