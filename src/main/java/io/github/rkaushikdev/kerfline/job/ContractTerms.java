package io.github.rkaushikdev.kerfline.job;

/**
 * The parameters every judgement on a job should be made with: the code and fluid service, the
 * most crown allowed, the lowest density the client accepts and the sensitivity called for.
 */
public record ContractTerms(
        String code,
        String service,
        double maximumReinforcementIn,
        double densityMinimum,
        double calledForSensitivity) {}
