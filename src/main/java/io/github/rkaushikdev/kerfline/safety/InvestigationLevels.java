package io.github.rkaushikdev.kerfline.safety;

/** The two shop investigation levels, in millirem a year, set well below the annual limit. */
public record InvestigationLevels(double levelOneMrem, double levelTwoMrem) {}
