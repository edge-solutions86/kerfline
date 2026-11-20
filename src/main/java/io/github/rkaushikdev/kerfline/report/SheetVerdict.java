package io.github.rkaushikdev.kerfline.report;

import java.util.List;
import java.util.Optional;

/**
 * The judgement an interpretation sheet records: the disposition, the code clause it was made
 * under, and the reasons, one line each. Either text may be missing on a sheet still being filled
 * in.
 */
public record SheetVerdict(Optional<String> disposition, Optional<String> clause, List<String> reasons) {

    public SheetVerdict {
        reasons = List.copyOf(reasons);
    }

    /** A verdict from its parts; a null disposition or clause is recorded as missing. */
    public static SheetVerdict of(String disposition, String clause, List<String> reasons) {
        return new SheetVerdict(Optional.ofNullable(disposition), Optional.ofNullable(clause), reasons);
    }

    /** A verdict that gives no reasons, which is what an accepted joint usually carries. */
    public static SheetVerdict of(String disposition, String clause) {
        return of(disposition, clause, List.of());
    }
}
