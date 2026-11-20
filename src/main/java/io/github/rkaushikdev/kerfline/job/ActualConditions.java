package io.github.rkaushikdev.kerfline.job;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * What actually happened on a shot, for comparing against the contract: the code it was judged on,
 * the fluid service assumed, the lowest density read, the crown measured and the coverage
 * achieved. Anything not recorded is empty and is not compared. Start from {@link #NOTHING} and
 * fill in what is known with the copying setters.
 */
public record ActualConditions(
        Optional<String> code,
        Optional<String> service,
        OptionalDouble densityMinimum,
        OptionalDouble reinforcementIn,
        OptionalDouble coverageFraction) {

    /** Nothing recorded, so nothing to compare. */
    public static final ActualConditions NOTHING = new ActualConditions(
            Optional.empty(), Optional.empty(), OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty());

    public ActualConditions {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(densityMinimum, "densityMinimum");
        Objects.requireNonNull(reinforcementIn, "reinforcementIn");
        Objects.requireNonNull(coverageFraction, "coverageFraction");
    }

    public ActualConditions code(String judgedOn) {
        return new ActualConditions(
                Optional.ofNullable(judgedOn), service, densityMinimum, reinforcementIn, coverageFraction);
    }

    public ActualConditions service(String assumed) {
        return new ActualConditions(
                code, Optional.ofNullable(assumed), densityMinimum, reinforcementIn, coverageFraction);
    }

    public ActualConditions densityMinimum(double lowestRead) {
        return new ActualConditions(code, service, OptionalDouble.of(lowestRead), reinforcementIn, coverageFraction);
    }

    public ActualConditions reinforcementIn(double measured) {
        return new ActualConditions(code, service, densityMinimum, OptionalDouble.of(measured), coverageFraction);
    }

    public ActualConditions coverageFraction(double achieved) {
        return new ActualConditions(code, service, densityMinimum, reinforcementIn, OptionalDouble.of(achieved));
    }
}
