// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

/**
 * Per-player scan schedule (pure logic, no Minecraft classes).
 *
 * <p>A charge period lasts one base refresh ({@code chargeTicks}). The first snapshot of a period pays; snapshots in
 * between (addon refreshes) are free but only while the payment succeeded. A change of the {@code key} (radar tier,
 * addons, held stack) starts a fresh, paid period at once.
 */
public final class ScanSchedule {
    /** Game time of the next snapshot. */
    long nextSend;
    /** Game time of the next base charge. */
    long nextCharge;
    /** The current charge period was paid: snapshots in between are free. */
    boolean paid;
    private Object key;

    /** True when a snapshot is due at {@code now}. A changed key makes it due immediately and forces a payment. */
    public boolean due(long now, Object newKey) {
        if (!newKey.equals(key)) {
            key = newKey;
            nextCharge = 0;
            nextSend = 0;
        }
        return now >= nextSend;
    }

    /** The next snapshot starts a new charge period (also when game time went backwards). */
    public boolean shouldPay(long now, long chargeTicks) {
        return now >= nextCharge || nextCharge - now > chargeTicks;
    }

    public boolean paid() {
        return paid;
    }

    /**
     * Records a snapshot that paid (or tried to).
     *
     * @param sendTicks the interval to the next snapshot if the payment succeeded
     */
    public void afterPaid(long now, long chargeTicks, long sendTicks, boolean noSignal) {
        nextCharge = now + chargeTicks;
        paid = !noSignal;
        // a paid period must not send past the next charge time: that snapshot has to pay
        nextSend = noSignal ? now + chargeTicks : Math.min(now + sendTicks, nextCharge);
    }

    /** Records a free snapshot inside a paid period. */
    public void afterFree(long now, long sendTicks) {
        nextSend = now + sendTicks;
    }

    /**
     * Makes the next (free) snapshot come within {@code delayTicks}. Only inside a paid period: an unpaid period keeps
     * waiting, and a paid one never sends past its next charge anyway, so this never causes an extra charge.
     */
    public void pullForward(long now, long delayTicks) {
        if (paid && nextSend > now + delayTicks) {
            nextSend = now + delayTicks;
        }
    }

    /** Records a NO SIGNAL snapshot inside a period that could not pay. */
    public void afterUnpaid(long now, long chargeTicks) {
        nextSend = now + chargeTicks;
    }

    public long nextSend() {
        return nextSend;
    }

    public long nextCharge() {
        return nextCharge;
    }
}
