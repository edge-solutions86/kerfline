package io.github.rkaushikdev.kerfline.errors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.kerfline.util.Checksum;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ErrorsTest {

    @Nested
    class TheBaseError {

        @Test
        void keepsTheCodeAndTheDetails() {
            KerflineException err = new KerflineException(ErrorCode.BAD_INPUT, "nope", Map.of("field", "thickness"));
            assertThat(err.code()).isEqualTo(ErrorCode.BAD_INPUT);
            assertThat(err.details()).isEqualTo(Map.of("field", "thickness"));
            assertThat(err.getMessage()).isEqualTo("nope");
        }

        @Test
        void isARealError() {
            KerflineException err = new KerflineException(ErrorCode.CONFLICT, "clash");
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getStackTrace()).isNotEmpty();
        }

        @Test
        void refusesACodeThatIsNotInTheSet() {
            assertThatThrownBy(() -> ErrorCode.of("WHATEVER"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown error code");
        }

        @Test
        void freezesDetailsSoAReportCannotEditThem() {
            KerflineException err = new KerflineException(ErrorCode.NOT_FOUND, "gone", Map.of("id", "J-114"));
            assertThatThrownBy(() -> err.details().put("id", "J-115"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThat(err.details().get("id")).isEqualTo("J-114");
        }

        @Test
        void copiesDetailsRatherThanHoldingTheCallerObject() {
            Map<String, Object> details = new HashMap<>(Map.of("id", "J-114"));
            KerflineException err = new KerflineException(ErrorCode.NOT_FOUND, "gone", details);
            details.put("id", "changed");
            assertThat(err.details().get("id")).isEqualTo("J-114");
        }

        @Test
        void serialisesToPlainData() {
            NoRuleException err = new NoRuleException("no paragraph covers this", Map.of("code", "API1104"));
            assertThat(err.toData())
                    .isEqualTo(Map.of(
                            "error", "NO_RULE",
                            "message", "no paragraph covers this",
                            "details", Map.of("code", "API1104")));
            assertThat(Checksum.stableStringify(err.toData())).contains("\"error\":\"NO_RULE\"");
        }

        @Test
        void defaultsDetailsToAnEmptyObject() {
            assertThat(new BadInputException("x").details()).isEmpty();
        }
    }

    @Nested
    class TheSubclasses {

        static Stream<Arguments> cases() {
            return Stream.of(
                    Arguments.of((Function<String, KerflineException>) BadInputException::new, ErrorCode.BAD_INPUT),
                    Arguments.of(
                            (Function<String, KerflineException>) OutOfRangeException::new, ErrorCode.OUT_OF_RANGE),
                    Arguments.of((Function<String, KerflineException>) NoRuleException::new, ErrorCode.NO_RULE),
                    Arguments.of((Function<String, KerflineException>) NotFoundException::new, ErrorCode.NOT_FOUND),
                    Arguments.of((Function<String, KerflineException>) ConflictException::new, ErrorCode.CONFLICT),
                    Arguments.of(
                            (Function<String, KerflineException>) UnsupportedException::new, ErrorCode.UNSUPPORTED));
        }

        @ParameterizedTest
        @MethodSource("cases")
        void carriesItsOwnCode(Function<String, KerflineException> make, ErrorCode code) {
            KerflineException err = make.apply("boom");
            assertThat(err.code()).isEqualTo(code);
            assertThat(err).isInstanceOf(KerflineException.class);
            assertThat(err.getClass()).isNotEqualTo(KerflineException.class);
        }
    }

    @Nested
    class IsKerflineError {

        @Test
        void acceptsOneOfOurs() {
            assertThat(Errors.isKerflineError(new OutOfRangeException("x"))).isTrue();
        }

        @Test
        void narrowsOnTheCodeWhenOneIsGiven() {
            OutOfRangeException err = new OutOfRangeException("x");
            assertThat(Errors.isKerflineError(err, ErrorCode.OUT_OF_RANGE)).isTrue();
            assertThat(Errors.isKerflineError(err, ErrorCode.BAD_INPUT)).isFalse();
        }

        @Test
        void acceptsARevivedPlainShapeThatCrossedABoundary() {
            Map<String, Object> revived = Map.of("code", "CONFLICT", "message", "clash", "details", Map.of());
            assertThat(Errors.isKerflineError(revived)).isTrue();
            assertThat(Errors.isKerflineError(revived, ErrorCode.CONFLICT)).isTrue();
        }

        @Test
        void rejectsAPlainShapeCarryingACodeWeDoNotIssue() {
            assertThat(Errors.isKerflineError(Map.of("code", "ETIMEDOUT", "message", "x")))
                    .isFalse();
        }

        @Test
        void rejectsAnythingElse() {
            assertThat(Errors.isKerflineError(new IllegalStateException("plain")))
                    .isFalse();
            assertThat(Errors.isKerflineError(null)).isFalse();
            assertThat(Errors.isKerflineError("OUT_OF_RANGE")).isFalse();
            assertThat(Errors.isKerflineError(Map.of())).isFalse();
        }
    }

    @Nested
    class ErrorCodes {

        @Test
        void listsEveryCodeSorted() {
            assertThat(Errors.errorCodes())
                    .containsExactly("BAD_INPUT", "CONFLICT", "NOT_FOUND", "NO_RULE", "OUT_OF_RANGE", "UNSUPPORTED");
        }

        @Test
        void handsBackAListACallerCannotEdit() {
            List<String> codes = Errors.errorCodes();
            assertThatThrownBy(() -> codes.add("MADE_UP")).isInstanceOf(UnsupportedOperationException.class);
            assertThat(Errors.errorCodes()).hasSize(6);
        }
    }
}
