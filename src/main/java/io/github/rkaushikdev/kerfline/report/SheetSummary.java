package io.github.rkaushikdev.kerfline.report;

import java.util.Optional;

/**
 * An interpretation sheet on one line, for a register or an index of sheets: the joint, the day it
 * was read, the disposition once judged, how many indications it carries and the largest of them
 * in inches, and whether it can be issued.
 */
public record SheetSummary(
        Optional<String> jointId,
        Optional<String> day,
        Optional<String> disposition,
        int indications,
        double largestIn,
        boolean issuable) {}
