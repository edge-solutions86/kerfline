package io.github.rkaushikdev.kerfline.exposure;

import io.github.rkaushikdev.kerfline.joint.Joint;
import java.util.List;

/**
 * Joints that need the same technique: the key that groups them, the joints in register order, the
 * joint the sheet is worked out from, and how many there are.
 */
public record SheetGroup(String key, List<Joint> joints, Joint example, int count) {

    public SheetGroup {
        joints = List.copyOf(joints);
    }
}
