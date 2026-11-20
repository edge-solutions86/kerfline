package io.github.rkaushikdev.kerfline.density;

/** What a film that failed the variation rule needs next. */
public enum RemedyAction {
    NONE("none"),
    ADD_EXPOSURE("add an exposure"),
    RESHOOT_JOINT("reshoot the joint");

    private final String label;

    RemedyAction(String label) {
        this.label = label;
    }

    /** The word the sheets print for it. */
    public String label() {
        return label;
    }
}
