package io.github.rkaushikdev.kerfline.personnel;

/**
 * A technician's certification to read film. The certification runs five years from {@code
 * certifiedOn} and the vision test one year from {@code visionOn}; the two clocks are independent.
 * It is {@code valid} only when the examination passed and the hours were met.
 */
public record Certification(
        String person,
        String level,
        String certifiedOn,
        String expiresOn,
        String visionOn,
        String visionExpiresOn,
        ExaminationResult examination,
        HoursCheck hours,
        boolean valid) {}
