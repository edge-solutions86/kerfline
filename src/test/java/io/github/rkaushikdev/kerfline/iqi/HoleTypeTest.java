package io.github.rkaushikdev.kerfline.iqi;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HoleTypeTest {

    @Nested
    class TheStockedPlaques {

        @Test
        void climbsFromFiveToTwoHundredAndEighty() {
            List<Integer> ids = HoleType.holeIqiIdentities();
            assertThat(ids.get(0)).isEqualTo(5);
            assertThat(ids.get(ids.size() - 1)).isEqualTo(280);
            assertThat(ids).isSorted();
        }

        @Test
        void namesTheThreeHoles() {
            assertThat(HoleType.holeNames()).containsExactly("1T", "2T", "4T");
        }

        @Test
        void answersHasHoleIqiWithoutThrowing() {
            assertThat(HoleType.hasHoleIqi(25)).isTrue();
            assertThat(HoleType.hasHoleIqi(26)).isFalse();
        }

        @Test
        void readsTheIdentityAsThousandthsOfAnInch() {
            assertThat(HoleType.holeIqiThicknessIn(25)).isCloseTo(0.025, digits(9));
            assertThat(HoleType.holeIqiThicknessIn(200)).isCloseTo(0.2, digits(9));
        }

        @Test
        void refusesAPlaqueNobodyStocks() {
            assertThatThrownBy(() -> HoleType.holeIqiThicknessIn(26))
                    .hasMessageContaining("no hole type indicator numbered 26");
        }
    }

    @Nested
    class HoleMultiple {

        @Test
        void readsTheThreeMultiplesOffTheNames() {
            assertThat(HoleType.holeMultiple("1T")).isEqualTo(1);
            assertThat(HoleType.holeMultiple("2T")).isEqualTo(2);
            assertThat(HoleType.holeMultiple("4T")).isEqualTo(4);
        }

        @Test
        void refusesAHoleThatIsNotDrilled() {
            assertThatThrownBy(() -> HoleType.holeMultiple("3T")).hasMessageContaining("no such hole 3T");
        }
    }

    @Nested
    class HoleDiameterIn {

        @Test
        void isTheMultipleOfThePlaqueThicknessOnAThickPlaque() {
            assertThat(HoleType.holeDiameterIn(25, "1T")).isCloseTo(0.025, digits(6));
            assertThat(HoleType.holeDiameterIn(25, "2T")).isCloseTo(0.05, digits(6));
            assertThat(HoleType.holeDiameterIn(25, "4T")).isCloseTo(0.1, digits(6));
        }

        @Test
        void neverDrillsBelowTenThousandths() {
            assertThat(HoleType.holeDiameterIn(5, "1T")).isCloseTo(0.01, digits(6));
            assertThat(HoleType.holeDiameterIn(7, "1T")).isCloseTo(0.01, digits(6));
            assertThat(HoleType.minimumHoleDiameterIn()).isEqualTo(0.01);
        }

        @Test
        void opensThe2THoleTooOnTheVeryThinnestPlaque() {
            assertThat(HoleType.holeDiameterIn(5, "2T")).isCloseTo(0.01, digits(6));
            assertThat(HoleType.holeDiameterIn(7, "2T")).isCloseTo(0.014, digits(6));
        }

        @Test
        void reachesTheNominalMultipleAsSoonAsTheArithmeticClearsTheFloor() {
            assertThat(HoleType.holeDiameterIn(10, "1T")).isCloseTo(0.01, digits(6));
            assertThat(HoleType.holeDiameterIn(12, "1T")).isCloseTo(0.012, digits(6));
        }

        @Test
        void saysWhichHolesWereOpenedUp() {
            assertThat(HoleType.holeIsAtFloor(5, "1T")).isTrue();
            assertThat(HoleType.holeIsAtFloor(5, "4T")).isFalse();
            assertThat(HoleType.holeIsAtFloor(25, "1T")).isFalse();
        }

        @Test
        void doesNotCountAHoleThatLandsExactlyOnTheFloorAsOpenedUp() {
            // The 5 plaque is 0.005 in, so its 2T hole is 0.010 in on the nose. It is
            // drilled at its nominal size and the sensitivity arithmetic is honest.
            assertThat(HoleType.holeDiameterIn(5, "2T")).isCloseTo(0.01, digits(6));
            assertThat(HoleType.holeIsAtFloor(5, "2T")).isFalse();
        }
    }

    @Nested
    class HoleIqiRecord {

        @Test
        void givesTheWholePlaqueForTheSheet() {
            assertThat(HoleType.holeIqi(25))
                    .isEqualTo(new HoleIqi(25, 0.025, Map.of("1T", 0.025, "2T", 0.05, "4T", 0.1), List.of()));
        }

        @Test
        void listsTheOpenedHolesOnAThinPlaque() {
            assertThat(HoleType.holeIqi(5).atFloor()).containsExactly("1T");
        }

        @Test
        void agreesWithHoleDiameterInOnEveryStockedPlaque() {
            for (int identity : HoleType.holeIqiIdentities()) {
                HoleIqi plaque = HoleType.holeIqi(identity);
                for (String hole : HoleType.holeNames()) {
                    assertThat(plaque.holes().get(hole)).isEqualTo(HoleType.holeDiameterIn(identity, hole));
                }
            }
        }
    }

    @Nested
    class NextHoleIqi {

        @Test
        void stepsUpAndDownTheStockedSet() {
            assertThat(HoleType.nextHoleIqi(25)).isEqualTo(30);
            assertThat(HoleType.nextHoleIqi(25, -1)).isEqualTo(20);
        }

        @Test
        void refusesToRunOffEitherEnd() {
            assertThatThrownBy(() -> HoleType.nextHoleIqi(5, -1)).hasMessageContaining("no plaque past 5");
            assertThatThrownBy(() -> HoleType.nextHoleIqi(280, 1)).hasMessageContaining("no plaque past 280");
        }

        @Test
        void refusesADirectionThatIsNotOneStep() {
            assertThatThrownBy(() -> HoleType.nextHoleIqi(25, 2)).hasMessageContaining("must be 1 or -1");
        }
    }
}
