package io.github.rkaushikdev.kerfline.personnel;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Whether somebody is current to work on a day, judged on their latest radiation safety course:
 * the reason when they are not, the course it was judged on, and the days left when they are.
 */
public record SafetyStatus(
        boolean current, Optional<String> reason, Optional<TrainingRecord> latest, OptionalInt daysLeft) {}
