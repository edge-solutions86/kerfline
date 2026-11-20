package io.github.rkaushikdev.kerfline.film;

/**
 * A box of film as it arrives from the store: the batch number, the size, the day it was boxed,
 * the sheet count and the film in it. Built with {@link #builder()}, which starts from D7 film; an
 * unset sheet count is NaN, so it is refused as not a whole number.
 */
public record StockInput(String batch, String size, String boxedOn, double sheets, String filmId) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .batch(batch)
                .size(size)
                .boxedOn(boxedOn)
                .sheets(sheets)
                .filmId(filmId);
    }

    /** Builds the input; anything not set keeps the default. */
    public static final class Builder {
        private String batch;
        private String size;
        private String boxedOn;
        private double sheets = Double.NaN;
        private String filmId = "D7";

        private Builder() {}

        public Builder batch(String value) {
            batch = value;
            return this;
        }

        public Builder size(String value) {
            size = value;
            return this;
        }

        public Builder boxedOn(String value) {
            boxedOn = value;
            return this;
        }

        /** The sheet count, taken as a number so that a fraction can be refused rather than truncated. */
        public Builder sheets(double value) {
            sheets = value;
            return this;
        }

        public Builder filmId(String value) {
            filmId = value;
            return this;
        }

        public StockInput build() {
            return new StockInput(batch, size, boxedOn, sheets, filmId);
        }
    }
}
