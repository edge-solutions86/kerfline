package io.github.rkaushikdev.kerfline.accept;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.indication.Indication;
import io.github.rkaushikdev.kerfline.util.Guard;
import io.github.rkaushikdev.kerfline.util.Text;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * What one code paragraph hands back about a weld.
 *
 * <p>A verdict always names the code and the clause it came from. That is not decoration. A weld
 * argued about three months later is argued about on the clause, and a verdict that cannot say
 * which paragraph it applied is a verdict nobody can defend. Anything but an acceptance carries a
 * reason, and may carry the indication it is about and the size that was measured. The code and
 * clause are stored trimmed.
 */
public record ClauseVerdict(
        Disposition disposition,
        String code,
        String clause,
        Optional<String> reason,
        Optional<Indication> indication,
        OptionalDouble measured)
        implements Ruling {

    public ClauseVerdict {
        if (disposition == null) {
            throw Disposition.unknown(null);
        }
        code = Guard.requireLabel(code, "a verdict names the code it came from");
        clause = Guard.requireLabel(clause, "a verdict names the clause it came from");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(indication, "indication");
        Objects.requireNonNull(measured, "measured");
        if (disposition != Disposition.ACCEPT
                && reason.map(text -> Text.trim(text).isEmpty()).orElse(true)) {
            throw new BadInputException(
                    "a verdict that is not an acceptance carries a reason", Details.of("got", reason.orElse(null)));
        }
    }
}
