package io.github.rkaushikdev.kerfline.safety;

/** One source's dose rate, in milliroentgen per hour, in a side by side comparison of cameras. */
public record SourceRate(String isotope, double rateMrPerHour) {}
