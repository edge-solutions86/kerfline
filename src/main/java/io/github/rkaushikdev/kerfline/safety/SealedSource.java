package io.github.rkaushikdev.kerfline.safety;

import java.util.Objects;
import java.util.Optional;

/**
 * A sealed source on the inventory, as its certificate describes it: serial number, isotope, the
 * assayed activity in curies and the assay day, and the camera it is loaded in when that is known.
 */
public record SealedSource(String serial, String isotope, double assayCi, String assayDay, Optional<String> camera) {

    public SealedSource {
        Objects.requireNonNull(camera, "camera");
    }
}
