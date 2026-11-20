package io.github.rkaushikdev.kerfline.density;

/** Which way a reading in the area of interest fell against the variation band. */
public enum VariationCall {
    ACCEPT("accept"),
    TOO_LIGHT("too light"),
    TOO_DARK("too dark");

    private final String label;

    VariationCall(String label) {
        this.label = label;
    }

    /** The word the sheets print for it. */
    public String label() {
        return label;
    }
}
