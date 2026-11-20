package io.github.rkaushikdev.kerfline.film;

/**
 * One film along a weld: its number from one, the location marker written on it, and the stretch
 * of weld it covers, measured on the part.
 */
public record LocationMarker(int film, String marker, double startIn, double endIn) {}
