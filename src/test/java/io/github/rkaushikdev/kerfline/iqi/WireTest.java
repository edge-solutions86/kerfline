package io.github.rkaushikdev.kerfline.iqi;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WireTest {

    @Nested
    class TheWireLadder {

        @Test
        void runsFromOneToTwentyOne() {
            assertThat(Wire.wireNumbers()).hasSize(21);
            assertThat(Wire.wireNumbers().get(0)).isEqualTo(1);
            assertThat(Wire.wireNumbers().get(20)).isEqualTo(21);
        }

        @Test
        void getsThickerWithEveryStep() {
            List<Double> diameters =
                    Wire.wireNumbers().stream().map(Wire::wireDiameterIn).toList();
            assertThat(diameters).isSorted();
        }

        @Test
        void quotesThePublishedDiameters() {
            assertThat(Wire.wireDiameterIn(1)).isCloseTo(0.0032, digits(6));
            assertThat(Wire.wireDiameterIn(6)).isCloseTo(0.01, digits(6));
            assertThat(Wire.wireDiameterIn(11)).isCloseTo(0.032, digits(6));
            assertThat(Wire.wireDiameterIn(21)).isCloseTo(0.32, digits(6));
        }

        @Test
        void refusesAWireThatDoesNotExist() {
            assertThatThrownBy(() -> Wire.wireDiameterIn(22)).hasMessageContaining("no wire numbered 22");
            assertThatThrownBy(() -> Wire.wireDiameterIn(0)).hasMessageContaining("no wire numbered 0");
        }

        @Test
        void answersHasWireWithoutThrowing() {
            assertThat(Wire.hasWire(6)).isTrue();
            assertThat(Wire.hasWire(22)).isFalse();
            assertThat(Wire.hasWire(0)).isFalse();
        }
    }

    @Nested
    class TheFourSets {

        @Test
        void namesThemAToD() {
            assertThat(Wire.wireSetLetters()).containsExactly("A", "B", "C", "D");
        }

        @Test
        void holdsSixWiresEach() {
            for (String letter : Wire.wireSetLetters()) {
                assertThat(Wire.wireSet(letter)).hasSize(6);
            }
        }

        @Test
        void overlapsByExactlyOneWireAtEachJoin() {
            assertThat(Wire.wireSet("A").get(5)).isEqualTo(Wire.wireSet("B").get(0));
            assertThat(Wire.wireSet("B").get(5)).isEqualTo(Wire.wireSet("C").get(0));
            assertThat(Wire.wireSet("C").get(5)).isEqualTo(Wire.wireSet("D").get(0));
        }

        @Test
        void coversTheWholeLadderBetweenThem() {
            TreeSet<Integer> covered = new TreeSet<>();
            Wire.wireSetLetters().forEach(letter -> covered.addAll(Wire.wireSet(letter)));
            assertThat(List.copyOf(covered)).isEqualTo(Wire.wireNumbers());
        }

        @Test
        void takesALowerCaseLetter() {
            assertThat(Wire.wireSet("b")).isEqualTo(Wire.wireSet("B"));
        }

        @Test
        void refusesASetThatDoesNotExist() {
            assertThatThrownBy(() -> Wire.wireSet("E")).hasMessageContaining("no wire set E");
        }

        @Test
        void handsBackASetACallerCannotEdit() {
            List<Integer> set = Wire.wireSet("A");
            assertThatThrownBy(() -> set.add(99)).isInstanceOf(UnsupportedOperationException.class);
            assertThat(Wire.wireSet("A")).hasSize(6);
        }
    }

    @Nested
    class SetsForWire {

        @Test
        void namesOneSetForAWireInTheMiddleOfASet() {
            assertThat(Wire.setsForWire(3)).containsExactly("A");
            assertThat(Wire.setsForWire(9)).containsExactly("B");
        }

        @Test
        void namesBothSetsForAJoinWire() {
            assertThat(Wire.setsForWire(6)).containsExactly("A", "B");
            assertThat(Wire.setsForWire(11)).containsExactly("B", "C");
            assertThat(Wire.setsForWire(16)).containsExactly("C", "D");
        }

        @Test
        void drawsTheFinerSetAtAJoin() {
            assertThat(Wire.preferredSetForWire(6)).isEqualTo("A");
            assertThat(Wire.preferredSetForWire(16)).isEqualTo("C");
            assertThat(Wire.preferredSetForWire(9)).isEqualTo("B");
        }
    }

    @Nested
    class WireForDiameterIn {

        @Test
        void findsTheWireAtAnExactDiameter() {
            assertThat(Wire.wireForDiameterIn(0.01)).isEqualTo(6);
            assertThat(Wire.wireForDiameterIn(0.032)).isEqualTo(11);
        }

        @Test
        void takesTheNextWireUpWhenTheCallFallsBetweenTwo() {
            assertThat(Wire.wireForDiameterIn(0.011)).isEqualTo(7);
            assertThat(Wire.wireForDiameterIn(0.0001)).isEqualTo(1);
        }

        @Test
        void refusesACallPastTheCoarsestWire() {
            assertThatThrownBy(() -> Wire.wireForDiameterIn(0.5)).hasMessageContaining("no wire reaches");
        }

        @Test
        void refusesADiameterThatIsNotPositive() {
            assertThatThrownBy(() -> Wire.wireForDiameterIn(0)).hasMessageContaining("positive number");
        }
    }

    @Nested
    class WireSetRecordFor {

        @Test
        void describesASetForTheTechniqueSheet() {
            WireSetRecord record = Wire.wireSetRecord("B");
            assertThat(record.letter()).isEqualTo("B");
            assertThat(record.wires()).containsExactly(6, 7, 8, 9, 10, 11);
            assertThat(record.finestIn()).isCloseTo(0.01, digits(6));
            assertThat(record.coarsestIn()).isCloseTo(0.032, digits(6));
            assertThat(record.diametersIn()).hasSize(6);
        }

        @Test
        void keepsTheDiametersInStepWithTheWires() {
            for (String letter : List.of("A", "B", "C", "D")) {
                WireSetRecord record = Wire.wireSetRecord(letter);
                for (int index = 0; index < record.wires().size(); index++) {
                    assertThat(record.diametersIn().get(index))
                            .isEqualTo(Wire.wireDiameterIn(record.wires().get(index)));
                }
            }
        }
    }
}
