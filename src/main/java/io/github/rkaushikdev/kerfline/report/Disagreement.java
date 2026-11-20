package io.github.rkaushikdev.kerfline.report;

/** A second reading that disagreed with the first, and which way it went. */
public record Disagreement(Review review, ReviewDirection direction) {

    public String jointId() {
        return review.jointId();
    }
}
