package io.github.rkaushikdev.kerfline.film;

import java.util.List;

/**
 * The verdict on a film's lead identification: every required field that is missing, in the
 * order they are required, whether the day can be read as a day, and whether the film passes.
 */
public record IdentificationCheck(List<String> missing, boolean dayIsReadable, boolean complete) {

    public IdentificationCheck {
        missing = List.copyOf(missing);
    }
}
