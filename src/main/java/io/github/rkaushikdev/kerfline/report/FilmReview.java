package io.github.rkaushikdev.kerfline.report;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.sampling.Select;
import io.github.rkaushikdev.kerfline.util.Column;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Ids;
import io.github.rkaushikdev.kerfline.util.Num;
import io.github.rkaushikdev.kerfline.util.Rate;
import io.github.rkaushikdev.kerfline.util.RootCollation;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Second reading.
 *
 * <p>A share of every reader's interpretations is read again by somebody senior, without being
 * told what the first reader said. The point is not to catch a bad reader, which almost never
 * happens; it is to catch a drift, where a reader has quietly moved where they put a borderline
 * call and nobody has noticed because every individual film looked reasonable.
 *
 * <p>So the number that matters is not how many disagreements there were but which way they went.
 * Ten disagreements evenly split is two people reading borderline films differently. Ten
 * disagreements all in the same direction is a reader who has moved, and that is a finding.
 */
public final class FilmReview {

    private static final int WIDTH = Layout.pageWidth();
    private static final double DEFAULT_FRACTION = 0.1;
    private static final List<String> SEVERITY = List.of("accept", "repair", "reject");
    private static final int DRIFT = 3;
    private static final String NO_READER = "unknown";

    private FilmReview() {}

    public static double defaultFraction() {
        return DEFAULT_FRACTION;
    }

    public static <T> List<T> selectForReview(
            List<T> interpretations, Function<? super T, String> jointIdOf, String job) {
        return selectForReview(interpretations, jointIdOf, job, DEFAULT_FRACTION);
    }

    /**
     * Pick the interpretations to read again: the share asked for, rounded up, then put back in
     * register order. Seeded off the job so an auditor can rerun the selection and get the same
     * films.
     *
     * @param jointIdOf how to read the joint id off an interpretation; a null id sorts first
     */
    public static <T> List<T> selectForReview(
            List<T> interpretations, Function<? super T, String> jointIdOf, String job, double fraction) {
        Guard.requireArray(interpretations, "interpretations");
        Objects.requireNonNull(jointIdOf, "jointIdOf");
        String named = Guard.requireLabel(job, "a review selection belongs to a job");
        Guard.requireAbove(fraction, 0, "review fraction");
        if (fraction > 1) {
            throw new BadInputException(
                    "a review fraction cannot be more than all of them", Details.of("fraction", fraction));
        }
        int wanted = (int) Math.min(interpretations.size(), Math.ceil(interpretations.size() * fraction));
        List<T> picked = new ArrayList<>(
                Select.shuffle(interpretations, Select.seedFrom(named)).subList(0, wanted));
        Function<T, String> idOf = item -> Objects.requireNonNullElse(jointIdOf.apply(item), "");
        picked.sort(Comparator.comparing(idOf, Ids.REGISTER_ORDER));
        return List.copyOf(picked);
    }

    /** Record a second reading. Both calls are compared as written, surrounding space ignored. */
    public static Review makeReview(ReviewInput input) {
        Dates.requireDay(input.day(), "review date");
        String jointId = Guard.requireLabel(input.jointId(), "a review names the joint");
        String reviewer = Guard.requireLabel(input.reviewer(), "a review names who read it again");
        String firstCall = Guard.requireLabel(input.firstCall(), "a review records what the first reader called it");
        String secondCall = Guard.requireLabel(input.secondCall(), "a review records what the second reader called it");
        return new Review(
                jointId,
                input.day(),
                reviewer,
                firstCall,
                secondCall,
                firstCall.equals(secondCall),
                Optional.ofNullable(input.note()));
    }

    /** Which way a disagreement went, from the first reader's point of view. */
    public static ReviewDirection direction(Review review) {
        if (review.agrees()) {
            return ReviewDirection.AGREED;
        }
        int first = SEVERITY.indexOf(review.firstCall());
        int second = SEVERITY.indexOf(review.secondCall());
        if (first == -1 || second == -1) {
            return ReviewDirection.NOT_COMPARABLE;
        }
        return second > first ? ReviewDirection.LENIENT : ReviewDirection.STRICT;
    }

    /**
     * The summary that matters. Reports the split as well as the rate, because an even split and a
     * one sided one mean different things. Nothing disagreed with is full agreement, not none of
     * it, so an empty set agrees with itself.
     */
    public static Agreement agreement(List<Review> reviews) {
        Guard.requireArray(reviews, "reviews");
        int agreed = (int) reviews.stream().filter(Review::agrees).count();
        List<ReviewDirection> directions =
                reviews.stream().map(FilmReview::direction).toList();
        int lenient = (int)
                directions.stream().filter(ReviewDirection.LENIENT::equals).count();
        int strict =
                (int) directions.stream().filter(ReviewDirection.STRICT::equals).count();
        int disagreements = reviews.size() - agreed;
        boolean oneWay = lenient == 0 || strict == 0;
        return new Agreement(
                reviews.size(),
                agreed,
                disagreements,
                Rate.rateOf(agreed, reviews.size(), 1),
                lenient,
                strict,
                disagreements > 0 && oneWay,
                disagreements >= DRIFT && oneWay);
    }

    /**
     * Reviews by first reader, worst agreement first, so a drift belongs to somebody. A review
     * whose reader cannot be found counts under {@code unknown}.
     */
    public static List<ReaderAgreement> byReader(List<Review> reviews, Function<? super Review, String> readerOf) {
        Guard.requireArray(reviews, "reviews");
        if (readerOf == null) {
            throw new BadInputException("grouping by reader needs a way to find the reader", Details.of("got", null));
        }
        Map<String, List<Review>> groups = new LinkedHashMap<>();
        for (Review review : reviews) {
            String reader = Objects.requireNonNullElse(readerOf.apply(review), NO_READER);
            groups.computeIfAbsent(reader, key -> new ArrayList<>()).add(review);
        }
        return groups.entrySet().stream()
                .map(entry -> new ReaderAgreement(entry.getKey(), agreement(entry.getValue())))
                .sorted(Comparator.comparingDouble(ReaderAgreement::rate)
                        .thenComparing(ReaderAgreement::reader, RootCollation.ORDER))
                .toList();
    }

    /** The reviews that disagreed, each with the way it went. */
    public static List<Disagreement> disagreements(List<Review> reviews) {
        Guard.requireArray(reviews, "reviews");
        return reviews.stream()
                .filter(review -> !review.agrees())
                .map(review -> new Disagreement(review, direction(review)))
                .toList();
    }

    public static String renderReview(List<Review> reviews) {
        return renderReview(reviews, null, DEFAULT_FRACTION);
    }

    public static String renderReview(List<Review> reviews, String job) {
        return renderReview(reviews, job, DEFAULT_FRACTION);
    }

    /** The second reading page; a one sided run of disagreements adds a finding at the foot. */
    public static String renderReview(List<Review> reviews, String job, double fraction) {
        Guard.requireAtLeast(fraction, 0, "review fraction");
        Agreement summary = agreement(reviews);
        List<Disagreement> found = disagreements(reviews);

        List<String> blocks = new ArrayList<>(List.of(
                Layout.heading("SECOND READING", WIDTH),
                Layout.block(
                        "SUMMARY",
                        Text.keyValueBlock(List.of(
                                Text.pair("Job", job == null ? "" : job),
                                Text.pair("Share reviewed", Num.text(Num.round(fraction * 100, 1)) + " percent"),
                                Text.pair("Reviewed", String.valueOf(summary.reviewed())),
                                Text.pair("Agreed", summary.agreed() + " of " + summary.reviewed()),
                                Text.pair("Lenient", String.valueOf(summary.lenient())),
                                Text.pair("Strict", String.valueOf(summary.strict())))),
                        WIDTH),
                Layout.block("DISAGREEMENTS", found.isEmpty() ? "None." : disagreementTable(found), WIDTH)));

        if (summary.drifting()) {
            blocks.add(Layout.block(
                    "FINDING",
                    Text.wrap(
                            "Every disagreement went the same way. That is a reader who has moved, not a run of"
                                    + " borderline films.",
                            WIDTH),
                    WIDTH));
        }
        return Layout.page(blocks);
    }

    private static String disagreementTable(List<Disagreement> found) {
        List<Map<String, Object>> rows = found.stream()
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("jointId", entry.review().jointId());
                    row.put("firstCall", entry.review().firstCall());
                    row.put("secondCall", entry.review().secondCall());
                    row.put("direction", entry.direction().label());
                    return row;
                })
                .toList();
        return Text.renderTable(
                rows,
                List.of(
                        Column.of("jointId").label("joint"),
                        Column.of("firstCall").label("first"),
                        Column.of("secondCall").label("second"),
                        Column.of("direction").label("direction")));
    }
}
