package io.github.rkaushikdev.kerfline.isotope;

import java.util.Locale;

/** What produces the beam: a sealed gamma source or an X-ray tube head. */
public enum SourceKind {
    GAMMA,
    XRAY;

    /** The lower case word the paperwork and the density tables use: {@code gamma} or {@code xray}. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
