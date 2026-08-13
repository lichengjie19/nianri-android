package com.nianri.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DateEventAlertTest {
    @Test
    public void newDateFollowsSystemSoundAndVibrationByDefault() {
        DateEvent event = new DateEvent();

        assertTrue(event.reminderEnabled);
        assertTrue(event.followSystemAlert);
        assertTrue(event.alertSound);
        assertTrue(event.alertVibration);
    }

    @Test
    public void copyPreservesCustomAlertMode() {
        DateEvent event = new DateEvent();
        event.followSystemAlert = false;
        event.alertSound = false;
        event.alertVibration = true;

        DateEvent copy = event.copy();

        assertFalse(copy.followSystemAlert);
        assertFalse(copy.alertSound);
        assertTrue(copy.alertVibration);
    }
}
