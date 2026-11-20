package io.github.rkaushikdev.kerfline.joint;

import java.util.List;

/**
 * The joint register for one job: a flat list of every joint, in the order they were entered. It
 * holds what each joint is and not what has happened to it; that lives in the journal.
 */
public record JointRegister(String job, List<Joint> joints) {

    public JointRegister {
        joints = List.copyOf(joints);
    }
}
