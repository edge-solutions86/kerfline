package io.github.rkaushikdev.kerfline.pipeline;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One film of an interpretation: its number, when it had one, and how it graded. */
public record GradedFilm(Optional<String> film, FilmGrade grade) {

    public GradedFilm {
        Objects.requireNonNull(film, "film");
        Objects.requireNonNull(grade, "grade");
    }

    public boolean acceptable() {
        return grade.acceptable();
    }

    public List<QualityFailure> failures() {
        return grade.failures();
    }
}
