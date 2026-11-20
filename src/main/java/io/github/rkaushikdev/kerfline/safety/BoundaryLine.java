package io.github.rkaushikdev.kerfline.safety;

/**
 * The two boundaries on a radiography job: the high radiation line at 100 mR/h, inside which
 * nobody stands, and the restricted area line at 2 mR/h, which is the rope the public is kept
 * behind.
 */
public enum BoundaryLine {
    HIGH_RADIATION(100),
    RESTRICTED(2);

    private final double limitMrPerHour;

    BoundaryLine(double limitMrPerHour) {
        this.limitMrPerHour = limitMrPerHour;
    }

    /** The dose rate, in milliroentgen per hour, at which this line falls. */
    public double limitMrPerHour() {
        return limitMrPerHour;
    }
}
