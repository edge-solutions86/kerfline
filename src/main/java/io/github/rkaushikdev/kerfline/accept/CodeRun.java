package io.github.rkaushikdev.kerfline.accept;

import java.util.Optional;

/**
 * One code's answer when a joint is run past every code: either a {@link CodeJudgement} or a
 * {@link CodeFailure} saying why that code could not run, so one code that cannot judge the joint
 * does not throw the other answers away.
 */
public sealed interface CodeRun permits CodeJudgement, CodeFailure {

    /** The code this answer is from. */
    String code();

    /** The disposition the code reached, or empty when it could not run. */
    Optional<Disposition> outcome();
}
