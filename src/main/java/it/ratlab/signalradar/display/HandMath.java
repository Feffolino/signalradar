// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.display;

/**
 * Pure math behind the first-person hand poses (no Minecraft types, unit tested).
 *
 * <p>Vanilla {@code ItemTransform.apply(leftHand, ps)} already mirrors a display transform when the item is drawn in
 * the left hand: it negates translation x and rotation y and z. A {@code firstperson_lefthand} entry that is itself
 * written as a mirror of the right one is therefore mirrored twice (translation x ends up mirrored, yaw does not), and
 * the screen turns away from the camera. The model JSON must list the same numbers for both hands.
 */
public final class HandMath {
    private HandMath() {}

    /** +1 for the right arm, -1 for the left arm. */
    public static int side(boolean leftHand) {
        return leftHand ? -1 : 1;
    }

    /** Translation x that vanilla actually applies for a display-transform value. */
    public static float effectiveTranslationX(float jsonX, boolean leftHand) {
        return side(leftHand) * jsonX;
    }

    /** Rotation y (degrees) that vanilla actually applies for a display-transform value. */
    public static float effectiveYaw(float jsonYawDeg, boolean leftHand) {
        return side(leftHand) * jsonYawDeg;
    }

    /** Rotation z (degrees) that vanilla actually applies for a display-transform value. */
    public static float effectiveRoll(float jsonRollDeg, boolean leftHand) {
        return side(leftHand) * jsonRollDeg;
    }

    /**
     * Raised pose translation x for an arm, eased by {@code t} from the rest x to the raised x (both positive
     * magnitudes for the right arm; the left arm mirrors the sign).
     */
    public static float raisedX(int side, float restX, float raisedX, float t) {
        return side * (restX + (raisedX - restX) * t);
    }

    /** Raised pose yaw (degrees) for an arm; mirrors the right arm's sign. */
    public static float raisedYaw(int side, float yawDeg, float t) {
        return side * yawDeg * t;
    }
}
