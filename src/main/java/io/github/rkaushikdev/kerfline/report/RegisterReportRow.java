package io.github.rkaushikdev.kerfline.report;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One joint on the register report, as printed: what the joint is (id, spool, welder, size and
 * wall) from the register, and what has happened to it (the day of its latest shot, its latest
 * disposition and clause, and its state) from the journal. A field with nothing to show is blank.
 */
public record RegisterReportRow(
        String id,
        String spool,
        String welder,
        String size,
        String wallIn,
        String shotOn,
        String disposition,
        String clause,
        String state) {

    /** The row keyed by column, for the printed table. */
    Map<String, Object> cells() {
        Map<String, Object> cells = new LinkedHashMap<>();
        cells.put("id", id);
        cells.put("spool", spool);
        cells.put("welder", welder);
        cells.put("size", size);
        cells.put("wallIn", wallIn);
        cells.put("shotOn", shotOn);
        cells.put("disposition", disposition);
        cells.put("clause", clause);
        cells.put("state", state);
        return cells;
    }
}
