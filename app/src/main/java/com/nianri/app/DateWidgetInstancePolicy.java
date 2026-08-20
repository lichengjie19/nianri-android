package com.nianri.app;

final class DateWidgetInstancePolicy {
    private DateWidgetInstancePolicy() {
    }

    static boolean canConfigure(
            int currentWidgetId,
            int[] activeWidgetIds,
            boolean currentWidgetWasConfigured
    ) {
        if (currentWidgetWasConfigured) {
            return true;
        }
        if (activeWidgetIds == null) {
            return true;
        }
        for (int activeWidgetId : activeWidgetIds) {
            if (activeWidgetId != currentWidgetId) {
                return false;
            }
        }
        return true;
    }

    static boolean canRequestPin(int[] activeWidgetIds) {
        return activeWidgetIds == null || activeWidgetIds.length == 0;
    }
}
