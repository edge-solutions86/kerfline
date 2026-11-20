package io.github.rkaushikdev.kerfline.indication;

/**
 * What a reader writes down for one indication: its type, the size measured on the film, and
 * optionally the magnification it was measured at (1 when absent), a measured width, its position
 * along the weld, the film it is on and a note. Start from {@link #of} and adjust with the copying
 * setters; a null component was not recorded.
 */
public record IndicationInput(
        String type, double measuredIn, Double magnification, Double widthIn, Double at, String film, String note) {

    public static IndicationInput of(String type, double measuredIn) {
        return new IndicationInput(type, measuredIn, null, null, null, null, null);
    }

    public IndicationInput magnification(double factor) {
        return new IndicationInput(type, measuredIn, factor, widthIn, at, film, note);
    }

    public IndicationInput widthIn(double width) {
        return new IndicationInput(type, measuredIn, magnification, width, at, film, note);
    }

    public IndicationInput at(double position) {
        return new IndicationInput(type, measuredIn, magnification, widthIn, position, film, note);
    }

    public IndicationInput film(String number) {
        return new IndicationInput(type, measuredIn, magnification, widthIn, at, number, note);
    }

    public IndicationInput note(String text) {
        return new IndicationInput(type, measuredIn, magnification, widthIn, at, film, text);
    }
}
