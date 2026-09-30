// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScanScheduleTest {
    private static final long CHARGE = 100;

    @Test
    void firstSnapshotPaysAndFreeOnesFollowInsideThePeriod() {
        ScanSchedule s = new ScanSchedule();
        assertTrue(s.due(1000, "a"));
        assertTrue(s.shouldPay(1000, CHARGE));
        s.afterPaid(1000, CHARGE, 20, false);
        assertFalse(s.due(1019, "a"));
        assertTrue(s.due(1020, "a"));
        assertFalse(s.shouldPay(1020, CHARGE));
        assertTrue(s.paid());
    }

    @Test
    void aPaidSendNeverSkipsPastTheNextCharge() {
        ScanSchedule s = new ScanSchedule();
        s.due(0, "a");
        s.afterPaid(0, CHARGE, 250, false); // addon interval longer than the charge period
        assertEquals(CHARGE, s.nextSend());
        assertEquals(CHARGE, s.nextCharge());
    }

    @Test
    void failedPaymentWaitsAWholePeriod() {
        ScanSchedule s = new ScanSchedule();
        s.due(0, "a");
        s.afterPaid(0, CHARGE, 20, true);
        assertFalse(s.paid());
        assertEquals(CHARGE, s.nextSend());
    }

    @Test
    void changedKeyRestartsAPaidPeriodAtOnce() {
        ScanSchedule s = new ScanSchedule();
        s.due(0, "a");
        s.afterPaid(0, CHARGE, 20, false);
        s.afterFree(20, 20);
        assertFalse(s.due(21, "a"));
        assertTrue(s.due(21, "b"));
        assertTrue(s.shouldPay(21, CHARGE));
        // same key afterwards does not reset again
        s.afterPaid(21, CHARGE, 20, false);
        assertFalse(s.due(22, "b"));
    }

    @Test
    void gameTimeGoingBackwardsPaysAgain() {
        ScanSchedule s = new ScanSchedule();
        s.due(5000, "a");
        s.afterPaid(5000, CHARGE, 20, false);
        assertTrue(s.shouldPay(10, CHARGE));
    }

    @Test
    void pullForwardOnlyInsideAPaidPeriod() {
        ScanSchedule s = new ScanSchedule();
        s.due(0, "a");
        s.afterPaid(0, CHARGE, 200, false);
        s.pullForward(10, 5);
        assertEquals(15, s.nextSend());
        s.pullForward(10, 50); // never postpones
        assertEquals(15, s.nextSend());
        ScanSchedule u = new ScanSchedule();
        u.due(0, "a");
        u.afterPaid(0, CHARGE, 20, true);
        u.pullForward(10, 5);
        assertEquals(CHARGE, u.nextSend());
    }
}
