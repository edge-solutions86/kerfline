package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.geom.ViewingTechnique;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * A technique sheet on one line, for a job folder index: the joint, the material, the viewing
 * technique, and, where they resolved, the source, the exposure time in minutes and the number of
 * shots, with whether the sheet is ready to work from.
 */
public record TechniqueSummary(
        String jointId,
        String material,
        ViewingTechnique technique,
        Optional<String> isotope,
        OptionalDouble minutes,
        OptionalInt shots,
        boolean usable) {}
