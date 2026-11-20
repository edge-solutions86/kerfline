package io.github.rkaushikdev.kerfline.joint;

/**
 * A material the shop welds, with the two numbers that change a radiographic answer: the {@code
 * equivalence}, which turns a section of this material into the steel thickness that absorbs the
 * same, and the {@code iqiGroup}, which says which indicators are radiographically similar to it.
 */
public record PipeMaterial(String id, String label, String pNumber, String iqiGroup, double equivalence) {}
