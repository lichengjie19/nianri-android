package com.nianri.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DateWidgetLayoutTest {
    @Test
    public void twoDigitCountdownKeepsTheFullAPlusPlusSize() {
        assertEquals(
                24f,
                DateWidgetLayout.countdownTextSizeSp("13"),
                0.01f
        );
    }

    @Test
    public void longCountdownShrinksBeforeItCanBeClipped() {
        float shortValue = DateWidgetLayout.countdownTextSizeSp(
                "13"
        );
        float longValue = DateWidgetLayout.countdownTextSizeSp(
                "73000"
        );

        assertTrue(longValue < shortValue);
        assertEquals(12f, longValue, 0.01f);
    }

    @Test
    public void todayWordingUsesAWidthSafeSize() {
        assertEquals(
                19f,
                DateWidgetLayout.countdownTextSizeSp("今天"),
                0.01f
        );
    }
}
