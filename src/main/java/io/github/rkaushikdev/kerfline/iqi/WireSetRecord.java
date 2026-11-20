package io.github.rkaushikdev.kerfline.iqi;

import java.util.List;

/** A stocked wire set as a technique sheet describes it: its letter, its wires and their diameters. */
public record WireSetRecord(
        String letter, List<Integer> wires, List<Double> diametersIn, double finestIn, double coarsestIn) {

    public WireSetRecord {
        wires = List.copyOf(wires);
        diametersIn = List.copyOf(diametersIn);
    }
}
