package io.github.rkaushikdev.kerfline.report;

import java.util.OptionalDouble;

/**
 * One line of the findings table: its number round the weld, what the indication is, where it sits
 * when located, its governing size written to three places, and the film it was read on (blank
 * when not recorded).
 */
public record IndicationRow(int item, String type, OptionalDouble at, String sizeIn, String film) {}
