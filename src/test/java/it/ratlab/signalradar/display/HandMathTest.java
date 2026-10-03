// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HandMathTest {
    private static final float[] ROT = {10f, -12f, 0f};
    private static final float[] TR = {-2f, 4f, -1f};

    @Test
    void identicalJsonGivesExactMirror() {
        // Same numbers for both hands: effective left = mirror of effective right.
        assertEquals(-HandMath.effectiveTranslationX(TR[0], false), HandMath.effectiveTranslationX(TR[0], true));
        assertEquals(-HandMath.effectiveYaw(ROT[1], false), HandMath.effectiveYaw(ROT[1], true));
        assertEquals(HandMath.effectiveRoll(ROT[2], false), -HandMath.effectiveRoll(ROT[2], true));
    }

    @Test
    void preMirroredLeftJsonIsMirroredTwice() {
        // The old bug: left JSON yaw = +12 (written as a mirror) -> vanilla applies -12, same as the right hand.
        assertEquals(HandMath.effectiveYaw(-12f, false), HandMath.effectiveYaw(12f, true));
    }

    @Test
    void raisedPoseMirrors() {
        for (float t : new float[] {0f, 0.4f, 1f}) {
            assertEquals(-HandMath.raisedX(1, 0.56f, 0.14f, t), HandMath.raisedX(-1, 0.56f, 0.14f, t), 1e-6f);
            assertEquals(-HandMath.raisedYaw(1, 12f, t), HandMath.raisedYaw(-1, 12f, t), 1e-6f);
        }
    }

    @Test
    void raisedYawCancelsDisplayYawInBothHands() {
        for (boolean left : new boolean[] {false, true}) {
            float total = HandMath.raisedYaw(HandMath.side(left), 12f, 1f) + HandMath.effectiveYaw(ROT[1], left);
            assertEquals(0f, total, 1e-6f);
        }
    }
}
