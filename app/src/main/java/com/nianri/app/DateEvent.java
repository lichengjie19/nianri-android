package com.nianri.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class DateEvent {
    public static final String TYPE_BIRTHDAY = "生日";
    public static final String TYPE_ANNIVERSARY = "纪念日";
    public static final String TYPE_OTHER = "其它";

    public static final String CALENDAR_SOLAR = "SOLAR";
    public static final String CALENDAR_LUNAR = "LUNAR";

    public long id;
    public String title = "";
    public String type = TYPE_OTHER;
    public String tagId = TagStore.TAG_OTHER;
    public String calendarType = CALENDAR_SOLAR;
    public int year;
    public boolean yearKnown = true;
    public int month;
    public int day;
    public boolean leapMonth;
    public boolean yearly = true;
    public boolean reminderEnabled = true;
    public boolean followSystemAlert = true;
    public boolean alertSound = true;
    public boolean alertVibration = true;
    public int reminderHour = ReminderTime.DEFAULT_HOUR;
    public final List<Integer> reminderDays = new ArrayList<>();
    public String note = "";
    public String externalId = "";
    public long createdAt;
    public long deletedAt;

    public DateEvent() {
        reminderDays.add(0);
    }

    public DateEvent copy() {
        DateEvent result = new DateEvent();
        result.id = id;
        result.title = title;
        result.type = type;
        result.tagId = tagId;
        result.calendarType = calendarType;
        result.year = year;
        result.yearKnown = yearKnown;
        result.month = month;
        result.day = day;
        result.leapMonth = leapMonth;
        result.yearly = yearly;
        result.reminderEnabled = reminderEnabled;
        result.followSystemAlert = followSystemAlert;
        result.alertSound = alertSound;
        result.alertVibration = alertVibration;
        result.reminderHour = reminderHour;
        result.reminderDays.clear();
        result.reminderDays.addAll(reminderDays);
        result.note = note;
        result.externalId = externalId;
        result.createdAt = createdAt;
        result.deletedAt = deletedAt;
        return result;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("title", title);
        object.put("type", type);
        object.put("tagId", tagId);
        object.put("calendarType", calendarType);
        object.put("year", year);
        object.put("yearKnown", yearKnown);
        object.put("month", month);
        object.put("day", day);
        object.put("leapMonth", leapMonth);
        object.put("yearly", yearly);
        object.put("reminderEnabled", reminderEnabled);
        object.put("followSystemAlert", followSystemAlert);
        object.put("alertSound", alertSound);
        object.put("alertVibration", alertVibration);
        object.put("reminderHour", reminderHour);
        JSONArray reminders = new JSONArray();
        for (int reminder : reminderDays) {
            reminders.put(reminder);
        }
        object.put("reminderDays", reminders);
        object.put("note", note);
        object.put("externalId", externalId);
        object.put("createdAt", createdAt);
        object.put("deletedAt", deletedAt);
        return object;
    }

    public static DateEvent fromJson(JSONObject object) {
        DateEvent result = new DateEvent();
        result.id = object.optLong("id", System.currentTimeMillis());
        result.title = object.optString("title", "重要日期");
        result.type = object.optString("type", TYPE_OTHER);
        result.tagId = object.has("tagId")
                ? object.optString("tagId", TagStore.legacyIdForName(result.type))
                : TagStore.legacyIdForName(result.type);
        if (TagStore.TAG_OTHER.equals(result.tagId) && "其他".equals(result.type)) {
            result.type = TYPE_OTHER;
        }
        result.calendarType = object.optString("calendarType", CALENDAR_SOLAR);
        result.year = object.optInt("year", 2026);
        result.yearKnown = object.has("yearKnown")
                ? object.optBoolean("yearKnown", true)
                : true;
        result.month = object.optInt("month", 1);
        result.day = object.optInt("day", 1);
        result.leapMonth = object.optBoolean("leapMonth", false);
        result.yearly = object.optBoolean("yearly", true);
        result.reminderEnabled = object.has("reminderEnabled")
                ? object.optBoolean("reminderEnabled", true)
                : true;
        result.followSystemAlert = object.has("followSystemAlert")
                ? object.optBoolean("followSystemAlert", true)
                : true;
        result.alertSound = object.has("alertSound")
                ? object.optBoolean("alertSound", true)
                : true;
        result.alertVibration = object.has("alertVibration")
                ? object.optBoolean("alertVibration", true)
                : true;
        result.reminderHour = ReminderTime.normalizeHour(
                object.optInt("reminderHour", ReminderTime.DEFAULT_HOUR)
        );
        result.reminderDays.clear();
        JSONArray reminders = object.optJSONArray("reminderDays");
        if (reminders != null) {
            for (int i = 0; i < reminders.length(); i++) {
                int value = reminders.optInt(i, -1);
                if (value >= 0 && !result.reminderDays.contains(value)) {
                    result.reminderDays.add(value);
                }
            }
        }
        if (result.reminderDays.isEmpty()) {
            result.reminderDays.add(0);
        }
        result.note = object.optString("note", "");
        result.externalId = object.optString("externalId", "");
        result.createdAt = object.optLong("createdAt", result.id);
        result.deletedAt = object.optLong("deletedAt", 0L);
        return result;
    }
}
