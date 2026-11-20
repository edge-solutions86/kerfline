package io.github.rkaushikdev.kerfline.joint;

import java.util.Optional;

/**
 * One heat of pipe as received: its stamped number, the material it was ordered as, the day it
 * came in and the mill test report that proves the metal, if it arrived with one. A heat with no
 * mill report is not {@code traceable}, and nothing welded from it is either.
 */
public record PipeHeat(
        String number,
        String materialId,
        String material,
        String receivedOn,
        Optional<String> millReport,
        Optional<String> supplier,
        boolean traceable) {}
