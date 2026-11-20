package io.github.rkaushikdev.kerfline.safety;

import java.util.List;

/**
 * What one source did over a period: how many log entries, the minutes out of shield, the
 * exposures made, and who had it on which days, each listed once in sorted order.
 */
public record Utilisation(
        String serial,
        String from,
        String to,
        int entries,
        double minutes,
        int exposures,
        List<String> users,
        List<String> days) {

    public Utilisation {
        users = List.copyOf(users);
        days = List.copyOf(days);
    }
}
