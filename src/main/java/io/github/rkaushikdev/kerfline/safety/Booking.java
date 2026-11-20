package io.github.rkaushikdev.kerfline.safety;

/** A source booked out of the store on a day, with the curies it holds that day. */
public record Booking(String serial, String day, double activityCi) {}
