package io.github.rkaushikdev.kerfline.personnel;

/**
 * Whether a person's training in one method and their experience reach a certification level,
 * and how many hours short of each they are.
 */
public record Readiness(
        String level,
        String method,
        double trainingHours,
        double experienceHours,
        double trainingShort,
        double experienceShort,
        boolean ready) {}
