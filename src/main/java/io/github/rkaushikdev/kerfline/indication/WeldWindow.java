package io.github.rkaushikdev.kerfline.indication;

import java.util.List;

/**
 * A run of weld judged for cumulative length: the total length of the indications inside it, where
 * it starts along the weld, and the indications it holds.
 */
public record WeldWindow(double totalIn, double startIn, List<Indication> indications) {

    public WeldWindow {
        indications = List.copyOf(indications);
    }
}
