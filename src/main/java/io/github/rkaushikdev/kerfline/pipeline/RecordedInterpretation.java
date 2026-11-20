package io.github.rkaushikdev.kerfline.pipeline;

import io.github.rkaushikdev.kerfline.store.Journal;

/** The journal with an interpretation written into it, and the interpretation that was written. */
public record RecordedInterpretation(Journal journal, Interpretation result) {}
