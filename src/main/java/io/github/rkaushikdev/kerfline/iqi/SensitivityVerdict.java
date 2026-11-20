package io.github.rkaushikdev.kerfline.iqi;

/**
 * A film judged on sensitivity: what was achieved and what the job called for, both in percent of
 * the thickness, whether that passes, and by how much it cleared or missed, so a rejected film can
 * be argued about with numbers rather than opinions.
 */
public record SensitivityVerdict(double achievedPercent, double calledForPercent, boolean pass, double marginPercent) {}
