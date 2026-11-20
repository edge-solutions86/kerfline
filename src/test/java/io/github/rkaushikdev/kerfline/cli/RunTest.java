package io.github.rkaushikdev.kerfline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RunTest {

    private static final ExitCodes CODES = Run.exitCodes();

    private static final List<String> TECHNIQUE = List.of(
            "technique",
            "--joint",
            "J-118",
            "--size",
            "6",
            "--schedule",
            "40",
            "--activity",
            "40",
            "--distance",
            "48",
            "--standoff",
            "6.625");

    @Nested
    class TheExitCodes {

        @Test
        void keepsAFindingApartFromAMistake() {
            assertThat(CODES.ok()).isZero();
            assertThat(CODES.finding()).isEqualTo(1);
            assertThat(CODES.misuse()).isEqualTo(2);
            assertThat(Set.of(CODES.ok(), CODES.finding(), CODES.misuse())).hasSize(3);
        }
    }

    @Nested
    class HelpAndVersion {

        @Test
        void printsUsageAndFailsWhenGivenNothing() {
            CliResult result = Run.run(List.of());
            assertThat(result.out()).contains("usage: kerf <command>");
            assertThat(result.code()).isEqualTo(CODES.misuse());
        }

        @Test
        void printsUsageAndSucceedsWhenHelpIsAskedFor() {
            for (String flag : List.of("--help", "-h", "help")) {
                CliResult result = Run.run(List.of(flag));
                assertThat(result.out()).contains("usage: kerf <command>");
                assertThat(result.code()).isEqualTo(CODES.ok());
            }
        }

        @Test
        void printsTheVersion() {
            assertThat(Run.run(List.of("--version")).out()).isEqualTo(Run.version());
            assertThat(Run.run(List.of("-v")).code()).isEqualTo(CODES.ok());
        }

        @Test
        void printsHelpForOneCommandWithoutNeedingValidArguments() {
            CliResult result = Run.run(List.of("technique", "--help"));
            assertThat(result.out()).contains("usage: kerf technique");
            assertThat(result.code()).isEqualTo(CODES.ok());
        }

        @Test
        void answersHelpEvenWhenTheOtherArgumentsAreNonsense() {
            CliResult result = Run.run(List.of("technique", "--nonsense", "--help"));
            assertThat(result.code()).isEqualTo(CODES.ok());
            assertThat(result.out()).contains("usage: kerf technique");
        }
    }

    @Nested
    class RunningACommand {

        @Test
        void printsTheSheetAndSucceeds() {
            CliResult result = Run.run(TECHNIQUE);
            assertThat(result.out()).contains("RADIOGRAPHIC TECHNIQUE");
            assertThat(result.err()).isEmpty();
            assertThat(result.code()).isEqualTo(CODES.ok());
        }

        @Test
        void reportsAFindingWithTheFindingCodeAndNotTheMisuseOne() {
            CliResult result = Run.run(List.of("judge", "--size", "6", "--schedule", "40", "CRACK:0.2:2"));
            assertThat(result.code()).isEqualTo(CODES.finding());
            assertThat(result.err()).isEmpty();
            assertThat(result.out()).containsPattern("REJECT");
        }

        @Test
        void acceptsACleanJointWithTheOkCode() {
            assertThat(Run.run(List.of("judge", "--size", "6", "--schedule", "40"))
                            .code())
                    .isEqualTo(CODES.ok());
        }

        @Test
        void listsTheSources() {
            CliResult result = Run.run(List.of("sources"));
            assertThat(result.out()).contains("Ir-192");
            assertThat(result.code()).isEqualTo(CODES.ok());
        }
    }

    @Nested
    class Refusing {

        @Test
        void refusesACommandNobodyWroteAndShowsTheList() {
            CliResult result = Run.run(List.of("shoot"));
            assertThat(result.err()).contains("no command shoot");
            assertThat(result.err()).contains("usage: kerf <command>");
            assertThat(result.code()).isEqualTo(CODES.misuse());
            assertThat(result.out()).isEmpty();
        }

        @Test
        void refusesAMissingRequiredOptionAndShowsThatCommandUsage() {
            CliResult result = Run.run(List.of("technique", "--size", "6"));
            assertThat(result.err()).contains("missing required option");
            assertThat(result.err()).contains("usage: kerf technique");
            assertThat(result.code()).isEqualTo(CODES.misuse());
        }

        @Test
        void refusesAnUnknownOption() {
            List<String> argv = new ArrayList<>(TECHNIQUE);
            argv.addAll(List.of("--nonsense", "x"));
            CliResult result = Run.run(argv);
            assertThat(result.err()).contains("no such option --nonsense");
            assertThat(result.code()).isEqualTo(CODES.misuse());
        }

        @Test
        void refusesABadIndicationWithTheMisuseCode() {
            CliResult result = Run.run(List.of("judge", "--size", "6", "--schedule", "40", "LAMINATION:0.5"));
            assertThat(result.code()).isEqualTo(CODES.misuse());
            assertThat(result.err()).contains("no indication type");
        }

        @Test
        void neverWritesToBothStreamsAtOnce() {
            for (List<String> args :
                    List.of(List.<String>of(), List.of("--help"), TECHNIQUE, List.of("shoot"), List.of("technique"))) {
                CliResult result = Run.run(args);
                assertThat(result.out().isEmpty() || result.err().isEmpty()).isTrue();
            }
        }

        @Test
        void survivesBeingHandedSomethingThatIsNotAList() {
            assertThat(Run.run(null).code()).isEqualTo(CODES.misuse());
            assertThat(Run.run(null).out()).contains("usage");
        }
    }

    @Nested
    class RenderError {

        @Test
        void printsTheMessageWithNoStackTrace() {
            String text = Run.renderError(new BadInputException("nope"));
            assertThat(text).isEqualTo("kerf: nope");
            assertThat(text).doesNotContain("at ");
        }

        @Test
        void printsTheDetailsUnderneath() {
            String text = Run.renderError(new BadInputException("nope", Details.of("option", "wall", "got", "thick")));
            assertThat(text).contains("option: wall");
            assertThat(text).contains("got: thick");
        }

        @Test
        void joinsAListOfKnownValues() {
            String text = Run.renderError(new BadInputException("nope", Details.of("known", List.of("a", "b"))));
            assertThat(text).contains("known: a, b");
        }

        @Test
        void handlesAPlainErrorToo() {
            assertThat(Run.renderError(new IllegalStateException("boom"))).isEqualTo("kerf: boom");
        }
    }
}
