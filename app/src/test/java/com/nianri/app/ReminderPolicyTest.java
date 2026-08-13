package com.nianri.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

public final class ReminderPolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 13);

    @Test
    public void reminderFromTodayIsMarkedMissedAfterGracePeriod() {
        assertTrue(ReminderPolicy.isMissedToday(
                TODAY,
                TODAY,
                1_000L,
                1_000L + ReminderPolicy.ON_TIME_GRACE_MILLIS,
                false
        ));
    }

    @Test
    public void futureReminderIsNotCaughtUpEarly() {
        assertFalse(ReminderPolicy.isMissedToday(
                TODAY,
                TODAY,
                1_000L + ReminderPolicy.ON_TIME_GRACE_MILLIS,
                1_000L,
                false
        ));
    }

    @Test
    public void deliveredReminderIsNotRepeated() {
        assertFalse(ReminderPolicy.isMissedToday(
                TODAY,
                TODAY,
                1_000L,
                1_000L + ReminderPolicy.ON_TIME_GRACE_MILLIS,
                true
        ));
    }

    @Test
    public void reminderFromPreviousDayIsNotShownLate() {
        assertFalse(ReminderPolicy.isMissedToday(
                TODAY.minusDays(1),
                TODAY,
                1_000L,
                2_000L,
                false
        ));
    }

    @Test
    public void deliveryKeyDistinguishesReminderHour() {
        assertEquals("42:20678:0:9", ReminderPolicy.deliveryKey(42L, 20678L, 0, 9));
        assertFalse(
                ReminderPolicy.deliveryKey(42L, 20678L, 0, 9)
                        .equals(ReminderPolicy.deliveryKey(42L, 20678L, 0, 18))
        );
    }

    @Test
    public void dueReminderCanBeDeliveredWhileAppIsOpen() {
        assertTrue(ReminderPolicy.isDueNow(
                TODAY,
                TODAY,
                1_000L,
                2_000L,
                false
        ));
    }

    @Test
    public void dueReminderStopsBeingDeliveredAfterGracePeriod() {
        assertFalse(ReminderPolicy.isDueNow(
                TODAY,
                TODAY,
                1_000L,
                1_000L + ReminderPolicy.ON_TIME_GRACE_MILLIS,
                false
        ));
    }
}
