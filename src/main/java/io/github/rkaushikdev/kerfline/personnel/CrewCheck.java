package io.github.rkaushikdev.kerfline.personnel;

import java.util.List;

/**
 * Whether a crew may work on a day: its size, who on it may read film, and every reason it may
 * not work, all at once so a crew short two things gets both fixed in one trip.
 */
public record CrewCheck(String day, int size, List<String> readers, boolean mayWork, List<String> reasons) {

    public CrewCheck {
        readers = List.copyOf(readers);
        reasons = List.copyOf(reasons);
    }
}
