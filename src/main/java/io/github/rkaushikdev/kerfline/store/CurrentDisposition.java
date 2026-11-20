package io.github.rkaushikdev.kerfline.store;

import java.util.Optional;

/**
 * Where one joint stands: the disposition on its latest verdict entry, empty when that entry
 * carried none, with the day and sequence number of the entry it was read from.
 */
public record CurrentDisposition(String subject, Optional<String> disposition, String day, int seq) {}
