package io.github.rkaushikdev.kerfline.repair;

/** The state a joint's repair chain is in. */
public enum ChainStatus {
    NOT_SHOT("not shot"),
    AWAITING_REPAIR("awaiting repair"),
    ACCEPTED("accepted"),
    CUT_OUT("cut out");

    private final String label;

    ChainStatus(String label) {
        this.label = label;
    }

    /** The words the register prints, such as {@code awaiting repair}. */
    public String label() {
        return label;
    }
}
