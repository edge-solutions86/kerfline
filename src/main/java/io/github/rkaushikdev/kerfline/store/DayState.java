package io.github.rkaushikdev.kerfline.store;

import java.util.Map;

/** The state of the record at the end of one day something happened on it. */
public record DayState(String day, Map<String, String> state) {}
