package io.github.rkaushikdev.kerfline.report;

/** One line of a pack's contents list: the page number, its title and how many items it holds. */
public record PackPage(int page, String title, int items) {}
