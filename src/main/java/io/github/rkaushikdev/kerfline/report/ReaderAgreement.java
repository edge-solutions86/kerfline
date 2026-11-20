package io.github.rkaushikdev.kerfline.report;

/** One first reader's second readings, so a drift belongs to somebody. */
public record ReaderAgreement(String reader, Agreement agreement) {

    /** The reader's agreement rate. */
    public double rate() {
        return agreement.rate();
    }
}
