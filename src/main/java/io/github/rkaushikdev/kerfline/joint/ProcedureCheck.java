package io.github.rkaushikdev.kerfline.joint;

import java.util.List;

/**
 * Whether a procedure covers a joint, and when it does not, every one of the bands the joint is
 * out on, because a joint welded on the wrong procedure is usually out on more than one.
 */
public record ProcedureCheck(String procedure, boolean covers, List<String> reasons) {

    public ProcedureCheck {
        reasons = List.copyOf(reasons);
    }
}
