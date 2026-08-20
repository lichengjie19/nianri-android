package com.nianri.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DateWidgetInstancePolicyTest {
    @Test
    public void firstWidgetCanBeConfigured() {
        assertTrue(DateWidgetInstancePolicy.canConfigure(12, new int[]{12}, false));
    }

    @Test
    public void secondWidgetIsRejected() {
        assertFalse(DateWidgetInstancePolicy.canConfigure(13, new int[]{12, 13}, false));
    }

    @Test
    public void existingWidgetCanStillBeReconfigured() {
        assertTrue(DateWidgetInstancePolicy.canConfigure(12, new int[]{12, 13}, true));
    }

    @Test
    public void pinRequestRequiresAnEmptyDesktopInstanceList() {
        assertTrue(DateWidgetInstancePolicy.canRequestPin(new int[0]));
        assertFalse(DateWidgetInstancePolicy.canRequestPin(new int[]{12}));
    }
}
