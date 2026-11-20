package io.github.rkaushikdev.kerfline.report;

/**
 * Which way a second reading went, from the first reader's point of view. A disagreement is
 * lenient when the second reader called the weld harsher than the first did, and strict when the
 * second reader called it softer.
 */
public enum ReviewDirection {
    AGREED("agreed"),
    LENIENT("first reader was lenient"),
    STRICT("first reader was strict"),
    NOT_COMPARABLE("not comparable");

    private final String label;

    ReviewDirection(String label) {
        this.label = label;
    }

    /** The words the review sheet prints. */
    public String label() {
        return label;
    }
}
