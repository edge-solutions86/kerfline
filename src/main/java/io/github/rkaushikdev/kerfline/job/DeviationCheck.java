package io.github.rkaushikdev.kerfline.job;

import io.github.rkaushikdev.kerfline.report.Deviation;
import java.util.List;

/**
 * Where a shot departed from what the contract called for, on one job. Each deviation names the
 * field, what was agreed and what happened; the check conforms when there are none.
 */
public record DeviationCheck(String job, List<Deviation> deviations, boolean conforms) {

    public DeviationCheck {
        deviations = List.copyOf(deviations);
    }
}
