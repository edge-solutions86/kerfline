package io.github.rkaushikdev.kerfline.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GuardTest {

    @Nested
    class RequireNumber {

        @Test
        void handsTheValueStraightBack() {
            assertThat(Guard.requireNumber(0.28, "wall")).isEqualTo(0.28);
            assertThat(Guard.requireNumber(-3, "offset")).isEqualTo(-3);
        }

        @Test
        void refusesAnythingThatIsNotAFiniteNumberNamingTheField() {
            assertThatThrownBy(() -> Guard.requireNumber((Object) "0.28", "wall"))
                    .hasMessageContaining("wall must be a finite number");
            assertThatThrownBy(() -> Guard.requireNumber(Double.NaN, "wall")).hasMessageContaining("finite number");
            assertThatThrownBy(() -> Guard.requireNumber(Double.POSITIVE_INFINITY, "wall"))
                    .hasMessageContaining("finite number");
            assertThatThrownBy(() -> Guard.requireNumber((Object) null, "wall")).hasMessageContaining("finite number");
        }

        @Test
        void carriesTheOffendingValueInTheDetails() {
            BadInputException err =
                    catchThrowableOfType(BadInputException.class, () -> Guard.requireNumber((Object) "x", "wall"));
            assertThat(err.details().get("got")).isEqualTo("x");
            assertThat(err.code()).isEqualTo(ErrorCode.BAD_INPUT);
        }
    }

    @Nested
    class RequireAbove {

        @Test
        void acceptsAValuePastTheBound() {
            assertThat(Guard.requireAbove(1, 0, "distance")).isEqualTo(1);
        }

        @Test
        void refusesTheBoundItselfAndNamesIt() {
            assertThatThrownBy(() -> Guard.requireAbove(0, 0, "distance"))
                    .hasMessageContaining("distance must be greater than 0");
            assertThatThrownBy(() -> Guard.requireAbove(2, 2, "ratio")).hasMessageContaining("greater than 2");
        }

        @Test
        void checksTheValueIsANumberFirst() {
            assertThatThrownBy(() -> Guard.requireAbove(Double.NaN, 0, "distance"))
                    .hasMessageContaining("finite number");
        }
    }

    @Nested
    class RequireAtLeast {

        @Test
        void acceptsTheBoundItself() {
            assertThat(Guard.requireAtLeast(0, 0, "standoff")).isEqualTo(0);
        }

        @Test
        void refusesAnythingUnderIt() {
            assertThatThrownBy(() -> Guard.requireAtLeast(-0.1, 0, "standoff"))
                    .hasMessageContaining("standoff must not be below 0");
        }
    }

    @Nested
    class RequirePositive {

        @Test
        void saysZeroInWordsRatherThanAsABound() {
            assertThatThrownBy(() -> Guard.requirePositive(0, "activity"))
                    .hasMessageContaining("activity must be greater than zero");
            assertThatThrownBy(() -> Guard.requirePositive(-1, "activity")).hasMessageContaining("greater than zero");
        }

        @Test
        void acceptsAnythingAboveZero() {
            assertThat(Guard.requirePositive(0.001, "activity")).isEqualTo(0.001);
        }

        @Test
        void saysSomethingDifferentFromRequireAboveAtTheSameBound() {
            String fromPositive = catchThrowableOfType(BadInputException.class, () -> Guard.requirePositive(0, "x"))
                    .getMessage();
            String fromAbove = catchThrowableOfType(BadInputException.class, () -> Guard.requireAbove(0, 0, "x"))
                    .getMessage();
            assertThat(fromPositive).isNotEqualTo(fromAbove);
        }
    }

    @Nested
    class RequireWhole {

        @Test
        void acceptsAnIntegerIncludingANegativeOne() {
            assertThat(Guard.requireWhole(3, "count")).isEqualTo(3);
            assertThat(Guard.requireWhole(-3, "count")).isEqualTo(-3);
        }

        @Test
        void refusesAFractionOrANonNumber() {
            assertThatThrownBy(() -> Guard.requireWhole(1.5, "count"))
                    .hasMessageContaining("count must be a whole number");
            assertThatThrownBy(() -> Guard.requireWhole(Double.NaN, "count")).hasMessageContaining("whole number");
        }
    }

    @Nested
    class RequireOneOf {

        @Test
        void acceptsAMember() {
            assertThat(Guard.requireOneOf("film", List.of("source", "film"), "side"))
                    .isEqualTo("film");
        }

        @Test
        void listsTheOptionsWhenItRefuses() {
            assertThatThrownBy(() -> Guard.requireOneOf("back", List.of("source", "film"), "side"))
                    .hasMessageContaining("side must be one of source or film");
        }

        @Test
        void carriesTheOptionsInTheDetails() {
            BadInputException err = catchThrowableOfType(
                    BadInputException.class, () -> Guard.requireOneOf("back", List.of("source", "film"), "side"));
            assertThat(err.details().get("known")).isEqualTo(List.of("source", "film"));
        }
    }

    @Nested
    class RequireArray {

        @Test
        void acceptsAnArrayIncludingAnEmptyOne() {
            assertThat(Guard.requireArray(List.of(), "readings")).isEmpty();
        }

        @Test
        void refusesAnythingElse() {
            assertThatThrownBy(() -> Guard.requireArray(null, "readings"))
                    .hasMessageContaining("readings must be an array");
            assertThatThrownBy(() -> Guard.requireArray(null, "films")).hasMessageContaining("must be an array");
        }
    }
}
