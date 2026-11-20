package io.github.rkaushikdev.kerfline.iqi;

/**
 * The indicator chosen for a shot: the side it sits on and the basis thickness it was chosen at,
 * the hole type plaque with its essential hole and that hole's drilled size, the equivalent wire
 * with its diameter and the set a crew draws it from, and the backing thickness the choice left
 * out (nought when no joint description was involved).
 */
public record IqiSelection(
        String side,
        double basisThicknessIn,
        int holeIdentity,
        String essentialHole,
        double essentialHoleIn,
        double plaqueThicknessIn,
        int wireNumber,
        double wireDiameterIn,
        String wireSet,
        double backingIgnoredIn) {

    IqiSelection withBackingIgnoredIn(double backing) {
        return new IqiSelection(
                side,
                basisThicknessIn,
                holeIdentity,
                essentialHole,
                essentialHoleIn,
                plaqueThicknessIn,
                wireNumber,
                wireDiameterIn,
                wireSet,
                backing);
    }
}
