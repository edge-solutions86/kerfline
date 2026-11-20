package io.github.rkaushikdev.kerfline.safety;

/** How fast an incident has to be reported, most urgent first: the same day, inside a day, inside thirty. */
public enum ReportingWindow {
    IMMEDIATE("immediate", 0),
    ONE_DAY("oneDay", 1),
    THIRTY_DAY("thirtyDay", 30);

    private final String label;
    private final int days;

    ReportingWindow(String label, int days) {
        this.label = label;
        this.days = days;
    }

    /** The name the reporting procedure uses for the window. */
    public String label() {
        return label;
    }

    /** Days from the incident to the last day the report may go in. */
    public int days() {
        return days;
    }
}
