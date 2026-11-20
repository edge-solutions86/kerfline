package io.github.rkaushikdev.kerfline.cli;

import static io.github.rkaushikdev.kerfline.cli.Args.defineOption;
import static io.github.rkaushikdev.kerfline.cli.Args.parseArgs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ArgsTest {

    private static final List<OptionDef> OPTIONS = List.of(
            defineOption(OptionSpec.named("job").required(true).describe("the job number")),
            defineOption(OptionSpec.named("wall").kind(OptionKind.NUMBER).fallback(0.28)),
            defineOption(OptionSpec.named("day").kind(OptionKind.DAY)),
            defineOption(OptionSpec.named("verbose").kind(OptionKind.BOOLEAN)));

    @Nested
    class DefineOption {

        @Test
        void namesTheKinds() {
            assertThat(Args.optionKinds()).containsExactly("string", "number", "boolean", "day");
        }

        @Test
        void refusesANameThatIsNotAFlagName() {
            assertThatThrownBy(() -> defineOption(OptionSpec.named("Job"))).hasMessageContaining("lower case letters");
            assertThatThrownBy(() -> defineOption(OptionSpec.named("1job"))).hasMessageContaining("lower case letters");
        }

        @Test
        void refusesAKindItDoesNotHave() {
            assertThatThrownBy(() -> defineOption(OptionSpec.named("job").kind(OptionKind.of("date"))))
                    .hasMessageContaining("an option is one of");
        }

        @Test
        void refusesARequiredOptionWithAFallback() {
            assertThatThrownBy(() ->
                            defineOption(OptionSpec.named("job").required(true).fallback("x")))
                    .hasMessageContaining("cannot also have a fallback");
        }
    }

    @Nested
    class ParseArgs {

        @Test
        void readsAValueAfterTheFlag() {
            assertThat(parseArgs(List.of("--job", "C-4471"), OPTIONS).text("job"))
                    .contains("C-4471");
        }

        @Test
        void readsAValueJoinedByAnEqualsSign() {
            assertThat(parseArgs(List.of("--job=C-4471"), OPTIONS).text("job")).contains("C-4471");
        }

        @Test
        void coercesANumber() {
            ParsedArgs parsed = parseArgs(List.of("--job", "C-1", "--wall", "0.432"), OPTIONS);
            assertThat(parsed.options().get("wall")).isEqualTo(0.432);
        }

        @Test
        void refusesANumberThatIsNotOneNamingTheOption() {
            assertThatThrownBy(() -> parseArgs(List.of("--job", "C-1", "--wall", "thick"), OPTIONS))
                    .hasMessageContaining("--wall wants a number");
        }

        @Test
        void refusesADateInTheWrongShape() {
            assertThatThrownBy(() -> parseArgs(List.of("--job", "C-1", "--day", "14/03/2025"), OPTIONS))
                    .hasMessageContaining("--day wants a date");
        }

        @Test
        void takesAFlagWithNoValue() {
            assertThat(parseArgs(List.of("--job", "C-1", "--verbose"), OPTIONS)
                            .options()
                            .get("verbose"))
                    .isEqualTo(true);
        }

        @Test
        void leavesAnUnsetFlagFalse() {
            assertThat(parseArgs(List.of("--job", "C-1"), OPTIONS).options().get("verbose"))
                    .isEqualTo(false);
        }

        @Test
        void refusesAValueOnAFlag() {
            assertThatThrownBy(() -> parseArgs(List.of("--job", "C-1", "--verbose=yes"), OPTIONS))
                    .hasMessageContaining("is a flag and takes no value");
        }

        @Test
        void fillsInAFallback() {
            assertThat(parseArgs(List.of("--job", "C-1"), OPTIONS).options().get("wall"))
                    .isEqualTo(0.28);
        }

        @Test
        void leavesAnOptionWithNoFallbackUndefined() {
            ParsedArgs parsed = parseArgs(List.of("--job", "C-1"), OPTIONS);
            assertThat(parsed.options()).doesNotContainKey("day");
            assertThat(parsed.text("day")).isEmpty();
        }

        @Test
        void refusesAnOptionNobodyDefinedAndSaysWhatThereIs() {
            BadInputException err =
                    catchThrowableOfType(BadInputException.class, () -> parseArgs(List.of("--jobb", "C-1"), OPTIONS));
            assertThat(err).as("should have refused").isNotNull();
            assertThat(err.getMessage()).contains("no such option --jobb");
            assertThat(err.details().get("known"))
                    .asInstanceOf(InstanceOfAssertFactories.LIST)
                    .contains("job");
        }

        @Test
        void refusesAMissingRequiredOption() {
            assertThatThrownBy(() -> parseArgs(List.of("--wall", "0.3"), OPTIONS))
                    .hasMessageContaining("missing required option: --job");
        }

        @Test
        void namesEveryMissingOptionAtOnce() {
            List<OptionDef> both = List.of(
                    defineOption(OptionSpec.named("job").required(true)),
                    defineOption(OptionSpec.named("joint").required(true)));
            assertThatThrownBy(() -> parseArgs(List.of(), both))
                    .hasMessageContaining("missing required options: --job, --joint");
        }

        @Test
        void refusesAFlagLeftDanglingAtTheEnd() {
            assertThatThrownBy(() -> parseArgs(List.of("--job"), OPTIONS)).hasMessageContaining("--job wants a value");
        }

        @Test
        void refusesAFlagFollowedByAnotherFlag() {
            assertThatThrownBy(() -> parseArgs(List.of("--job", "--verbose"), OPTIONS))
                    .hasMessageContaining("--job wants a value");
        }

        @Test
        void collectsPositionalsInOrder() {
            ParsedArgs parsed = parseArgs(List.of("J-1", "--job", "C-1", "J-2"), OPTIONS);
            assertThat(parsed.positionals()).containsExactly("J-1", "J-2");
        }

        @Test
        void stopsParsingOptionsAfterABareDoubleDash() {
            ParsedArgs parsed = parseArgs(List.of("--job", "C-1", "--", "--not-an-option"), OPTIONS);
            assertThat(parsed.positionals()).containsExactly("--not-an-option");
        }

        @Test
        void refusesAnArgumentThatIsNotAString() {
            assertThatThrownBy(() -> parseArgs(Arrays.asList((String) null), OPTIONS))
                    .hasMessageContaining("must be a string");
        }
    }

    @Nested
    class DescribeOptionAndUsage {

        @Test
        void showsTheKindOfValueAnOptionWants() {
            assertThat(Args.describeOption(OPTIONS.get(1))).contains("--wall <number>");
        }

        @Test
        void marksARequiredOption() {
            assertThat(Args.describeOption(OPTIONS.get(0))).contains("(required)");
        }

        @Test
        void showsADefault() {
            assertThat(Args.describeOption(OPTIONS.get(1))).contains("(default 0.28)");
        }

        @Test
        void showsNoValueForAFlag() {
            assertThat(Args.describeOption(OPTIONS.get(3))).isEqualTo("--verbose");
        }

        @Test
        void appendsTheDescriptionWhenThereIsOne() {
            assertThat(Args.describeOption(OPTIONS.get(0))).contains("the job number");
        }

        @Test
        void buildsAUsageScreen() {
            String text = Args.usage("technique", OPTIONS);
            assertThat(text.split("\n")[0]).isEqualTo("usage: kerf technique [options]");
            assertThat(text).contains("--job");
            assertThat(text).contains("--verbose");
        }
    }
}
