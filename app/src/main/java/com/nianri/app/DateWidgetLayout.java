package com.nianri.app;

final class DateWidgetLayout {
    private DateWidgetLayout() {
    }

    static float countdownTextSizeSp(String number) {
        float maximum = 24f;
        float minimum = 12f;
        float digitStep = 4f;

        if (number == null || number.isEmpty()) {
            return maximum;
        }
        int characterCount = number.codePointCount(0, number.length());
        if (characterCount <= 1) {
            return maximum;
        }
        if (!containsOnlyDigits(number)) {
            return Math.max(minimum, Math.round(maximum * 0.8f));
        }
        int extraDigits = Math.max(0, characterCount - 2);
        return Math.max(minimum, maximum - extraDigits * digitStep);
    }

    private static boolean containsOnlyDigits(String value) {
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isDigit(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

}
