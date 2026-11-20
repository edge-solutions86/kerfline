package io.github.rkaushikdev.kerfline.accept;

/**
 * What one acceptance code was handed to judge a joint on. The codes do not measure the same joint
 * the same way, so each has its own shape, and the engine is the one place a joint record is turned
 * into it.
 */
public sealed interface CodeContext permits Api1104Context, Asme8Context, AsmeB313Context, AwsD11Context {}
