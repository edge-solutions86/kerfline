package io.github.rkaushikdev.kerfline.report;

import java.util.Optional;

/**
 * What checking an export's checksum line found. {@code checked} is false when there was no
 * usable checksum line at all; {@code expected} and {@code found} are present only when the check
 * actually ran, and {@code reason} is present whenever the export is not intact.
 */
public record ChecksumCheck(
        boolean checked, boolean intact, Optional<String> expected, Optional<String> found, Optional<String> reason) {

    static ChecksumCheck unchecked(String reason) {
        return new ChecksumCheck(false, false, Optional.empty(), Optional.empty(), Optional.of(reason));
    }
}
