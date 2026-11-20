package io.github.rkaushikdev.kerfline.personnel;

import java.util.Optional;

/**
 * One course a person attended: the method it counts towards, the hours, the day, the provider if
 * recorded, and the day it expires on for training that has to be refreshed.
 */
public record TrainingRecord(
        String person,
        String method,
        String course,
        double hours,
        String day,
        Optional<String> provider,
        Optional<String> expiresOn) {}
