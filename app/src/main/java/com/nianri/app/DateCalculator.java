package com.nianri.app;

import com.nlf.calendar.Lunar;
import com.nlf.calendar.LunarYear;
import com.nlf.calendar.Solar;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public final class DateCalculator {
    public static final int MIN_YEAR = 1900;
    public static final int MAX_YEAR = 2100;

    private DateCalculator() {
    }

    public static Occurrence occurrence(DateEvent event, LocalDate today) {
        LocalDate date = DateEvent.CALENDAR_LUNAR.equals(event.calendarType)
                ? nextLunarDate(event, today)
                : nextSolarDate(event, today);
        boolean expired = !event.yearly && date.isBefore(today);
        long days = ChronoUnit.DAYS.between(today, date);
        String solarText = String.format(Locale.CHINA, "公历 %d月%d日", date.getMonthValue(), date.getDayOfMonth());
        String lunarText = lunarText(date);
        if (DateEvent.CALENDAR_LUNAR.equals(event.calendarType)) {
            return new Occurrence(date, days, expired, lunarText, solarText);
        }
        return new Occurrence(date, days, expired, solarText, lunarText);
    }

    public static LocalDate nextOccurrenceAfter(DateEvent event, LocalDate after) {
        if (!event.yearly) {
            return occurrence(event, after).solarDate;
        }
        Occurrence current = occurrence(event, after);
        if (current.solarDate.isAfter(after)) {
            return current.solarDate;
        }
        return occurrence(event, current.solarDate.plusDays(1)).solarDate;
    }

    public static void anchorOneTimeDateWithoutYear(DateEvent event, LocalDate today) {
        if (event.yearKnown || event.yearly) {
            return;
        }
        DateEvent recurring = event.copy();
        recurring.yearly = true;
        LocalDate nextDate = occurrence(recurring, today).solarDate;
        if (DateEvent.CALENDAR_LUNAR.equals(event.calendarType)) {
            Lunar lunar = Solar.fromYmd(
                    nextDate.getYear(),
                    nextDate.getMonthValue(),
                    nextDate.getDayOfMonth()
            ).getLunar();
            event.year = lunar.getYear();
        } else {
            event.year = nextDate.getYear();
        }
    }

    private static LocalDate nextSolarDate(DateEvent event, LocalDate today) {
        if (!event.yearly) {
            return safeSolarDate(event.year, event.month, event.day);
        }
        int startYear = today.getYear();
        for (int year = startYear; year <= startYear + 2; year++) {
            LocalDate candidate = safeSolarDate(year, event.month, event.day);
            if (!candidate.isBefore(today)) {
                return candidate;
            }
        }
        return safeSolarDate(startYear + 1, event.month, event.day);
    }

    private static LocalDate nextLunarDate(DateEvent event, LocalDate today) {
        if (!event.yearly) {
            return lunarToSolar(event.year, event.month, event.day, event.leapMonth, true);
        }
        Lunar currentLunar = Solar.fromYmd(today.getYear(), today.getMonthValue(), today.getDayOfMonth()).getLunar();
        int startYear = currentLunar.getYear();
        for (int lunarYear = startYear; lunarYear <= startYear + 3; lunarYear++) {
            LocalDate candidate = lunarToSolar(lunarYear, event.month, event.day, event.leapMonth, false);
            if (!candidate.isBefore(today)) {
                return candidate;
            }
        }
        return lunarToSolar(startYear + 1, event.month, event.day, event.leapMonth, false);
    }

    public static LocalDate lunarToSolar(int year, int month, int day, boolean leapMonth, boolean strictLeap) {
        int actualMonth = month;
        if (leapMonth) {
            int leap = LunarYear.fromYear(year).getLeapMonth();
            if (leap == month) {
                actualMonth = -month;
            } else if (strictLeap) {
                throw new IllegalArgumentException(year + "年没有闰" + month + "月");
            }
        }
        RuntimeException lastError = null;
        for (int candidateDay = Math.min(day, 30); candidateDay >= 1; candidateDay--) {
            try {
                Solar solar = Lunar.fromYmd(year, actualMonth, candidateDay).getSolar();
                return LocalDate.of(solar.getYear(), solar.getMonth(), solar.getDay());
            } catch (RuntimeException error) {
                lastError = error;
            }
        }
        throw new IllegalArgumentException("无效的农历日期", lastError);
    }

    public static boolean isValidLunarDate(int year, int month, int day, boolean leapMonth) {
        if (year < MIN_YEAR || year > MAX_YEAR || month < 1 || month > 12 || day < 1 || day > 30) {
            return false;
        }
        if (leapMonth && LunarYear.fromYear(year).getLeapMonth() != month) {
            return false;
        }
        int actualMonth = leapMonth ? -month : month;
        try {
            Lunar lunar = Lunar.fromYmd(year, actualMonth, day);
            return lunar.getYear() == year && Math.abs(lunar.getMonth()) == month && lunar.getDay() == day;
        } catch (RuntimeException error) {
            return false;
        }
    }

    public static int leapMonthOf(int lunarYear) {
        return LunarYear.fromYear(lunarYear).getLeapMonth();
    }

    public static LocalDate convertCalendar(
            DateEvent event,
            String targetCalendar,
            LocalDate today
    ) {
        if (!DateEvent.CALENDAR_SOLAR.equals(targetCalendar)
                && !DateEvent.CALENDAR_LUNAR.equals(targetCalendar)) {
            throw new IllegalArgumentException("未知的日期历法");
        }
        LocalDate solarDate = representedSolarDate(event, today);
        if (DateEvent.CALENDAR_LUNAR.equals(targetCalendar)) {
            Lunar lunar = Solar.fromYmd(
                    solarDate.getYear(),
                    solarDate.getMonthValue(),
                    solarDate.getDayOfMonth()
            ).getLunar();
            event.calendarType = DateEvent.CALENDAR_LUNAR;
            event.year = lunar.getYear();
            event.month = Math.abs(lunar.getMonth());
            event.day = lunar.getDay();
            event.leapMonth = lunar.getMonth() < 0;
        } else {
            event.calendarType = DateEvent.CALENDAR_SOLAR;
            event.year = solarDate.getYear();
            event.month = solarDate.getMonthValue();
            event.day = solarDate.getDayOfMonth();
            event.leapMonth = false;
        }
        return solarDate;
    }

    private static LocalDate representedSolarDate(DateEvent event, LocalDate today) {
        if (DateEvent.CALENDAR_LUNAR.equals(event.calendarType)) {
            if (event.yearKnown || !event.yearly) {
                return lunarToSolar(
                        event.year,
                        event.month,
                        event.day,
                        event.leapMonth,
                        event.yearKnown
                );
            }
            return nextLunarDate(event, today);
        }
        if (event.yearKnown || !event.yearly) {
            return safeSolarDate(event.year, event.month, event.day);
        }
        return nextSolarDate(event, today);
    }

    public static String lunarText(LocalDate solarDate) {
        Lunar lunar = Solar.fromYmd(
                solarDate.getYear(),
                solarDate.getMonthValue(),
                solarDate.getDayOfMonth()
        ).getLunar();
        String month = lunar.getMonthInChinese();
        if (lunar.getMonth() < 0 && !month.startsWith("闰")) {
            month = "闰" + month;
        }
        return "农历 " + month + "月" + lunar.getDayInChinese();
    }

    public static String fullSolarText(LocalDate date) {
        return String.format(Locale.CHINA, "%d年%d月%d日", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    private static LocalDate safeSolarDate(int year, int month, int day) {
        int safeMonth = Math.max(1, Math.min(12, month));
        int safeDay = Math.max(1, Math.min(31, day));
        while (safeDay >= 1) {
            try {
                return LocalDate.of(year, safeMonth, safeDay);
            } catch (DateTimeException ignored) {
                safeDay--;
            }
        }
        return LocalDate.of(year, safeMonth, 1);
    }
}
