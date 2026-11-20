package io.github.rkaushikdev.kerfline.personnel;

import java.util.Optional;

/**
 * A crew member: name, role on the crew, the film reading certification they hold if any, and
 * whether they are a monitored radiation worker.
 */
public record Person(String name, String role, Optional<Certification> certification, boolean monitored) {}
