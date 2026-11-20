package io.github.rkaushikdev.kerfline.joint;

/** One pipe size on a job and how many joints are in it, so a crew knows what to load out. */
public record SizeCount(String size, int count) {}
