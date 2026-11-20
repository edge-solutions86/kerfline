package io.github.rkaushikdev.kerfline.repair;

/** One welder's repair rate, with both counts it came from. */
public record WelderRate(String welder, int shot, int repaired, double rate) {

    static WelderRate of(String welder, RepairRate rate) {
        return new WelderRate(welder, rate.shot(), rate.repaired(), rate.rate());
    }
}
