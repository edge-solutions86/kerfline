package io.github.rkaushikdev.kerfline.film;

import io.github.rkaushikdev.kerfline.errors.ConflictException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.errors.NotFoundException;
import io.github.rkaushikdev.kerfline.exposure.ScreenSet;
import io.github.rkaushikdev.kerfline.exposure.Screens;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Num;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Film stock and the cassettes it goes in.
 *
 * <p>Two things here have bitten this shop and both are in the tests. Film has a shelf life and
 * film past it fogs, which reads as a light weld on a dark background and gets called a technique
 * problem. And a cassette loaded with the wrong screens is invisible on the film until somebody
 * works out that every shot from one particular cassette is running a stop light.
 *
 * <p>So a cassette knows what screens are in it and stock knows when it expires, and both refuse
 * rather than being helpful about it.
 */
public final class Cassette {

    private record Dimensions(double widthIn, double lengthIn) {}

    private static final Map<String, Dimensions> SIZES = sizes();
    private static final int SHELF_MONTHS = 18;
    private static final double USE_UP_WINDOW_DAYS = 60;
    private static final String DEFAULT_SCREENS = "LEAD_STANDARD";

    private Cassette() {}

    private static Map<String, Dimensions> sizes() {
        Map<String, Dimensions> sizes = new LinkedHashMap<>();
        sizes.put("4.5x17", new Dimensions(4.5, 17));
        sizes.put("4.5x10", new Dimensions(4.5, 10));
        sizes.put("4.5x8.5", new Dimensions(4.5, 8.5));
        sizes.put("3.5x17", new Dimensions(3.5, 17));
        sizes.put("7x17", new Dimensions(7, 17));
        sizes.put("10x12", new Dimensions(10, 12));
        sizes.put("14x17", new Dimensions(14, 17));
        return Collections.unmodifiableMap(sizes);
    }

    /** The sizes the store carries, in the order it lists them. */
    public static List<String> filmSizes() {
        return List.copyOf(SIZES.keySet());
    }

    /**
     * A stocked size with its area worked out.
     *
     * @throws NotFoundException when the store does not carry that size
     */
    public static FilmSize sizeOf(String name) {
        Dimensions found = name == null ? null : SIZES.get(name);
        if (found == null) {
            throw new NotFoundException(
                    "no film size " + name + " is stocked", Details.of("asked", name, "known", filmSizes()));
        }
        return new FilmSize(name, found.widthIn(), found.lengthIn(), Num.round(found.widthIn() * found.lengthIn(), 2));
    }

    public static int shelfLifeMonths() {
        return SHELF_MONTHS;
    }

    public static FilmSize smallestCovering(double lengthIn) {
        return smallestCovering(lengthIn, 0);
    }

    /**
     * The smallest stocked film, by area, that covers a length of weld plus its overlap and is at
     * least the width asked for.
     *
     * @throws NotFoundException when nothing stocked reaches
     */
    public static FilmSize smallestCovering(double lengthIn, double widthIn) {
        Guard.requireAtLeast(lengthIn, 0, "weld length");
        return filmSizes().stream()
                .map(Cassette::sizeOf)
                .filter(size -> size.lengthIn() >= lengthIn && size.widthIn() >= widthIn)
                .min(Comparator.comparingDouble(FilmSize::areaSquareInches))
                .orElseThrow(() -> new NotFoundException(
                        "no stocked film covers " + Num.text(lengthIn) + " in",
                        Details.of("lengthIn", lengthIn, "widthIn", widthIn)));
    }

    /** A box of film, with its expiry worked out from the day it was boxed. */
    public static FilmStock makeStock(StockInput input) {
        String batch = Guard.requireLabel(input.batch(), "film stock carries its batch number");
        FilmSize dimensions = sizeOf(input.size());
        Dates.requireDay(input.boxedOn(), "boxing date");
        Guard.requireWhole(input.sheets(), "sheet count");
        Guard.requireAtLeast(input.sheets(), 0, "sheet count");
        return new FilmStock(
                batch,
                dimensions.name(),
                input.filmId(),
                input.boxedOn(),
                Dates.addMonths(input.boxedOn(), SHELF_MONTHS),
                (int) input.sheets());
    }

    /** True up to and including the expiry day. */
    public static boolean stockIsCurrent(FilmStock stock, String day) {
        Dates.requireDay(day, "date");
        return Dates.daysBetween(day, stock.expiresOn()) >= 0;
    }

    /** Days from the given day to the expiry; negative once the box has expired. */
    public static int daysOfLifeLeft(FilmStock stock, String day) {
        Dates.requireDay(day, "date");
        return Dates.daysBetween(day, stock.expiresOn());
    }

    public static List<StockAge> stockToUseUp(List<FilmStock> stocks, String day) {
        return stockToUseUp(stocks, day, USE_UP_WINDOW_DAYS);
    }

    /**
     * Stock that has expired or will inside the window, nearest to expiry first. A box with no
     * sheets left is not worth listing.
     */
    public static List<StockAge> stockToUseUp(List<FilmStock> stocks, String day, double withinDays) {
        Guard.requireArray(stocks, "stock");
        Guard.requireAtLeast(withinDays, 0, "window");
        return stocks.stream()
                .map(stock -> new StockAge(stock, daysOfLifeLeft(stock, day)))
                .filter(age -> age.daysLeft() <= withinDays && age.stock().sheets() > 0)
                .sorted(Comparator.comparingInt(StockAge::daysLeft))
                .toList();
    }

    public static FilmCassette makeCassette(String id, String size) {
        return makeCassette(id, size, DEFAULT_SCREENS);
    }

    /** An empty cassette, recording the screen set and its lead thicknesses. */
    public static FilmCassette makeCassette(String id, String size, String screens) {
        String number = Guard.requireLabel(id, "a cassette is identified by its number");
        FilmSize dimensions = sizeOf(size);
        ScreenSet set = Screens.screen(screens);
        return new FilmCassette(number, dimensions.name(), set.name(), set.frontIn(), set.backIn(), Optional.empty());
    }

    /**
     * Load a cassette. Refuses expired stock, stock of the wrong size, and a cassette that already
     * has film in it, which is how a double exposure happens. The cassette and the box handed in
     * are left as they were.
     *
     * @throws ConflictException when any of those is the case, or the box is empty
     */
    public static CassetteLoad load(FilmCassette cassette, FilmStock stock, String day) {
        Dates.requireDay(day, "loading date");
        if (cassette.loaded().isPresent()) {
            throw new ConflictException(
                    "cassette " + cassette.id() + " already has film in it", Details.of("id", cassette.id()));
        }
        if (!stock.size().equals(cassette.size())) {
            throw new ConflictException(
                    "cassette " + cassette.id() + " takes " + cassette.size() + ", not " + stock.size(),
                    Details.of("cassette", cassette.size(), "stock", stock.size()));
        }
        if (!stockIsCurrent(stock, day)) {
            throw new ConflictException(
                    "batch " + stock.batch() + " expired on " + stock.expiresOn(),
                    Details.of("batch", stock.batch(), "expiresOn", stock.expiresOn()));
        }
        if (stock.sheets() < 1) {
            throw new ConflictException("batch " + stock.batch() + " is empty", Details.of("batch", stock.batch()));
        }
        LoadedFilm sheet = new LoadedFilm(stock.batch(), stock.filmId(), day);
        return new CassetteLoad(cassette.withLoaded(Optional.of(sheet)), stock.withSheets(stock.sheets() - 1));
    }

    /**
     * Take the sheet out.
     *
     * @throws ConflictException when the cassette is already empty
     */
    public static FilmCassette unload(FilmCassette cassette) {
        if (cassette.loaded().isEmpty()) {
            throw new ConflictException(
                    "cassette " + cassette.id() + " is already empty", Details.of("id", cassette.id()));
        }
        return cassette.withLoaded(Optional.empty());
    }

    /** Whether a cassette carries the screens a technique calls for. */
    public static boolean screensMatch(FilmCassette cassette, String wanted) {
        return cassette.screens().equals(Screens.screen(wanted).name());
    }
}
