package io.github.rkaushikdev.kerfline.personnel;

/**
 * What a certification level asks for in classroom and field hours, and what it lets the holder
 * do: interpret film, and sign for it.
 */
public record LevelRequirements(double trainingHours, double experienceHours, boolean mayInterpret, boolean maySign) {}
