package io.github.rkaushikdev.kerfline.util;

/** A rate together with the two counts it came from, which every report prints side by side. */
public record CountedRate(int part, int whole, double rate) {}
