package io.github.rkaushikdev.kerfline.pipeline;

/**
 * One film quality check a film failed: which check ({@code identification}, {@code density},
 * {@code variation}, {@code sensitivity} or {@code backscatter}) and why, in words for the sheet.
 */
public record QualityFailure(String check, String reason) {}
