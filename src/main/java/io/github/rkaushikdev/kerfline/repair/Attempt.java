package io.github.rkaushikdev.kerfline.repair;

import java.util.Optional;

/**
 * One attempt on a joint, the original shot or a repair of it: its label ({@code original},
 * {@code R1}, ...), the day, how it ended ({@code accept}, {@code repair} or {@code reject}), the
 * clock position repaired when there is one, and a note.
 */
public record Attempt(String label, String day, String disposition, Optional<String> at, Optional<String> note) {}
