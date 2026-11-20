package io.github.rkaushikdev.kerfline.pipeline;

import io.github.rkaushikdev.kerfline.store.Journal;

/** The journal with a shot written into it, and the plan that was written. */
public record RecordedShot(Journal journal, ShotPlan plan) {}
