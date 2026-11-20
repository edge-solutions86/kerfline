package io.github.rkaushikdev.kerfline.film;

/** How the lead B imaged against its background, which is the whole of the backscatter rule. */
public enum BackscatterAppearance {
    /** Lighter than the background: radiation came back through the film from behind. */
    LIGHT("light"),
    /** Darker than the background: the letter absorbed the beam on its way through. */
    DARK("dark"),
    /** The same density as the background: the B did not image. */
    NOT_VISIBLE("not visible");

    private final String label;

    BackscatterAppearance(String label) {
        this.label = label;
    }

    /** The words the interpretation sheet uses. */
    public String label() {
        return label;
    }
}
