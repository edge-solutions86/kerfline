package io.github.rkaushikdev.kerfline.film;

import static io.github.rkaushikdev.kerfline.testing.Close.digits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CassetteTest {

    private static final StockInput BOX = StockInput.builder()
            .batch("B-2291")
            .size("4.5x17")
            .boxedOn("2024-11-01")
            .sheets(100)
            .build();

    private final FilmStock stock = Cassette.makeStock(BOX);
    private final FilmCassette cassette = Cassette.makeCassette("C-4", "4.5x17");

    @Nested
    class TheStockedSizes {

        @Test
        void listsWhatTheStoreCarries() {
            assertThat(Cassette.filmSizes()).contains("4.5x17");
            assertThat(Cassette.filmSizes()).contains("14x17");
        }

        @Test
        void worksTheAreaOut() {
            assertThat(Cassette.sizeOf("4.5x17").areaSquareInches()).isCloseTo(76.5, digits(2));
        }

        @Test
        void refusesASizeNobodyStocks() {
            assertThatThrownBy(() -> Cassette.sizeOf("5x20")).hasMessageContaining("no film size 5x20");
        }

        @Test
        void keepsFilmForEighteenMonths() {
            assertThat(Cassette.shelfLifeMonths()).isEqualTo(18);
        }
    }

    @Nested
    class SmallestCovering {

        @Test
        void picksTheSmallestFilmThatReaches() {
            assertThat(Cassette.smallestCovering(8).name()).isEqualTo("4.5x8.5");
            // 3.5x17 is 59.5 square inches against 76.5 for 4.5x17, so it wins on
            // area even though it is the longer roll of the two narrow sizes.
            assertThat(Cassette.smallestCovering(12).name()).isEqualTo("3.5x17");
        }

        @Test
        void goesToTheWiderFilmOnceTheWeldNeedsTheWidth() {
            assertThat(Cassette.smallestCovering(12, 4).name()).isEqualTo("4.5x17");
        }

        @Test
        void takesAWidthRequirementIntoAccount() {
            assertThat(Cassette.smallestCovering(12, 6).name()).isEqualTo("7x17");
        }

        @Test
        void refusesWhenNothingStockedReaches() {
            assertThatThrownBy(() -> Cassette.smallestCovering(30)).hasMessageContaining("no stocked film covers");
        }

        @Test
        void picksSomethingForAVeryShortWeld() {
            assertThat(Cassette.smallestCovering(1).lengthIn()).isGreaterThan(0);
        }
    }

    @Nested
    class MakeStock {

        @Test
        void worksTheExpiryOutFromTheBoxingDate() {
            assertThat(stock.expiresOn()).isEqualTo("2026-05-01");
        }

        @Test
        void refusesStockWithNoBatchNumber() {
            assertThatThrownBy(() -> Cassette.makeStock(
                            BOX.toBuilder().batch(" ").sheets(10).build()))
                    .hasMessageContaining("carries its batch number");
        }

        @Test
        void refusesAFractionalSheetCount() {
            assertThatThrownBy(() -> Cassette.makeStock(
                            BOX.toBuilder().batch("B-1").sheets(1.5).build()))
                    .hasMessageContaining("whole number");
        }

        @Test
        void isCurrentWellBeforeTheExpiryAndNotAfterIt() {
            assertThat(Cassette.stockIsCurrent(stock, "2025-03-14")).isTrue();
            assertThat(Cassette.stockIsCurrent(stock, "2026-05-01")).isTrue();
            assertThat(Cassette.stockIsCurrent(stock, "2026-05-02")).isFalse();
        }

        @Test
        void countsTheDaysOfLifeLeft() {
            assertThat(Cassette.daysOfLifeLeft(stock, "2026-04-30")).isEqualTo(1);
            assertThat(Cassette.daysOfLifeLeft(stock, "2026-06-01")).isNegative();
        }
    }

    @Nested
    class StockToUseUp {

        private final FilmStock older = Cassette.makeStock(StockInput.builder()
                .batch("B-1")
                .size("4.5x17")
                .boxedOn("2023-11-01")
                .sheets(20)
                .build());
        private final FilmStock empty = Cassette.makeStock(StockInput.builder()
                .batch("B-0")
                .size("4.5x17")
                .boxedOn("2023-11-01")
                .sheets(0)
                .build());

        @Test
        void findsStockGoingOutOfDate() {
            List<StockAge> list = Cassette.stockToUseUp(List.of(stock, older), "2025-04-01");
            assertThat(list.stream().map(age -> age.stock().batch())).containsExactly("B-1");
        }

        @Test
        void putsTheNearestToExpiryFirst() {
            List<StockAge> list = Cassette.stockToUseUp(List.of(stock, older), "2025-04-01", 5000);
            assertThat(list.get(0).stock().batch()).isEqualTo("B-1");
        }

        @Test
        void leavesOutBoxesWithNoSheetsLeft() {
            assertThat(Cassette.stockToUseUp(List.of(empty), "2025-04-01")).isEmpty();
        }

        @Test
        void findsNothingWhenEverythingIsFresh() {
            assertThat(Cassette.stockToUseUp(List.of(stock), "2025-01-01", 30)).isEmpty();
        }
    }

    @Nested
    class MakeCassette {

        @Test
        void recordsWhatScreensAreInIt() {
            assertThat(cassette.screens()).isEqualTo("LEAD_STANDARD");
            assertThat(cassette.frontIn()).isCloseTo(0.005, digits(4));
            assertThat(cassette.backIn()).isCloseTo(0.01, digits(4));
        }

        @Test
        void startsEmpty() {
            assertThat(cassette.loaded()).isEmpty();
        }

        @Test
        void refusesACassetteWithNoNumber() {
            assertThatThrownBy(() -> Cassette.makeCassette(" ", "4.5x17"))
                    .hasMessageContaining("identified by its number");
        }

        @Test
        void refusesScreensItDoesNotStock() {
            assertThatThrownBy(() -> Cassette.makeCassette("C-1", "4.5x17", "FLUORO"))
                    .hasMessageContaining("no screen set called");
        }

        @Test
        void answersWhetherItCarriesTheScreensATechniqueWants() {
            assertThat(Cassette.screensMatch(cassette, "LEAD_STANDARD")).isTrue();
            assertThat(Cassette.screensMatch(cassette, "LEAD_HEAVY")).isFalse();
        }
    }

    @Nested
    class LoadAndUnload {

        @Test
        void takesASheetOutOfTheBoxAndPutsItInTheCassette() {
            CassetteLoad result = Cassette.load(cassette, stock, "2025-03-14");
            assertThat(result.cassette().loaded().orElseThrow().batch()).isEqualTo("B-2291");
            assertThat(result.stock().sheets()).isEqualTo(99);
        }

        @Test
        void leavesTheOriginalsAlone() {
            Cassette.load(cassette, stock, "2025-03-14");
            assertThat(cassette.loaded()).isEmpty();
            assertThat(stock.sheets()).isEqualTo(100);
        }

        @Test
        void refusesToLoadACassetteThatAlreadyHasFilmInIt() {
            CassetteLoad once = Cassette.load(cassette, stock, "2025-03-14");
            assertThatThrownBy(() -> Cassette.load(once.cassette(), once.stock(), "2025-03-14"))
                    .hasMessageContaining("already has film");
        }

        @Test
        void refusesFilmOfTheWrongSize() {
            FilmStock wrong = Cassette.makeStock(StockInput.builder()
                    .batch("B-9")
                    .size("14x17")
                    .boxedOn("2024-11-01")
                    .sheets(10)
                    .build());
            assertThatThrownBy(() -> Cassette.load(cassette, wrong, "2025-03-14"))
                    .hasMessageContaining("takes 4.5x17");
        }

        @Test
        void refusesExpiredFilm() {
            assertThatThrownBy(() -> Cassette.load(cassette, stock, "2026-06-01"))
                    .hasMessageContaining("expired on 2026-05-01");
        }

        @Test
        void refusesAnEmptyBox() {
            FilmStock none =
                    Cassette.makeStock(BOX.toBuilder().batch("B-0").sheets(0).build());
            assertThatThrownBy(() -> Cassette.load(cassette, none, "2025-03-14"))
                    .hasMessageContaining("is empty");
        }

        @Test
        void unloadsBackToEmpty() {
            CassetteLoad once = Cassette.load(cassette, stock, "2025-03-14");
            assertThat(Cassette.unload(once.cassette()).loaded()).isEmpty();
        }

        @Test
        void refusesToUnloadACassetteThatIsAlreadyEmpty() {
            assertThatThrownBy(() -> Cassette.unload(cassette)).hasMessageContaining("already empty");
        }
    }
}
