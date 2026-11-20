package io.github.rkaushikdev.kerfline.pipeline;

import io.github.rkaushikdev.kerfline.geom.PlannedShot;
import java.util.List;
import java.util.Objects;

/** One shot round the joint with the lead marks that have to be laid on its film. */
public record MarkedShot(PlannedShot planned, List<String> marks) {

    public MarkedShot {
        Objects.requireNonNull(planned, "planned");
        marks = List.copyOf(marks);
    }

    /** The shot's number round the joint, from one. */
    public int shot() {
        return planned.shot();
    }

    /** Where the shot is centred, as a clock position. */
    public String clock() {
        return planned.clock();
    }
}
