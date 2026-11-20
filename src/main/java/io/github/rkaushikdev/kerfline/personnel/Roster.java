package io.github.rkaushikdev.kerfline.personnel;

import io.github.rkaushikdev.kerfline.errors.BadInputException;
import io.github.rkaushikdev.kerfline.errors.Details;
import io.github.rkaushikdev.kerfline.util.Dates;
import io.github.rkaushikdev.kerfline.util.Guard;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Who is on the crew, and whether that crew may work.
 *
 * <p>A radiography crew is never one person. The second body is not there to carry the camera: it
 * is there so that somebody who is not holding the crank is watching the boundary, and so that if
 * something goes wrong there is somebody to go for help who is not the person it went wrong to. So
 * a crew of one is refused here regardless of how well certified that one is.
 *
 * <p>Beyond that the rules are ordinary: at least one certified radiographer, everybody a
 * monitored radiation worker, and the person who signs the film current on the day they sign it.
 */
public final class Roster {

    private static final List<String> ROLES = List.of("radiographer", "assistant", "rso");
    private static final List<String> SENIORITY = List.of("I", "II", "III");
    private static final int MINIMUM_CREW = 2;

    private Roster() {}

    public static List<String> roles() {
        return ROLES;
    }

    public static int minimumCrew() {
        return MINIMUM_CREW;
    }

    public static Person makePerson(PersonInput input) {
        String name = Guard.requireLabel(input.name(), "a crew member has a name");
        if (!ROLES.contains(input.role())) {
            throw new BadInputException(
                    "a role is one of " + String.join(", ", ROLES), Details.of("got", input.role(), "known", ROLES));
        }
        return new Person(name, input.role(), Optional.ofNullable(input.certification()), input.monitored());
    }

    /** Whether one person may read film on a day, with the reason naming them when not. */
    public static InterpretPermission canInterpret(Person person, String day) {
        Dates.requireDay(day, "date");
        if (person.certification().isEmpty()) {
            return InterpretPermission.refused(person.name() + " holds no certification");
        }
        InterpretPermission check = Cert.mayInterpret(person.certification().get(), day);
        return new InterpretPermission(check.allowed(), check.reason().map(reason -> person.name() + ": " + reason));
    }

    public static List<Person> whoCanInterpret(List<Person> roster, String day) {
        Guard.requireArray(roster, "roster");
        return roster.stream()
                .filter(person -> canInterpret(person, day).allowed())
                .toList();
    }

    /**
     * Whether a crew may work on a day, and every reason it may not. All the reasons, because a
     * crew short two things gets both fixed in one trip rather than being turned round twice.
     */
    public static CrewCheck checkCrew(List<Person> crew, String day) {
        Guard.requireArray(crew, "crew");
        Dates.requireDay(day, "date");
        List<String> reasons = new ArrayList<>();

        if (crew.size() < MINIMUM_CREW) {
            reasons.add("a crew is at least " + MINIMUM_CREW + " people and this one is " + crew.size());
        }

        List<Person> readers = crew.stream()
                .filter(person -> canInterpret(person, day).allowed())
                .toList();
        boolean anyRadiographer = crew.stream().anyMatch(person -> person.role().equals("radiographer"));
        if (!anyRadiographer) {
            reasons.add("no radiographer on the crew");
        }
        if (readers.isEmpty()) {
            reasons.add("nobody on the crew may interpret film today");
        }

        List<Person> unmonitored =
                crew.stream().filter(person -> !person.monitored()).toList();
        if (!unmonitored.isEmpty()) {
            reasons.add("not a monitored radiation worker: "
                    + unmonitored.stream().map(Person::name).collect(Collectors.joining(", ")));
        }

        return new CrewCheck(
                day, crew.size(), readers.stream().map(Person::name).toList(), reasons.isEmpty(), reasons);
    }

    /**
     * The person who should sign the film: the most senior current reader, the first of them on
     * the crew when two are equally senior. Empty when nobody may sign.
     */
    public static Optional<Person> signatory(List<Person> crew, String day) {
        List<Person> readers = whoCanInterpret(crew, day);
        Person best = null;
        for (Person person : readers) {
            if (best == null || rank(person) > rank(best)) {
                best = person;
            }
        }
        return Optional.ofNullable(best);
    }

    private static int rank(Person person) {
        return SENIORITY.indexOf(person.certification().orElseThrow().level());
    }

    /** Everybody on the roster whose level permits interpretation at all. */
    public static List<QualifiedLevel> qualifiedLevels(List<Person> roster) {
        Guard.requireArray(roster, "roster");
        return roster.stream()
                .filter(person -> person.certification().isPresent())
                .filter(person -> Cert.requirementsFor(
                                person.certification().get().level())
                        .mayInterpret())
                .map(person -> new QualifiedLevel(
                        person.name(), person.certification().get().level()))
                .toList();
    }
}
