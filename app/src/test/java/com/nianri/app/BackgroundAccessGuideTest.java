package com.nianri.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BackgroundAccessGuideTest {
    @Test
    public void huaweiGuideExplainsManualStartupManagement() {
        BackgroundAccessGuide guide = BackgroundAccessGuide.forDevice("HUAWEI", "HUAWEI");

        assertEquals("华为", guide.brandName);
        assertTrue(guide.steps[1].contains("手动管理"));
        assertTrue(guide.steps[2].contains("允许自启动"));
        assertTrue(guide.steps[2].contains("允许后台活动"));
        assertTrue(guide.hasDedicatedSettingsPage());
    }

    @Test
    public void vivoAndIqooShareTheSameGuide() {
        assertEquals(
                "vivo / iQOO",
                BackgroundAccessGuide.forDevice("vivo", "iQOO").brandName
        );
    }

    @Test
    public void unknownBrandFallsBackToApplicationSettings() {
        BackgroundAccessGuide guide = BackgroundAccessGuide.forDevice("unknown", "unknown");

        assertEquals("Android 手机", guide.brandName);
        assertFalse(guide.hasDedicatedSettingsPage());
    }
}
