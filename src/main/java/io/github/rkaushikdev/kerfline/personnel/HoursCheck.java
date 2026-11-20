package io.github.rkaushikdev.kerfline.personnel;

/** Whether a candidate's training and experience hours reach a level, and how short each one is. */
public record HoursCheck(
        String level,
        boolean trainingMet,
        boolean experienceMet,
        double trainingShort,
        double experienceShort,
        boolean met) {}
