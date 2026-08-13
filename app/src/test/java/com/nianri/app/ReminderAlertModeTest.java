package com.nianri.app;

import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public final class ReminderAlertModeTest {
    @Test
    public void everySoundAndVibrationCombinationUsesItsOwnChannel() {
        String soundAndVibration = ReminderScheduler.alertChannelId(true, true);
        String soundOnly = ReminderScheduler.alertChannelId(true, false);
        String vibrationOnly = ReminderScheduler.alertChannelId(false, true);
        String silent = ReminderScheduler.alertChannelId(false, false);

        assertNotEquals(soundAndVibration, soundOnly);
        assertNotEquals(soundAndVibration, vibrationOnly);
        assertNotEquals(soundAndVibration, silent);
        assertNotEquals(soundOnly, vibrationOnly);
        assertNotEquals(soundOnly, silent);
        assertNotEquals(vibrationOnly, silent);
    }
}
