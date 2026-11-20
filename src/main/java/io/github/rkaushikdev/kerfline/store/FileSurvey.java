package io.github.rkaushikdev.kerfline.store;

import java.util.List;
import java.util.Map;

/**
 * What a journal file holds, read without importing it: how many non blank lines, how many
 * records of each kind, every line that could not be read, and whether there were none.
 */
public record FileSurvey(int lines, Map<String, Integer> kinds, List<LineProblem> problems, boolean readable) {

    public FileSurvey {
        kinds = Map.copyOf(kinds);
        problems = List.copyOf(problems);
    }
}
