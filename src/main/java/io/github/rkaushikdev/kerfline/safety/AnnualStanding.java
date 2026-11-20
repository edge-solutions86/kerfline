package io.github.rkaushikdev.kerfline.safety;

/**
 * A person's standing over the rolling twelve months: the dose period it was judged on, the annual
 * limit and investigation levels, the standing, how much of the limit is left and what fraction is
 * used.
 */
public record AnnualStanding(
        DosePeriod period,
        double limitMrem,
        InvestigationLevels levels,
        DoseStanding standing,
        double remainingMrem,
        double fractionUsed) {}
