package io.github.rkaushikdev.kerfline.cli;

/**
 * The three ways the tool can end: a clean answer, a finding (a weld or a technique that did not
 * pass), and misuse (being asked something it could not do). A shell script needs to tell those
 * apart, because a rejected weld is a result and a misspelled option is a mistake.
 */
public record ExitCodes(int ok, int finding, int misuse) {}
