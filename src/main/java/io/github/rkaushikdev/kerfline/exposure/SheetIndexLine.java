package io.github.rkaushikdev.kerfline.exposure;

import io.github.rkaushikdev.kerfline.geom.ViewingTechnique;
import io.github.rkaushikdev.kerfline.report.TechniqueSummary;

/** One line of the index at the front of a sheet folder: the group, how many joints, and the sheet's summary. */
public record SheetIndexLine(String key, int joints, TechniqueSummary summary) {

    /** The viewing technique the sheet calls for. */
    public ViewingTechnique technique() {
        return summary.technique();
    }
}
