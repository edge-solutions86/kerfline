package io.github.rkaushikdev.kerfline.density;

/** Which way a film reading fell against the code limits. */
public enum DensityCall {
    ACCEPT("accept"),
    LIGHT("light"),
    DARK("dark");

    private final String label;

    DensityCall(String label) {
        this.label = label;
    }

    /** The word the sheets print for it. */
    public String label() {
        return label;
    }
}
